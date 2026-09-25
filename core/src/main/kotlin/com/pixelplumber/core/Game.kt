package com.pixelplumber.core

import kotlin.math.min
import kotlin.random.Random

enum class State { TITLE, STORY, INTRO, PLAY, DYING, CLEAR, GAMEOVER, ENDING }

class Popup(var x: Float, var y: Float, val text: String) { var t = 0 }

/**
 * Das komplette Spiel. [update] wird 60x pro Sekunde aufgerufen, [render] zeichnet
 * in einen 160x144-Bildspeicher. Plattformunabhängig – die Android-App zeigt nur das Bild an.
 */
class Game(val audio: AudioSink = AudioSink.NONE, val save: SaveStore = MemorySave()) {
    val screen = Screen()
    val input = Input()
    private val prev = Input()
    var pressedA = false; private set
    var pressedB = false; private set
    private var pressedStart = false
    private var pressedLeft = false
    private var pressedRight = false

    var state = State.TITLE; private set
    private var stateT = 0
    var frame = 0; private set

    var lives = 3
    var score = 0
    var coins = 0
    var levelIndex = 0; private set
    private var titleSel = 0

    lateinit var level: Level; private set
    val player = Player()
    val entities = ArrayList<Entity>()
    private val toAdd = ArrayList<Entity>()
    private val pending = ArrayList<Spawn>()
    private val popups = ArrayList<Popup>()

    var camX = 0f; private set
    private var camXi = 0
    private var section = 0
    var time = 0; private set
    private var timeTick = 0
    private var checkpointReached = false
    var paused = false; private set
    var boss: Boss? = null; private set
    var bossDefeated = false; private set
    private var bossMusic = false
    private var bossClearT = -1
    var shake = 0
    var rng = Random(7)
    private var bumpX = -1
    private var bumpY = -1
    private var bumpT = 0
    private var clearHigh = false
    private var clearWait = 0

    val scrollSpeed: Float get() = if (level.mode == Mode.SHOOTER && camX < camMax() && state == State.PLAY) 0.5f else 0f
    val arenaLocked: Boolean get() = level.arenaX >= 0 && !bossDefeated && camX >= level.arenaX * 8f - 0.5f

    init { audio.music(Tracks.TITLE) }

    // ---------------------------------------------------------------- Hilfen für Entities
    fun sx(x: Float) = x.toInt() - camXi
    fun sy(y: Float) = y.toInt() + HUD_H
    fun add(e: Entity) { toAdd += e }
    fun sfx(s: Sfx) = audio.sfx(s)

    fun addScore(n: Int, x: Float, y: Float) {
        score += n
        popups += Popup(x, y, n.toString())
    }

    private fun addCoin() {
        coins++
        score += 100
        sfx(Sfx.COIN)
        if (coins >= 100) { coins -= 100; oneUp(player.x, player.y) }
    }

    private fun oneUp(x: Float, y: Float) {
        lives = min(99, lives + 1)
        popups += Popup(x, y, "1UP")
        sfx(Sfx.ONEUP)
    }

    // ---------------------------------------------------------------- Hauptschleife
    fun update(inp: Input) {
        input.set(inp)
        pressedA = input.a && !prev.a
        pressedB = input.b && !prev.b
        pressedStart = input.start && !prev.start
        pressedLeft = input.left && !prev.left
        pressedRight = input.right && !prev.right
        stateT++
        when (state) {
            State.TITLE -> updateTitle()
            State.STORY -> if ((stateT > 30 && (pressedA || pressedStart)) || stateT > 600) enterIntro()
            State.INTRO -> if (stateT > 150 || (stateT > 30 && (pressedA || pressedStart))) startLevel()
            State.PLAY -> updatePlay()
            State.DYING -> updateDying()
            State.CLEAR -> updateClear()
            State.GAMEOVER -> if (stateT > 300 || (stateT > 60 && (pressedA || pressedStart))) toTitle()
            State.ENDING -> if (stateT > 240 && (pressedA || pressedStart)) toTitle()
        }
        prev.set(input)
        frame++
    }

