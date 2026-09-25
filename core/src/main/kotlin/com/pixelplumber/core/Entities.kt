package com.pixelplumber.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

abstract class Entity(x: Float, y: Float, w: Int, h: Int) : Body(x, y, w, h) {
    var alive = true
    var t = 0

    /** Kann durch Feuer/Stern/Schüsse besiegt werden. */
    open val isEnemy: Boolean get() = true
    open val harmful: Boolean get() = true
    open val stompable: Boolean get() = true
    open val isItem: Boolean get() = false

    open fun update(g: Game) {}
    open fun sprite(g: Game): Sprite? = null
    open fun flip(): Boolean = vx > 0

    open fun draw(g: Game, s: Screen) {
        val sp = sprite(g) ?: return
        s.sprite(sp, g.sx(x) - (sp.w - w) / 2, g.sy(y) - (sp.h - h), flipX = flip())
    }

    open fun onStomp(g: Game) {
        alive = false
        g.addScore(100, x, y)
        g.sfx(Sfx.STOMP)
        g.add(Squash(x, y + h - 8f))
    }

    open fun onHit(g: Game) {
        alive = false
        g.addScore(200, x, y)
        g.sfx(Sfx.KICK)
        if (g.level.mode == Mode.SHOOTER) g.add(Explosion(cx, cy))
        else sprite(g)?.let { g.add(DeadFall(it, x, y)) }
    }

    open fun onFire(g: Game) = onHit(g)
    open fun touch(g: Game) {}

    protected fun gravity(amount: Float = 0.25f, max: Float = 3f) { vy = min(vy + amount, max) }
}

abstract class Effect(x: Float, y: Float, w: Int = 8, h: Int = 8) : Entity(x, y, w, h) {
    override val isEnemy: Boolean get() = false
    override val harmful: Boolean get() = false
    override val stompable: Boolean get() = false
}

// ======================= Effekte =======================

class Squash(x: Float, y: Float) : Effect(x, y) {
    override fun update(g: Game) { if (++t > 30) alive = false }
    override fun draw(g: Game, s: Screen) = s.sprite(Art.SQUASHED, g.sx(x), g.sy(y))
}

class DeadFall(private val sp: Sprite, x: Float, y: Float) : Effect(x, y) {
    init { vy = -2.5f; vx = 0.4f }
    override fun update(g: Game) { vy += 0.2f; y += vy; x += vx; if (y > 170) alive = false }
    override fun draw(g: Game, s: Screen) = s.sprite(sp, g.sx(x), g.sy(y), flipY = true)
}

class Debris(x: Float, y: Float, vx0: Float, vy0: Float) : Effect(x, y, 4, 4) {
    init { vx = vx0; vy = vy0 }
    override fun update(g: Game) { vy += 0.25f; x += vx; y += vy; if (y > 170) alive = false }
    override fun draw(g: Game, s: Screen) = s.sprite(Art.DEBRIS, g.sx(x), g.sy(y), flipX = (t / 4) % 2 == 0).also { t++ }
}

class CoinPop(x: Float, y: Float) : Effect(x, y) {
    init { vy = -3.2f }
    override fun update(g: Game) { vy += 0.22f; y += vy; if (++t > 26) alive = false }
    override fun draw(g: Game, s: Screen) = s.sprite(Art.COIN_SPIN[(t / 3) % 2], g.sx(x), g.sy(y))
}

class Explosion(cx0: Float, cy0: Float, private val big: Boolean = false) : Effect(cx0 - 4, cy0 - 4) {
    override fun update(g: Game) { if (++t > 22) alive = false }
    override fun draw(g: Game, s: Screen) {
        val r = (if (big) 2 else 1) * (2 + t / 3)
        val px = g.sx(x) + 4; val py = g.sy(y) + 4
        s.circle(px, py, r, 3)
        if (r > 2) s.circle(px, py, r - 2, if (t % 4 < 2) 1 else 0)
    }
}

// ======================= Gegenstände =======================

enum class PKind { BERRY, FLOWER, STAR, HEART }

class PowerItem(x: Float, y: Float, val kind: PKind) : Entity(x, y, 8, 8) {
    private var rise = 16
    private val blockTop = y
    override val isEnemy: Boolean get() = false
    override val harmful: Boolean get() = false
    override val stompable: Boolean get() = false
    override val isItem: Boolean get() = rise <= 8

    override fun update(g: Game) {
        t++
        if (rise > 0) {
            rise--; y -= 0.5f
            if (rise == 0 && kind != PKind.FLOWER) vx = 0.6f
            return
        }
        when (kind) {
            PKind.FLOWER -> {}
            PKind.STAR -> {
                if (vx == 0f) vx = 0.8f
                gravity(0.2f)
                val ovx = vx
                move(g.level)
                if (hitL || hitR) vx = -ovx
                if (onGround) vy = -3f
            }
            else -> {
                gravity()
                val ovx = vx
                move(g.level)
                if (hitL || hitR) vx = -ovx
            }
        }
    }

