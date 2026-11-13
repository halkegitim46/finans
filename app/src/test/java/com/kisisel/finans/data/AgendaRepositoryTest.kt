package com.kisisel.finans.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kisisel.finans.backup.BackupCodec
import com.kisisel.finans.backup.BackupSnapshotValidator
import com.kisisel.finans.data.local.DatabaseSnapshotter
import com.kisisel.finans.data.local.KisiselFinansDatabase
import com.kisisel.finans.data.repository.AgendaRepositoryImpl
import com.kisisel.finans.data.repository.PersonRepositoryImpl
import com.kisisel.finans.domain.FinanceException
import com.kisisel.finans.domain.model.AgendaUpdate
import com.kisisel.finans.domain.model.NewAgendaItem
import com.kisisel.finans.domain.model.NewPerson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class AgendaRepositoryTest {
    private lateinit var db: KisiselFinansDatabase
    private lateinit var agenda: AgendaRepositoryImpl
    private lateinit var persons: PersonRepositoryImpl
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-04T09:00:00Z"), ZoneId.of("Europe/Istanbul"))
    private val day: LocalDate = LocalDate.of(2026, 11, 4)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KisiselFinansDatabase::class.java).allowMainThreadQueries().build()
        agenda = AgendaRepositoryImpl(db, clock)
        persons = PersonRepositoryImpl(db.personDao(), clock)
    }

    @After fun tearDown() {
        db.close()
    }

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private fun item(title: String = "Ahmet'i ara", personId: Long? = null, time: LocalTime? = LocalTime.of(14, 30), remind: Int? = 60, date: LocalDate = day.plusDays(1)) =
        NewAgendaItem(title, "borcu hatırlat", date, time, personId, remind)

    @Test fun createAndReadBack() = runDb {
        val p = persons.create(NewPerson("Ahmet", null, "", 0))
        val id = agenda.create(item(personId = p))
        val a = agenda.getById(id)!!
        assertEquals("Ahmet'i ara", a.title)
        assertEquals(day.plusDays(1), a.date)
        assertEquals(LocalTime.of(14, 30), a.time)
        assertEquals("Ahmet", a.personName)
        assertEquals(60, a.remindMinutesBefore)
        assertFalse(a.isDone)
        assertEquals(1, agenda.observeAll().first().size)
    }

    @Test fun allDayItemHasNoTimeAndRemindsAtNineAm() = runDb {
        val id = agenda.create(item(time = null, remind = 0))
        val a = agenda.getById(id)!!
        assertNull(a.time)
        assertEquals(day.plusDays(1).atTime(9, 0), a.reminderAt())
    }

    @Test fun reminderAtSubtractsOffset() = runDb {
        val a = agenda.getById(agenda.create(item(time = LocalTime.of(14, 30), remind = 60)))!!
        assertEquals(day.plusDays(1).atTime(13, 30), a.reminderAt())
        val b = agenda.getById(agenda.create(item(time = LocalTime.of(14, 30), remind = 1440)))!!
        assertEquals(day.atTime(14, 30), b.reminderAt())
        val none = agenda.getById(agenda.create(item(remind = null)))!!
        assertNull(none.reminderAt())
    }

    @Test fun updateChangesFieldsButKeepsState() = runDb {
        val id = agenda.create(item())
        agenda.setDone(id, true)
        agenda.update(AgendaUpdate(id, "Yeni başlık", "yeni", day.plusDays(5), null, null, null))
        val a = agenda.getById(id)!!
        assertEquals("Yeni başlık", a.title)
        assertEquals(day.plusDays(5), a.date)
        assertNull(a.time)
        assertNull(a.remindMinutesBefore)
        assertTrue("Tamamlanma durumu korunur", a.isDone)
    }

    @Test fun doneCanBeToggledAndHidesFromReminderCandidates() = runDb {
        val id = agenda.create(item())
        assertEquals(1, agenda.getReminderCandidates().size)
        agenda.setDone(id, true)
        assertTrue(agenda.getById(id)!!.isDone)
        assertTrue(agenda.getById(id)!!.doneAt != null)
        assertEquals(0, agenda.getReminderCandidates().size)
        agenda.setDone(id, false)
        assertNull(agenda.getById(id)!!.doneAt)
        assertEquals(1, agenda.getReminderCandidates().size)
    }

    @Test fun itemsWithoutReminderAreNotCandidates() = runDb {
        agenda.create(item(remind = null))
        assertEquals(0, agenda.getReminderCandidates().size)
    }

    @Test fun archiveHidesButKeepsRecord() = runDb {
        val id = agenda.create(item())
        agenda.archive(id)
        assertNull(agenda.getById(id))
        assertEquals(0, agenda.observeAll().first().size)
        assertEquals(0, agenda.getReminderCandidates().size)
        assertEquals(1, DatabaseSnapshotter.export(db).agendaItems.size)       // fiziksel silme yok
        val error = runCatching { agenda.archive(id) }.exceptionOrNull()
        assertTrue(error is FinanceException.AgendaNotFound)
        assertTrue(runCatching { agenda.setDone(id, true) }.exceptionOrNull() is FinanceException.AgendaNotFound)
        assertTrue(runCatching { agenda.update(AgendaUpdate(id, "x", "", day, null, null, null)) }.exceptionOrNull() is FinanceException.AgendaNotFound)
    }

    @Test fun archivedOrMissingPersonIsRejected() = runDb {
        val p = persons.create(NewPerson("Ahmet", null, "", 0))
        persons.setArchived(p, true)
        assertTrue(runCatching { agenda.create(item(personId = p)) }.exceptionOrNull() is FinanceException.PersonArchived)
        assertTrue(runCatching { agenda.create(item(personId = 999)) }.exceptionOrNull() is FinanceException.PersonNotFound)
    }

    @Test fun personNameAppearsInObservedList() = runDb {
        val p = persons.create(NewPerson("Ayşe", null, "", 0))
        agenda.create(item(personId = p))
        assertEquals("Ayşe", agenda.observeAll().first().single().personName)
    }

    @Test fun listIsSortedByDateThenTime() = runDb {
        agenda.create(item("geç", time = LocalTime.of(18, 0), date = day.plusDays(2)))
        agenda.create(item("erken", time = LocalTime.of(8, 0), date = day.plusDays(2)))
        agenda.create(item("gün boyu", time = null, date = day.plusDays(2)))
        agenda.create(item("önceki gün", time = LocalTime.of(23, 0), date = day.plusDays(1)))
        assertEquals(listOf("önceki gün", "gün boyu", "erken", "geç"), agenda.observeAll().first().map { it.title })
    }

    @Test fun agendaSurvivesSnapshotBackupCodecAndValidation() = runDb {
        val p = persons.create(NewPerson("Ahmet", null, "", 0))
        val id = agenda.create(item(personId = p))
        agenda.create(item("tamamlanan", remind = null))
        agenda.setDone(id, true)

        val snapshot = DatabaseSnapshotter.export(db)
        assertEquals(2, snapshot.agendaItems.size)
        assertEquals(emptyList<String>(), BackupSnapshotValidator.validate(snapshot))

        val parsed = BackupCodec.decode(BackupCodec.encode(snapshot, 1))
        assertEquals(snapshot, parsed.snapshot)
        assertEquals(2, parsed.meta.counts["agendaItems"])

        // Başka bir veritabanına aktarım
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val other = Room.inMemoryDatabaseBuilder(ctx, KisiselFinansDatabase::class.java).allowMainThreadQueries().build()
        try {
            DatabaseSnapshotter.import(other, parsed.snapshot)
            assertEquals(snapshot, DatabaseSnapshotter.export(other))
            DatabaseSnapshotter.replaceAll(other, parsed.snapshot)           // ajanda → kişi silme sırası
            assertEquals(snapshot, DatabaseSnapshotter.export(other))
        } finally {
            other.close()
        }
    }
}
