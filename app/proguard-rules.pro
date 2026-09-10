# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Retrofit: keep generic signatures and annotation metadata for reflective proxies
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Retrofit 2.9 lacks the full-mode generic keeps shipped by newer Retrofit.
# Signature alone is insufficient: suspend methods reflect Continuation<Response<T>>.
# Preserve generic definitions without retaining every implementation member.
-keep,allowoptimization,allowshrinking,allowobfuscation class kotlin.coroutines.Continuation
-keep,allowoptimization,allowshrinking,allowobfuscation class retrofit2.Response
-keep,allowoptimization,allowshrinking,allowobfuscation interface retrofit2.Call

# Retrofit response bodies are converted reflectively by Gson
-keep class com.nordic.mediahub.api.** { *; }
-keep class com.nordic.mediahub.data.** { *; }

# Gson generic type tokens
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.errorprone.annotations.**

# OkHttp platform warnings
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Coil
-dontwarn coil.**

# security-crypto (EncryptedSharedPreferences) reflects into Tink
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
# Tink's optional KeysDownloader references joda-time; the code path is unused.
-dontwarn org.joda.time.Instant
