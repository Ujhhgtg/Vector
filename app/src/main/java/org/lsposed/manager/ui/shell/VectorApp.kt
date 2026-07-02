/*
 * This file is part of LSPosed.
 *
 * LSPosed is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LSPosed is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LSPosed.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2021 LSPosed Contributors
 */

package org.lsposed.manager.ui.shell

import android.content.Intent
import android.text.TextUtils
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.lsposed.manager.R
import org.lsposed.manager.ui.nav.AppList
import org.lsposed.manager.ui.nav.Home
import org.lsposed.manager.ui.nav.Logs
import org.lsposed.manager.ui.nav.Modules
import org.lsposed.manager.ui.nav.Repo
import org.lsposed.manager.ui.nav.RepoItem
import org.lsposed.manager.ui.nav.Settings
import org.lsposed.manager.ui.route.AppListRoute
import org.lsposed.manager.ui.route.HomeRoute
import org.lsposed.manager.ui.route.LogsRoute
import org.lsposed.manager.ui.route.ModulesRoute
import org.lsposed.manager.ui.route.RepoItemRoute
import org.lsposed.manager.ui.route.RepoRoute
import org.lsposed.manager.ui.route.SettingsRoute

/** A top-level (bottom-bar / rail) destination. */
private class TopLevel(
    val route: Any,
    val labelRes: Int,
    val icon: ImageVector,
    val badge: (ShellViewModel.Badges) -> BadgeKind,
    val visible: (ShellViewModel) -> Boolean,
)

private enum class BadgeKind { NONE, DOT, NUMBER }

/**
 * The Compose application shell: a [NavigationSuiteScaffold] (bottom bar on phones, rail on wide
 * screens) hosting a type-safe [NavHost]. Replaces the legacy MainActivity + nav XML + bottom nav.
 *
 * @param restart recreate the activity (theme/locale changes) — wired to the activity's restart().
 * @param pendingIntent the launch/new intent to route from (deep links, shortcuts); consumed once.
 */
@Composable
fun VectorApp(
    restart: () -> Unit,
    pendingIntent: Intent?,
    onIntentConsumed: () -> Unit,
) {
    val shellViewModel: ShellViewModel = viewModel()
    val navController = rememberNavController()
    val badges by shellViewModel.badges.collectAsStateWithLifecycle()

    // Mirror the legacy MainActivity.onResume: recompute badges (enabled count, manager-update dot)
    // and refresh the parasitic launcher shortcut every time the shell returns to the foreground.
    androidx.lifecycle.compose.LifecycleResumeEffect(shellViewModel) {
        shellViewModel.refresh()
        onPauseOrDispose { }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hintHost = remember(snackbarHostState) { HintHost(snackbarHostState, scope) }

    // The top-level destinations, in the legacy bottom-nav order (Home centered on phones).
    val topLevels = remember {
        listOf(
            TopLevel(Repo, R.string.module_repo, Icons.Outlined.GetApp,
                badge = { if (it.upgradableModules > 0) BadgeKind.NUMBER else BadgeKind.NONE },
                visible = { it.binderAlive || it.magiskInstalled }),
            TopLevel(Modules, R.string.Modules, Icons.Outlined.Extension,
                badge = { if (it.enabledModules > 0) BadgeKind.NUMBER else BadgeKind.NONE },
                visible = { it.binderAlive }),
            TopLevel(Home, R.string.overview, Icons.Outlined.Home,
                badge = { if (it.managerUpdate) BadgeKind.DOT else BadgeKind.NONE },
                visible = { true }),
            TopLevel(Logs, R.string.Logs, Icons.AutoMirrored.Outlined.Article,
                badge = { BadgeKind.NONE },
                visible = { it.binderAlive }),
            TopLevel(Settings, R.string.Settings, Icons.Outlined.Settings,
                badge = { BadgeKind.NONE },
                visible = { true }),
        )
    }
    val visibleTopLevels = topLevels.filter { it.visible(shellViewModel) }

    // Route deep links / shortcut intents to the matching destination, once per intent.
    if (pendingIntent != null) {
        androidx.compose.runtime.LaunchedEffect(pendingIntent) {
            routeIntent(pendingIntent, navController, shellViewModel)
            onIntentConsumed()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()

    // Subpages (module scope / repo detail) are pushed like a new activity: the nav bar/rail is
    // hidden there. On top-level destinations we use the platform default (bar on phones, rail wide).
    val onSubPage = backStackEntry?.destination?.let { it.isSubPage() } == true
    val layoutType = if (onSubPage) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    }

    CompositionLocalProvider(
        LocalHintHost provides hintHost,
        LocalRestart provides restart,
        org.lsposed.manager.ui.component.LocalSnackbarHostState provides snackbarHostState,
    ) {
        NavigationSuiteScaffold(
            layoutType = layoutType,
            navigationSuiteItems = {
                visibleTopLevels.forEach { dest ->
                    val selected = backStackEntry?.destination?.hierarchy?.any {
                        it.hasRoute(dest.route::class)
                    } == true
                    val kind = dest.badge(badges)
                    item(
                        selected = selected,
                        onClick = { navigateTopLevel(navController, dest.route) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    when (kind) {
                                        BadgeKind.NUMBER -> Badge { Text(badgeNumber(dest.route, badges).toString()) }
                                        BadgeKind.DOT -> Badge()
                                        BadgeKind.NONE -> {}
                                    }
                                },
                            ) {
                                Icon(dest.icon, contentDescription = stringResource(dest.labelRes))
                            }
                        },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            },
        ) {
            Scaffold(
                // The inner screens each own a TopAppBar that already applies the status-bar inset, and
                // NavigationSuiteScaffold already offsets content above the nav bar. Zero this Scaffold's
                // insets so the status-bar inset isn't applied twice (which left a blank gap up top).
                contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Home,
                    modifier = androidx.compose.ui.Modifier.padding(innerPadding),
                    // Top-level <-> top-level slides horizontally by relative tab order; pushing a
                    // subpage (module scope / repo detail) slides in from the right like a new screen.
                    enterTransition = {
                        val dir = slideDirection(initialState, targetState)
                        slideIntoContainer(dir, animationSpec = tween(NAV_ANIM_MS)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_MS))
                    },
                    exitTransition = {
                        val dir = slideDirection(initialState, targetState)
                        slideOutOfContainer(dir, animationSpec = tween(NAV_ANIM_MS)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_MS))
                    },
                    popEnterTransition = {
                        val dir = slideDirection(initialState, targetState)
                        slideIntoContainer(dir, animationSpec = tween(NAV_ANIM_MS)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_MS))
                    },
                    popExitTransition = {
                        val dir = slideDirection(initialState, targetState)
                        slideOutOfContainer(dir, animationSpec = tween(NAV_ANIM_MS)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_MS))
                    },
                ) {
                    composable<Home> { HomeRoute() }
                    composable<Modules> {
                        ModulesRoute(onNavigate = { route -> navController.navigate(route) })
                    }
                    composable<Repo> {
                        RepoRoute(
                            onModuleClick = { m -> navController.navigate(RepoItem(m.name.orEmpty())) },
                        )
                    }
                    composable<Logs> { LogsRoute() }
                    composable<Settings> { SettingsRoute() }
                    composable<AppList> { entry ->
                        val args = entry.toRoute<AppList>()
                        AppListRoute(
                            modulePackageName = args.modulePackageName,
                            moduleUserId = args.moduleUserId,
                            onNavigateUp = { navController.navigateUp() },
                        )
                    }
                    composable<RepoItem> { entry ->
                        val args = entry.toRoute<RepoItem>()
                        RepoItemRoute(packageName = args.modulePackageName)
                    }
                }
            }
        }
    }
}