    private fun setState(s: State) { state = s; stateT = 0 }

    private fun updateTitle() {
        val maxSel = min(save.unlocked, Levels.COUNT - 1)
        if (pressedLeft && titleSel > 0) { titleSel--; sfx(Sfx.TICK) }
        if (pressedRight && titleSel < maxSel) { titleSel++; sfx(Sfx.TICK) }
        if (stateT > 20 && (pressedA || pressedStart)) newGame(titleSel)
    }

    fun newGame(index: Int) {
        lives = 3; score = 0; coins = 0
        levelIndex = index.coerceIn(0, Levels.COUNT - 1)
        player.power = 0
        checkpointReached = false
        audio.music(null)
        if (index == 0) setState(State.STORY) else enterIntro()
    }

    fun toTitle() {
        if (score > save.highScore) save.highScore = score
        titleSel = min(save.unlocked, Levels.COUNT - 1)
        paused = false
        audio.setPaused(false)
        setState(State.TITLE)
        audio.music(Tracks.TITLE)
    }

    private fun enterIntro() {
        setState(State.INTRO)
        audio.music(null)
    }

    fun startLevel() {
        level = Levels.create(levelIndex)
        entities.clear(); toAdd.clear(); popups.clear()
        pending.clear(); pending.addAll(level.spawns)
        section = 0
        boss = null; bossDefeated = false; bossMusic = false; bossClearT = -1
        paused = false; shake = 0; bumpT = 0
        rng = Random(levelIndex * 31 + 7)
        val ship = level.mode == Mode.SHOOTER
        val startTx = if (checkpointReached && level.checkpointX > 0) level.checkpointX else level.startX
        if (ship) {
            player.reset(startTx * 8f + 24f, 56f, true)
            camX = maxOf(0f, startTx * 8f)
        } else {
            player.reset(startTx * 8f + 1f, 0f, false)
            var ty = ROWS - 1
            while (ty > 0 && T.solid(level[startTx, ty - 1])) ty--
            player.y = ty * 8f - player.h
            camX = (player.x - 64f).coerceIn(0f, camMax())
        }
        time = level.time
        timeTick = 0
        audio.music(levelMusic())
        setState(State.PLAY)
    }

    /** Für Tests: Spieler an Spalte [tx] setzen (auf den Boden). */
    fun teleport(tx: Int) {
        var ty = ROWS - 1
        while (ty > 0 && T.solid(level[tx, ty - 1])) ty--
        player.x = tx * 8f + 1
        player.y = ty * 8f - player.h
        camX = (player.x - 64f).coerceIn(0f, camMax())
    }

    /** Pausiert automatisch (z. B. wenn die App in den Hintergrund geht). */
    fun autoPause() {
        if (state == State.PLAY && !paused) { paused = true; audio.setPaused(true) }
    }

    /** Zurück-Taste: im Spiel pausieren. Gibt false zurück, wenn die App beendet werden darf. */
    fun onBack(): Boolean {
        if (state == State.TITLE) return false
        if (state == State.PLAY && !paused) { autoPause(); return true }
        toTitle()
        return true
    }

    private fun levelMusic(): Track = when {
        player.star > 0 -> Tracks.STAR
        bossMusic && !bossDefeated -> Tracks.BOSS
        level.mode == Mode.SHOOTER -> Tracks.SHOOTER
        else -> Tracks.forTheme(level.theme)
    }

    private fun camMax(): Float {
        val sec = level.sections[section]
        var m = sec.end * 8f - SCREEN_W
        if (section == 0 && level.arenaX >= 0 && !bossDefeated) m = min(m, level.arenaX * 8f)
        return m
    }

