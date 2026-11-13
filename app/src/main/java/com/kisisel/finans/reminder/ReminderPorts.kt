package com.kisisel.finans.reminder

import kotlinx.coroutines.flow.Flow

/** Gönderilmiş hatırlatma anahtarlarının kaydı (çift bildirimi önler). */
interface ReminderLog {
    suspend fun sentKeys(): Set<String>

    /** Yeni gönderilen anahtarları ekler. */
    suspend fun add(keys: Set<String>)

    /** Sahibi artık etkin olmayan (ödenmiş/iptal/silinmiş/tamamlanmış) kayıtların eski anahtarlarını temizler. */
    suspend fun retain(activeOwners: Set<String>)
}

/** Bildirim gönderici. İzin/ayar nedeniyle gösterilemezse false döner (kayıt tutulmaz, sonra tekrar denenir). */
interface ReminderNotifier {
    fun show(reminder: PlannedReminder): Boolean
}

/** Bir sonraki ajanda hatırlatması için tek seferlik kontrol planlar; null = planlı kontrolü iptal eder. */
interface AgendaScheduling {
    fun scheduleAt(delayMillis: Long?)
}

/** Hatırlatma tercihleri. */
interface ReminderPrefs {
    /** Borç/alacak vade hatırlatmaları (varsayılan açık). */
    val claimRemindersFlow: Flow<Boolean>

    suspend fun claimRemindersEnabled(): Boolean
    suspend fun setClaimRemindersEnabled(enabled: Boolean)
}
