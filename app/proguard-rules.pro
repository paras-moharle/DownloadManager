# ══════════════════════════════════════════════════════
# App classes — Protect Xposed and Android Entry Points
# ══════════════════════════════════════════════════════
-keep class com.downloadmanager.Hook { *; }
-keep class com.downloadmanager.ForwardActivity { *; }
-keep class com.downloadmanager.ModeProvider { *; }
-keep class com.downloadmanager.MainActivity { *; }

# Keep data models used by intents/reflection
-keep class com.downloadmanager.ForwardActivity$DmTarget { *; }
-keep class com.downloadmanager.MainActivity$DmInfo { *; }

# ══════════════════════════════════════════════════════
# Xposed API (Do not touch)
# ══════════════════════════════════════════════════════
-keep class de.robv.android.xposed.** { *; }
-keepclassmembers class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# ══════════════════════════════════════════════════════
# AndroidX + Material (Let ProGuard shrink them!)
# ══════════════════════════════════════════════════════
-dontwarn androidx.**
-dontwarn com.google.android.material.**

# ══════════════════════════════════════════════════════
# ContentProvider & Chromium Safety
# ══════════════════════════════════════════════════════
-keep class * extends android.content.ContentProvider { *; }
-dontwarn org.chromium.**
-dontwarn com.brave.**

# ══════════════════════════════════════════════════════
# Standard Obfuscation Safety
# ══════════════════════════════════════════════════════
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable