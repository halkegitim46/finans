package com.kisisel.finans.ui.persons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.ui.common.AmountField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: PersonEditViewModel = hiltViewModel()
) {
    val s by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isEdit) "Kişiyi düzenle" else "Kişi ekle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = s.name,
                onValueChange = vm::onName,
                label = { Text("Ad soyad") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.phone,
                onValueChange = vm::onPhone,
                label = { Text("Telefon (isteğe bağlı)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.note,
                onValueChange = vm::onNote,
                label = { Text("Açıklama") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            if (!s.isEdit) {
                Text("Başlangıç bakiyesi (isteğe bağlı)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !s.openingIsDebt,
                        onClick = { vm.onOpeningIsDebt(false) },
                        label = { Text("Bana borçlu (alacak)") }
                    )
                    FilterChip(
                        selected = s.openingIsDebt,
                        onClick = { vm.onOpeningIsDebt(true) },
                        label = { Text("Ben borçluyum") }
                    )
                }
                AmountField(
                    value = s.openingText,
                    onValueChange = vm::onOpening,
                    label = "Başlangıç tutarı"
                )
            }

            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { vm.save(onSaved) },
                enabled = !s.saving && !s.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Kaydet") }
        }
    }
}
