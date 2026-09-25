package com.pixelplumber.core

import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameTest {
    private val palette = intArrayOf(0xE0F8D0, 0x88C070, 0x346856, 0x081820)
    private val shots = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }

    private fun saveShot(g: Game, name: String) {
        val s = g.render()
        val img = BufferedImage(SCREEN_W * 2, SCREEN_H * 2, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until SCREEN_H * 2) for (x in 0 until SCREEN_W * 2) img.setRGB(x, y, palette[s.px[(y / 2) * SCREEN_W + x / 2].toInt()])
        ImageIO.write(img, "png", File(shots, "$name.png"))
    }

    private fun gameAt(level: Int): Game {
        val g = Game()
        val inp = Input()
        repeat(30) { g.update(inp) }
        g.newGame(level)
        g.startLevel()
        return g
    }

    @Test
    fun titleAndStoryFlow() {
        val g = Game()
        val inp = Input()
        repeat(30) { g.update(inp) }
        saveShot(g, "title")
        inp.a = true; g.update(inp); inp.a = false
        assertEquals(State.STORY, g.state)
        repeat(200) { g.update(inp) }
        saveShot(g, "story")
        inp.a = true; g.update(inp); inp.a = false
        assertEquals(State.INTRO, g.state)
        saveShot(g, "intro")
        repeat(200) { g.update(inp) }
        assertEquals(State.PLAY, g.state)
    }

    /** Ein einfacher Bot, der nach rechts rennt und springt. Muss ohne Absturz laufen. */
    @Test
    fun botPlaysAllLevelsWithoutCrash() {
        for (i in 0 until Levels.COUNT) {
            val g = gameAt(i)
            g.lives = 99
            val rnd = Random(i)
            val inp = Input()
            var maxX = 0f
            for (f in 0 until 6000) {
                inp.right = rnd.nextInt(10) < 8
                inp.left = !inp.right && rnd.nextInt(4) == 0
                inp.b = rnd.nextInt(3) > 0
                inp.a = (f / 12) % 3 == 0
                inp.up = rnd.nextBoolean(); inp.down = rnd.nextInt(6) == 0
                g.update(inp)
                g.render()
                if (g.state == State.PLAY) maxX = maxOf(maxX, g.player.x)
                if (f == 300) saveShot(g, "level_${g.level.id}")
                if (g.state != State.PLAY && g.state != State.DYING && g.state != State.CLEAR && g.state != State.INTRO) break
            }
            assertTrue(maxX > 100f, "Bot kam in Level $i nicht voran ($maxX)")
        }
    }

    @Test
    fun bossesCanBeDefeated() {
        for (i in listOf(2, 5, 8, 11)) {
            val g = gameAt(i)
            val inp = Input()
            g.player.star = 100000
            g.lives = 99
            var boss: Boss? = null
            if (g.level.mode == Mode.PLATFORM) g.teleport(g.level.arenaX - 4)
            var shot = false
            for (f in 0 until 12000) {
                if (g.level.mode == Mode.PLATFORM) { inp.right = g.player.x < g.level.arenaX * 8f + 60 }
                g.update(inp)
                if (g.boss != null) boss = g.boss
                val b = boss
                if (b != null && b.alive && (g.arenaLocked || g.level.mode == Mode.SHOOTER) && f % 50 == 0) {
                    if (!shot) { saveShot(g, "boss_${g.level.id}"); shot = true }
                    b.damage(g, 1)
                }
                if (g.bossDefeated) break
            }
            assertTrue(g.bossDefeated, "Boss in Level $i nicht besiegt")
            if (g.level.mode == Mode.PLATFORM) {
                for (y in 0 until ROWS) assertTrue(g.level[g.level.arenaX + 19, y] != T.BARRIER)
            }
            var k = 0
            while (g.state == State.PLAY && k++ < 1500) g.update(Input().apply { right = true; a = k % 40 < 20 })
            assertTrue(g.state == State.CLEAR || g.state == State.INTRO || g.state == State.ENDING, "Level $i nicht abgeschlossen: ${g.state}")
        }
    }

    @Test
    fun stompingKillsWalker() {
        val g = gameAt(0)
        val w = Walker(g.player.x, g.player.y - 20, false)
        g.entities += w
        g.player.vy = 2f
        g.player.y = w.y - 8
        repeat(10) { g.update(Input()) }
        assertTrue(!w.alive)
    }

    @Test
    fun powerUpAndDamage() {
        val g = gameAt(0)
        g.collect(PKind.BERRY, 0f, 0f)
        assertEquals(1, g.player.power)
        assertEquals(14, g.player.h)
        g.hurtPlayer()
        assertEquals(0, g.player.power)
        assertEquals(State.PLAY, g.state)
        g.player.invuln = 0
        g.hurtPlayer()
        assertEquals(State.DYING, g.state)
    }

    @Test
    fun finishingLastLevelShowsEnding() {
        val save = MemorySave()
        val g = Game(save = save)
        g.newGame(11)
        g.startLevel()
        var f0 = 0
        while (g.boss == null && f0++ < 5000) { g.player.star = 1000; g.update(Input()) }
        val b = g.boss!!
        repeat(b.maxHp) { b.hurtT = 0; b.damage(g, 1) }
        var f = 0
        while (g.state != State.ENDING && f++ < 5000) g.update(Input())
        assertEquals(State.ENDING, g.state)
        saveShot(g, "ending")
        assertEquals(11, save.unlocked)
    }
}