    override fun touch(g: Game) {
        if (!isItem) return
        alive = false
        g.collect(kind, x, y)
    }

    override fun sprite(g: Game) = when (kind) {
        PKind.BERRY -> Art.BERRY; PKind.FLOWER -> Art.FLOWER; PKind.STAR -> Art.STAR; PKind.HEART -> Art.HEART
    }

    override fun draw(g: Game, s: Screen) {
        val sp = sprite(g)
        if (rise > 0) {
            s.clip(0, HUD_H, SCREEN_W, g.sy(blockTop))
            s.sprite(sp, g.sx(x), g.sy(y))
            s.clip(0, HUD_H, SCREEN_W, SCREEN_H)
        } else {
            val remap = if (kind == PKind.STAR) Art.REMAP_STAR[(t / 4) % 3] else null
            s.sprite(sp, g.sx(x), g.sy(y), remap = remap)
        }
    }
}

// ======================= Plattformen =======================

class Platform(x: Float, y: Float, private val kind: Int, private val range: Int) : Entity(x, y, 24, 4) {
    private val x0 = x
    private val y0 = y
    private var dir = 1f
    private var standT = 0
    var dx = 0f; private set
    var dy = 0f; private set

    override val isEnemy: Boolean get() = false
    override val harmful: Boolean get() = false
    override val stompable: Boolean get() = false

    fun stoodOn() { if (kind == 2 && standT == 0) standT = 1 }

    override fun update(g: Game) {
        val ox = x; val oy = y
        when (kind) {
            0 -> { x += dir * 0.5f; if (x >= x0 + range * 8) { x = x0 + range * 8f; dir = -1f } else if (x <= x0) { x = x0; dir = 1f } }
            1 -> { y -= dir * 0.5f; if (y <= y0 - range * 8) { y = y0 - range * 8f; dir = -1f } else if (y >= y0) { y = y0; dir = 1f } }
            else -> {
                if (standT > 0) standT++
                if (standT > 24) { vy = min(vy + 0.08f, 3f); y += vy }
                if (y > 170) alive = false
            }
        }
        dx = x - ox; dy = y - oy
    }

    override fun draw(g: Game, s: Screen) {
        val shakeX = if (kind == 2 && standT in 1..24 && (standT / 2) % 2 == 0) 1 else 0
        for (i in 0 until 3) s.sprite(Art.PLATFORM, g.sx(x) + i * 8 + shakeX, g.sy(y))
    }
}

// ======================= Gegner (Jump'n'Run) =======================

/** Krabbelkäfer (normal) oder Stachler (spiky = nicht zertretbar). */
class Walker(x: Float, y: Float, private val spiky: Boolean) : Entity(x, y, 8, 8) {
    init { vx = -0.35f }
    override val stompable: Boolean get() = !spiky
    override fun update(g: Game) {
        t++
        gravity()
        val ovx = vx
        move(g.level)
        if (hitL || hitR) vx = -ovx
    }
    override fun sprite(g: Game) = if (spiky) Art.SPIKY[(t / 10) % 2] else Art.WALKER[(t / 10) % 2]
}

/** Hüpfer – springt in Richtung des Spielers. */
class Hopper(x: Float, y: Float) : Entity(x, y, 8, 8) {
    private var wait = 40
    override fun update(g: Game) {
        t++
        gravity(0.2f)
        if (onGround) {
            vx = 0f
            if (--wait <= 0) { vy = -3.2f; vx = if (g.player.cx < cx) -0.7f else 0.7f; wait = 55 }
        }
        val ovx = vx
        move(g.level)
        if (hitL || hitR) vx = -ovx * 0.5f
    }
    override fun sprite(g: Game) = Art.HOPPER[if (onGround) 0 else 1]
}

/** Flatterling – fliegt in Wellen. */
class Flyer(x: Float, y: Float) : Entity(x, y, 8, 7) {
    private val baseY = y
    override fun update(g: Game) {
        t++
        x -= 0.4f
        y = baseY + sin(t * 0.06f) * 14f
    }
    override fun sprite(g: Game) = Art.FLYER[(t / 8) % 2]
}

/** Steinwerfer – wirft Brocken im Bogen. */
class Thrower(x: Float, y: Float) : Entity(x, y, 8, 8) {
    private var face = -1f
    override fun update(g: Game) {
        t++
        gravity()
        move(g.level)
        face = if (g.player.cx < cx) -1f else 1f
        if (t % 100 == 70 && abs(g.player.cx - cx) < 130) {
            val dvx = ((g.player.cx - cx) / 55f).coerceIn(-1.6f, 1.6f)
            g.add(EnemyShot(cx - 3, y - 4, dvx, -3.2f, gravity = 0.12f, collide = true, sp = Art.BALL))
        }
    }
    override fun sprite(g: Game) = Art.THROWER[if (t % 100 in 60..75) 1 else 0]
    override fun flip() = face > 0
}