    // ---------------------------------------------------------------- Spielen
    private fun updatePlay() {
        if (pressedStart) {
            paused = !paused
            audio.setPaused(paused)
            sfx(Sfx.PAUSE)
        }
        if (paused) return
        if (shake > 0) shake--
        if (bumpT > 0) bumpT--

        if (level.time > 0) {
            if (++timeTick >= 24) {
                timeTick = 0
                time--
                if (time == 100) sfx(Sfx.PAUSE)
                if (time <= 0) { time = 0; killPlayer(); return }
            }
        }

        if (level.mode == Mode.SHOOTER) camX = min(camX + scrollSpeed, camMax())
        activateSpawns()
        updateEntities()

        if (level.mode == Mode.PLATFORM) {
            player.updatePlatform(this)
            if (player.warpT > 0) { updatePopups(); return }
            val target = player.x - 64f
            camX = maxOf(camX, min(target, camMax()))
            if (player.x < camX) { player.x = camX; if (player.vx < 0) player.vx = 0f }
            if (player.x > camX + SCREEN_W - player.w) { player.x = camX + SCREEN_W - player.w; player.vx = 0f }
            if (level.checkpointX > 0 && player.x > level.checkpointX * 8f) checkpointReached = true
        } else {
            player.updateShip(this)
            if (player.x < camX) {
                player.x = camX
                if (player.overlapsSolid(level)) { killPlayer(); return }
            }
        }

        if (arenaLocked && !bossMusic) { bossMusic = true; audio.music(levelMusic()) }

        tileInteractions()
        if (state != State.PLAY) return
        entityInteractions()
        if (state != State.PLAY) return

        if (level.mode == Mode.PLATFORM && player.y > ROWS * 8f + 4) { killPlayer(); return }

        if (bossClearT > 0 && --bossClearT == 0) {
            if (level.mode == Mode.SHOOTER) startClear(false) else audio.music(levelMusic())
        }

        if (level.goalX >= 0 && section == 0 && player.cx >= level.goalX * 8f + 12) startClear(player.y < 72f)
        updatePopups()
    }

    private fun activateSpawns() {
        val sec = level.sections[section]
        val it = pending.iterator()
        while (it.hasNext()) {
            val s = it.next()
            val px = s.tx * 8f
            if (s.tx >= sec.start && s.tx < sec.end && px < camX + SCREEN_W + 16 && px >= camX - 16) {
                spawn(s)
                it.remove()
            }
        }
    }

    private fun spawn(s: Spawn) {
        val px = s.tx * 8f
        val py = s.ty * 8f
        val e: Entity = when (s.type) {
            EType.WALKER -> Walker(px, py, false)
            EType.SPIKY -> Walker(px, py, true)
            EType.HOPPER -> Hopper(px, py)
            EType.FLYER -> Flyer(px, py)
            EType.THROWER -> Thrower(px, py)
            EType.LEAPER -> Leaper(px, py)
            EType.CANNON -> Cannon(px, py)
            EType.PLAT_H -> Platform(px, py, 0, s.param)
            EType.PLAT_V -> Platform(px, py, 1, s.param)
            EType.PLAT_FALL -> Platform(px, py, 2, 0)
            EType.DRIFTER -> Drifter(px, py)
            EType.DIVER -> Diver(px, py)
            EType.TURRET -> Turret(px, py)
            EType.MINE -> Mine(px, py)
            EType.BOSS_SCARAB -> BossScarab(px, 14 * 8f - 13)
            EType.BOSS_GOLEM -> BossGolem(px, 14 * 8f - 29)
            EType.BOSS_KRAKEN -> BossKraken(camX + SCREEN_W + 8, py)
            EType.BOSS_STORM -> BossStorm(camX + SCREEN_W + 8, py)
        }
        if (e is Boss) boss = e
        // Plattformen zuerst aktualisieren, damit der Spieler korrekt mitfährt
        if (e is Platform) entities.add(0, e) else entities.add(e)
    }

    private fun updateEntities() {
        for (i in entities.indices) {
            val e = entities[i]
            if (e.alive) e.update(this)
        }
        entities.removeAll { e ->
            !e.alive || (e !is Boss && (e.x + e.w < camX - 40 || e.x > camX + SCREEN_W + 80 || e.y > ROWS * 8f + 40 || e.y < -120))
        }
        entities.addAll(toAdd)
        toAdd.clear()
    }

