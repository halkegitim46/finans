package com.kisisel.finans.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Menüyü açan eylem; AppRoot sağlar. Üst düzey ekranların başlık çubukları [MenuNavigationIcon] ile kullanır. */
val LocalOpenDrawer = staticCompositionLocalOf<() -> Unit> { {} }

/** Sol köşedeki hamburger menü düğmesi (üst düzey ekranların `navigationIcon` alanı). */
@Composable
fun MenuNavigationIcon() {
    val open = LocalOpenDrawer.current
    IconButton(onClick = open) {
        Icon(Icons.Filled.Menu, contentDescription = "Menü")
    }
}

/**
 * Yan menü: alt çubuktaki altı ana bölüm + Ajanda + Şifre Kasası.
 * Şifre Kasası'na buradan girişte de yeniden doğrulama istenir (kapı VaultGate'dedir).
 */
@Composable
fun AppDrawerContent(
    currentRoute: String?,
    onNavigateTop: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val itemModifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "Kişisel Finans Takip",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 24.dp)
            )
            Text(
                "Çevrimdışı ve şifreli",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 2.dp, bottom = 16.dp)
            )
            TopDestinations.forEach { d ->
                NavigationDrawerItem(
                    label = { Text(d.label) },
                    selected = currentRoute == d.route,
                    onClick = { onNavigateTop(d.route) },
                    icon = { Icon(d.icon, contentDescription = null) },
                    modifier = itemModifier
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text("Ajanda") },
                selected = currentRoute == Routes.AGENDA,
                onClick = { onNavigate(Routes.AGENDA) },
                icon = { Icon(Icons.Filled.Event, contentDescription = null) },
                modifier = itemModifier
            )
            NavigationDrawerItem(
                label = { Text("Şifre Kasası") },
                selected = currentRoute == Routes.VAULT,
                onClick = { onNavigate(Routes.VAULT) },
                icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                modifier = itemModifier
            )
        }
    }
}