private fun badgeNumber(route: Any, badges: ShellViewModel.Badges): Int = when (route) {
    Modules -> badges.enabledModules
    Repo -> badges.upgradableModules
    else -> 0
}

/** Shared duration for the shell's navigation slide/fade transitions. */
private const val NAV_ANIM_MS = 300

/**
 * Top-level destinations in left-to-right bottom-bar order. Used to pick the slide direction when
 * moving between two top-level tabs (right-of -> slide in from the right, left-of -> from the left).
 */
private val topLevelOrder: List<kotlin.reflect.KClass<*>> = listOf(
    Repo::class, Modules::class, Home::class, Logs::class, Settings::class,
)

/** Subpages are pushed onto the back stack like a new activity (no bottom bar). */
private fun NavDestination.isSubPage(): Boolean =
    hasRoute(AppList::class) || hasRoute(RepoItem::class)

/** Order index of a top-level destination, or -1 if it isn't one. */
private fun NavDestination.topLevelIndex(): Int =
    topLevelOrder.indexOfFirst { hasRoute(it) }

/**
 * Picks the horizontal slide direction for a transition between [initial] and [target]:
 * - to a subpage: slide forward (in from the right); back out reverses automatically via pop*.
 * - between top-level tabs: compare their bottom-bar order.
 */
private fun slideDirection(
    initial: androidx.navigation.NavBackStackEntry,
    target: androidx.navigation.NavBackStackEntry,
): AnimatedContentTransitionScope.SlideDirection {
    val initDest = initial.destination
    val targetDest = target.destination
    // Forward into a subpage, or backward out of one.
    if (targetDest.isSubPage() && !initDest.isSubPage()) {
        return AnimatedContentTransitionScope.SlideDirection.Start
    }
    if (initDest.isSubPage() && !targetDest.isSubPage()) {
        return AnimatedContentTransitionScope.SlideDirection.End
    }
    val from = initDest.topLevelIndex()
    val to = targetDest.topLevelIndex()
    return if (from >= 0 && to >= 0 && to < from) {
        AnimatedContentTransitionScope.SlideDirection.End
    } else {
        AnimatedContentTransitionScope.SlideDirection.Start
    }
}

/** Navigates to a top-level destination, single-top and popping back to the graph start. */
private fun navigateTopLevel(navController: NavController, route: Any) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Mirrors the legacy MainActivity.handleIntent deep-link / shortcut routing. */
private fun routeIntent(intent: Intent, navController: NavController, shell: ShellViewModel) {
    val action = intent.action
    if (action == "android.intent.action.APPLICATION_PREFERENCES") {
        navigateTopLevel(navController, Settings)
        return
    }
    if (!shell.binderAlive) return
    val dataString = intent.dataString
    if (TextUtils.isEmpty(dataString)) return
    when (dataString) {
        "modules" -> navigateTopLevel(navController, Modules)
        "logs" -> navigateTopLevel(navController, Logs)
        "repo" -> if (shell.magiskInstalled) navigateTopLevel(navController, Repo)
        "settings" -> navigateTopLevel(navController, Settings)
        else -> {
            val data = intent.data
            if (data != null && data.scheme == "module") {
                val userId = data.port.takeIf { it >= 0 } ?: 0
                navController.navigate(AppList(data.host.orEmpty(), userId)) {
                    launchSingleTop = true
                    popUpTo(navController.graph.findStartDestination().id)
                }
            }
        }
    }
}
