package com.kisisel.finans.reminder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.reminderDataStore: DataStore<Preferences> by preferencesDataStore(name = "reminders")

@Singleton
class DataStoreReminderLog @Inject constructor(
    @ApplicationContext private val context: Context
) : ReminderLog {

    private val sentKey = stringSetPreferencesKey("sent_reminder_keys")

    override suspend fun sentKeys(): Set<String> =
        context.reminderDataStore.data.first()[sentKey].orEmpty()

    override suspend fun record(newKeys: Set<String>, activeOccurrenceIds: Set<Long>) {
        context.reminderDataStore.edit { prefs ->
            val kept = prefs[sentKey].orEmpty().filter { occurrenceIdOfKey(it) in activeOccurrenceIds }
            prefs[sentKey] = kept.toSet() + newKeys
        }
    }
}
