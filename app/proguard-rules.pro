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

# Keep line numbers so release crash traces map back through mapping.txt,
# and hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# SQLCipher's native side resolves these classes via JNI by name; the @aar
# dependency ships no consumer rules.
-keep class net.zetetic.database.** { *; }

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
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

# CameraX, Compose, and Coroutines deliberately have NO blanket keep here.
# Every reflective anchor those libraries need is already shipped in their
# own consumer proguard rules (Room's `-keep class * extends RoomDatabase`,
# camera-camera2's `Camera2Config$DefaultProvider` keep, coroutines' volatile
# field-updater keep) or generated from the manifest/layouts by aapt
# (PreviewView's two-arg constructor, MetadataHolderService). A blanket keep
# on androidx.compose.** alone forced the entire material-icons-extended set
# (~5,000 classes) into the APK against 30 icons actually referenced —
# removing these three keeps took the release APK from 62 MB to 28 MB.
# Verified: app code has no Class.forName/getDeclaredMethod/ServiceLoader/
# kotlin.reflect, and the two @Parcelize classes never cross an Intent.

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# error_prone annotations, pulled in transitively by material's compile-time
# dependency graph, reference javax.lang.model.* compiler-only types that
# don't exist on Android at runtime — annotations are erased, so this is
# harmless. (ML Kit's AAR used to ship a consumer rule covering this; it
# went away with the ML Kit -> ZXing swap.)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.lang.model.**
