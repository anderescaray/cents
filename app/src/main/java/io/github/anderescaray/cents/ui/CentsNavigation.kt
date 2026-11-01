package io.github.anderescaray.cents.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import io.github.anderescaray.cents.ui.components.EaseOutExpo
import io.github.anderescaray.cents.ui.navigation.Destination
import io.github.anderescaray.cents.ui.navigation.SheetForms
import io.github.anderescaray.cents.ui.navigation.TopLevelTab
import io.github.anderescaray.cents.ui.navigation.routes

/** How long a push or pop takes: a brisk slide, not a slow fade. */
private const val PUSH_DURATION_MS = 320

/** How long changing tabs takes: a quick cross-fade, because the capsule already moved. */
private const val TAB_FADE_MS = 180

/** How long a sheet takes to rise, and to fall away again. */
private const val SHEET_RISE_MS = 380
private const val SHEET_FALL_MS = 260

/**
 * The tab a destination belongs to: the tab it *is*, or the tab it was pushed inside.
 *
 * Null for a screen more than one tab can reach — see [TopLevelTab.owns], which is where that
 * is decided, so the bar and the slide can never disagree about who owns what.
 */
internal fun NavDestination.owningTab(): TopLevelTab? =
    TopLevelTab.entries.firstOrNull { tab -> tab.routes.any { hasRoute(it) } }

/**
 * The tab the bar shows as selected: the nearest screen *down* the stack that a tab owns.
 *
 * Reading only the top of the stack is what used to light *Transactions* over an account's
 * detail screen — a pushed destination matched no tab and the bar fell back to the first one.
 * Reading the whole stack answers the screens two tabs can reach as well, and answers them
 * correctly rather than by convention: the movement form sits above an account's detail when it
 * was opened from an account, and directly above the list when it was opened from the list, so
 * the same screen is filed under wherever you actually opened it.
 *
 * **Remembering the last tab instead would be wrong, and it is the version this branch shipped
 * first.** Each tab keeps its own saved stack (`switchToTab`), so leaving Accounts with a
 * movement form open, visiting Reports and coming back restores that form — and a single
 * remembered value would still be saying *Reports*, a tab the owner had just left. The stack is
 * the thing that knows, and it survives a rotation and process death without being saved
 * separately.
 *
 * Null before the graph is set — the one state where there is honestly no answer rather than a
 * first tab to fall back on.
 */
internal fun List<NavBackStackEntry>.selectedTab(): TopLevelTab? =
    asReversed().firstNotNullOfOrNull { it.destination.owningTab() }

/**
 * Which way this navigation is travelling, so the slide agrees with it.
 *
 * Every `navigate()` is a push as far as the `NavHost` is concerned, and a tab switch is a
 * `navigate()` — so with a single direction the animation said "forward" even when the tab
 * bar had moved you *left*, which reads as wrong every time and is the animation seen most.
 * `TopLevelTab` already orders the tabs, so the sign is knowable: moving to a lower tab is
 * travelling back, and the screen should arrive from the side it went out to.
 *
 * It asks [owningTab] rather than "is this destination a tab", so switching to a tab whose
 * saved stack has a detail screen on top travels the way the bar moved: the screen arriving is
 * an account's detail, but it is Accounts you went to. A push inside a tab has the same tab on
 * both sides and stays a forward slide, and so does anything involving a screen no tab owns.
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.travelDirection() =
    slideDirectionFor(
        from = initialState.destination.owningTab(),
        to = targetState.destination.owningTab(),
    )

/**
 * Separated from the transition so it can be tested: reading a slide direction off a running
 * animation is not something a JVM test can do, but the rule deciding it is arithmetic.
 */
internal fun slideDirectionFor(
    from: TopLevelTab?,
    to: TopLevelTab?,
): AnimatedContentTransitionScope.SlideDirection =
    if (from != null && to != null && to.ordinal < from.ordinal) {
        AnimatedContentTransitionScope.SlideDirection.End
    } else {
        AnimatedContentTransitionScope.SlideDirection.Start
    }

/**
 * Tabs switch, they do not stack: one entry per tab, state preserved.
 *
 * Every tab sits directly on [Destination.Home], the start destination, so back from a tab's root
 * returns home and back from home leaves the app.
 */
