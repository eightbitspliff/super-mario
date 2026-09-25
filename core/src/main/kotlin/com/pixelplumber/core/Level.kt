package com.pixelplumber.core

enum class Theme { DESERT, SEA, MOUNTAIN, SKY }
enum class Mode { PLATFORM, SHOOTER }
enum class Item { COIN, POWER, STAR, HEART }

enum class EType {
    WALKER, HOPPER, FLYER, SPIKY, THROWER, LEAPER, CANNON,
    PLAT_H, PLAT_V, PLAT_FALL,
    DRIFTER, DIVER, TURRET, MINE,
    BOSS_SCARAB, BOSS_GOLEM, BOSS_KRAKEN, BOSS_STORM;

    val isBoss get() = name.startsWith("BOSS")
}

class Spawn(val type: EType, val tx: Int, val ty: Int, val param: Int = 0)
class Warp(val tx: Int, val ty: Int, val destX: Int, val destY: Int, val destSection: Int)
class Section(val start: Int, val end: Int)

class Level(
    val world: Int,
    val stage: Int,
    val name: String,
    val theme: Theme,
    val mode: Mode,
    val width: Int,
    val tiles: IntArray,
    val contents: MutableMap<Int, Item>,
    val spawns: List<Spawn>,
    val warps: List<Warp>,
    val sections: List<Section>,
    val startX: Int,
    val checkpointX: Int,
    val goalX: Int,
    val arenaX: Int,
    val time: Int
) {
    val id get() = "$world-$stage"

    operator fun get(tx: Int, ty: Int): Int = when {
        tx < 0 || tx >= width -> T.HARD
        ty < 0 || ty >= ROWS -> T.EMPTY
        else -> tiles[ty * width + tx]
    }

    operator fun set(tx: Int, ty: Int, v: Int) {
        if (tx in 0 until width && ty in 0 until ROWS) tiles[ty * width + tx] = v
    }

    fun content(tx: Int, ty: Int): Item? = contents[ty * width + tx]
    fun takeContent(tx: Int, ty: Int): Item? = contents.remove(ty * width + tx)
}

