# (v0.72.x: the LlamaEngine JNI surface is gone — the
#  on-device Phi-4 / llama.cpp path was removed when the
#  cloud LLM became the only letter writer. The native
#  JNI keep is kept here as a no-op rather than a delete
#  so a future regression that re-introduces LlamaEngine
#  doesn't have to re-derive the keep rule from a stack
#  trace. If you have re-introduced it and the keep is
#  still required, uncomment the block below.)
#
# -keepclasseswithmembernames,includedescriptorclasses class org.mindanchor.narrate.LlamaEngine {
#     native <methods>;
# }

# kotlinx-serialization: keep the generated $$serializer companions
# and the @Serializable classes. The plugin emits the serializer for
# every annotated class; minification breaks the reflective lookup.
# Without this, every BackupCodec encode/decode and every GroqClient
# response decode will hit MissingFieldException at runtime.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp + Okio — the platform log detection and DNS reflection
# cross classloader boundaries and break under R8 without these.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Tink (transitive via androidx.security:security-crypto). The
# credential store and encrypted prefs read the registry reflectively.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# kotlinx-coroutines internals used by the CameraX await() bridge.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
-dontwarn kotlinx.coroutines.flow.**

# Room (transitive) keeps its consumer rules but the @TypeConverters
# under org.mindanchor.data.* are referenced by the generated DAO impls;
# redundant keeps don't hurt and protect against the rare rename.
-keep class org.mindanchor.data.** { *; }

# Auto-update (v0.25.9+): Retrofit/OkHttp on the GitHub releases check
# path needs the same OkHttp keeps as the rest of the app.
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# AppUpdateChecker (v0.25.9+): the GitHub releases JSON DTO is a
# @Serializable data class; the $$serializer is needed.
-keep class org.mindanchor.update.** { *; }
