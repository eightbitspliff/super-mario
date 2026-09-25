package com.pixelplumber.core

import kotlin.math.floor

const val SCREEN_W = 160
const val SCREEN_H = 144
const val TILE = 8
const val HUD_H = 16
const val ROWS = 16

/** Zustand der Tasten (Steuerkreuz, A = Springen, B = Rennen/Feuer, Start = Pause). */
class Input {
    var left = false
    var right = false
    var up = false
    var down = false
    var a = false
    var b = false
    var start = false

    fun set(o: Input) {
        left = o.left; right = o.right; up = o.up; down = o.down
        a = o.a; b = o.b; start = o.start
    }

    fun or(o: Input) {
        left = left || o.left; right = right || o.right; up = up || o.up; down = down || o.down
        a = a || o.a; b = b || o.b; start = start || o.start
    }

    fun clear() {
        left = false; right = false; up = false; down = false
        a = false; b = false; start = false
    }
}

/** Speicher für Fortschritt und Rekord. */
interface SaveStore {
    var unlocked: Int
    var highScore: Int
}

class MemorySave : SaveStore {
    override var unlocked = 0
    override var highScore = 0
}

/** Kachel-IDs. */
object T {
    const val EMPTY = 0
    const val GROUND = 1
    const val BRICK = 2
    const val QBLOCK = 3
    const val USED = 4
    const val HARD = 5
    const val PIPE_TL = 6
    const val PIPE_TR = 7
    const val PIPE_L = 8
    const val PIPE_R = 9
    const val BARRIER = 10
    const val SEMI = 11
    const val SPIKES = 12
    const val COIN = 13
    const val HILL_L = 14
    const val HILL_R = 15
    const val HILL_F = 16
    const val CLOUD_L = 17
    const val CLOUD_M = 18
    const val CLOUD_R = 19
    const val BUSH = 20
    const val PALM = 21
    const val TRUNK = 22
    const val CACTUS = 23
    const val WAVE = 24
    const val WATER = 25
    const val CRYSTAL = 26

    fun solid(t: Int) = t in GROUND..BARRIER
    fun semi(t: Int) = t == SEMI
}

fun fdiv(v: Float): Int = floor(v / TILE).toInt()

/** Rechteckiger Körper mit Kachel-Kollision. */
open class Body(var x: Float, var y: Float, var w: Int, var h: Int) {
    var vx = 0f
    var vy = 0f
    var onGround = false
    var hitL = false
    var hitR = false
    var hitC = false
    var useSemi = true

    fun overlaps(o: Body) = x < o.x + o.w && x + w > o.x && y < o.y + o.h && y + h > o.y
    fun overlaps(ox: Float, oy: Float, ow: Int, oh: Int) = x < ox + ow && x + w > ox && y < oy + oh && y + h > oy

    val cx get() = x + w / 2f
    val cy get() = y + h / 2f

    fun overlapsSolid(lv: Level): Boolean {
        for (c in fdiv(x)..fdiv(x + w - 0.01f)) for (r in fdiv(y)..fdiv(y + h - 0.01f)) if (T.solid(lv[c, r])) return true
        return false
    }

    fun move(lv: Level) {
        hitL = false; hitR = false; hitC = false; onGround = false
        if (vx != 0f) {
            x += vx
            val top = fdiv(y)
            val bottom = fdiv(y + h - 0.01f)
            if (vx > 0) {
                val col = fdiv(x + w - 0.01f)
                for (r in top..bottom) if (T.solid(lv[col, r])) { x = col * 8f - w; hitR = true; break }
            } else {
                val col = fdiv(x)
                for (r in top..bottom) if (T.solid(lv[col, r])) { x = (col + 1) * 8f; hitL = true; break }
            }
        }
        val oldBottom = y + h
        y += vy
        val l = fdiv(x)
        val r = fdiv(x + w - 0.01f)
        if (vy > 0) {
            val row = fdiv(y + h - 0.01f)
            var landed = false
            for (c in l..r) {
                val t = lv[c, row]
                if (T.solid(t) || (useSemi && T.semi(t) && oldBottom <= row * 8f + 0.01f)) landed = true
            }
            if (landed) { y = row * 8f - h; vy = 0f; onGround = true }
        } else if (vy < 0) {
            val row = fdiv(y)
            var hit = false
            for (c in l..r) if (T.solid(lv[c, row])) hit = true
            if (hit) { y = (row + 1) * 8f; vy = 0f; hitC = true }
        }
    }
}
