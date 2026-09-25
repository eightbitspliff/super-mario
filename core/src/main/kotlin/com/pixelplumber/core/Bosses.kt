package com.pixelplumber.core

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

abstract class Boss(x: Float, y: Float, w: Int, h: Int, val maxHp: Int, val title: String) : Entity(x, y, w, h) {
    var hp = maxHp
    var hurtT = 0
    override val harmful: Boolean get() = hurtT == 0
    override val stompable: Boolean get() = false

    fun damage(g: Game, n: Int) {
        if (hurtT > 0 || !alive) return
        hp -= n
        hurtT = 45
        g.sfx(Sfx.BOSSHIT)
        if (hp <= 0) {
            hp = 0
            alive = false
            g.onBossDefeated(this)
        }
    }

    override fun onFire(g: Game) = damage(g, 1)
    override fun onHit(g: Game) {}
    override fun onStomp(g: Game) = damage(g, 2)

    protected fun tickHurt() { if (hurtT > 0) hurtT-- }
    protected val flashing get() = hurtT > 0 && (hurtT / 3) % 2 == 0

    /** Farben für prozedurale Zeichnung (beim Blinken aufgehellt). */
    protected fun c(v: Int) = if (flashing) maxOf(0, v - 2) else v
}

/** Welt 1: Skarabäus Rex – springt, stampft und lässt Kugeln rollen. */
class BossScarab(x: Float, y: Float) : Boss(x, y, 16, 12, 6, "SKARABAEUS REX") {
    private var jumpT = 0
    private var face = -1f
    private var wasAir = false
    override val stompable: Boolean get() = hurtT == 0

    override fun update(g: Game) {
        t++; tickHurt()
        gravity(0.2f, 4f)
        if (!g.arenaLocked) { vx = 0f; move(g.level); return }
        val angry = hp <= 3
        face = if (g.player.cx < cx) -1f else 1f
        if (onGround) {
            jumpT++
            if (jumpT > (if (angry) 70 else 110)) { vy = -4.2f; jumpT = 0; vx = face * 1.1f }
            else vx = face * (if (angry) 0.8f else 0.5f)
        }
        move(g.level)
        val minX = g.level.arenaX * 8f + 8
        val maxX = (g.level.arenaX + 19) * 8f - w
        x = x.coerceIn(minX, maxX)
        if (onGround && wasAir) {
            g.shake = 8
            if (angry) { g.add(Roller(x - 6, y + 4, -1f)); g.add(Roller(x + w, y + 4, 1f)) }
        }
        wasAir = !onGround
    }

    override fun draw(g: Game, s: Screen) {
        val sx = g.sx(x); val sy = g.sy(y)
        val cx = sx + 8; val cy = sy + 6
        val leg = if ((t / 6) % 2 == 0) 1 else -1
        for (i in 0..2) s.line(sx + 3 + i * 5, sy + 9, sx + 2 + i * 5 + leg, sy + 12, c(3))
        s.ellipse(cx, cy, 8, 6, c(3))
        s.ellipse(cx, cy, 6, 4, c(2))
        s.line(cx, sy + 1, cx, sy + 10, c(3))
        s.set(cx - 3, cy - 2, c(1)); s.set(cx - 4, cy - 1, c(1)); s.set(cx + 3, cy - 2, c(1))
        val hx = if (face < 0) sx + 1 else sx + 15
        s.circle(hx, cy + 1, 3, c(3))
        s.line(hx + face.toInt() * 2, cy - 1, hx + face.toInt() * 6, cy - 6, c(3))
        s.set(hx + face.toInt(), cy, 0)
    }
}

/** Welt 3: Felsgolem – wirft Brocken und lässt Felsen von der Decke regnen. */
class BossGolem(x: Float, y: Float) : Boss(x, y, 20, 28, 8, "FELSGOLEM") {
    private var actionT = 0
    private var count = 0
    private var dir = -1f
    private var wasAir = false
    override val stompable: Boolean get() = hurtT == 0

    override fun update(g: Game) {
        t++; tickHurt()
        gravity(0.2f, 4f)
        if (!g.arenaLocked) { vx = 0f; move(g.level); return }
        val angry = hp <= 4
        val minX = g.level.arenaX * 8f + 8
        val maxX = (g.level.arenaX + 19) * 8f - w
        if (onGround) vx = dir * (if (angry) 0.5f else 0.3f)
        actionT++
        if (actionT >= (if (angry) 60 else 90)) {
            actionT = 0
            count++
            if (count % 3 == 0) {
                vy = -2.8f
            } else {
                val dvx = ((g.player.cx - cx) / 55f).coerceIn(-2f, 2f)
                g.add(EnemyShot(cx - 2, y + 4, dvx, -3f, gravity = 0.12f, collide = true, sp = Art.BALL))
                g.sfx(Sfx.KICK)
            }
        }
        move(g.level)
        if (x <= minX) { x = minX; dir = 1f }
        if (x >= maxX) { x = maxX; dir = -1f }
        if (onGround && wasAir) {
            g.shake = 20
            g.sfx(Sfx.CANNON)
            repeat(if (angry) 4 else 3) {
                val rx = minX + g.rng.nextFloat() * (maxX - minX)
                g.add(FallingRock(rx, 8f + it * -20f))
            }
        }
        wasAir = !onGround
    }

