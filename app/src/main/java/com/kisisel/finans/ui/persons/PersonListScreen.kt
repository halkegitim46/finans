package com.kisisel.finans.ui.persons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.core.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonListScreen(
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    vm: PersonListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = { TopAppBar(title = { Text("Kişiler") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text("Kişi ekle") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQueryChange,
                label = { Text("Kişi ara") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Arşivlenenleri göster", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = state.showArchived, onCheckedChange = vm::onShowArchivedChange)
            }

            if (state.loaded && state.rows.isEmpty()) {
                Text(
                    text = when {
                        state.query.isNotBlank() -> "Aramayla eşleşen kişi yok."
                        state.showArchived -> "Arşivlenmiş kişi yok."
                        else -> "Henüz kişi yok. Başlamak için \"Kişi ekle\"ye dokunun."
                    },
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
            ) {
                items(state.rows, key = { it.person.id }) { row ->
                    PersonCard(row, onClick = { onOpen(row.person.id) })
                }
            }
        }
    }
}

@Composable
private fun PersonCard(row: PersonRow, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(row.person.fullName, style = MaterialTheme.typography.titleMedium)
                row.person.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            val b = row.balance
            Column(horizontalAlignment = Alignment.End) {
                if (b == null) {
                    Text("Arşivde", style = MaterialTheme.typography.labelLarge)
                } else {
                    if (b.remainingReceivable > 0) {
                        Text(
                            "Alacak ${Money.format(b.remainingReceivable)}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (b.remainingPayable > 0) {
                        Text(
                            "Borç ${Money.format(b.remainingPayable)}",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (b.remainingReceivable == 0L && b.remainingPayable == 0L) {
                        Text("Hesap kapalı", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
