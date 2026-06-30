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

# Keep FlowPay specific classes
-keep class com.flowpay.app.** { *; }

# Keep data classes
-keep class * extends com.flowpay.app.data.** { *; }
-keep class * extends com.flowpay.app.models.** { *; }

# Keep managers and services
-keep class com.flowpay.app.managers.** { *; }
-keep class com.flowpay.app.services.** { *; }

# Keep UI components
-keep class com.flowpay.app.ui.** { *; }

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep custom exceptions
-keep public class * extends java.lang.Exception

# Keep R class
-keep class **.R
-keep class **.R$* {
    <fields>;
}

# Keep annotation classes
-keep class * extends java.lang.annotation.Annotation { *; }

# Keep reflection classes
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Gson classes
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep CameraX classes
-keep class androidx.camera.** { *; }

# Keep Compose classes
-keep class androidx.compose.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Optional dependencies not on classpath (OkHttp/Meta SDK optional platforms)
-dontwarn com.facebook.common.preconditions.Preconditions
-dontwarn com.facebook.infer.annotation.**
-dontwarn com.facebook.secure.sanitizer.intf.DataSanitizer
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
