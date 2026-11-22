package com.fabric.ledger

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.fabric.ledger.ui.LockScreen
import com.fabric.ledger.ui.NewSaleScreen

/** يُقفل التطبيق إن غاب في الخلفية أكثر من هذه المدة */
private const val LOCK_AFTER_MS = 30_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

@androidx.compose.runtime.Composable
fun AppRoot() {
    val vm: MainViewModel = viewModel()
    val context = LocalContext.current
    val locked by vm.locked.collectAsStateWithLifecycle()

    var stoppedAt by remember { mutableLongStateOf(0L) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { stoppedAt = System.currentTimeMillis() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (stoppedAt > 0L && System.currentTimeMillis() - stoppedAt >= LOCK_AFTER_MS) vm.lockIfEnabled()
    }
    // رسائل الأخطاء العامة (مثل تعذر الحفظ)
    LaunchedEffect(vm) { vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() } }

    AppTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
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
                    // شاشة القفل فوق كل شيء؛ الحالة تحتها تبقى كما هي
                    if (locked) LockScreen(vm.pinLength()) { pin -> vm.unlock(pin) }
                }
            }
        }
    }
}