    private fun updatePopups() {
        for (p in popups) { p.t++; p.y -= 0.5f }
        popups.removeAll { it.t > 40 }
    }

    // ---------------------------------------------------------------- Kollisionen
    private fun tileInteractions() {
        val p = player
        for (c in fdiv(p.x)..fdiv(p.x + p.w - 0.01f)) for (r in fdiv(p.y)..fdiv(p.y + p.h - 0.01f)) {
            val t = level[c, r]
            if (t == T.COIN) { level[c, r] = T.EMPTY; addCoin() }
            else if (t == T.SPIKES && p.y + p.h > r * 8f + 3) hurtPlayer()
        }
        if (level.mode == Mode.PLATFORM && p.onGround) {
            val below = fdiv(p.y + p.h + 0.5f)
            for (c in fdiv(p.x)..fdiv(p.x + p.w - 0.01f)) if (level[c, below] == T.SPIKES) hurtPlayer()
        }
    }

    private fun entityInteractions() {
        val p = player
        for (e in entities) {
            if (!e.alive || !p.overlaps(e)) continue
            if (e.isItem) { e.touch(this); continue }
            if (level.mode == Mode.PLATFORM && e.stompable && p.vy > 0 && p.prevBottom <= e.y + 4) {
                e.onStomp(this)
                p.vy = if (input.a) -3.6f else -2.4f
                p.y = e.y - p.h
                continue
            }
            if (e.harmful) {
                if (p.star > 0) { if (e.isEnemy && e !is Boss) e.onHit(this) }
                else { hurtPlayer(); if (state != State.PLAY) return }
            }
        }
    }

    fun landOnPlatforms(p: Player) {
        if (p.vy < 0) return
        for (e in entities) {
            if (e !is Platform || !e.alive) continue
            if (p.x + p.w <= e.x || p.x >= e.x + e.w) continue
            val prevTop = e.y - e.dy
            if (p.prevBottom <= prevTop + 1.5f && p.y + p.h >= e.y) {
                p.y = e.y - p.h
                p.vy = 0f
                p.onGround = true
                p.ride = e
                e.stoodOn()
                return
            }
        }
    }

    fun bumpHead(p: Player) {
        val ty = fdiv(p.y - 1f)
        val cC = fdiv(p.cx)
        val cL = fdiv(p.x)
        val cR = fdiv(p.x + p.w - 0.01f)
        val tx = when {
            T.solid(level[cC, ty]) -> cC
            T.solid(level[cL, ty]) -> cL
            else -> cR
        }
        bumpBlock(tx, ty)
    }

    private fun bumpBlock(tx: Int, ty: Int) {
        val t = level[tx, ty]
        val bx = tx * 8f
        val by = ty * 8f
        if (t != T.QBLOCK && t != T.BRICK) { sfx(Sfx.BUMP); return }
        // Gegner auf dem Block werden weggeschleudert
        for (e in entities) if (e.alive && e.isEnemy && e !is Boss && e.overlaps(bx, by - 8f, 8, 8)) e.onHit(this)
        if (level[tx, ty - 1] == T.COIN) { level[tx, ty - 1] = T.EMPTY; addCoin(); add(CoinPop(bx, by - 8f)) }
        val item = level.takeContent(tx, ty) ?: if (t == T.QBLOCK) Item.COIN else null
        if (item != null) {
            level[tx, ty] = T.USED
            bumpX = tx; bumpY = ty; bumpT = 8
            when (item) {
                Item.COIN -> { addCoin(); add(CoinPop(bx, by - 8f)) }
                Item.POWER -> { add(PowerItem(bx, by, if (player.power == 0) PKind.BERRY else PKind.FLOWER)); sfx(Sfx.SPROUT) }
                Item.STAR -> { add(PowerItem(bx, by, PKind.STAR)); sfx(Sfx.SPROUT) }
                Item.HEART -> { add(PowerItem(bx, by, PKind.HEART)); sfx(Sfx.SPROUT) }
            }
        } else if (player.power > 0) {
            level[tx, ty] = T.EMPTY
            score += 50
            sfx(Sfx.BREAK)
            add(Debris(bx, by, -1f, -3f)); add(Debris(bx + 4, by, 1f, -3f))
            add(Debris(bx, by + 4, -1f, -2f)); add(Debris(bx + 4, by + 4, 1f, -2f))
        } else {
            bumpX = tx; bumpY = ty; bumpT = 8
            sfx(Sfx.BUMP)
        }
    }

