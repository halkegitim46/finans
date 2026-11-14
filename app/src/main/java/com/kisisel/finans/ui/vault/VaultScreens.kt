package com.kisisel.finans.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kisisel.finans.domain.model.VaultItem
import com.kisisel.finans.ui.common.ConfirmDialog
import com.kisisel.finans.ui.common.PasswordField
import com.kisisel.finans.vault.PasswordGenerator
import com.kisisel.finans.vault.PasswordStrength
import com.kisisel.finans.vault.SecureClipboard
import com.kisisel.finans.vault.Strength
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ======================= Liste =======================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(onBack: () -> Unit, onOpenItem: (Long) -> Unit, onAdd: () -> Unit) {
    VaultGate(onBack = onBack) {
        val vm: VaultListViewModel = hiltViewModel()
        val s by vm.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { vm.touch() }
        // Kasadan çıkarken kilitle (ayrıntı/düzenleme ekranlarına geçerken kilitlenmez).
        BackHandler { vm.lock(); onBack() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Şifre Kasası") },
                    navigationIcon = {
                        IconButton(onClick = { vm.lock(); onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.lock() }) {
                            Icon(Icons.Filled.Lock, contentDescription = "Kasayı kilitle")
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Ekle") }
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding)) {
                OutlinedTextField(
                    value = s.query,
                    onValueChange = vm::setQuery,
                    label = { Text("Ara (başlık, kullanıcı adı, adres)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (s.loaded && s.total == 0) {
                        item {
                            Text(
                                "Henüz kayıt yok. \"Ekle\" ile hesap, site veya uygulama şifrelerinizi kaydedin. " +
                                    "Şifreler cihazda şifreli veritabanında tutulur ve yedeğe (yedek parolanızla şifrelenmiş olarak) dahil edilir.",
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else if (s.loaded && s.items.isEmpty()) {
                        item { Text("Eşleşen kayıt yok.", modifier = Modifier.padding(vertical = 16.dp)) }
                    }
                    items(s.items, key = { it.id }) { row ->
                        Card(onClick = { onOpenItem(row.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(row.title, style = MaterialTheme.typography.titleSmall)
                                val sub = listOf(row.username, row.url).filter { it.isNotBlank() }.joinToString(" • ")
                                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ======================= Ayrıntı =======================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultItemScreen(onBack: () -> Unit, onEdit: (Long) -> Unit) {
    VaultGate(onBack = onBack) {
        val vm: VaultItemViewModel = hiltViewModel()
        val s by vm.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        var revealed by remember { mutableStateOf(false) }
        var confirmDelete by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { vm.touch() }

        val item = s.item
        // Kayıt arşivlendi (veya yok): geri dön.
        LaunchedEffect(s.loading, item == null) { if (!s.loading && item == null) onBack() }

        fun copy(label: String, value: String) {
            SecureClipboard.copy(context, label, value)
            vm.touch()
            scope.launch { snackbar.showSnackbar("$label panoya kopyalandı; 30 sn sonra temizlenir.") }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(item?.title ?: "Şifre Kasası") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                        }
                    }
                )
            }
        ) { padding ->
            if (item != null) {
                Column(
                    Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (item.username.isNotBlank()) {
                        DetailRow("Kullanıcı adı", item.username, onCopy = { copy("Kullanıcı adı", item.username) })
                    }
                    SecretRow(item, revealed, onToggle = { revealed = !revealed; vm.touch() }, onCopy = { copy("Şifre", item.secret) })
                    if (item.url.isNotBlank()) DetailRow("Adres", item.url, onCopy = { copy("Adres", item.url) })
                    if (item.note.isNotBlank()) DetailRow("Not", item.note, onCopy = null)

                    s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { onEdit(item.id) }, modifier = Modifier.fillMaxWidth()) { Text("Düzenle") }
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sil", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (confirmDelete) {
            ConfirmDialog(
                title = "Kaydı sil",
                message = "Kayıt kasadan kalkar. (Arşivde saklanır, yedeğe dahil olmaya devam eder.)",
                confirmText = "Sil",
                onConfirm = { confirmDelete = false; vm.archive() },
                onDismiss = { confirmDelete = false }
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, onCopy: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            if (onCopy != null) {
                IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "$label kopyala") }
            }
        }
    }
}

@Composable
private fun SecretRow(item: VaultItem, revealed: Boolean, onToggle: () -> Unit, onCopy: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Şifre", style = MaterialTheme.typography.labelMedium)
                Text(
                    if (revealed) item.secret else "•".repeat(minOf(item.secret.length, 16)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (revealed) "Şifreyi gizle" else "Şifreyi göster"
                )
            }
            IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Şifreyi kopyala") }
        }
    }
}

// ======================= Ekle / düzenle =======================
@Composable
private fun strengthColor(s: Strength) = when (s) {
    Strength.VERY_WEAK, Strength.WEAK -> MaterialTheme.colorScheme.error
    Strength.FAIR -> MaterialTheme.colorScheme.tertiary
    Strength.STRONG, Strength.VERY_STRONG -> MaterialTheme.colorScheme.primary
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultEditScreen(onBack: () -> Unit, onDone: () -> Unit) {
    VaultGate(onBack = onBack) {
        val vm: VaultEditViewModel = hiltViewModel()
        val s by vm.state.collectAsStateWithLifecycle()
        var showGenerator by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (s.isEdit) "Kaydı düzenle" else "Yeni kayıt") },
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
                    value = s.title, onValueChange = vm::onTitle, label = { Text("Başlık (ör. Banka, E-posta)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = s.username, onValueChange = vm::onUsername, label = { Text("Kullanıcı adı / e-posta") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                PasswordField(value = s.secret, onValueChange = vm::onSecret, label = "Şifre", modifier = Modifier.fillMaxWidth())
                if (s.secret.isNotEmpty()) {
                    val rating = PasswordStrength.rate(s.secret)
                    Text("Güç tahmini: ${rating.label}", color = strengthColor(rating), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { showGenerator = true }, modifier = Modifier.fillMaxWidth()) { Text("Güçlü şifre üret") }
                OutlinedTextField(
                    value = s.url, onValueChange = vm::onUrl, label = { Text("Adres (isteğe bağlı)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = s.note, onValueChange = vm::onNote, label = { Text("Not (isteğe bağlı)") },
                    minLines = 3, modifier = Modifier.fillMaxWidth()
                )
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = { vm.save(onDone) },
                    enabled = !s.saving && !s.loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Kaydet") }
            }
        }

        if (showGenerator) {
            GeneratorDialog(
                onUse = { vm.onSecret(it); showGenerator = false },
                onDismiss = { showGenerator = false }
            )
        }
    }
}

@Composable
private fun GeneratorDialog(onUse: (String) -> Unit, onDismiss: () -> Unit) {
    var length by remember { mutableFloatStateOf(16f) }
    var lower by remember { mutableStateOf(true) }
    var upper by remember { mutableStateOf(true) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(true) }
    var generated by remember { mutableStateOf(PasswordGenerator.generate(PasswordGenerator.Options())) }

    fun options() = PasswordGenerator.Options(length.roundToInt(), lower, upper, digits, symbols)
    fun regenerate() {
        val o = options()
        if (o.pools.isNotEmpty()) generated = PasswordGenerator.generate(o)
    }

    val valid = options().pools.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Güçlü şifre üret") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (valid) generated else "En az bir karakter türü seçin.",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace
                )
                if (valid) {
                    val rating = PasswordStrength.rate(generated)
                    Text("Güç tahmini: ${rating.label}", color = strengthColor(rating), style = MaterialTheme.typography.bodySmall)
                }
                Text("Uzunluk: ${length.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = length,
                    onValueChange = { length = it },
                    onValueChangeFinished = { regenerate() },
                    valueRange = 8f..40f,
                    steps = 31
                )
                CheckRow("Küçük harf", lower) { lower = it; regenerate() }
                CheckRow("Büyük harf", upper) { upper = it; regenerate() }
                CheckRow("Rakam", digits) { digits = it; regenerate() }
                CheckRow("Sembol", symbols) { symbols = it; regenerate() }
                TextButton(onClick = { regenerate() }, enabled = valid) { Text("Yeniden üret") }
            }
        },
        confirmButton = { TextButton(onClick = { onUse(generated) }, enabled = valid) { Text("Kullan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } }
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
