package dev.snivik.byedirty.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.snivik.byedirty.R
import dev.snivik.byedirty.data.ShowerStore

sealed interface ScanState {
    data class Waiting(val fullMemory: Boolean) : ScanState
    data class Reading(val progress: Float) : ScanState
    data class Success(val message: String) : ScanState
    data class Error(val message: String, val nfcOff: Boolean = false) : ScanState
}

class AppActions(
    val sync: (fullMemory: Boolean) -> Unit,
    val dismissScan: () -> Unit,
    val openNfcSettings: () -> Unit,
    val shareDumps: () -> Unit,
    val toggleReminders: () -> Unit,
    val setShowersPerWeek: (Int) -> Unit,
)

private enum class Tab(val label: String, @param:DrawableRes val icon: Int, @param:DrawableRes val selectedIcon: Int) {
    HOME("Home", R.drawable.ic_home_outline, R.drawable.ic_home),
    FILTER("Filter", R.drawable.ic_drop_outline, R.drawable.ic_drop),
    HISTORY("History", R.drawable.ic_bar_chart_outline, R.drawable.ic_bar_chart),
}

@Composable
fun ByeDirtyApp(store: ShowerStore, scanState: ScanState?, actions: AppActions) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var showDevice by rememberSaveable { mutableStateOf(false) }

    if (showDevice) {
        BackHandler { showDevice = false }
        DeviceScreen(store, onBack = { showDevice = false }, actions)
    } else {
        MainScaffold(store, tab, onTab = { tab = it }, onDevice = { showDevice = true }, actions)
    }
    scanState?.let { ScanSheet(it, actions) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(store: ShowerStore, tab: Tab, onTab: (Tab) -> Unit, onDevice: () -> Unit, actions: AppActions) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { BrandTitle() },
                actions = {
                    IconButton(actions.toggleReminders) {
                        Icon(
                            painterResource(if (store.remindersEnabled) R.drawable.ic_bell_on else R.drawable.ic_bell),
                            if (store.remindersEnabled) "Turn off filter reminder" else "Remind me to replace the filter",
                        )
                    }
                    IconButton(onDevice) { Icon(painterResource(R.drawable.ic_info), "Shower head details") }
                },
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { onTab(t) },
                        icon = { Icon(painterResource(if (tab == t) t.selectedIcon else t.icon), null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer),
                    )
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { actions.sync(false) },
                icon = { Icon(painterResource(R.drawable.ic_nfc), null) },
                text = { Text("Sync") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        when (tab) {
            Tab.HOME -> HomeScreen(store, padding)
            Tab.FILTER -> FilterScreen(store, padding, actions)
            Tab.HISTORY -> HistoryScreen(store, padding)
        }
    }
}

/** ByeDirty wordmark: the drop logo, a bold "Bye" and a light "Dirty". */
@Composable
private fun BrandTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_logo), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Bye") }
                withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("Dirty") }
            },
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/** Scrollable page body inside the scaffold, leaving room for the FAB at the bottom. */
@Composable
fun ScreenColumn(padding: PaddingValues, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}
