package com.kisisel.finans.ui

import com.kisisel.finans.domain.model.Person
import com.kisisel.finans.ui.persons.PersonRow
import com.kisisel.finans.ui.persons.filterPersonRows
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonFilterTest {
    private fun row(id: Long, name: String) =
        PersonRow(Person(id, name, null, "", 0, false, 0, 0), null)

    private val rows = listOf(row(1, "Ahmet Dayı"), row(2, "Işıl Hanım"), row(3, "Mehmet Kaya"))

    @Test fun emptyQueryReturnsAll() {
        assertEquals(3, filterPersonRows(rows, "  ").size)
    }

    @Test fun caseInsensitive() {
        assertEquals(listOf(1L), filterPersonRows(rows, "ahmet").map { it.person.id })
        assertEquals(listOf(1L, 3L), filterPersonRows(rows, "MET").map { it.person.id })
    }

    @Test fun turkishDottedAndDotlessI() {
        assertEquals(listOf(2L), filterPersonRows(rows, "ışıl").map { it.person.id })
        assertEquals(listOf(2L), filterPersonRows(rows, "Işıl").map { it.person.id })
    }

    @Test fun noMatch() {
        assertEquals(0, filterPersonRows(rows, "xyz").size)
    }
}
