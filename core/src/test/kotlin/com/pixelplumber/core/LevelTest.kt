package com.pixelplumber.core

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class LevelTest {
    private fun standable(lv: Level, x: Int, y: Int): Boolean {
        val t = lv[x, y]
        val above = lv[x, y - 1]
        return (T.solid(t) || T.semi(t)) && t != T.BARRIER && (!T.solid(above) || above == T.BARRIER)
    }

    /** Grobe Erreichbarkeitsprüfung: Kann man vom Start zum Ziel springen? */
    private fun reachable(lv: Level): Boolean {
        val nodes = HashSet<Pair<Int, Int>>()
        val main = lv.sections[0]
        for (x in 0 until lv.width) for (y in 0 until ROWS) if (standable(lv, x, y)) nodes += x to y
        for (s in lv.spawns) {
            when (s.type) {
                EType.PLAT_H -> for (x in s.tx until s.tx + s.param + 3) nodes += x to s.ty
                EType.PLAT_V -> for (y in s.ty - s.param..s.ty) for (x in s.tx until s.tx + 3) nodes += x to y
                EType.PLAT_FALL -> for (x in s.tx until s.tx + 3) nodes += x to s.ty
                else -> {}
            }
        }
        fun sectionOf(x: Int) = lv.sections.indexOfFirst { x >= it.start && x < it.end }
        var startY = ROWS - 1
        while (startY > 0 && T.solid(lv[lv.startX, startY - 1])) startY--
        val start = lv.startX to startY
        val seen = HashSet<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue += start; seen += start
        val bySection = nodes.groupBy { sectionOf(it.first) }
        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            if (sectionOf(x) == 0 && x >= lv.goalX && x < main.end) return true
            val next = ArrayList<Pair<Int, Int>>()
            for (n in bySection[sectionOf(x)] ?: emptyList()) {
                val dx = abs(n.first - x)
                val up = y - n.second
                // Sprungweite laut Physik: ca. 7 Kacheln mit Anlauf, bis 4 Kacheln hoch
                val maxDx = when {
                    up >= 4 -> 3
                    up == 3 -> 4
                    up >= 0 -> 5
                    else -> 6
                }
                if (dx <= maxDx && up <= 4) next += n
            }
            for (w in lv.warps) if (abs(w.tx - x) <= 1 && w.ty == y) {
                var dy = w.destY
                while (dy < ROWS && !standable(lv, w.destX, dy)) dy++
                next += w.destX to dy
            }
            for (n in next) if (seen.add(n)) queue += n
        }
        return false
    }

    @Test
    fun allLevelsBuild() {
        for (i in 0 until Levels.COUNT) {
            val lv = Levels.create(i)
            assertEquals(i / 3 + 1, lv.world)
            assertEquals(i % 3 + 1, lv.stage)
            if (lv.mode == Mode.PLATFORM) assertTrue(lv.goalX > 0, "Level ${lv.id} braucht ein Ziel")
            if (lv.mode == Mode.SHOOTER) assertTrue(lv.spawns.any { it.type.isBoss }, "Shooter ${lv.id} braucht einen Boss")
        }
    }

    @Test
    fun platformLevelsAreCompletable() {
        for (i in 0 until Levels.COUNT) {
            val lv = Levels.create(i)
            if (lv.mode != Mode.PLATFORM) continue
            if (!reachable(lv)) fail("Level ${lv.id} ${lv.name} ist nicht vom Start bis zum Ziel begehbar")
        }
    }

    @Test
    fun goalHasGroundAndIsVisible() {
        for (i in 0 until Levels.COUNT) {
            val lv = Levels.create(i)
            if (lv.mode != Mode.PLATFORM) continue
            for (x in lv.goalX - 2..lv.goalX + 6) assertTrue(T.solid(lv[x, 14]), "Level ${lv.id}: Boden am Ziel fehlt bei $x")
            assertTrue(lv.goalX * 8 + 40 <= lv.sections[0].end * 8, "Level ${lv.id}: Ziel zu nah am Rand")
        }
    }

    @Test
    fun shooterCorridorsArePassable() {
        for (i in 0 until Levels.COUNT) {
            val lv = Levels.create(i)
            if (lv.mode != Mode.SHOOTER) continue
            val end = lv.sections[0].end
            for (x in 0 until end - 1) {
                val a = (0 until ROWS).filter { !T.solid(lv[x, it]) }.toSet()
                val b = (0 until ROWS).filter { !T.solid(lv[x + 1, it]) }.toSet()
                assertTrue((a intersect b).size >= 2, "Shooter ${lv.id}: Engstelle bei Spalte $x")
            }
        }
    }

    @Test
    fun warpsSitOnPipes() {
        for (i in 0 until Levels.COUNT) {
            val lv = Levels.create(i)
            for (w in lv.warps) assertEquals(T.PIPE_TL, lv[w.tx, w.ty], "Level ${lv.id}: Warp ohne Röhre bei ${w.tx}")
        }
    }

    @Test
    fun enemiesDoNotSpawnInsideWalls() {
        for (i in 0 until Levels.COUNT) {
            val lv = Levels.create(i)
            for (s in lv.spawns) {
                if (s.type == EType.LEAPER || s.type == EType.CANNON || s.type.isBoss) continue
                assertTrue(!T.solid(lv[s.tx, s.ty]), "Level ${lv.id}: ${s.type} steckt in einer Wand bei ${s.tx},${s.ty}")
            }
        }
    }
}
