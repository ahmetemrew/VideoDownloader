# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# yt-dlp initializes its bundled Python runtime by unzipping assets via
# Apache Commons Compress. That package registers ZIP extra field handlers
# reflectively, and shrinking can turn those classes into invalid targets
# for that registration in release builds.
-keep class org.apache.commons.compress.archivers.zip.** { *; }

# yt-dlp parses JSON responses with Jackson into these model classes.
# Keep them stable for reflective field access in release builds.
-keep class com.yausername.youtubedl_android.mapper.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.JsonProperty <fields>;
    @com.fasterxml.jackson.annotation.JsonProperty <methods>;
}
