# Room / Hilt kendi kurallarını getirir.
# SQLCipher JNI üzerinden sınıf/metot adlarına erişir; küçültmeden korunmalı.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.** { *; }
