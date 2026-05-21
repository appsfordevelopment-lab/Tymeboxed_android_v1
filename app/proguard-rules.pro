# =====================================================================
# Tymeboxed — release ProGuard / R8 rules
# isMinifyEnabled = true, so keep the things reflection/codegen depend on.
# =====================================================================

# ---- General ---------------------------------------------------------
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod,
                Exceptions, SourceFile, LineNumberTable
# Useful for crash reports against stack traces; remove if you want
# fully obfuscated traces and ship a mapping file instead.
-renamesourcefileattribute SourceFile

# Coroutines: internal probes / debug agents look this up by name.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.flow.**inlined**
-dontwarn kotlinx.coroutines.debug.**
-dontwarn kotlinx.coroutines.test.**

# ---- AndroidX / Kotlin metadata --------------------------------------
-dontwarn kotlin.Unit
-dontwarn org.jetbrains.annotations.**
-keep class kotlin.Metadata { *; }

# ---- Compose ---------------------------------------------------------
# Compose runtime/relies on reflection in a few spots. The Compose
# compiler emits @Composer with required attributes, keep them.
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ---- Hilt / Dagger ---------------------------------------------------
# Hilt generates classes that reflectively reference @Inject targets,
# Hilt-defined modules (incl. @InstallIn), and @ViewModelInject /
# @HiltViewModel constructors. Plus the EntryPoint interfaces we hit via
# EntryPointAccessors in TymeBoxedDatabase.getInstance.
-keep class dagger.hilt.** { *; }
-keep class dagger.internal.** { *; }
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep,allowobfuscation @interface dagger.hilt.android.HiltAndroidApp
-keep,allowobfuscation @interface dagger.hilt.EntryPoint
-keep,allowobfuscation @interface dagger.hilt.InstallIn
-keep,allowobfuscation @interface dagger.Module
-keep,allowobfuscation @interface dagger.Provides
-keep,allowobfuscation @interface dagger.Binds
-keep,allowobfuscation @interface dagger.hilt.android.lifecycle.HiltViewModel
-keepclassmembers,allowobfuscation class * {
    @javax.inject.Inject <init>(...);
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
    @dagger.Provides <methods>;
    @dagger.Binds <methods>;
}
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.EntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.Module class * { *; }
# Hilt-generated classes (Module_..._Factory, _HiltModules, Hilt_*) are
# referenced reflectively at runtime by the generated component graph.
-keep class **_HiltModules { *; }
-keep class **_HiltModules$* { *; }
-keep class **_Factory { *; }
-keep class **_Provide*Factory { *; }
-keep class **Hilt_*

# ---- Room ------------------------------------------------------------
# Entities, DAOs, and the generated *_Impl classes need to be kept.
-keep class dev.ambitionsoftware.tymeboxed.data.db.entities.** { *; }
-keep class dev.ambitionsoftware.tymeboxed.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep class **_Impl { *; }
-dontwarn androidx.room.paging.**

# ---- Gson ------------------------------------------------------------
# Gson uses reflection on model classes; keep the type-token helper
# subclasses, all serialized field names, and any data classes used
# for serialization.
-keepattributes AnnotationDefault
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Keep domain models that travel through Gson (broadly safe; refine if needed).
-keep class dev.ambitionsoftware.tymeboxed.domain.model.** { *; }
-keep class dev.ambitionsoftware.tymeboxed.data.repository.** { *; }

# ---- DataStore / Protobuf-lite (used transitively) -------------------
# DataStore-preferences reflectively serializes the wire model and
# transitively pulls in protobuf-javalite. R8 trims symbols protobuf-lite
# expects; keep the runtime façade + extension fields.
-keep class androidx.datastore.** { *; }
-keep class androidx.datastore.core.** { *; }
-keep class androidx.datastore.preferences.** { *; }
-keep class androidx.datastore.preferences.protobuf.** { *; }
-keep class androidx.datastore.preferences.core.PreferenceDataStoreFactory { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-dontwarn com.google.protobuf.**
-dontwarn androidx.datastore.**

# ---- WorkManager -----------------------------------------------------
# Workers are instantiated by name; keep their constructors.
-keep public class * extends androidx.work.Worker
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---- Google Sign-In / play-services-auth -----------------------------
# Google ID-token sign-in reflects over GoogleSignInOptions / Account
# parcelables. Keep the auth API surface and the Tasks API.
-keep class com.google.android.gms.auth.api.signin.** { *; }
-keep class com.google.android.gms.auth.api.identity.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.android.gms.**

# ---- AuthRepository network DTOs ------------------------------------
# Gson deserializes responses from /api/auth/* and /api/nfc/verify into
# private data classes inside AuthRepository.kt. Without these keep rules
# R8 strips the backing fields and the envelopes silently become all-null.
-keep class dev.ambitionsoftware.tymeboxed.data.repository.AuthRepository$* { *; }
-keep class dev.ambitionsoftware.tymeboxed.nfc.NfcTagVerifyResult { *; }
-keep class dev.ambitionsoftware.tymeboxed.nfc.NfcTagVerifyResult$* { *; }
# Keep our internal NFC helpers (used by the AccessibilityService).
-keep class dev.ambitionsoftware.tymeboxed.data.prefs.** { *; }

# ---- Accompanist permissions ----------------------------------------
-dontwarn com.google.accompanist.**

# ---- Coil ------------------------------------------------------------
-dontwarn coil.**
-dontwarn okio.**
-dontwarn okhttp3.**

# ---- App entry points used reflectively by the framework -------------
# BroadcastReceivers / Services / Activities declared in AndroidManifest
# are already kept by AAPT; but accessibility / blocking services may
# reference helper inner classes via reflection in some flavors.
-keep class dev.ambitionsoftware.tymeboxed.service.** { *; }
-keep class dev.ambitionsoftware.tymeboxed.nfc.** { *; }

# ---- Strip Log.v / Log.d in release (size + perf + leak avoidance) ---
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
