-dontobfuscate
-dontoptimize

# Loaded by name from assets/xposed_init
-keep class com.ceco.r.gravitybox.GravityBox { *; }
-keep class * implements de.robv.android.xposed.IXposedMod { *; }

# Hook classes and their members are reached reflectively from the hooked processes
-keep class com.ceco.r.gravitybox.** { *; }
-keepclassmembers class * extends de.robv.android.xposed.XC_MethodHook { *; }
-dontwarn de.robv.android.xposed.**
