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
