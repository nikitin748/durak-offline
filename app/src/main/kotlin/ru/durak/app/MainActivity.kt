package ru.durak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Root(vm) }
    }
}

@Composable
fun Root(vm: AppVm) {
    BackHandler(enabled = vm.screen != Screen.MENU) { vm.leaveToMenu() }
    when (vm.screen) {
        Screen.MENU -> MenuScreen(vm)
        Screen.HOST_LOBBY -> HostLobbyScreen(vm)
        Screen.FIND_ROOM -> FinderScreen(vm)
        Screen.CLIENT_LOBBY -> ClientLobbyScreen(vm)
        Screen.GAME -> GameScreen(vm)
    }
}