    override fun draw(g: Game, s: Screen) {
        val sx = g.sx(x) - 2; val sy = g.sy(y)
        val swing = (sin(t * 0.1) * 2).toInt()
        // Beine
        s.rect(sx + 4, sy + 21, 6, 7, c(3)); s.rect(sx + 5, sy + 21, 4, 6, c(2))
        s.rect(sx + 14, sy + 21, 6, 7, c(3)); s.rect(sx + 15, sy + 21, 4, 6, c(2))
        // Körper
        s.rect(sx + 1, sy + 9, 22, 13, c(3)); s.rect(sx + 2, sy + 10, 20, 11, c(2))
        s.line(sx + 6, sy + 12, sx + 10, sy + 16, c(3)); s.line(sx + 15, sy + 11, sx + 18, sy + 18, c(3))
        s.rect(sx + 8, sy + 17, 8, 2, c(1))
        // Arme
        s.rect(sx - 3, sy + 10 + swing, 5, 10, c(3)); s.rect(sx - 2, sy + 11 + swing, 3, 8, c(1))
        s.rect(sx + 22, sy + 10 - swing, 5, 10, c(3)); s.rect(sx + 23, sy + 11 - swing, 3, 8, c(1))
        // Kopf
        s.rect(sx + 6, sy, 12, 10, c(3)); s.rect(sx + 7, sy + 1, 10, 8, c(2))
        val glow = if ((t / 10) % 2 == 0) 0 else 1
        s.rect(sx + 8, sy + 4, 3, 2, glow); s.rect(sx + 13, sy + 4, 3, 2, glow)
        s.rect(sx + 9, sy + 7, 6, 1, c(3))
    }
}

/** Welt 2: Tiefseekrake – Tintenschüsse und Quallenbrut. */
class BossKraken(x: Float, y: Float) : Boss(x, y, 26, 24, 24, "TIEFSEEKRAKE") {
    private var entered = false
    override fun update(g: Game) {
        t++; tickHurt()
        val targetX = g.camX + 116
        if (!entered) { x = maxOf(targetX, x - 0.8f); if (x <= targetX) entered = true; y = 50f; return }
        x = targetX + sin(t * 0.017f) * 6f
        y = 48f + sin(t * 0.03f) * 34f
        val angry = hp <= 12
        if (t % (if (angry) 55 else 80) == 0) {
            val a = atan2(g.player.cy - cy, g.player.cx - cx)
            for (k in -1..1) {
                val aa = a + k * 0.35f
                g.add(EnemyShot(x + 2, cy, cos(aa) * 1.3f, sin(aa) * 1.3f))
            }
        }
        if (t % 210 == 100) g.add(Drifter(x, cy))
    }

    override fun draw(g: Game, s: Screen) {
        val sx = g.sx(x); val sy = g.sy(y)
        val cx = sx + 13; val cy = sy + 8
        for (k in 0..4) for (i in 0..9) {
            val tx = cx - 12 + k * 6 + (sin(t * 0.12 + k + i * 0.6) * 2).toInt()
            s.rect(tx, cy + 6 + i * 2, 2, 2, c(3))
        }
        s.ellipse(cx, cy, 14, 10, c(3))
        s.ellipse(cx, cy, 12, 8, c(2))
        s.ellipse(cx + 3, cy - 4, 4, 2, c(1))
        s.circle(cx - 6, cy + 2, 3, 0); s.circle(cx + 4, cy + 2, 3, 0)
        s.rect(cx - 7, cy + 2, 2, 2, 3); s.rect(cx + 3, cy + 2, 2, 2, 3)
    }
}

/** Welt 4 (Endboss): Sturmfürst – Fächerschüsse und Blitze. */
class BossStorm(x: Float, y: Float) : Boss(x, y, 28, 24, 36, "STURMFUERST") {
    private var entered = false
    override fun update(g: Game) {
        t++; tickHurt()
        val baseX = g.camX + 108
        if (!entered) { x = maxOf(baseX, x - 0.8f); if (x <= baseX) entered = true; y = 50f; return }
        val angry = hp <= 18
        val sp = if (angry) 1.6f else 1f
        x = baseX + sin(t * 0.02f * sp) * 18f
        y = 52f + sin(t * 0.04f * sp) * 36f
        if (t % (if (angry) 45 else 70) == 0) {
            for (k in -2..2) {
                val a = PI.toFloat() + k * 0.28f
                g.add(EnemyShot(x, cy, cos(a) * 1.3f, sin(a) * 1.3f))
            }
        }
        if (angry && t % 150 == 75) g.add(Lightning(g.player.cx - 3))
    }

    override fun draw(g: Game, s: Screen) {
        val sx = g.sx(x); val sy = g.sy(y)
        val cx = sx + 14; val cy = sy + 12
        s.circle(cx - 8, cy + 2, 9, c(3)); s.circle(cx + 8, cy + 2, 9, c(3)); s.circle(cx, cy - 5, 11, c(3))
        s.circle(cx - 8, cy + 2, 7, c(1)); s.circle(cx + 8, cy + 2, 7, c(1)); s.circle(cx, cy - 5, 9, c(1))
        s.line(cx - 8, cy - 7, cx - 3, cy - 4, 3); s.line(cx + 8, cy - 7, cx + 3, cy - 4, 3)
        s.rect(cx - 6, cy - 3, 3, 3, 3); s.rect(cx + 3, cy - 3, 3, 3, 3)
        s.rect(cx - 4, cy + 4, 8, 2, 3)
        if ((t / 8) % 2 == 0) {
            s.line(cx - 12, cy + 10, cx - 15, cy + 15, 3); s.line(cx - 15, cy + 15, cx - 12, cy + 15, 3); s.line(cx - 12, cy + 15, cx - 15, cy + 20, 3)
            s.line(cx + 12, cy + 10, cx + 9, cy + 15, 3); s.line(cx + 9, cy + 15, cx + 12, cy + 15, 3); s.line(cx + 12, cy + 15, cx + 9, cy + 20, 3)
        }
    }
}

