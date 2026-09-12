# R8 keep rules for Stims.
#
# Deliberately almost empty. MainActivity, StimsService and BootReceiver are all declared in
# AndroidManifest.xml, and AAPT2 generates keep rules for manifest-declared components
# automatically. Compose, AndroidX and Material all ship their own consumer rules inside their
# AARs. The app itself does no reflection, JSON binding or dynamic class loading, so any manual
# -keep here would only shield code from R8 for no reason.

# Keep line numbers so release stack traces stay readable, while still obfuscating the file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
