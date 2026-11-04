# MANHNT R8 / ProGuard rules (release builds: minify + shrinkResources enabled)

# --- Crash reports stay readable (upload mapping.txt with each release) ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Reflection-driven serialization needs generics + annotations intact ---
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,Exceptions

# --- Firebase REST wire models (Gson reflects over field names; enum names are JSON values) ---
-keep class com.manhunt.app.data.model.** { *; }
-keepclassmembers class com.manhunt.app.data.model.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# --- Retrofit service interface (method annotations + generic return types) ---
-keep interface com.manhunt.app.network.FirebaseRestApi { *; }
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# --- Networking stack: optional platform providers are referenced but not bundled ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- OSMDroid (tile providers and overlays are looked up reflectively) ---
-dontwarn org.osmdroid.**
-keep class org.osmdroid.** { *; }