    fun collect(kind: PKind, x: Float, y: Float) {
        when (kind) {
            PKind.BERRY -> { if (player.power == 0) player.changePower(1); addScore(1000, x, y); sfx(Sfx.POWERUP) }
            PKind.FLOWER -> { player.changePower(2); addScore(1000, x, y); sfx(Sfx.POWERUP) }
            PKind.STAR -> { player.star = 600; addScore(1000, x, y); sfx(Sfx.POWERUP); audio.music(Tracks.STAR) }
            PKind.HEART -> oneUp(x, y)
        }
    }

    fun onStarEnd() { audio.music(levelMusic()) }

    fun finishWarp(w: Warp) {
        section = w.destSection
        player.x = w.destX * 8f + 1
        player.y = w.destY * 8f
        player.vx = 0f; player.vy = 0f; player.warp = null
        entities.clear(); toAdd.clear()
        val sec = level.sections[section]
        camX = (player.x - 64f).coerceIn(sec.start * 8f, sec.end * 8f - SCREEN_W)
        sfx(Sfx.PIPE)
    }

    fun onBossDefeated(b: Boss) {
        bossDefeated = true
        score += 5000
        popups += Popup(b.x, b.y, "5000")
        sfx(Sfx.EXPLODE)
        for (i in 0 until 6) add(Explosion(b.x + rng.nextFloat() * b.w, b.y + rng.nextFloat() * b.h, true))
        for (tx in 0 until level.width) for (ty in 0 until ROWS) if (level[tx, ty] == T.BARRIER) level[tx, ty] = T.EMPTY
        entities.forEach { if (it is EnemyShot || it is Roller || it is FallingRock || it is Lightning) it.alive = false }
        audio.music(null)
        bossClearT = 120
    }

    fun hurtPlayer() {
        val p = player
        if (p.invuln > 0 || p.star > 0 || p.dead || state != State.PLAY) return
        if (level.mode == Mode.SHOOTER || p.power == 0) { killPlayer(); return }
        p.changePower(0)
        p.invuln = 120
        sfx(Sfx.HURT)
    }

    fun killPlayer() {
        if (player.dead) return
        player.dead = true
        player.power = 0
        player.star = 0
        player.vx = 0f; player.vy = 0f
        if (!player.ship) { player.y = player.y + player.h - 7f; player.h = 7 }
        paused = false
        audio.setPaused(false)
        audio.music(Tracks.DEATH)
        setState(State.DYING)
    }

    private fun updateDying() {
        if (stateT == 30) player.vy = -3.5f
        if (stateT > 30) { player.vy += 0.15f; player.y += player.vy }
        updatePopups()
        if (stateT >= 170) {
            lives--
            if (lives <= 0) {
                setState(State.GAMEOVER)
                audio.music(Tracks.GAMEOVER)
                if (score > save.highScore) save.highScore = score
            } else enterIntro()
        }
    }

    private fun startClear(high: Boolean) {
        clearHigh = high
        clearWait = 0
        setState(State.CLEAR)
        audio.music(Tracks.CLEAR)
        if (high) oneUp(player.x, player.y - 8)
    }

    private fun updateClear() {
        if (shake > 0) shake--
        val p = player
        if (p.ship) { p.x += 1.5f } else {
            p.vx = 0.8f; p.facing = 1
            p.vy = min(p.vy + 0.3f, 3.5f)
            p.move(level)
        }
        updateEntities()
        updatePopups()
        if (stateT > 70) {
            if (time > 0) {
                val d = min(time, 3)
                time -= d; score += d * 10
                if (stateT % 4 == 0) sfx(Sfx.TICK)
            } else if (++clearWait > 80) nextLevel()
        }
    }

