# PlayBox TV — conservative R8 / ProGuard rules.
# Keep everything in our own code, plus the libraries R8 must not strip.
-keep class com.playbox.tv.** { *; }
-keep class androidx.media3.** { *; }
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.documentfile.** { *; }
-dontwarn androidx.media3.**
-dontwarn androidx.compose.**
