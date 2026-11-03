package com.kisisel.finans.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.core.Money
import com.kisisel.finans.domain.model.LedgerLine
import com.kisisel.finans.domain.model.TransactionType
import com.kisisel.finans.ui.common.label
import com.kisisel.finans.ui.common.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onOpenPerson: (Long) -> Unit,
    vm: TransactionsViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = { TopAppBar(title = { Text("İşlemler") }) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQuery,
                label = { Text("Kişi veya açıklama ara") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = state.type == null, onClick = { vm.onType(null) }, label = { Text("Tümü") })
                TransactionType.entries.forEach { t ->
                    FilterChip(selected = state.type == t, onClick = { vm.onType(t) }, label = { Text(t.label()) })
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("İptal edilenleri göster", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = state.showCancelled, onCheckedChange = vm::onShowCancelled)
            }
            state.totalKurus?.let {
                Text(
                    "${state.lines.count { l -> !l.entry.isCancelled }} işlem • Toplam: ${Money.format(it)}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            if (state.loaded && state.lines.isEmpty()) {
                Text("Kayıt bulunamadı.", modifier = Modifier.padding(vertical = 24.dp))
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)
            ) {
                items(state.lines, key = { it.entry.id }) { line ->
                    LineCard(line) { onOpenPerson(line.entry.personId) }
                }
            }
        }
    }
}

@Composable
private fun LineCard(line: LedgerLine, onClick: () -> Unit) {
    val e = line.entry
    val decoration = if (e.isCancelled) TextDecoration.LineThrough else TextDecoration.None
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.personName, style = MaterialTheme.typography.titleSmall, textDecoration = decoration)
                Text(
                    "${e.type.label()} • ${e.date.tr()}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodySmall)
                if (e.isCancelled) {
                    Text("İptal edildi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                Money.format(e.amountKurus),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = decoration
            )
        }
    }
}
