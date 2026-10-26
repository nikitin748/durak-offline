package ru.durak.app

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.durak.engine.Card
import ru.durak.engine.GameView
import java.io.File
import java.io.FileOutputStream

/**
 * Plays whole games against bots purely through the UI (taps and drags on cards and buttons),
 * checking after every move that the engine accepted it, and saves screenshots for review.
 */
@RunWith(AndroidJUnit4::class)
class PlaythroughTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private var shotNo = 0

    private fun snap(name: String) {
        // The test clock drives Compose frames: advance it so the screen shows the current state.
        rule.mainClock.advanceTimeBy(1200)
        rule.waitForIdle()
        Thread.sleep(300)
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(rule.activity.getExternalFilesDir(null), "shots").apply { mkdirs() }
        FileOutputStream(File(dir, "%02d_%s.png".format(shotNo++, name))).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    private val vm get() = rule.activity.vm
    private fun view(): GameView = vm.view!!

    private fun myTurn(v: GameView): Boolean {
        val l = v.legal
        return v.gameOver || l.defend.isNotEmpty() || l.canTake || (l.attack.isNotEmpty() && v.table.isEmpty()) || l.canPass
    }

    private fun waitChange(timeout: Long = 15000, what: String, cond: (GameView) -> Boolean) {
        try { rule.waitUntil(timeout) { cond(view()) } }
        catch (e: Throwable) { snap("FAIL_$what"); throw AssertionError("UI action had no effect: $what. view=${view()}", e) }
    }

    private fun tapCard(c: Card) {
        rule.onNodeWithTag("hand:$c").performTouchInput { click(Offset(width * 0.15f, height * 0.3f)) }
    }

    private fun dragCardTo(c: Card, targetTag: String?) {
        val from = rule.onNodeWithTag("hand:$c").fetchSemanticsNode().boundsInRoot
        val to = if (targetTag != null) rule.onNodeWithTag(targetTag).fetchSemanticsNode().boundsInRoot.center
        else Offset(rule.onRoot().fetchSemanticsNode().boundsInRoot.center.x, from.top - 350f)
        rule.onNodeWithTag("hand:$c").performTouchInput {
            val start = Offset(width * 0.25f, height * 0.3f)
            swipe(start, start + (to - Offset(from.left + width * 0.25f, from.top + height * 0.3f)), 500)
        }
    }

    private fun playOneGame(players: Int, transfer: Boolean, seed: Long, label: String) {
        rule.runOnUiThread { vm.hostSolo(players, transfer, seed) }
        rule.waitUntil(10000) { vm.view != null }
        snap("${label}_deal")
        var defendsByTap = 0; var defendsByDrag = 0; var attacks = 0; var takes = 0; var passes = 0; var steps = 0
        while (true) {
            rule.waitUntil(60000) { myTurn(view()) }
            val v = view()
            if (v.gameOver) break
            check(++steps < 500) { "game too long" }
            val l = v.legal
            when {
                l.defend.isNotEmpty() -> {
                    val idx = v.table.indexOfFirst { p -> p.defense == null && l.defend.any { it.index == v.table.indexOf(p) } }
                    val opt = l.defend.filter { it.index == idx }.minBy { it.card.rank + if (it.card.suit == v.trump) 100 else 0 }
                    val useDrag = defendsByDrag < 3 && steps % 2 == 0
                    if (useDrag) { dragCardTo(opt.card, "attack:$idx"); defendsByDrag++ }
                    else {
                        tapCard(opt.card)
                        if (l.defend.count { it.card == opt.card } > 1) {
                            rule.waitForIdle()
                            rule.onNodeWithTag("attack:$idx").performTouchInput { click() }
                        }
                        defendsByTap++
                    }
                    waitChange(what = "defend ${opt.card} on #$idx (drag=$useDrag)") { opt.card !in it.myHand }
                    if (defendsByTap + defendsByDrag <= 2) snap("${label}_defended")
                }
                l.canTake -> {
                    rule.onNodeWithTag("btn:take").performClick(); takes++
                    waitChange(what = "take") { !it.legal.canTake }
                }
                l.attack.isNotEmpty() && v.table.isEmpty() -> {
                    val c = l.attack.minBy { it.rank + if (it.suit == v.trump) 100 else 0 }
                    if (attacks % 2 == 0) tapCard(c) else dragCardTo(c, null)
                    attacks++
                    waitChange(what = "attack $c") { c !in it.myHand }
                    if (attacks <= 2) snap("${label}_attack")
                }
                l.canPass -> {
                    rule.onNodeWithTag("btn:pass").performClick(); passes++
                    waitChange(what = "pass") { !it.legal.canPass || it.table.isEmpty() }
                }
            }
        }
        snap("${label}_gameover")
        Log.i("Playthrough", "$label: steps=$steps tapDef=$defendsByTap dragDef=$defendsByDrag attacks=$attacks takes=$takes passes=$passes loser=${view().loser}")
        assertTrue("never defended", defendsByTap + defendsByDrag > 0)
    }

    @Test fun playGamesThroughUi() {
        snap("menu")
        playOneGame(2, false, 11, "duel")
        playOneGame(4, true, 7, "four_transfer")
    }
}
