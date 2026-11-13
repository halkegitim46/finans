package com.kisisel.finans.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val HOME = "home"
    const val PERSONS = "persons"
    const val INVOICES = "invoices"
    const val TRANSACTIONS = "transactions"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    const val ARG_PERSON_ID = "personId"
    const val PERSON_DETAIL = "person/{$ARG_PERSON_ID}"
    const val PERSON_EDIT = "person_edit/{$ARG_PERSON_ID}"

    const val ARG_OCCURRENCE_ID = "occurrenceId"
    const val ARG_TEMPLATE_ID = "templateId"
    const val ARG_AGENDA_ID = "agendaId"
    const val AGENDA = "agenda"
    const val AGENDA_EDIT = "agenda_edit/{$ARG_AGENDA_ID}"
    const val INVOICE_DETAIL = "invoice/{$ARG_OCCURRENCE_ID}"
    const val INVOICE_EDIT = "invoice_edit/{$ARG_TEMPLATE_ID}"

    fun personDetail(id: Long) = "person/$id"

    /** id = 0 yeni kişi ekler. */
    fun personEdit(id: Long) = "person_edit/$id"

    fun invoiceDetail(occurrenceId: Long) = "invoice/$occurrenceId"

    /** id = 0 yeni ajanda notu ekler. */
    fun agendaEdit(id: Long) = "agenda_edit/$id"

    /** templateId = 0 yeni fatura ekler. */
    fun invoiceEdit(templateId: Long) = "invoice_edit/$templateId"
}

data class TopDestination(val route: String, val label: String, val icon: ImageVector)

val TopDestinations = listOf(
    TopDestination(Routes.HOME, "Ana Sayfa", Icons.Filled.Home),
    TopDestination(Routes.PERSONS, "Kişiler", Icons.Filled.People),
    TopDestination(Routes.INVOICES, "Faturalar", Icons.Filled.Receipt),
    TopDestination(Routes.TRANSACTIONS, "İşlemler", Icons.Filled.SwapHoriz),
    TopDestination(Routes.REPORTS, "Raporlar", Icons.Filled.BarChart),
    TopDestination(Routes.SETTINGS, "Ayarlar", Icons.Filled.Settings)
)
