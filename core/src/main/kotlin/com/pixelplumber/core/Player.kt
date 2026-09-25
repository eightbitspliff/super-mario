package com.pixelplumber.core

import kotlin.math.abs
import kotlin.math.min

/** Pip, der Held. Im Shooter-Modus steuert er ein U-Boot bzw. Flugzeug. */
class Player : Body(0f, 0f, 6, 7) {
    var power = 0          // 0 = klein, 1 = groß, 2 = Feuer
    var facing = 1
    var invuln = 0
    var star = 0
    var dead = false
    var ship = false
    var ride: Platform? = null
    var prevBottom = 0f
    var warp: Warp? = null
    var warpT = 0
    private var shotCool = 0
    private var anim = 0f
    private var growT = 0

    fun reset(px: Float, py: Float, asShip: Boolean) {
        ship = asShip
        dead = false; invuln = 0; star = 0; warp = null; warpT = 0; ride = null
        vx = 0f; vy = 0f; facing = 1; anim = 0f
        if (ship) { w = 12; h = 6 } else { w = 6; h = if (power > 0) 14 else 7 }
        x = px; y = py
    }

    fun changePower(p: Int) {
        val bottom = y + h
        val wasSmall = power == 0
        power = p
        if (!ship) {
            h = if (p > 0) 14 else 7
            y = bottom - h
            if (wasSmall && p > 0) growT = 30
        }
    }

    fun updatePlatform(g: Game) {
        val inp = g.input
        if (invuln > 0) invuln--
        if (star > 0) { star--; if (star == 0) g.onStarEnd() }
        if (growT > 0) growT--
        if (shotCool > 0) shotCool--

        if (warpT > 0) {
            warpT--
            y += 0.5f
            if (warpT == 0) g.finishWarp(warp!!)
            return
        }

        if (onGround && inp.down) {
            val w = g.level.warps.firstOrNull { wp ->
                fdiv(y + h + 0.5f) == wp.ty && cx >= wp.tx * 8f + 3 && cx <= wp.tx * 8f + 13
            }
            if (w != null) {
                warp = w; warpT = 36; vx = 0f; vy = 0f
                x = w.tx * 8f + 5
                g.sfx(Sfx.PIPE)
                return
            }
        }

        val run = inp.b
        val maxS = if (run) 1.5f else 0.9f
        val acc = if (onGround) (if (run) 0.07f else 0.05f) else 0.04f
        if (inp.left && !inp.right) {
            facing = -1
            vx = when {
                vx > 0 -> vx - 0.12f
                vx > -maxS -> maxOf(-maxS, vx - acc)
                else -> min(-maxS, vx + 0.04f)
            }
        } else if (inp.right && !inp.left) {
            facing = 1
            vx = when {
                vx < 0 -> vx + 0.12f
                vx < maxS -> min(maxS, vx + acc)
                else -> maxOf(maxS, vx - 0.04f)
            }
        } else {
            val fr = if (onGround) 0.06f else 0.015f
            vx = if (vx > 0) maxOf(0f, vx - fr) else min(0f, vx + fr)
        }

        if (g.pressedA && onGround) {
            vy = -(3.3f + abs(vx) * 0.25f)
            onGround = false
            ride = null
            g.sfx(Sfx.JUMP)
        }

        if (g.pressedB && power == 2 && shotCool == 0 && g.entities.count { it is Fireball && it.alive } < 2) {
            g.add(Fireball(if (facing > 0) x + w else x - 4, y + 3, facing.toFloat()))
            shotCool = 8
            g.sfx(Sfx.FIRE)
        }

        val grav = if (vy < 0) (if (inp.a) 0.15f else 0.45f) else 0.3f
        vy = min(vy + grav, 3.5f)

        ride?.let { p ->
            if (p.alive && x + w > p.x && x < p.x + p.w) { x += p.dx; y = p.y - h } else ride = null
        }

        prevBottom = y + h
        move(g.level)
        if (hitC) g.bumpHead(this)
        if (hitL || hitR) vx = 0f
        ride = null
        g.landOnPlatforms(this)
        anim += abs(vx)
    }

    fun updateShip(g: Game) {
        val inp = g.input
        if (invuln > 0) invuln--
        if (star > 0) { star--; if (star == 0) g.onStarEnd() }
        if (shotCool > 0) shotCool--
        val sp = 1.3f
        vx = ((if (inp.right) 1 else 0) - (if (inp.left) 1 else 0)) * sp + g.scrollSpeed
        vy = ((if (inp.down) 1 else 0) - (if (inp.up) 1 else 0)) * sp
        prevBottom = y + h
        move(g.level)
        y = y.coerceIn(0f, ROWS * 8f - h)
        if (x > g.camX + SCREEN_W - w) x = g.camX + SCREEN_W - w
        if ((inp.a || inp.b) && shotCool == 0 && g.entities.count { it is PlayerShot && it.alive } < 3) {
            g.add(PlayerShot(x + w, y + 2))
            shotCool = 9
            g.sfx(Sfx.SHOOT)
        }
        anim += 1f
    }

    fun draw(g: Game, s: Screen) {
        val sx = g.sx(x)
        val sy = g.sy(y)
        if (ship) {
            if (!dead && invuln > 0 && (invuln / 3) % 2 == 0) return
            val sp = if (g.level.theme == Theme.SEA) Art.SUB else Art.PLANE
            val remap = if (star > 0) Art.REMAP_STAR[(star / 4) % 3] else null
            s.sprite(sp, sx - 2, sy - 1 + if (dead) 0 else ((anim / 16).toInt() % 2), remap = remap)
            if (g.level.theme == Theme.SKY && !dead) s.rect(sx + 13, sy + 1 + (anim.toInt() / 2 % 2) * 3, 1, 3, 3)
            return
        }
        if (dead) {
            s.sprite(Art.PIP_S_DEAD, sx - 1, sy - 1)
            return
        }
        if (invuln > 0 && (invuln / 3) % 2 == 0) return
        val big = power > 0 && !(growT > 0 && (growT / 4) % 2 == 0)
        val moving = abs(vx) > 0.1f
        val walkFrame = (anim / 6).toInt() % 2 == 1
        val sp = if (big) {
            when { !onGround -> Art.PIP_B_JUMP; moving && walkFrame -> Art.PIP_B_WALK; else -> Art.PIP_B_STAND }
        } else {
            when { !onGround -> Art.PIP_S_JUMP; moving && walkFrame -> Art.PIP_S_WALK; else -> Art.PIP_S_STAND }
        }
        val remap = when {
            star > 0 -> Art.REMAP_STAR[(star / 4) % 3]
            power == 2 -> Art.REMAP_FIRE
            else -> null
        }
        val oy = if (big) (if (power > 0) sy - 2 else sy - 9) else (if (power > 0) sy + 6 else sy - 1)
        val w0 = warp
        if (warpT > 0 && w0 != null) s.clip(0, HUD_H, SCREEN_W, g.sy(w0.ty * 8f))
        s.sprite(sp, sx - 1, oy, flipX = facing < 0, remap = remap)
        if (warpT > 0) s.clip(0, HUD_H, SCREEN_W, SCREEN_H)
    }
}
