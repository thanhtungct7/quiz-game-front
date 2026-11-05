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
# =========================================================================
# QUY TẮC R8 / PROGUARD TỐI ƯU HÓA HIỆU NĂNG CHO BẢN OPTIMIZED
# =========================================================================

# 1. Giữ an toàn cho các DTO Serialization tránh bị đổi tên trường JSON
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class com.kma.quiz_game.data.remote.dto.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }

# 2. Giữ an toàn cho thư viện đồ họa native libGDX (C++ JNI và ApplicationListener)
-keep class com.badlogic.gdx.backends.android.** { *; }
-keep class * extends com.badlogic.gdx.ApplicationListener { *; }
-keepclassmembers class com.badlogic.gdx.** {
    native <methods>;
}

# 3. Tối ưu hóa thu nhỏ bytecode và loại bỏ các lời gọi ghi log rác trong release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
