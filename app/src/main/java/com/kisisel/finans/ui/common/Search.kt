package com.kisisel.finans.ui.common

import java.util.Locale

private val TR = Locale("tr", "TR")

/** Büyük/küçük harf ve Türkçe i/ı farkına toleranslı "içerir" araması. Boş sorgu her şeyle eşleşir. */
fun String.containsSearch(query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return lowercase(TR).contains(q.lowercase(TR)) || lowercase(Locale.ROOT).contains(q.lowercase(Locale.ROOT))
}
