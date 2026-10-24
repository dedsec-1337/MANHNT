# MANHNT ProGuard rules
# Keep Firebase REST / Gson models
-keepclassmembers class com.manhunt.app.data.model.** { *; }
-keep class com.manhunt.app.data.model.** { *; }

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes *Annotation*

# OSMDroid
-dontwarn org.osmdroid.**
