# Keep rules applied ONLY to the `minified` build type — the debug-signed twin of release that
# exists so the instrumented suite can run against R8 output. The shipping `release` build never
# reads this file, and test code is never compiled into the app APK in the first place
# (androidTest builds a separate APK), so none of this affects what users install.
#
# Why it is needed: AGP does not feed androidTest usages into the app's R8 run
# (https://issuetracker.google.com/issues/126429384 — the same gap Slack's Keeper plugin exists
# to close). The test APK omits every class the app under test already provides, so anything R8
# shrank out of the app is simply missing at runtime and the run dies before any test executes:
# androidx.tracing.Trace, kotlin.LazyKt, MonotonicFrameClock$DefaultImpls,
# androidx.lifecycle.ViewTreeLifecycleOwner, and so on.
#
# The trade-off is deliberate: library code stays intact here, while the app's own code —
# acidburn.stims.** — is still shrunk, optimised and obfuscated exactly as in release, and
# resource shrinking still runs. That is the part these tests exist to verify.

-keep class androidx.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class com.google.android.material.** { *; }
-dontwarn androidx.**
-dontwarn kotlin.**