/** Kleine DSL, um Level übersichtlich im Code zu beschreiben. Boden liegt standardmäßig ab Zeile 14. */
class LevelBuilder(
    private val world: Int,
    private val stage: Int,
    private val name: String,
    private val theme: Theme,
    private val mainWidth: Int,
    private val mode: Mode = Mode.PLATFORM
) {
    private val width = mainWidth + 24
    private val tiles = IntArray(width * ROWS)
    private val contents = HashMap<Int, Item>()
    private val spawns = ArrayList<Spawn>()
    private val warps = ArrayList<Warp>()
    private var hasBonus = false
    var startX = 2
    var checkpointX = -1
    private var goalX = -1
    private var arenaX = -1
    var time = if (mode == Mode.SHOOTER) 0 else 400

    fun get(x: Int, y: Int): Int = if (x in 0 until width && y in 0 until ROWS) tiles[y * width + x] else T.EMPTY
    fun set(x: Int, y: Int, t: Int) { if (x in 0 until width && y in 0 until ROWS) tiles[y * width + x] = t }
    private fun deco(x: Int, y: Int, t: Int) { if (get(x, y) == T.EMPTY) set(x, y, t) }

    fun fill(x: Int, y: Int, w: Int, h: Int, t: Int) { for (i in x until x + w) for (j in y until y + h) set(i, j, t) }
    fun ground(x: Int, len: Int, top: Int = 14) = fill(x, top, len, ROWS - top, T.GROUND)
    fun hard(x: Int, y: Int, w: Int = 1, h: Int = 1) = fill(x, y, w, h, T.HARD)
    fun bricks(x: Int, y: Int, len: Int) = fill(x, y, len, 1, T.BRICK)
    fun semi(x: Int, y: Int, len: Int) = fill(x, y, len, 1, T.SEMI)
    fun spikes(x: Int, y: Int, len: Int) = fill(x, y, len, 1, T.SPIKES)
    fun coins(x: Int, y: Int, len: Int) = fill(x, y, len, 1, T.COIN)

    fun q(x: Int, y: Int, item: Item = Item.COIN) { set(x, y, T.QBLOCK); contents[y * width + x] = item }
    fun brickWith(x: Int, y: Int, item: Item) { set(x, y, T.BRICK); contents[y * width + x] = item }

    fun pipe(x: Int, top: Int) {
        set(x, top, T.PIPE_TL); set(x + 1, top, T.PIPE_TR)
        var y = top + 1
        while (y < ROWS && !T.solid(get(x, y))) { set(x, y, T.PIPE_L); set(x + 1, y, T.PIPE_R); y++ }
    }

    fun stairsUp(x: Int, n: Int) { for (i in 0 until n) hard(x + i, 13 - i, 1, i + 1) }
    fun stairsDown(x: Int, n: Int) { for (i in 0 until n) hard(x + i, 13 - (n - 1 - i), 1, n - i) }

    fun cannon(x: Int, y: Int) {
        hard(x, y, 1, 1)
        var yy = y + 1
        while (yy < ROWS && !T.solid(get(x, yy))) { set(x, yy, T.HARD); yy++ }
        spawns += Spawn(EType.CANNON, x, y)
    }

    fun water(x: Int, len: Int) { for (i in x until x + len) { deco(i, 14, T.WAVE); deco(i, 15, T.WATER) } }

    fun enemy(type: EType, x: Int, y: Int = 13, param: Int = 0) { spawns += Spawn(type, x, y, param) }
    fun platH(x: Int, y: Int, range: Int) = enemy(EType.PLAT_H, x, y, range)
    fun platV(x: Int, y: Int, range: Int) = enemy(EType.PLAT_V, x, y, range)
    fun platFall(x: Int, y: Int) = enemy(EType.PLAT_FALL, x, y)

    // Dekoration (nicht fest)
    fun hill(x: Int, h: Int, base: Int = 13) {
        for (r in 0 until h) {
            val y = base - r
            deco(x + r, y, T.HILL_L)
            for (i in x + r + 1 until x + 2 * h - 1 - r) deco(i, y, T.HILL_F)
            deco(x + 2 * h - 1 - r, y, T.HILL_R)
        }
    }
    fun cloud(x: Int, y: Int, len: Int = 1) { deco(x, y, T.CLOUD_L); for (i in 1..len) deco(x + i, y, T.CLOUD_M); deco(x + len + 1, y, T.CLOUD_R) }
    fun bush(x: Int, y: Int = 13) = deco(x, y, T.BUSH)
    fun palm(x: Int, h: Int, base: Int = 13) { for (i in 0 until h) deco(x, base - i, T.TRUNK); deco(x, base - h, T.PALM) }
    fun cactus(x: Int, h: Int, base: Int = 13) { for (i in 0 until h) deco(x, base - i, T.CACTUS) }
    fun crystal(x: Int, y: Int) = deco(x, y, T.CRYSTAL)

    fun checkpoint(x: Int) { checkpointX = x }
    fun goal(x: Int) { goalX = x }

    /** Bonusraum hinter einer Röhre. Rückkehr erfolgt bei [returnX] (von oben herabfallend). */
    fun bonusRoom(pipeX: Int, pipeTop: Int, returnX: Int, variant: Int = 0) {
        require(!hasBonus) { "Nur ein Bonusraum pro Level" }
        hasBonus = true
        pipe(pipeX, pipeTop)
        val rs = mainWidth + 2
        ground(rs, 20)
        hard(rs, 0, 1, 14); hard(rs + 19, 0, 1, 14); hard(rs, 0, 20, 1)
        coins(rs + 3, 6, 11); coins(rs + 3, 8, 11); coins(rs + 4, 10, 9)
        bricks(rs + 3, 4, 11)
        if (variant == 1) q(rs + 8, 4, Item.HEART) else q(rs + 8, 4, Item.POWER)
        pipe(rs + 16, 12)
        warps += Warp(pipeX, pipeTop, rs + 3, 2, 1)
        warps += Warp(rs + 16, 12, returnX, 2, 0)
    }

    /** Boss-Arena: Kamera stoppt bei [x], rechts versperrt eine Säule bis zum Sieg. */
    fun bossArena(x: Int, boss: EType, bossX: Int = x + 14) {
        arenaX = x
        for (y in 0 until 14) set(x + 19, y, T.BARRIER)
        spawns += Spawn(boss, bossX, 10)
    }

    fun boss(type: EType, x: Int, y: Int = 7) { spawns += Spawn(type, x, y) }

    // Shooter-Gelände
    fun floor(x: Int, len: Int, top: Int) = fill(x, top, len, ROWS - top, T.GROUND)
    fun ceil(x: Int, len: Int, bottom: Int) = fill(x, 0, len, bottom + 1, T.HARD)

    fun build(): Level {
        val sections = ArrayList<Section>()
        sections += Section(0, mainWidth)
        if (hasBonus) sections += Section(mainWidth + 2, mainWidth + 22)
        return Level(
            world, stage, name, theme, mode, width, tiles.copyOf(), HashMap(contents),
            spawns.sortedBy { it.tx }, warps.toList(), sections,
            startX, checkpointX, goalX, arenaX, time
        )
    }
}
