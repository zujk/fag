package com.studiofagnani.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.studiofagnani.AppContainer
import com.studiofagnani.StudioFagnaniApp
import com.studiofagnani.ui.library.ClipDetailScreen
import com.studiofagnani.ui.library.ClipDetailViewModel
import com.studiofagnani.ui.library.LibraryScreen
import com.studiofagnani.ui.library.LibraryViewModel
import com.studiofagnani.ui.practice.PracticeScreen
import com.studiofagnani.ui.practice.PracticeViewModel
import com.studiofagnani.ui.session.SessionScreen
import com.studiofagnani.ui.session.SessionViewModel
import com.studiofagnani.ui.settings.SettingsScreen
import com.studiofagnani.ui.settings.SettingsViewModel
import com.studiofagnani.ui.theme.StudioFagnaniTheme
import com.studiofagnani.ui.today.TodayScreen
import com.studiofagnani.ui.today.TodayViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as StudioFagnaniApp).container
        setContent {
            StudioFagnaniTheme {
                AppNav(container)
            }
        }
    }
}

private object Rotte {
    const val OGGI = "oggi"
    const val LIBRERIA = "libreria"
    const val IMPOSTAZIONI = "impostazioni"
    const val SESSIONE = "sessione"
    const val CLIP = "clip/{clipId}"
    const val ESERCIZIO = "esercizio/{coda}"

    fun clip(id: String) = "clip/$id"

    /** Gli id delle clip sono slug senza virgole: si possono mettere in fila così. */
    fun esercizio(coda: List<String>) = "esercizio/${coda.joinToString(",")}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val rotta = entry?.destination?.route
    val mostraBarra = rotta == Rotte.OGGI || rotta == Rotte.LIBRERIA ||
        rotta == Rotte.IMPOSTAZIONI

    Scaffold(
        topBar = {
            if (!mostraBarra) {
                TopAppBar(
                    title = {
                        Text(
                            when {
                                rotta == Rotte.SESSIONE -> "Sessione di oggi"
                                rotta?.startsWith("esercizio") == true -> "Esercizio"
                                else -> "Clip"
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { nav.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (mostraBarra) {
                NavigationBar {
                    val destinazioni = entry?.destination?.hierarchy
                    NavigationBarItem(
                        selected = destinazioni?.any { it.route == Rotte.OGGI } == true,
                        onClick = { nav.navigateTab(Rotte.OGGI) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Oggi") },
                    )
                    NavigationBarItem(
                        selected = destinazioni?.any { it.route == Rotte.LIBRERIA } == true,
                        onClick = { nav.navigateTab(Rotte.LIBRERIA) },
                        icon = { Icon(Icons.Filled.List, contentDescription = null) },
                        label = { Text("Libreria") },
                    )
                    NavigationBarItem(
                        selected = destinazioni?.any { it.route == Rotte.IMPOSTAZIONI } == true,
                        onClick = { nav.navigateTab(Rotte.IMPOSTAZIONI) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Impostazioni") },
                    )
                }
            }
        },
    ) { insets ->
        NavHost(
            navController = nav,
            startDestination = Rotte.OGGI,
            modifier = Modifier.fillMaxSize().padding(insets),
        ) {
            composable(Rotte.OGGI) {
                val vm: TodayViewModel = viewModel(factory = TodayViewModel.factory(container))
                TodayScreen(
                    vm = vm,
                    onIniziaSessione = { nav.navigate(Rotte.SESSIONE) },
                    onIniziaRipasso = { coda ->
                        if (coda.isNotEmpty()) nav.navigate(Rotte.esercizio(coda))
                    },
                    onVaiInLibreria = { nav.navigateTab(Rotte.LIBRERIA) },
                )
            }

            composable(Rotte.IMPOSTAZIONI) {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(container),
                )
                SettingsScreen(vm = vm)
            }

            composable(Rotte.SESSIONE) {
                // Il ViewModel e' legato a questa voce dello stack, quindi la
                // sessione sopravvive all'andata e ritorno verso l'esercizio.
                val vm: SessionViewModel = viewModel(factory = SessionViewModel.factory(container))
                SessionScreen(
                    vm = vm,
                    onEsercizioSuClip = { coda ->
                        if (coda.isNotEmpty()) nav.navigate(Rotte.esercizio(coda))
                    },
                    onFine = { nav.navigateUp() },
                )
            }

            composable(Rotte.LIBRERIA) {
                val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
                LibraryScreen(vm = vm, onApriClip = { nav.navigate(Rotte.clip(it)) })
            }

            composable(
                Rotte.CLIP,
                arguments = listOf(navArgument("clipId") { type = NavType.StringType }),
            ) { backStack ->
                val clipId = backStack.arguments?.getString("clipId").orEmpty()
                val vm: ClipDetailViewModel = viewModel(
                    factory = ClipDetailViewModel.factory(container, clipId),
                )
                ClipDetailScreen(
                    vm = vm,
                    onEsercitati = { nav.navigate(Rotte.esercizio(listOf(it))) },
                )
            }

            composable(
                Rotte.ESERCIZIO,
                arguments = listOf(navArgument("coda") { type = NavType.StringType }),
            ) { backStack ->
                val coda = backStack.arguments?.getString("coda")
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                val vm: PracticeViewModel = viewModel(
                    factory = PracticeViewModel.factory(container, coda),
                )
                PracticeScreen(vm = vm, onFine = { nav.navigateUp() })
            }
        }
    }
}

/** Passaggio fra le due schede senza accumulare copie nello stack. */
private fun androidx.navigation.NavHostController.navigateTab(rotta: String) {
    navigate(rotta) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
