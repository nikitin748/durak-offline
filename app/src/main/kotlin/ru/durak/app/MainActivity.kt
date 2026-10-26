package ru.durak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable

class MainActivity : ComponentActivity() {
    val vm: AppVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crash = pendingCrash()
        if (crash != null) { showCrash(crash); return }
        enableEdgeToEdge()
        setContent { Root(vm) }
    }
}

private fun MainActivity.showCrash(text: String) {
    // Plain views on purpose: must work even if Compose is what crashed.
    val tv = android.widget.TextView(this).apply {
        this.text = "Прошлый запуск завершился сбоем. Сделайте скриншот этого экрана и отправьте разработчику.\n\n$text"
        textSize = 11f; setTextIsSelectable(true); setPadding(24, 48, 24, 24)
    }
    val btn = android.widget.Button(this).apply {
        this.text = "Очистить и запустить снова"
        setOnClickListener { clearCrash(); recreate() }
    }
    val col = android.widget.LinearLayout(this).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        addView(btn)
        addView(android.widget.ScrollView(this@showCrash).apply { addView(tv) })
    }
    setContentView(col)
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
