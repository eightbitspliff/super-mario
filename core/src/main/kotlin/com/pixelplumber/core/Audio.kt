package com.pixelplumber.core

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.pow

interface AudioSink {
    fun sfx(s: Sfx)
    fun music(t: Track?)
    fun setPaused(p: Boolean)

    companion object {
        val NONE = object : AudioSink {
            override fun sfx(s: Sfx) {}
            override fun music(t: Track?) {}
            override fun setPaused(p: Boolean) {}
        }
    }
}

class Seg(val f0: Float, val f1: Float, val ms: Int, val noise: Boolean = false)

enum class Sfx(val priority: Int, val segs: List<Seg>) {
    JUMP(1, listOf(Seg(280f, 640f, 110))),
    COIN(2, listOf(Seg(988f, 988f, 60), Seg(1319f, 1319f, 200))),
    STOMP(3, listOf(Seg(500f, 120f, 90), Seg(300f, 80f, 60, true))),
    KICK(3, listOf(Seg(800f, 200f, 90))),
    BUMP(1, listOf(Seg(160f, 110f, 70))),
    BREAK(3, listOf(Seg(3000f, 300f, 180, true))),
    SPROUT(2, listOf(Seg(200f, 900f, 260))),
    POWERUP(4, listOf(Seg(523f, 523f, 60), Seg(659f, 659f, 60), Seg(784f, 784f, 60), Seg(1047f, 1047f, 60), Seg(1319f, 1319f, 60), Seg(1568f, 1568f, 120))),
    ONEUP(5, listOf(Seg(659f, 659f, 80), Seg(784f, 784f, 80), Seg(1319f, 1319f, 80), Seg(1047f, 1047f, 80), Seg(1175f, 1175f, 80), Seg(1568f, 1568f, 160))),
    FIRE(1, listOf(Seg(1200f, 300f, 70))),
    SHOOT(1, listOf(Seg(1400f, 700f, 45))),
    HURT(4, listOf(Seg(700f, 150f, 350))),
    PIPE(3, listOf(Seg(300f, 100f, 110), Seg(300f, 100f, 110), Seg(300f, 100f, 110))),
    EXPLODE(4, listOf(Seg(1200f, 40f, 450, true))),
    BOSSHIT(4, listOf(Seg(400f, 90f, 160, true), Seg(200f, 100f, 80))),
    TICK(0, listOf(Seg(1800f, 1800f, 15))),
    PAUSE(5, listOf(Seg(988f, 988f, 60), Seg(784f, 784f, 60), Seg(988f, 988f, 60), Seg(784f, 784f, 60))),
    CANNON(2, listOf(Seg(600f, 60f, 200, true)))
}

class Note(val freq: Float, val steps: Int)

class Track(val stepFrames: Int, val loop: Boolean, vararg ch: String) {
    val channels: List<List<Note>> = ch.map { parseChannel(it) }
    val stepLengths get() = channels.map { c -> c.sumOf { it.steps } }

