package com.omelan.cofi.pages.settings.licenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.omelan.cofi.R
import com.omelan.cofi.components.PiPAwareAppBar
import com.omelan.cofi.components.createAppBarBehavior
import com.omelan.cofi.ui.Spacing
import com.omelan.cofi.utils.parseJsonToDependencyList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesList(goBack: () -> Unit) {
    val context = LocalContext.current
    val appBarBehavior = createAppBarBehavior()
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current

    val dependencyList = context.assets.open("open_source_licenses.json").bufferedReader().use {
        it.readText()
    }.parseJsonToDependencyList()
    Scaffold(
        topBar = {
            PiPAwareAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.settings_licenses_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(imageVector = Icons.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = appBarBehavior,
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .nestedScroll(appBarBehavior.nestedScrollConnection)
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = navigationBarPadding.calculateStartPadding(layoutDirection) +
                    it.calculateStartPadding(layoutDirection) + Spacing.big,
                top = navigationBarPadding.calculateTopPadding() +
                    it.calculateTopPadding() + Spacing.small,
                bottom = navigationBarPadding.calculateBottomPadding() +
                    it.calculateBottomPadding() + Spacing.big,
                end = navigationBarPadding.calculateEndPadding(layoutDirection) +
                    it.calculateEndPadding(layoutDirection) + Spacing.big
            ),
        ) {
            items(dependencyList) {
                DependencyItem(dependency = it)
            }
        }
    }
}