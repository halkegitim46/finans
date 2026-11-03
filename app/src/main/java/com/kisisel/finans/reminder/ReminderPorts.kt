package com.kisisel.finans.reminder

/** Gönderilmiş hatırlatma anahtarlarının kaydı (çift bildirimi önler). */
interface ReminderLog {
    suspend fun sentKeys(): Set<String>

    /** Yeni anahtarları ekler; artık aktif olmayan dönemlerin eski anahtarlarını temizler. */
    suspend fun record(newKeys: Set<String>, activeOccurrenceIds: Set<Long>)
}

/** Bildirim gönderici. İzin/ayar nedeniyle gösterilemezse false döner (kayıt tutulmaz, sonra tekrar denenir). */
interface ReminderNotifier {
    fun show(reminder: PlannedReminder): Boolean
}