    companion object {
        private val SEMIS = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

        fun noteFreq(n: String): Float {
            if (n == "R") return 0f
            var semi = SEMIS[n[0]] ?: error("Unbekannte Note $n")
            var i = 1
            if (n[i] == '#') { semi++; i++ } else if (n[i] == 'b') { semi--; i++ }
            val octave = n.substring(i).toInt()
            val midi = (octave + 1) * 12 + semi
            return (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()
        }

        fun parseChannel(s: String): List<Note> = s.trim().split(Regex("\\s+")).map { tok ->
            val parts = tok.split('/')
            Note(noteFreq(parts[0]), parts[1].toInt())
        }
    }
}

/** Eigene Kompositionen im Chiptune-Stil. Jede Stimme ist in 16tel-Schritten notiert. */
object Tracks {
    val TITLE = Track(
        7, true,
        "C5/2 E5/2 G5/2 C6/2 B5/2 G5/2 E5/4  F5/2 A5/2 C6/2 A5/2 G5/4 E5/4  D5/2 F5/2 A5/2 D6/2 C6/2 B5/2 A5/4  G5/2 B5/2 D6/2 B5/2 C6/8",
        "C3/4 G3/4 C3/4 G3/4  F3/4 C4/4 C3/4 G3/4  D3/4 A3/4 F3/4 A3/4  G3/4 D3/4 C3/8"
    )
    val DESERT = Track(
        8, true,
        "D5/2 E5/2 F5/2 E5/2 D5/2 C#5/2 D5/4  A4/2 Bb4/2 A4/2 G4/2 A4/6 R/2  D5/2 E5/2 F5/2 G5/2 A5/4 G5/2 F5/2  E5/2 F5/2 E5/2 C#5/2 D5/8",
        "D3/4 A3/4 D3/4 A3/4  A2/4 E3/4 A2/4 E3/4  Bb2/4 F3/4 G2/4 D3/4  A2/4 E3/4 D3/8"
    )
    val SEA = Track(
        8, true,
        "F5/3 A5/1 C6/4 A5/2 G5/2 F5/4  G5/3 A5/1 Bb5/4 A5/2 G5/2 E5/4  F5/3 A5/1 C6/4 D6/2 C6/2 A5/4  Bb5/2 A5/2 G5/2 E5/2 F5/8",
        "F3/4 C4/4 F3/4 C4/4  C3/4 G3/4 C3/4 G3/4  F3/4 C4/4 D3/4 A3/4  C3/4 G3/4 F3/8"
    )
    val MOUNTAIN = Track(
        8, true,
        "E5/2 E5/1 E5/1 G5/2 E5/2 B5/4 A5/2 G5/2  F#5/2 F#5/1 F#5/1 A5/2 F#5/2 D5/4 R/4  E5/2 G5/2 B5/2 E6/2 D6/2 B5/2 G5/4  A5/2 F#5/2 D#5/2 F#5/2 E5/8",
        "E3/2 B3/2 E3/2 B3/2 E3/2 B3/2 E3/2 B3/2  D3/2 A3/2 D3/2 A3/2 D3/2 A3/2 D3/2 A3/2  C3/2 G3/2 C3/2 G3/2 G2/2 D3/2 G2/2 D3/2  B2/2 F#3/2 B2/2 F#3/2 E3/8"
    )
    val SKY = Track(
        7, true,
        "G5/1 B5/1 D6/2 B5/2 G5/2 A5/2 B5/2 C6/4  B5/2 A5/2 G5/2 F#5/2 E5/4 D5/4  E5/1 F#5/1 G5/2 A5/2 B5/2 C6/2 D6/2 E6/4  D6/2 C6/2 B5/2 A5/2 G5/8",
        "G3/4 D4/4 G3/4 D4/4  C3/4 G3/4 D3/4 A3/4  C3/4 G3/4 G3/4 D4/4  D3/4 A3/4 G3/8"
    )
    val SHOOTER = Track(
        6, true,
        "A4/1 A4/1 C5/1 A4/1 E5/2 A4/2 D5/2 C5/2 B4/4  G4/1 G4/1 B4/1 G4/1 D5/2 G4/2 C5/2 B4/2 A4/4  F4/2 A4/2 C5/2 F5/2 E5/2 C5/2 A4/4  E4/2 G#4/2 B4/2 E5/2 D5/2 B4/2 G#4/4",
        "A2/2 A3/2 A2/2 A3/2 A2/2 A3/2 A2/2 A3/2  G2/2 G3/2 G2/2 G3/2 G2/2 G3/2 G2/2 G3/2  F2/2 F3/2 F2/2 F3/2 F2/2 F3/2 F2/2 F3/2  E2/2 E3/2 E2/2 E3/2 E2/2 E3/2 E2/2 E3/2"
    )
    val BOSS = Track(
        6, true,
        "C5/1 C#5/1 C5/1 C#5/1 C5/2 G4/2 C5/1 C#5/1 C5/1 C#5/1 D#5/4  D5/1 D#5/1 D5/1 D#5/1 D5/2 A4/2 D5/1 D#5/1 D5/1 D#5/1 F5/4",
        "C3/2 C3/2 G2/2 C3/2 C3/2 G2/2 F#2/2 G2/2  D3/2 D3/2 A2/2 D3/2 D3/2 A2/2 G#2/2 A2/2"
    )
    val STAR = Track(
        5, true,
        "C5/1 C5/1 C5/1 D5/1 R/1 C5/1 E5/1 G5/1 C6/2 G5/2 E5/2 D5/2",
        "C3/2 G3/2 C3/2 G3/2 C3/2 G3/2 C3/2 G3/2"
    )
    val CLEAR = Track(
        7, false,
        "C5/2 E5/2 G5/2 C6/4 G5/2 C6/8",
        "C3/2 C3/2 E3/2 G3/4 G3/2 C4/8"
    )
    val DEATH = Track(
        7, false,
        "B4/2 F5/2 R/1 F5/2 F5/2 E5/2 D5/2 C5/8",
        "G3/2 G3/2 R/1 G3/2 G3/2 G3/2 F3/2 E3/8"
    )
    val GAMEOVER = Track(
        8, false,
        "C5/4 G4/4 E4/4 A4/3 B4/3 A4/3 G#4/4 A#4/4 G#4/4 G4/2 F4/2 G4/8",
        "C3/12 F3/9 E3/12 G2/12"
    )

