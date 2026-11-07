package app.datalaw.lexa.ui

import androidx.compose.runtime.mutableStateListOf

sealed interface Route {
    data object Home : Route
    data object Cards : Route
    data object Saved : Route
    data object History : Route
    data object Settings : Route
    data class Word(val word: String) : Route
    data class Find(val query: String) : Route
}

/** Простая навигация: стек экранов; вкладки нижней панели заменяют стек, слова и обратный поиск кладутся сверху. */
class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(route: Route) {
        if (route != current) stack.add(route)
    }

    /** Заменяет верхний экран (например, переход от формы слова к лемме). */
    fun replaceTop(route: Route) {
        stack[stack.lastIndex] = route
    }

    /** Переключение вкладки: «назад» с любой вкладки ведёт на «Поиск». */
    fun tab(route: Route) {
        stack.clear()
        if (route != Route.Home) stack.add(Route.Home)
        stack.add(route)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun openWord(word: String, replace: Boolean = false) {
        if (replace) replaceTop(Route.Word(word)) else push(Route.Word(word))
    }

    /** Вкладка, к которой относится текущий экран (для подсветки нижней панели). */
    val activeTab: Route?
        get() = when (val c = current) {
            Route.Home, Route.Cards, Route.Saved, Route.History, Route.Settings -> c
            else -> null
        }
}
