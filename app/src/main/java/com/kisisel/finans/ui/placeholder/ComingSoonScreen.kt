package com.kisisel.finans.ui.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Henüz geliştirilmemiş bölümler için geçici ekran; ilgili aşamada gerçek ekranla değiştirilir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonScreen(title: String, stage: Int) {
    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        topBar = { TopAppBar(title = { Text(title) }) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Bu bölüm Aşama $stage'te eklenecek.")
        }
    }
}