/** Springfisch – springt aus Gruben und Wasser. */
class Leaper(x: Float, y: Float) : Entity(x, ROWS * 8f + 6, 8, 8) {
    private val baseY = ROWS * 8f + 6
    private var wait = 60
    override fun update(g: Game) {
        t++
        if (y >= baseY && vy >= 0f) {
            y = baseY; vy = 0f
            if (--wait <= 0) { vy = -4.6f; wait = 90 }
        } else {
            vy += 0.13f
            y += vy
        }
    }
    override fun sprite(g: Game) = Art.LEAPER
    override fun draw(g: Game, s: Screen) {
        if (y < ROWS * 8f) s.sprite(Art.LEAPER, g.sx(x), g.sy(y), flipY = vy > 0)
    }
}

/** Kanone – feuert Geschosse, ist unzerstörbar. */
class Cannon(x: Float, y: Float) : Effect(x, y) {
    override fun update(g: Game) {
        t++
        val onScreen = x > g.camX - 8 && x < g.camX + SCREEN_W
        if (t % 140 == 80 && onScreen && abs(g.player.cx - cx) > 16) {
            val dir = if (g.player.cx < cx) -1f else 1f
            g.add(Bullet(x + dir * 6, y + 1, dir))
            g.sfx(Sfx.CANNON)
        }
    }
    override fun draw(g: Game, s: Screen) = s.sprite(Art.CANNON, g.sx(x), g.sy(y), flipX = g.player.cx > cx)
}

class Bullet(x: Float, y: Float, dir: Float) : Entity(x, y, 8, 6) {
    init { vx = dir * 1.1f }
    override fun update(g: Game) { t++; x += vx }
    override fun sprite(g: Game) = Art.BULLET
}

/** Gegnerisches Geschoss (Stein, Tinte, Blitzkugel ...). */
class EnemyShot(
    x: Float, y: Float, vx0: Float, vy0: Float,
    private val gravity: Float = 0f,
    private val collide: Boolean = false,
    private val sp: Sprite = Art.FIREBALL
) : Entity(x, y, 4, 4) {
    init { vx = vx0; vy = vy0 }
    override val isEnemy: Boolean get() = false
    override val stompable: Boolean get() = false
    override fun update(g: Game) {
        t++
        vy += gravity
        if (collide) {
            move(g.level)
            if (onGround || hitL || hitR || hitC) alive = false
        } else { x += vx; y += vy }
        if (t > 400) alive = false
    }
    override fun draw(g: Game, s: Screen) = s.sprite(sp, g.sx(x) - (sp.w - 4) / 2, g.sy(y) - (sp.h - 4) / 2, remap = if (t % 8 < 4) null else Art.REMAP_FLASH)
}

/** Rollkugel – rollt über den Boden (Boss Welt 1). */
class Roller(x: Float, y: Float, dir: Float) : Entity(x, y, 6, 6) {
    init { vx = dir * 1.3f }
    override fun update(g: Game) {
        t++
        gravity()
        move(g.level)
        if (hitL || hitR) alive = false
    }
    override fun sprite(g: Game) = Art.BALL
    override fun draw(g: Game, s: Screen) = s.sprite(Art.BALL, g.sx(x) - 1, g.sy(y), flipX = (t / 6) % 2 == 0)
}

/** Fallender Felsbrocken (Boss Welt 3). */
class FallingRock(x: Float, y: Float) : Entity(x, y, 6, 6) {
    override val stompable: Boolean get() = false
    override val isEnemy: Boolean get() = false
    override fun update(g: Game) {
        t++
        gravity(0.12f, 3f)
        move(g.level)
        if (onGround) {
            alive = false
            g.add(Debris(x, y, -1f, -2f)); g.add(Debris(x + 3, y, 1f, -2f))
        }
    }
    override fun draw(g: Game, s: Screen) = s.sprite(Art.BALL, g.sx(x) - 1, g.sy(y))
}

/** Blitzstrahl (Endboss): erst Warnung, dann gefährlich. */
class Lightning(x: Float) : Entity(x, 0f, 6, ROWS * 8) {
    override val isEnemy: Boolean get() = false
    override val stompable: Boolean get() = false
    override val harmful: Boolean get() = t in 45..65
    override fun update(g: Game) { if (++t > 66) alive = false }
    override fun draw(g: Game, s: Screen) {
        val px = g.sx(x)
        if (t < 45) {
            if ((t / 4) % 2 == 0) for (yy in 0 until ROWS * 8 step 6) s.rect(px + 2, HUD_H + yy, 2, 3, 2)
        } else {
            var bx = px + 3
            for (yy in 0 until ROWS * 8 step 4) {
                bx = px + 1 + ((yy / 4 + t) % 3)
                s.rect(bx, HUD_H + yy, 3, 4, if (t % 2 == 0) 3 else 1)
            }
        }
    }
}