internal fun NavHostController.switchToTab(destination: Destination) {
    navigate(destination) {
        popUpTo(Destination.Home) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * A tap on the capsule. Another tab switches to it, keeping where it was left; **the tab already
 * open goes back to its first screen**, the way iOS does. Without that, Home could never be got
 * back to once Settings was opened from it: the tab restores its stack, and a second tap did
 * nothing.
 */
internal fun NavHostController.selectTab(
    tab: TopLevelTab,
    current: TopLevelTab?,
) {
    if (tab == current) {
        popBackStack(tab.destination, inclusive = false)
    } else {
        switchToTab(tab.destination)
    }
}

/**
 * A drill-down arrives at the movements list itself, never at whatever was left open on top
 * of it. `switchToTab` restores the tab's saved stack, and that stack can be holding the
 * filters screen — which is precisely where you go to read the filter a previous drill-down
 * applied. Restoring it would answer "show me this category" with the filter form.
 */
internal fun NavHostController.openMovementsList() {
    switchToTab(Destination.Transactions)
    popBackStack(Destination.Transactions, inclusive = false)
}

/**
 * Whether this is a change of tab rather than a push inside one: both ends belong to a tab, and
 * not the same one. A tap on the capsule cross-fades — the capsule has already shown where you
 * went, and a slide on top of that would be two motions for one tap. A swipe slides, because
 * there the slide is the finger's own motion carried on.
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val from = initialState.destination.owningTab()
    val to = targetState.destination.owningTab()
    return from != null && to != null && from != to
}

private fun NavDestination.isSheet(): Boolean = SheetForms.any { hasRoute(it) }

/**
 * What a screen arriving looks like: a sheet rising for the movement forms, a fade for a change
 * of tab (a slide when [slideTabs] says it was swiped), and a push otherwise. Named rather than
 * written inline four times, because the pop pair differs from the push pair by one word and that
 * is exactly the kind of thing that drifts.
 */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.arriving(
    direction: AnimatedContentTransitionScope.SlideDirection = travelDirection(),
    slideTabs: Boolean = false,
): EnterTransition =
    when {
        targetState.destination.isSheet() && direction != BACKWARDS -> {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Up,
                animationSpec = tween(SHEET_RISE_MS, easing = EaseOutExpo),
            )
        }

        initialState.destination.isSheet() && direction == BACKWARDS -> {
            EnterTransition.None
        }

        // A swipe slides the tabs the way the finger went; a tap on the capsule fades.
        isTabSwitch() && slideTabs -> {
            slideIntoContainer(direction, animationSpec = tween(PUSH_DURATION_MS, easing = EaseOutExpo))
        }

        isTabSwitch() -> {
            fadeIn(tween(TAB_FADE_MS))
        }

        else -> {
            slideIntoContainer(direction, animationSpec = tween(PUSH_DURATION_MS))
        }
    }

/** The mirror of [arriving], for the screen being left behind. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.leaving(
    direction: AnimatedContentTransitionScope.SlideDirection = travelDirection(),
    slideTabs: Boolean = false,
): ExitTransition =
    when {
        // The screen under a rising sheet stays where it is, the way a sheet covers it.
        targetState.destination.isSheet() && direction != BACKWARDS -> {
            ExitTransition.KeepUntilTransitionsFinished
        }

        initialState.destination.isSheet() && direction == BACKWARDS -> {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Down,
                animationSpec = tween(SHEET_FALL_MS),
            )
        }

        isTabSwitch() && slideTabs -> {
            slideOutOfContainer(direction, animationSpec = tween(PUSH_DURATION_MS, easing = EaseOutExpo))
        }

        // The tab left stays opaque while the new one fades in on top of it. Fading both at
        // once dimmed every card halfway through, a blink between two screens that are both lit.
        isTabSwitch() -> {
            ExitTransition.KeepUntilTransitionsFinished
        }

        else -> {
            slideOutOfContainer(direction, animationSpec = tween(PUSH_DURATION_MS))
        }
    }

/** A pop always travels back, whichever way the push that made it came from. */
internal val BACKWARDS = AnimatedContentTransitionScope.SlideDirection.End