    private fun nextLevel() {
        levelIndex++
        checkpointReached = false
        if (levelIndex > save.unlocked) save.unlocked = min(levelIndex, Levels.COUNT - 1)
        if (score > save.highScore) save.highScore = score
        if (levelIndex >= Levels.COUNT) {
            levelIndex = Levels.COUNT - 1
            setState(State.ENDING)
            audio.music(Tracks.TITLE)
        } else enterIntro()
    }

    // ---------------------------------------------------------------- Zeichnen
    fun render(): Screen {
        val s = screen
        s.resetClip()
        when (state) {
            State.TITLE -> drawTitle(s)
            State.STORY -> drawStory(s)
            State.INTRO -> drawIntro(s)
            State.PLAY, State.DYING, State.CLEAR -> {
                drawWorld(s)
                drawHud(s)
                if (paused) {
                    s.rect(44, 64, 72, 20, 0); s.frame(44, 64, 72, 20, 3)
                    s.textCentered("PAUSE", 71)
                }
            }
            State.GAMEOVER -> {
                s.clear(0)
                drawHud(s)
                s.textCentered("GAME OVER", 64)
                s.textCentered("PUNKTE " + score.toString().padStart(6, '0'), 88, 2)
            }
            State.ENDING -> drawEnding(s)
        }
        return s
    }

    private fun drawWorld(s: Screen) {
        s.clear(0)
        val sh = if (shake > 0) (if (frame % 2 == 0) 1 else -1) else 0
        camXi = camX.toInt() + sh
        s.clip(0, HUD_H, SCREEN_W, SCREEN_H)
        val c0 = Math.floorDiv(camXi, 8)
        for (tx in c0..c0 + 21) {
            if (tx < 0 || tx >= level.width) continue
            for (ty in 0 until ROWS) {
                val t = level[tx, ty]
                if (t == T.EMPTY) continue
                val sp = Art.tile(t, level.theme, level[tx, ty - 1] == T.GROUND, frame) ?: continue
                val yo = if (tx == bumpX && ty == bumpY && bumpT > 0) -(if (bumpT > 4) 8 - bumpT else bumpT) else 0
                s.sprite(sp, tx * 8 - camXi, HUD_H + ty * 8 + yo)
            }
        }
        drawGoal(s)
        for (e in entities) if (e.alive) e.draw(this, s)
        player.draw(this, s)
        for (p in popups) s.tiny(p.text, sx(p.x), sy(p.y), 3)
        val b = boss
        if (b != null && b.alive && (level.mode == Mode.SHOOTER || arenaLocked) && state == State.PLAY) {
            s.rect(40, HUD_H + 2, 82, 6, 0)
            s.frame(40, HUD_H + 2, 82, 6, 3)
            s.rect(41, HUD_H + 3, 80 * b.hp / b.maxHp, 4, 2)
        }
        s.resetClip()
    }

    private fun drawGoal(s: Screen) {
        if (level.goalX < 0 || section != 0) return
        val gx = level.goalX * 8 - camXi
        if (gx < -40 || gx > SCREEN_W + 8) return
        val top = HUD_H + 5 * 8
        val bottom = HUD_H + 14 * 8
        for (px in intArrayOf(gx, gx + 24)) {
            s.rect(px, top, 6, bottom - top, 3)
            s.rect(px + 1, top + 1, 4, bottom - top - 1, 1)
        }
        s.rect(gx - 2, top - 4, 34, 5, 3)
        s.rect(gx - 1, top - 3, 32, 3, 2)
        val by = top + 8 + ((frame / 2) % 56)
        s.rect(gx + 6, by, 18, 2, 3)
        s.text("ZIEL", gx + 1, top - 12, 3)
    }

