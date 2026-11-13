package com.kisisel.finans.ui.agenda

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.agenda.AgendaEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    onBack: () -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onAddNote: () -> Unit,
    vm: AgendaViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showDone by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val view = state.view
    val today = state.today
    val open: (AgendaEntry) -> Unit = { e ->
        when (e) {
            is AgendaEntry.Invoice -> onOpenInvoice(e.occurrence.id)
            is AgendaEntry.Claim -> onOpenPerson(e.claim.personId)
            is AgendaEntry.Note -> onOpenNote(e.item.id)
        }
    }
    val toggle: (AgendaEntry) -> ((Boolean) -> Unit)? = { e ->
        if (e is AgendaEntry.Note) ({ checked: Boolean -> vm.toggleDone(e.item.id, checked) }) else null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Ajanda") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddNote,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Not ekle") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.loaded && view.overdue.isEmpty() && view.days.isEmpty() && view.done.isEmpty()) {
                item {
                    Text(
                        "Ajandanız boş. Faturalar, vadesi olan borç/alacaklar ve notlarınız burada günlere göre görünür. " +
                            "\"Not ekle\" ile tarih ve saatli hatırlatıcı kurabilirsiniz.",
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }

            if (view.overdue.isNotEmpty()) {
                item(key = "h-overdue") {
                    Text("Geciken (${view.overdue.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
                items(view.overdue, key = { "o" + agendaEntryKey(it) }) { e ->
                    AgendaEntryCard(e, today, onClick = { open(e) }, onToggleDone = toggle(e))
                }
            }

            for (day in view.days) {
                item(key = "h-${day.date}") {
                    Text(
                        agendaDayLabel(day.date, today),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(day.entries, key = { "d${day.date}" + agendaEntryKey(it) }) { e ->
                    AgendaEntryCard(e, today, onClick = { open(e) }, onToggleDone = toggle(e))
                }
            }

            if (view.laterCount > 0) {
                item { Text("Daha sonraki ${view.laterCount} kayıt (30 günden ileri)", style = MaterialTheme.typography.bodySmall) }
            }

            if (view.done.isNotEmpty()) {
                item(key = "h-done") {
                    TextButton(onClick = { showDone = !showDone }) {
                        Text(if (showDone) "Tamamlananları gizle" else "Tamamlananlar (${view.done.size})")
                    }
                }
                if (showDone) {
                    items(view.done, key = { "x${it.id}" }) { item ->
                        AgendaEntryCard(
                            AgendaEntry.Note(item), today,
                            onClick = { onOpenNote(item.id) },
                            onToggleDone = { checked -> vm.toggleDone(item.id, checked) }
                        )
                    }
                }
            }
        }
    }
}
