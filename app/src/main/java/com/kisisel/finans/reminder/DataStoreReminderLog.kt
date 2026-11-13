package com.kisisel.finans.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(name = "reminders")
private val Context.reminderPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "reminder_prefs")

@Singleton
class DataStoreReminderLog @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderLog {

    private val sentKey = stringSetPreferencesKey("sent_reminder_keys")

    override suspend fun sentKeys(): Set<String> =
        context.reminderDataStore.data.first()[sentKey].orEmpty()

    override suspend fun add(keys: Set<String>) {
        if (keys.isEmpty()) return
        context.reminderDataStore.edit { prefs -> prefs[sentKey] = prefs[sentKey].orEmpty() + keys }
    }

    override suspend fun retain(activeOwners: Set<String>) {
        context.reminderDataStore.edit { prefs ->
            val current = prefs[sentKey].orEmpty()
            val kept = current.filter { ownerOfKey(it) in activeOwners }.toSet()
            if (kept.size != current.size) prefs[sentKey] = kept
        }
    }
}

@Singleton
class DataStoreReminderPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderPrefs {

    private val claimKey = booleanPreferencesKey("claim_reminders_enabled")

    override val claimRemindersFlow: Flow<Boolean> =
        context.reminderPrefsDataStore.data.map { it[claimKey] ?: true }

    override suspend fun claimRemindersEnabled(): Boolean = claimRemindersFlow.first()

    override suspend fun setClaimRemindersEnabled(enabled: Boolean) {
        context.reminderPrefsDataStore.edit { it[claimKey] = enabled }
    }
}