    private fun drawHud(s: Screen) {
        s.rect(0, 0, SCREEN_W, HUD_H, 0)
        s.text("PIP*" + lives.toString().padStart(2, '0'), 0, 0)
        s.text("WELT", 88, 0)
        s.text("ZEIT", 128, 0)
        s.text(score.toString().padStart(6, '0'), 0, 8)
        s.text("$*" + coins.toString().padStart(2, '0'), 54, 8)
        s.text("${levelIndex / 3 + 1}-${levelIndex % 3 + 1}", 92, 8)
        s.text(if (state != State.INTRO && ::level.isInitialized && level.time > 0) time.toString().padStart(3, '0') else "---", 132, 8)
        s.rect(0, 15, SCREEN_W, 1, 2)
    }

    private fun drawTitle(s: Screen) {
        s.clear(0)
        s.rect(4, 4, 152, 64, 3)
        s.rect(6, 6, 148, 60, 1)
        s.textCentered("PIXEL", 10, 3, 2)
        s.textCentered("PLUMBER", 28, 3, 2)
        s.textCentered("LAND", 46, 3, 2)
        for (tx in 0 until 20) {
            s.sprite(Art.tile(T.GROUND, Theme.DESERT, false, 0)!!, tx * 8, 128)
            s.sprite(Art.tile(T.GROUND, Theme.DESERT, true, 0)!!, tx * 8, 136)
        }
        s.sprite(Art.BUSH, 128, 120); s.sprite(Art.CACTUS, 20, 120); s.sprite(Art.CACTUS, 20, 112)
        val px = 40 + ((frame / 2) % 90)
        s.sprite(if ((frame / 8) % 2 == 0) Art.PIP_S_STAND else Art.PIP_S_WALK, px, 120)
        s.sprite(Art.WALKER[(frame / 10) % 2], 150 - ((frame / 3) % 150), 120)
        val sel = "LEVEL " + (if (titleSel > 0) "<" else " ") + " ${titleSel / 3 + 1}-${titleSel % 3 + 1} " + (if (titleSel < min(save.unlocked, Levels.COUNT - 1)) ">" else " ")
        if ((frame / 30) % 2 == 0) s.textCentered("DRUECKE A", 76)
        s.textCentered(sel, 90, 2)
        s.textCentered("REKORD " + save.highScore.toString().padStart(6, '0'), 104, 2)
    }

    private val storyLines = listOf(
        "DER STURMFUERST", "HAT DAS", "WOLKENREICH", "UEBERFALLEN UND", "KOENIGIN LUMI", "ENTFUEHRT!", "",
        "PIP MACHT SICH", "AUF DEN WEG ..."
    )

    private fun drawStory(s: Screen) {
        s.clear(0)
        val shown = stateT / 20
        for ((i, line) in storyLines.withIndex()) if (i <= shown) s.textCentered(line, 20 + i * 11)
        s.sprite(Art.PIP_S_STAND, 76, 124)
    }

    private fun drawIntro(s: Screen) {
        s.clear(0)
        drawHud(s)
        val w = levelIndex / 3 + 1
        val st = levelIndex % 3 + 1
        s.textCentered("WELT $w-$st", 44)
        s.textCentered(Levels.worldNames[w - 1], 60, 2)
        s.sprite(Art.PIP_S_STAND, 62, 84)
        s.text("* " + lives.toString().padStart(2, '0'), 76, 84)
    }

    private fun drawEnding(s: Screen) {
        s.clear(0)
        s.textCentered("GESCHAFFT!", 16)
        s.textCentered("DER STURMFUERST", 34, 2)
        s.textCentered("IST BESIEGT.", 45, 2)
        s.textCentered("KOENIGIN LUMI", 60, 2)
        s.textCentered("IST FREI!", 71, 2)
        s.sprite(Art.PIP_B_STAND, 64, 90)
        s.sprite(Art.QUEEN, 88, 90)
        if ((frame / 20) % 2 == 0) s.sprite(Art.HEART, 76, 80)
        for (tx in 0 until 20) s.sprite(Art.tile(T.GROUND, Theme.SKY, false, 0)!!, tx * 8, 106)
        s.textCentered("PUNKTE " + score.toString().padStart(6, '0'), 120)
        if (stateT > 240 && (frame / 30) % 2 == 0) s.textCentered("ENDE", 132, 2)
    }
}