// ======================= Spieler-Geschosse =======================

class Fireball(x: Float, y: Float, dir: Float) : Entity(x, y, 4, 4) {
    init { vx = dir * 2.2f; vy = 1.5f; useSemi = true }
    override val isEnemy: Boolean get() = false
    override val harmful: Boolean get() = false
    override val stompable: Boolean get() = false

    override fun update(g: Game) {
        t++
        gravity(0.25f, 3f)
        move(g.level)
        if (onGround) vy = -2.1f
        if (hitL || hitR || hitC || t > 150) { alive = false; g.add(Explosion(cx, cy)); return }
        for (e in g.entities) {
            if (e !== this && e.alive && e.isEnemy && overlaps(e)) {
                e.onFire(g); alive = false; g.add(Explosion(cx, cy)); return
            }
        }
    }
    override fun draw(g: Game, s: Screen) = s.sprite(Art.FIREBALL, g.sx(x), g.sy(y), remap = if ((t / 3) % 2 == 0) null else Art.REMAP_FLASH)
}

class PlayerShot(x: Float, y: Float) : Entity(x, y, 6, 2) {
    init { vx = 3.5f }
    override val isEnemy: Boolean get() = false
    override val harmful: Boolean get() = false
    override val stompable: Boolean get() = false

    override fun update(g: Game) {
        t++
        move(g.level)
        if (hitL || hitR || x > g.camX + SCREEN_W + 4) { alive = false; return }
        for (e in g.entities) {
            if (e !== this && e.alive && e.isEnemy && overlaps(e)) { e.onFire(g); alive = false; return }
        }
    }
    override fun draw(g: Game, s: Screen) { s.rect(g.sx(x), g.sy(y), 6, 2, 3); s.rect(g.sx(x) + 4, g.sy(y), 2, 2, 1) }
}

// ======================= Gegner (Shooter) =======================

abstract class ShooterEnemy(x: Float, y: Float, w: Int, h: Int, var hp: Int) : Entity(x, y, w, h) {
    private var flash = 0
    override val stompable: Boolean get() = false
    override fun onFire(g: Game) {
        hp--
        flash = 6
        if (hp <= 0) {
            alive = false
            g.addScore(300, x, y)
            g.sfx(Sfx.EXPLODE)
            g.add(Explosion(cx, cy))
        } else g.sfx(Sfx.BUMP)
    }
    override fun onHit(g: Game) { hp = 1; onFire(g) }
    override fun draw(g: Game, s: Screen) {
        if (flash > 0) flash--
        val sp = sprite(g) ?: return
        s.sprite(sp, g.sx(x) - (sp.w - w) / 2, g.sy(y) - (sp.h - h), flipX = flip(), remap = if (flash > 0) Art.REMAP_FLASH else null)
    }
}

class Drifter(x: Float, y: Float) : ShooterEnemy(x, y, 8, 7, 1) {
    private val baseY = y
    override fun update(g: Game) { t++; x -= 0.6f; y = baseY + sin(t * 0.05f) * 12f }
    override fun sprite(g: Game) = Art.DRIFTER
    override fun flip() = false
}

class Diver(x: Float, y: Float) : ShooterEnemy(x, y, 8, 6, 1) {
    override fun update(g: Game) {
        t++
        x -= 0.8f
        if (abs(g.player.cx - cx) < 80) y += sign(g.player.cy - cy) * 0.55f
        y = y.coerceIn(0f, ROWS * 8f - h)
    }
    override fun sprite(g: Game) = Art.DIVER
    override fun flip() = false
}

class Turret(x: Float, y: Float) : ShooterEnemy(x, y, 8, 8, 2) {
    override fun update(g: Game) {
        t++
        if (t % 110 == 50 && x < g.camX + SCREEN_W - 8 && x > g.camX + 8) {
            val a = atan2(g.player.cy - cy, g.player.cx - cx)
            g.add(EnemyShot(cx - 2, y, cos(a) * 1.2f, sin(a) * 1.2f))
        }
    }
    override fun sprite(g: Game) = Art.TURRET
    override fun flip() = false
}

class Mine(x: Float, y: Float) : ShooterEnemy(x, y, 8, 8, 3) {
    private val baseY = y
    override fun update(g: Game) { t++; y = baseY + sin(t * 0.08f) * 3f }
    override fun sprite(g: Game) = Art.MINE
    override fun flip() = false
}

fun dist(ax: Float, ay: Float, bx: Float, by: Float) = sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by))
