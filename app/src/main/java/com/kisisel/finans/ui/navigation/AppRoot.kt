package com.kisisel.finans.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kisisel.finans.ui.common.NotificationPermissionPrompt
import com.kisisel.finans.ui.home.HomeScreen
import com.kisisel.finans.ui.invoices.InvoiceDetailScreen
import com.kisisel.finans.ui.invoices.InvoiceEditScreen
import com.kisisel.finans.ui.invoices.InvoiceListScreen
import com.kisisel.finans.ui.persons.PersonDetailScreen
import com.kisisel.finans.ui.persons.PersonEditScreen
import com.kisisel.finans.ui.persons.PersonListScreen
import com.kisisel.finans.ui.placeholder.ComingSoonScreen
import com.kisisel.finans.ui.transactions.TransactionsScreen

@Composable
fun AppRoot(
    pendingOccurrenceId: Long? = null,
    onPendingConsumed: () -> Unit = {}
) {
    val nav = rememberNavController()

    // Bildirime dokunulunca ilgili fatura dönemini aç.
    LaunchedEffect(pendingOccurrenceId) {
        if (pendingOccurrenceId != null && pendingOccurrenceId > 0) {
            nav.navigate(Routes.invoiceDetail(pendingOccurrenceId))
            onPendingConsumed()
        }
    }
    NotificationPermissionPrompt()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = TopDestinations.any { it.route == currentRoute }

    val personArg = listOf(navArgument(Routes.ARG_PERSON_ID) { type = NavType.LongType })
    val occurrenceArg = listOf(navArgument(Routes.ARG_OCCURRENCE_ID) { type = NavType.LongType })
    val templateArg = listOf(navArgument(Routes.ARG_TEMPLATE_ID) { type = NavType.LongType })

    val goTop: (String) -> Unit = { route ->
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    TopDestinations.forEach { d ->
                        NavigationBarItem(
                            selected = currentRoute == d.route,
                            onClick = {
                                nav.navigate(d.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(d.icon, contentDescription = null) },
                            label = { Text(d.label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = inner.calculateBottomPadding())
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenOccurrence = { nav.navigate(Routes.invoiceDetail(it)) },
                    onOpenPerson = { nav.navigate(Routes.personDetail(it)) },
                    onAddInvoice = { nav.navigate(Routes.invoiceEdit(0)) },
                    onAddPerson = { nav.navigate(Routes.personEdit(0)) },
                    onSeeInvoices = { goTop(Routes.INVOICES) },
                    onSeeTransactions = { goTop(Routes.TRANSACTIONS) }
                )
            }
            composable(Routes.PERSONS) {
                PersonListScreen(
                    onOpen = { nav.navigate(Routes.personDetail(it)) },
                    onAdd = { nav.navigate(Routes.personEdit(0)) }
                )
            }
            composable(Routes.INVOICES) {
                InvoiceListScreen(
                    onOpenOccurrence = { nav.navigate(Routes.invoiceDetail(it)) },
                    onOpenTemplate = { nav.navigate(Routes.invoiceEdit(it)) },
                    onAdd = { nav.navigate(Routes.invoiceEdit(0)) }
                )
            }
            composable(Routes.INVOICE_DETAIL, arguments = occurrenceArg) {
                InvoiceDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEditTemplate = { nav.navigate(Routes.invoiceEdit(it)) }
                )
            }
            composable(Routes.INVOICE_EDIT, arguments = templateArg) {
                InvoiceEditScreen(
                    onBack = { nav.popBackStack() },
                    onDone = { nav.popBackStack() }
                )
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsScreen(onOpenPerson = { nav.navigate(Routes.personDetail(it)) })
            }
            composable(Routes.REPORTS) { ComingSoonScreen("Raporlar", 10) }
            composable(Routes.SETTINGS) { ComingSoonScreen("Ayarlar", 8) }

            composable(Routes.PERSON_DETAIL, arguments = personArg) {
                PersonDetailScreen(
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(Routes.personEdit(it)) }
                )
            }
            composable(Routes.PERSON_EDIT, arguments = personArg) {
                PersonEditScreen(
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() }
                )
            }
        }
    }
}
