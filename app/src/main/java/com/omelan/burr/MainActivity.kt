package com.omelan.burr

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayout
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Providers
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.staticAmbientOf
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.viewinterop.viewModel
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navDeepLink
import com.omelan.burr.model.AppDatabase
import com.omelan.burr.model.Recipe
import com.omelan.burr.model.RecipeViewModel
import com.omelan.burr.model.StepsViewModel
import com.omelan.burr.pages.RecipeDetails
import com.omelan.burr.pages.RecipeEdit
import com.omelan.burr.pages.RecipeList
import com.omelan.burr.pages.settings.AppSettings
import com.omelan.burr.pages.settings.AppSettingsAbout
import com.omelan.burr.pages.settings.Licenses
import com.omelan.burr.ui.BurrTheme
import com.omelan.burr.utils.SystemUIHelpers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.time.ExperimentalTime

val AmbientPiPState = staticAmbientOf<Boolean> {
    error("AmbientPiPState value not available.")
}

const val appDeepLinkUrl = "https://burr.omelan.com"

@ExperimentalTime
class MainActivity : AppCompatActivity() {
    private val mainActivityViewModel: MainActivityViewModel by viewModels()
    @ExperimentalMaterialApi
    @ExperimentalLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Burr)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        SystemUIHelpers.setStatusBarIconsTheme(window = window, darkIcons = false)
        setContent {
            MainNavigation()
        }
    }

    @ExperimentalMaterialApi
    @ExperimentalLayout
    @Composable
    fun MainNavigation() {
        val navController = rememberNavController()
        val db = AppDatabase.getInstance(this)
        val isInPiP = mainActivityViewModel.pipState.observeAsState(false)
        // Transition animations for navController.navigate are not supported right now
        // monitor this issue https://issuetracker.google.com/issues/172112072
        // val builder: NavOptionsBuilder.() -> Unit = {
        //     anim {
        //         enter = android.R.anim.slide_out_right
        //         exit = android.R.anim.fade_out
        //         popEnter = android.R.anim.slide_out_right
        //         popExit = android.R.anim.fade_out
        //     }
        // }
        val goBack: () -> Unit = {
            navController.popBackStack()
        }
        BurrTheme {
            Providers(AmbientPiPState provides isInPiP.value) {
                Column {
                    NavHost(navController, startDestination = "list") {
                        composable("list") {
                            mainActivityViewModel.setCanGoToPiP(false)
                            RecipeList(
                                navigateToRecipe = { recipeId ->
                                    navController.navigate(
                                        route = "recipe/${recipeId}",
                                    )
                                },
                                addNewRecipe = {
                                    navController.navigate(
                                        route = "add_recipe",
                                    )
                                },
                                goToSettings = {
                                    navController.navigate(
                                        route = "settings"
                                    )
                                }
                            )
                        }
                        composable(
                            "recipe/{recipeId}",
                            arguments = listOf(navArgument("recipeId") { type = NavType.IntType }),
                            deepLinks = listOf(navDeepLink { uriPattern = "$appDeepLinkUrl/recipe/{recipeId}" }),
                        ) { backStackEntry ->
                            val recipeId = backStackEntry.arguments?.getInt("recipeId")
                                ?: throw IllegalStateException("No Recipe ID")
                            mainActivityViewModel.setCanGoToPiP(true)
                            RecipeDetails(
                                recipeId = recipeId,
                                onRecipeEnd = { recipe ->
                                    lifecycleScope.launch {
                                        db.recipeDao()
                                            .updateRecipe(recipe.copy(lastFinished = Date().time))
                                    }
                                },
                                goBack = goBack,
                                goToEdit = {
                                    navController.navigate(
                                        route = "edit/${recipeId}",
                                    )
                                }
                            )
                        }
                        composable(
                            "edit/{recipeId}",
                            arguments = listOf(navArgument("recipeId") { type = NavType.IntType }),
                        ) { backStackEntry ->
                            val recipeId = backStackEntry.arguments?.getInt("recipeId")
                                ?: throw IllegalStateException("No Recipe ID")
                            mainActivityViewModel.setCanGoToPiP(false)
                            val recipeViewModel: RecipeViewModel = viewModel()
                            val stepsViewModel: StepsViewModel = viewModel()
                            val recipe = recipeViewModel.getRecipe(recipeId)
                                .observeAsState(Recipe(name = "", description = ""))
                            val steps = stepsViewModel.getAllStepsForRecipe(recipeId)
                                .observeAsState(listOf())
                            RecipeEdit(
                                goBack = goBack,
                                isEditing = true,
                                recipeToEdit = recipe.value,
                                stepsToEdit = steps.value,
                                saveRecipe = { _recipe, _steps ->
                                    lifecycleScope.launch {
                                        db.recipeDao().updateRecipe(_recipe)
                                        db.stepDao().updateSteps(_steps)
                                    }
                                    goBack()
                                },
                                deleteRecipe = {
                                    lifecycleScope.launch {
                                        db.recipeDao().deleteById(recipeId = recipeId)
                                        db.stepDao()
                                            .deleteAllStepsForRecipe(recipeId = recipeId)
                                    }
                                    navController.navigate("list") {
                                        this.popUpTo("list") {
                                            inclusive = true
                                        }
                                    }
                                })
                        }
                        composable("add_recipe") {
                            mainActivityViewModel.setCanGoToPiP(false)
                            RecipeEdit(
                                saveRecipe = { recipe, steps ->
                                    lifecycleScope.launch {
                                        val idOfRecipe =
                                            db.recipeDao().insertRecipe(recipe)
                                        db.stepDao()
                                            .insertAll(steps.map { it.copy(recipeId = idOfRecipe.toInt()) })

                                    }
                                    goBack()
                                },
                                goBack = goBack,
                            )
                        }
                        navigation(startDestination = "list", route = "settings") {
                            composable("list") {
                                AppSettings(
                                    goBack = {
                                        navController.navigate(
                                            route = "list",
                                        )
                                    },
                                    goToAbout = {
                                        navController.navigate("about")
                                    },
                                )
                            }
                            composable("about") {
                                AppSettingsAbout(goBack = goBack, openLicenses = {
                                    navController.navigate("licenses")
                                })
                            }
                            composable("licenses") {
                                Licenses(goBack = goBack)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        SystemUIHelpers.setStatusBarIconsTheme(window = window, darkIcons = false)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        mainActivityViewModel.setIsInPiP(isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            mainActivityViewModel.canGoToPiP.value == true
        ) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(1, 1)).build()
            )
        }
    }
}
