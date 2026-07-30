package acidburn.stims

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import acidburn.stims.ui.theme.StimsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable? = null
)

class MainActivity : ComponentActivity() {
    private lateinit var prefs: SharedPreferences
    private var showOverlayWarning by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(StimsService.PREFS_NAME, Context.MODE_PRIVATE)

        setContent {
            StimsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppListScreen(
                        prefs = prefs,
                        showOverlayWarning = showOverlayWarning,
                        onOpenOverlaySettings = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.fromParts("package", packageName, null)
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasUsageStatsPermission(this)) {
            Toast.makeText(this, "Please enable Usage Stats permission", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        val needsOverlay = StimsService.OVERLAY_VENDORS.any {
            Build.MANUFACTURER.equals(it, ignoreCase = true)
        }
        showOverlayWarning = needsOverlay && !Settings.canDrawOverlays(this)
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    prefs: SharedPreferences,
    showOverlayWarning: Boolean,
    onOpenOverlaySettings: () -> Unit,
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val stimmedPackages = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet(StimsService.KEY_STIMMED_APPS, emptySet()) ?: emptySet())
        }
    }

    val tempSelected = remember { mutableStateListOf<String>() }

    var forceOverlay by remember { mutableStateOf(prefs.getBoolean(StimsService.KEY_FORCE_OVERLAY, false)) }

    LaunchedEffect(stimmedPackages.toList(), forceOverlay) {
        prefs.edit().putStringSet(StimsService.KEY_STIMMED_APPS, stimmedPackages.toSet()).apply()

        val intent = Intent(context, StimsService::class.java).apply {
            putStringArrayListExtra(StimsService.EXTRA_SELECTED_PACKAGES, ArrayList(stimmedPackages))
            putExtra(StimsService.EXTRA_FORCE_OVERLAY, forceOverlay)
        }

        if (stimmedPackages.isEmpty()) {
            context.stopService(intent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = packageManager.queryIntentActivities(intent, 0)

            resolveInfos.map { resolveInfo ->
                AppInfo(
                    name = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }.distinctBy { it.packageName }
             .sortedBy { it.name }
        }
        isLoading = false
    }

    var searchQuery by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    val stimList = remember(stimmedPackages.toList(), allApps) {
        allApps.filter { it.packageName in stimmedPackages }
    }

    val availableApps = remember(searchQuery, stimmedPackages.toList(), allApps) {
        allApps.filter {
            it.packageName !in stimmedPackages &&
            (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true))
        }
    }

    if (showSettings) {
        SettingsScreen(
            forceOverlay = forceOverlay,
            onForceOverlayChange = { checked ->
                forceOverlay = checked
                prefs.edit().putBoolean(StimsService.KEY_FORCE_OVERLAY, checked).apply()
            },
            onOpenOverlaySettings = onOpenOverlaySettings,
            onBack = { showSettings = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stims Manager", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(padding)) {
                if (showOverlayWarning) {
                    OverlayWarningBanner(onClick = onOpenOverlaySettings)
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Search apps to stim...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (stimList.isNotEmpty()) {
                        item { SectionHeader("STAY AWAKE (STIMMED)") }
                        items(stimList) { app ->
                            StimmedAppItem(app = app) {
                                stimmedPackages.remove(app.packageName)
                            }
                        }
                    }

                    item { SectionHeader("ALL APPS") }
                    items(availableApps) { app ->
                        SelectableAppItem(
                            app = app,
                            isSelected = app.packageName in tempSelected,
                            onToggle = {
                                if (app.packageName in tempSelected) tempSelected.remove(app.packageName)
                                else tempSelected.add(app.packageName)
                            }
                        )
                    }
                }

                Button(
                    onClick = {
                        stimmedPackages.addAll(tempSelected)
                        tempSelected.clear()
                        Toast.makeText(context, "Apps Stimmed!", Toast.LENGTH_SHORT).show()
                    },
                    enabled = tempSelected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("STIM SELECTED APPS", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    forceOverlay: Boolean,
    onForceOverlayChange: (Boolean) -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val canDrawOverlays = Settings.canDrawOverlays(context)
    val isVendorDevice = StimsService.OVERLAY_VENDORS.any {
        Build.MANUFACTURER.equals(it, ignoreCase = true)
    }
    val effectiveOverlay = forceOverlay || isVendorDevice
    val overlayActive = effectiveOverlay && canDrawOverlays

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {

            SettingsSectionHeader("DISPLAY")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Force awake overlay", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = when {
                            overlayActive && isVendorDevice -> "Active — required on this device"
                            overlayActive                   -> "Active — screen kept on via overlay"
                            effectiveOverlay                -> "Enabled — overlay permission required"
                            else                            -> "Inactive"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            overlayActive    -> Color(0xFF2E7D32)
                            effectiveOverlay -> MaterialTheme.colorScheme.error
                            else             -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Switch(
                    checked = effectiveOverlay,
                    enabled = !isVendorDevice,
                    onCheckedChange = { checked ->
                        onForceOverlayChange(checked)
                        if (checked && !canDrawOverlays) {
                            onOpenOverlaySettings()
                        }
                    }
                )
            }

            HorizontalDivider()

            SettingsSectionHeader("ABOUT")

            SettingsInfoRow(label = "Version", value = BuildConfig.VERSION_NAME)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            SettingsInfoRow(label = "Author", value = "acidburnmonkey")
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/acidburnmonkey/stims"))
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GitHub",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open GitHub",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.secondary,
            letterSpacing = 1.2.sp
        ),
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun StimmedAppItem(app: AppInfo, onRemove: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val cardColor = if (dark) Color(0xFF1B3A2B) else Color(0xFFE8F5E9)
    val nameColor = if (dark) Color(0xFFE8F5E9) else Color(0xFF1B5E20)
    val stimmedColor = if (dark) Color(0xFFFFB74D) else Color(0xFFEF6C00)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app.icon)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = nameColor)
                Text(text = "STIMMED", style = MaterialTheme.typography.labelSmall, color = stimmedColor)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = if (dark) Color(0xFFB0BEC5) else Color.Gray)
            }
        }
    }
}

@Composable
fun SelectableAppItem(app: AppInfo, isSelected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.icon)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = app.packageName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
fun AppIcon(icon: Drawable?) {
    if (icon != null) {
        Image(
            bitmap = icon.toBitmap().asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(44.dp)
        )
    } else {
        Surface(
            modifier = Modifier.size(44.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small
        ) {}
    }
}

@Composable
fun OverlayWarningBanner(onClick: () -> Unit) {
    val warningColor = Color(0xFFEA580C)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(width = 1.5.dp, color = warningColor, shape = MaterialTheme.shapes.small)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Visibility,
            contentDescription = null,
            tint = warningColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Overlay permission required (Samsung)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E40AF)
            )
            Text(
                text = "Tap → Allow display over other apps",
                style = MaterialTheme.typography.bodySmall,
                color = warningColor
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = warningColor,
            modifier = Modifier.size(16.dp)
        )
    }
}
