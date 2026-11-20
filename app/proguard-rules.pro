# Room / Hilt kendi kurallarını getirir.
# SQLCipher JNI üzerinden sınıf/metot adlarına erişir; küçültmeden korunmalı.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.** { *; }

# Yalnızca derleme zamanı açıklama kütüphanelerinden gelen eksik-sınıf uyarıları R8'i durdurmasın.
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn org.jetbrains.annotations.**

# Enum değerleri adla (valueOf/name) saklanıp okunuyor (Room, yedek dosyası): adlar korunmalı.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Compose ---
# Compose runtime reflection kullanır; küçültme Compose UI'ı bozabilir.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material3.** { *; }
-dontwarn androidx.compose.**

# --- DataStore ---
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# --- WorkManager ---
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-dontwarn androidx.work.**

# --- Kotlin Coroutines ---
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# --- Hilt / Dagger ---
# Hilt kendi consumer kurallarını getirir ama ek güvence:
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# --- Kotlin metadata ---
# Kotlin reflection / serialization kullanılıyorsa meta veriler korunmalı.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations