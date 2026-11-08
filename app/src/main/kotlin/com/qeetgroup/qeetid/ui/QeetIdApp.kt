package com.qeetgroup.qeetid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.qeetgroup.qeetid.AppContainer
import com.qeetgroup.qeetid.admin.AdminAgentsRoute
import com.qeetgroup.qeetid.admin.AdminEventScreenRoute
import com.qeetgroup.qeetid.admin.AdminEventsRoute
import com.qeetgroup.qeetid.admin.AdminHomeRoute
import com.qeetgroup.qeetid.admin.AdminOrganizationsRoute
import com.qeetgroup.qeetid.admin.AdminPeopleRoute
import com.qeetgroup.qeetid.admin.AdminRequestScreenRoute
import com.qeetgroup.qeetid.admin.AdminRequestsRoute
import com.qeetgroup.qeetid.admin.AdminSignInRoute
import com.qeetgroup.qeetid.admin.AdminSuspendRoute
import com.qeetgroup.qeetid.admin.NoPasskeys
import com.qeetgroup.qeetid.admin.PasskeyPrompt
import com.qeetgroup.qeetid.approvals.LinkPhoneRoute
import com.qeetgroup.qeetid.approvals.NamePhoneRoute
import com.qeetgroup.qeetid.approvals.PhoneLinkedRoute
import com.qeetgroup.qeetid.approvals.RequestsWaitingRoute
import com.qeetgroup.qeetid.approvals.ScanPairingRoute
import com.qeetgroup.qeetid.approvals.SignInRequestScreenRoute
import com.qeetgroup.qeetid.codes.AccountsRoute
import com.qeetgroup.qeetid.codes.ScanAccountRoute
import com.qeetgroup.qeetid.codes.TypeKeyRoute
import com.qeetgroup.qeetid.lock.LockedScreen
import com.qeetgroup.qeetid.lock.UserVerification
import com.qeetgroup.qeetid.onboarding.LockSetupScreen
import com.qeetgroup.qeetid.onboarding.NotificationsPrimerScreen
import com.qeetgroup.qeetid.onboarding.WelcomeScreen
import com.qeetgroup.qeetid.settings.AboutRoute
import com.qeetgroup.qeetid.settings.MoveRoute
import com.qeetgroup.qeetid.settings.SettingsRoute
import com.qeetgroup.qeetid.settings.UpdateRequiredScreen
import com.qeetgroup.qeetrix.theme.QeetrixTheme

/**
 * The whole app: Qeetrix's theme (light or dark from the system), the lock, and the screens. While locked only the
 * Locked screen is composed; the back stack waits in [navController] and comes back on unlock.
 */
@Composable
fun QeetIdApp(
    container: AppContainer,
    verification: UserVerification,
    passkeys: PasskeyPrompt = NoPasskeys,
    navController: NavHostController = rememberNavController(),
) {
    QeetrixTheme {
        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalUserVerification provides verification,
            LocalPasskeys provides passkeys,
        ) {
            val onboarded by container.preferences.onboarded.collectAsStateWithLifecycle()
            val locked by container.lock.locked.collectAsStateWithLifecycle()
            val next by container.inbox.next.collectAsStateWithLifecycle()
            val updateRequired by container.api.updateRequired.collectAsStateWithLifecycle()
            val start: Any = remember { if (container.preferences.onboarded.value) Accounts else Welcome }

            if (locked) {
                LockedScreen()
            } else {
                AppNavHost(navController, start, container)
                // A request or a pairing link waiting to be opened: once the app is unlocked and set up.
                LaunchedEffect(next, onboarded) {
                    val route = next ?: return@LaunchedEffect
                    if (!onboarded) return@LaunchedEffect
                    navController.navigate(route) { launchSingleTop = true }
                    container.inbox.opened(route)
                }
                // Qeet ID no longer accepts this version for approvals: say so once; codes still work.
                LaunchedEffect(updateRequired, onboarded) {
                    if (updateRequired && onboarded) navController.navigate(UpdateRequired) { launchSingleTop = true }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    start: Any,
    container: AppContainer,
) {
    val onboardingSteps = 2
    NavHost(navController, startDestination = start) {
        composable<Welcome> { WelcomeScreen(onGetStarted = { navController.navigate(LockSetup) }) }
        composable<LockSetup> {
            LockSetupScreen(
                totalSteps = onboardingSteps,
                onBack = { navController.popBackStack() },
                onDone = { navController.navigate(NotificationsPrimer) },
            )
        }
        composable<NotificationsPrimer> {
            NotificationsPrimerScreen(
                totalSteps = onboardingSteps,
                onBack = { navController.popBackStack() },
                onDone = {
                    container.preferences.setOnboarded(true)
                    navController.navigate(Accounts) { popUpTo(navController.graph.id) { inclusive = true } }
                },
            )
        }
        composable<Accounts> { entry -> AccountsRoute(navController, entry) }
        composable<ScanAccount> { ScanAccountRoute(navController) }
        composable<TypeKey> { TypeKeyRoute(navController) }
        composable<LinkPhone> { LinkPhoneRoute(navController) }
        composable<ScanPairing> { ScanPairingRoute(navController) }
        composable<NamePhone> { entry -> NamePhoneRoute(navController, entry.toRoute<NamePhone>().code) }
        composable<PhoneLinked> { PhoneLinkedRoute(navController) }
        composable<SignInRequestRoute> { entry -> SignInRequestScreenRoute(navController, entry.toRoute<SignInRequestRoute>().id) }
        composable<RequestsWaiting> { RequestsWaitingRoute(navController) }
        composable<Settings> { SettingsRoute(navController) }
        composable<About> { AboutRoute(navController) }
        composable<MoveDevice> { MoveRoute(navController) }
        composable<AdminSignIn> { AdminSignInRoute(navController) }
        composable<AdminOrganizations> { AdminOrganizationsRoute(navController) }
        composable<AdminHome> { AdminHomeRoute(navController) }
        composable<AdminRequests> { AdminRequestsRoute(navController) }
        composable<AdminRequestRoute> { entry -> AdminRequestScreenRoute(navController, entry.toRoute<AdminRequestRoute>().id) }
        composable<AdminPeople> { AdminPeopleRoute(navController) }
        composable<AdminSuspend> { entry -> AdminSuspendRoute(navController, entry.toRoute<AdminSuspend>().userId) }
        composable<AdminAgents> { AdminAgentsRoute(navController) }
        composable<AdminEvents> { AdminEventsRoute(navController) }
        composable<AdminEventRoute> { entry -> AdminEventScreenRoute(navController, entry.toRoute<AdminEventRoute>().id) }
        composable<UpdateRequired> {
            UpdateRequiredScreen(
                onShowCodes = { if (!navController.popBackStack(Accounts, inclusive = false)) navController.navigate(Accounts) },
            )
        }
    }
}
