package com.qeetgroup.qeetid.codes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import com.qeetgroup.qeetid.R
import com.qeetgroup.qeetid.ui.Screen
import com.qeetgroup.qeetrix.components.emptystate.QeetrixEmptyState
import com.qeetgroup.qeetrix.components.navigation.QeetrixTopAppBar

@Composable
fun AccountsRoute(
    @Suppress("UNUSED_PARAMETER") navController: NavHostController,
) {
    Screen(topBar = { QeetrixTopAppBar(stringResource(R.string.accounts_title)) }) {
        QeetrixEmptyState(stringResource(R.string.accounts_empty_title))
    }
}
