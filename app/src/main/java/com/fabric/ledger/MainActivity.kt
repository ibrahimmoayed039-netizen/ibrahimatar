package com.fabric.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fabric.ledger.ui.AppTheme
import com.fabric.ledger.ui.CustomerScreen
import com.fabric.ledger.ui.FabricScreen
import com.fabric.ledger.ui.HomeScreen
import com.fabric.ledger.ui.NewSaleScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

@androidx.compose.runtime.Composable
fun AppRoot() {
    AppTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                val vm: MainViewModel = viewModel()
                val nav = rememberNavController()
                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            vm,
                            onOpenCustomer = { nav.navigate("customer/$it") },
                            onNewSale = { nav.navigate("sale/-1") },
                            onEditSale = { nav.navigate("editSale/$it") },
                            onOpenFabric = { nav.navigate("fabric/$it") }
                        )
                    }
                    composable("fabric/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        FabricScreen(vm, it.arguments!!.getLong("id"), onBack = { nav.popBackStack() })
                    }
                    composable("customer/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                        val id = it.arguments!!.getLong("id")
                        CustomerScreen(
                            vm, id,
                            onBack = { nav.popBackStack() },
                            onNewSale = { nav.navigate("sale/$id") },
                            onEditSale = { sid -> nav.navigate("editSale/$sid") }
                        )
                    }
                    composable("sale/{cid}", arguments = listOf(navArgument("cid") { type = NavType.LongType })) {
                        NewSaleScreen(vm, it.arguments!!.getLong("cid"), editSaleId = -1L, onDone = { nav.popBackStack() })
                    }
                    composable("editSale/{sid}", arguments = listOf(navArgument("sid") { type = NavType.LongType })) {
                        NewSaleScreen(vm, -1L, editSaleId = it.arguments!!.getLong("sid"), onDone = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
