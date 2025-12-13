# ContentSync ProGuard Rules

# Keep Firebase classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep ExoPlayer classes
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep Socket.IO classes
-keep class io.socket.** { *; }
-keep class org.json.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes
-keep class com.withyou.app.network.** { *; }
-keep class com.withyou.app.utils.FileMetadata { *; }
-keep class com.withyou.app.utils.CodecInfo { *; }

# Kotlin
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
