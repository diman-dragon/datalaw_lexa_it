package app.datalaw.lexa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.datalaw.lexa.LexaApp
import app.datalaw.lexa.ui.components.ToastHost
import app.datalaw.lexa.ui.components.rememberTap
import app.datalaw.lexa.ui.i18n.Str
import app.datalaw.lexa.ui.i18n.t
import app.datalaw.lexa.ui.screens.FindScreen
import app.datalaw.lexa.ui.screens.HistoryScreen
import app.datalaw.lexa.ui.screens.HomeScreen
import app.datalaw.lexa.ui.screens.SavedScreen
import app.datalaw.lexa.ui.screens.SettingsScreen
import app.datalaw.lexa.ui.screens.WordScreen
import app.datalaw.lexa.ui.theme.L
import app.datalaw.lexa.ui.theme.LexaTheme

private class Tab(val route: Route, val icon: ImageVector, val activeIcon: ImageVector, val label: Str)

private val TABS = listOf(
    Tab(Route.Home, Icons.Rounded.Search, Icons.Rounded.Search, Str.navSearch),
    Tab(Route.Saved, Icons.Rounded.StarBorder, Icons.Rounded.Star, Str.navSaved),
    Tab(Route.History, Icons.Rounded.History, Icons.Rounded.History, Str.navHistory),
    Tab(Route.Settings, Icons.Rounded.Tune, Icons.Rounded.Tune, Str.navSettings),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LexaRoot() {
    val store = LexaApp.instance.store
    LexaTheme(store.settings.theme) {
        val c = L.c
        val nav = remember { Nav() }
        val tap = rememberTap()
        BackHandler(enabled = nav.canGoBack) { nav.back() }

        Scaffold(
            modifier = Modifier.imePadding(),
            containerColor = c.bg,
            bottomBar = {
                // с клавиатурой нижняя панель только мешает
                if (!WindowInsets.isImeVisible) {
                    NavigationBar(containerColor = c.surface) {
                        for (tab in TABS) {
                            val on = nav.activeTab == tab.route
                            NavigationBarItem(
                                selected = on,
                                onClick = {
                                    tap(false)
                                    nav.tab(tab.route)
                                },
                                icon = { Icon(if (on) tab.activeIcon else tab.icon, contentDescription = null) },
                                label = { Text(t(tab.label), maxLines = 1, softWrap = false, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = c.accentInk, selectedTextColor = c.accent,
                                    indicatorColor = c.accent, unselectedIconColor = c.ink3, unselectedTextColor = c.ink3,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().background(c.bg).padding(padding), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                    val route = nav.current
                    // состояние экрана (прокрутка, ввод) сбрасывается при смене маршрута
                    key(route) {
                        when (route) {
                            Route.Home -> HomeScreen(nav)
                            Route.Saved -> SavedScreen(nav)
                            Route.History -> HistoryScreen(nav)
                            Route.Settings -> SettingsScreen()
                            is Route.Word -> WordScreen(nav, route.word)
                            is Route.Find -> FindScreen(nav, route.query)
                        }
                    }
                }
                ToastHost(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
            }
        }
    }
}
