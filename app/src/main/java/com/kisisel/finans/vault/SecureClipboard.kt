package com.kisisel.finans.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/**
 * Hassas kopyalama: pano içeriği "hassas" işaretlenir (Android 13+ pano önizlemesinde gizlenir) ve
 * [CLEAR_AFTER_MS] sonra, hâlâ bizim kopyaladığımız değerse otomatik temizlenir.
 * Uygulama süreci arada sonlandırılırsa otomatik temizleme çalışmaz: panoyu elle temizlemek gerekir.
 */
object SecureClipboard {
    const val CLEAR_AFTER_MS = 30_000L
    private const val SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun copy(context: Context, label: String, text: String, clearAfterMs: Long = CLEAR_AFTER_MS) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply { putBoolean(SENSITIVE_KEY, true) }
        cm.setPrimaryClip(clip)

        pending?.let { handler.removeCallbacks(it) }
        val task = Runnable { clearIfStillOurs(cm, text) }
        pending = task
        handler.postDelayed(task, clearAfterMs)
    }

    private fun clearIfStillOurs(cm: ClipboardManager, text: String) {
        // Arka planda pano okunamayabilir (null): bizim değerimiz olduğunu varsayıp temizleriz.
        val current = runCatching { cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString() }.getOrNull()
        if (current != null && current != text) return
        if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}