    val all = listOf(TITLE, DESERT, SEA, MOUNTAIN, SKY, SHOOTER, BOSS, STAR, CLEAR, DEATH, GAMEOVER)

    fun forTheme(t: Theme) = when (t) {
        Theme.DESERT -> DESERT; Theme.SEA -> SEA; Theme.MOUNTAIN -> MOUNTAIN; Theme.SKY -> SKY
    }
}

/**
 * Einfacher Klangerzeuger: Pulswelle (Melodie), Dreieck (Bass), plus ein Effektkanal
 * mit Puls- oder Rauschgenerator. Erzeugt 16-Bit-Mono-Samples.
 */
class Synth(val rate: Int = 22050) : AudioSink {
    private val sfxQueue = ConcurrentLinkedQueue<Sfx>()
    @Volatile private var pendingTrack: Track? = null
    @Volatile private var trackRequested = false
    @Volatile private var paused = false
    @Volatile var enabled = true

    private var track: Track? = null
    private val noteIdx = IntArray(2)
    private val noteLeft = IntArray(2)
    private val noteLen = IntArray(2)
    private val freq = FloatArray(2)
    private val phase = FloatArray(2)

    private var curSfx: Sfx? = null
    private var segIdx = 0
    private var segPos = 0
    private var sfxPhase = 0f
    private var lfsr = 0x7FFF
    private var noiseAcc = 0f
    private var noiseOut = 1f

    override fun sfx(s: Sfx) { sfxQueue.add(s) }
    override fun music(t: Track?) { pendingTrack = t; trackRequested = true }
    override fun setPaused(p: Boolean) { paused = p }

    fun render(out: ShortArray, n: Int) {
        if (trackRequested) {
            trackRequested = false
            track = pendingTrack
            noteIdx.fill(0); noteLeft.fill(0); freq.fill(0f)
        }
        var s = sfxQueue.poll()
        while (s != null) {
            val c = curSfx
            if (c == null || s.priority >= c.priority) { curSfx = s; segIdx = 0; segPos = 0 }
            s = sfxQueue.poll()
        }
        val t = track
        for (i in 0 until n) {
            var v = 0f
            if (t != null && !paused) v += musicSample(t)
            if (curSfx != null) v += sfxSample()
            if (!enabled) v = 0f
            out[i] = (v.coerceIn(-1f, 1f) * 26000f).toInt().toShort()
        }
    }

    private fun musicSample(t: Track): Float {
        var v = 0f
        val stepSamples = t.stepFrames * rate / 60
        for (c in 0 until minOf(2, t.channels.size)) {
            val ch = t.channels[c]
            if (noteLeft[c] <= 0) {
                if (noteIdx[c] >= ch.size) {
                    if (t.loop) noteIdx[c] = 0 else { freq[c] = 0f; noteLeft[c] = Int.MAX_VALUE; noteLen[c] = Int.MAX_VALUE; continue }
                }
                val note = ch[noteIdx[c]++]
                noteLen[c] = note.steps * stepSamples
                noteLeft[c] = noteLen[c]
                freq[c] = note.freq
            }
            noteLeft[c]--
            val f = freq[c]
            if (f > 0f) {
                val pos = 1f - noteLeft[c].toFloat() / noteLen[c]
                if (pos < 0.88f) {
                    phase[c] = (phase[c] + f / rate) % 1f
                    val env = 1f - pos * 0.5f
                    v += if (c == 0) (if (phase[c] < 0.25f) 1f else -1f) * 0.12f * env
                    else (4f * abs(phase[c] - 0.5f) - 1f) * 0.24f * env
                }
            }
        }
        return v
    }

    private fun sfxSample(): Float {
        val sfx = curSfx ?: return 0f
        val seg = sfx.segs[segIdx]
        val segLen = maxOf(1, seg.ms * rate / 1000)
        val p = segPos.toFloat() / segLen
        val f = seg.f0 + (seg.f1 - seg.f0) * p
        val out = if (seg.noise) {
            noiseAcc += f / rate
            while (noiseAcc >= 1f) {
                noiseAcc -= 1f
                val bit = (lfsr xor (lfsr shr 1)) and 1
                lfsr = (lfsr shr 1) or (bit shl 14)
                noiseOut = if (lfsr and 1 == 1) 1f else -1f
            }
            noiseOut
        } else {
            sfxPhase = (sfxPhase + f / rate) % 1f
            if (sfxPhase < 0.5f) 1f else -1f
        }
        val env = 1f - p * 0.4f
        segPos++
        if (segPos >= segLen) {
            segPos = 0; segIdx++
            if (segIdx >= sfx.segs.size) curSfx = null
        }
        return out * 0.14f * env
    }
}
