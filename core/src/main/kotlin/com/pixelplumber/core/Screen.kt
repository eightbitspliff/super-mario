package com.pixelplumber.core

import kotlin.math.abs

class Sprite(val w: Int, val h: Int, val data: ByteArray)

/** Baut ein Sprite aus Textzeilen: '.' = transparent, '0'..'3' = Graustufe (0 = hell, 3 = dunkel). */
fun spr(vararg rows: String): Sprite {
    val h = rows.size
    val w = rows[0].length
    for (r in rows) require(r.length == w) { "Sprite-Zeilen ungleich lang: ${rows.joinToString("/")}" }
    val d = ByteArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
        d[y * w + x] = when (rows[y][x]) {
            '0' -> 0; '1' -> 1; '2' -> 2; '3' -> 3; else -> -1
        }
    }
    return Sprite(w, h, d)
}

/** 160x144 Bildspeicher mit 4 Farbstufen, wie beim klassischen Handheld. */
class Screen {
    val px = ByteArray(SCREEN_W * SCREEN_H)
    private var cx0 = 0
    private var cy0 = 0
    private var cx1 = SCREEN_W
    private var cy1 = SCREEN_H

    fun clip(x0: Int, y0: Int, x1: Int, y1: Int) {
        cx0 = maxOf(0, x0); cy0 = maxOf(0, y0); cx1 = minOf(SCREEN_W, x1); cy1 = minOf(SCREEN_H, y1)
    }

    fun resetClip() = clip(0, 0, SCREEN_W, SCREEN_H)

    fun clear(c: Int) = px.fill(c.toByte())

    fun set(x: Int, y: Int, c: Int) {
        if (x >= cx0 && y >= cy0 && x < cx1 && y < cy1) px[y * SCREEN_W + x] = c.toByte()
    }

    fun get(x: Int, y: Int): Int = if (x in 0 until SCREEN_W && y in 0 until SCREEN_H) px[y * SCREEN_W + x].toInt() else 0

    fun rect(x: Int, y: Int, w: Int, h: Int, c: Int) {
        val x0 = maxOf(x, cx0); val y0 = maxOf(y, cy0)
        val x1 = minOf(x + w, cx1); val y1 = minOf(y + h, cy1)
        val b = c.toByte()
        for (yy in y0 until y1) for (xx in x0 until x1) px[yy * SCREEN_W + xx] = b
    }

    fun frame(x: Int, y: Int, w: Int, h: Int, c: Int) {
        rect(x, y, w, 1, c); rect(x, y + h - 1, w, 1, c)
        rect(x, y, 1, h, c); rect(x + w - 1, y, 1, h, c)
    }

    fun ellipse(cx: Int, cy: Int, rx: Int, ry: Int, c: Int) {
        if (rx <= 0 || ry <= 0) return
        val rx2 = rx * rx; val ry2 = ry * ry
        for (dy in -ry..ry) for (dx in -rx..rx) {
            if (dx * dx * ry2 + dy * dy * rx2 <= rx2 * ry2) set(cx + dx, cy + dy, c)
        }
    }

    fun circle(cx: Int, cy: Int, r: Int, c: Int) = ellipse(cx, cy, r, r, c)

    fun line(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            set(x, y, c)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    fun sprite(s: Sprite, x: Int, y: Int, flipX: Boolean = false, flipY: Boolean = false, remap: IntArray? = null, scale: Int = 1) {
        for (sy in 0 until s.h) for (sx in 0 until s.w) {
            var c = s.data[sy * s.w + sx].toInt()
            if (c < 0) continue
            if (remap != null) c = remap[c]
            val dx = if (flipX) s.w - 1 - sx else sx
            val dy = if (flipY) s.h - 1 - sy else sy
            if (scale == 1) set(x + dx, y + dy, c) else rect(x + dx * scale, y + dy * scale, scale, scale, c)
        }
    }

    fun text(str: String, x: Int, y: Int, c: Int = 3, scale: Int = 1) {
        var cx = x
        for (ch in str) {
            val g = Font.glyph(ch)
            if (g != null) {
                for (row in 0 until 7) for (col in 0 until 5) if (g[row * 5 + col]) rect(cx + col * scale, y + row * scale, scale, scale, c)
            }
            cx += 8 * scale
        }
    }

    fun textWidth(str: String, scale: Int = 1) = if (str.isEmpty()) 0 else (str.length * 8 - 3) * scale

    fun textCentered(str: String, y: Int, c: Int = 3, scale: Int = 1) = text(str, (SCREEN_W - textWidth(str, scale)) / 2, y, c, scale)

    /** Kleine 3x5-Schrift für Punkte-Einblendungen. */
    fun tiny(str: String, x: Int, y: Int, c: Int = 3) {
        var cx = x
        for (ch in str) {
            val g = Font.tiny(ch)
            if (g != null) for (row in 0 until 5) for (col in 0 until 3) if (g[row * 3 + col]) set(cx + col, y + row, c)
            cx += 4
        }
    }
}

object Font {
    private val glyphs = HashMap<Char, BooleanArray>()
    private val tinyGlyphs = HashMap<Char, BooleanArray>()

    private fun def(c: Char, vararg rows: String) {
        require(rows.size == 7 && rows.all { it.length == 5 }) { "Glyph $c fehlerhaft" }
        glyphs[c] = BooleanArray(35) { rows[it / 5][it % 5] == '#' }
    }

    private fun tdef(c: Char, rows: String) {
        require(rows.length == 15) { "Tiny-Glyph $c fehlerhaft" }
        tinyGlyphs[c] = BooleanArray(15) { rows[it] == '#' }
    }

    fun glyph(c: Char): BooleanArray? = glyphs[c.uppercaseChar()]
    fun tiny(c: Char): BooleanArray? = tinyGlyphs[c.uppercaseChar()]

    init {
        def('A', " ### ", "#   #", "#   #", "#####", "#   #", "#   #", "#   #")
        def('B', "#### ", "#   #", "#   #", "#### ", "#   #", "#   #", "#### ")
        def('C', " ### ", "#   #", "#    ", "#    ", "#    ", "#   #", " ### ")
        def('D', "#### ", "#   #", "#   #", "#   #", "#   #", "#   #", "#### ")
        def('E', "#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#####")
        def('F', "#####", "#    ", "#    ", "#### ", "#    ", "#    ", "#    ")
        def('G', " ### ", "#   #", "#    ", "# ###", "#   #", "#   #", " ####")
        def('H', "#   #", "#   #", "#   #", "#####", "#   #", "#   #", "#   #")
        def('I', " ### ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### ")
        def('J', "  ###", "   # ", "   # ", "   # ", "   # ", "#  # ", " ##  ")
        def('K', "#   #", "#  # ", "# #  ", "##   ", "# #  ", "#  # ", "#   #")
        def('L', "#    ", "#    ", "#    ", "#    ", "#    ", "#    ", "#####")
        def('M', "#   #", "## ##", "# # #", "# # #", "#   #", "#   #", "#   #")
        def('N', "#   #", "##  #", "# # #", "#  ##", "#   #", "#   #", "#   #")
        def('O', " ### ", "#   #", "#   #", "#   #", "#   #", "#   #", " ### ")
        def('P', "#### ", "#   #", "#   #", "#### ", "#    ", "#    ", "#    ")
        def('Q', " ### ", "#   #", "#   #", "#   #", "# # #", "#  # ", " ## #")
        def('R', "#### ", "#   #", "#   #", "#### ", "# #  ", "#  # ", "#   #")
        def('S', " ####", "#    ", "#    ", " ### ", "    #", "    #", "#### ")
        def('T', "#####", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "  #  ")
        def('U', "#   #", "#   #", "#   #", "#   #", "#   #", "#   #", " ### ")
        def('V', "#   #", "#   #", "#   #", "#   #", "#   #", " # # ", "  #  ")
        def('W', "#   #", "#   #", "#   #", "# # #", "# # #", "# # #", " # # ")
        def('X', "#   #", "#   #", " # # ", "  #  ", " # # ", "#   #", "#   #")
        def('Y', "#   #", "#   #", " # # ", "  #  ", "  #  ", "  #  ", "  #  ")
        def('Z', "#####", "    #", "   # ", "  #  ", " #   ", "#    ", "#####")
        def('0', " ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### ")
        def('1', "  #  ", " ##  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### ")
        def('2', " ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####")
        def('3', "#####", "   # ", "  #  ", "   # ", "    #", "#   #", " ### ")
        def('4', "   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # ")
        def('5', "#####", "#    ", "#### ", "    #", "    #", "#   #", " ### ")
        def('6', "  ## ", " #   ", "#    ", "#### ", "#   #", "#   #", " ### ")
        def('7', "#####", "    #", "   # ", "  #  ", " #   ", " #   ", " #   ")
        def('8', " ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### ")
        def('9', " ### ", "#   #", "#   #", " ####", "    #", "   # ", " ##  ")
        def('-', "     ", "     ", "     ", "#####", "     ", "     ", "     ")
        def('!', "  #  ", "  #  ", "  #  ", "  #  ", "  #  ", "     ", "  #  ")
        def('.', "     ", "     ", "     ", "     ", "     ", " ##  ", " ##  ")
        def(',', "     ", "     ", "     ", "     ", " ##  ", "  #  ", " #   ")
        def(':', "     ", " ##  ", " ##  ", "     ", " ##  ", " ##  ", "     ")
        def('?', " ### ", "#   #", "    #", "   # ", "  #  ", "     ", "  #  ")
        def('\'', "  #  ", "  #  ", " #   ", "     ", "     ", "     ", "     ")
        def('*', "     ", "#   #", " # # ", "  #  ", " # # ", "#   #", "     ")
        def('>', " #   ", "  #  ", "   # ", "    #", "   # ", "  #  ", " #   ")
        def('<', "   # ", "  #  ", " #   ", "#    ", " #   ", "  #  ", "   # ")
        def('(', "   # ", "  #  ", " #   ", " #   ", " #   ", "  #  ", "   # ")
        def(')', " #   ", "  #  ", "   # ", "   # ", "   # ", "  #  ", " #   ")
        def('/', "    #", "    #", "   # ", "  #  ", " #   ", "#    ", "#    ")
        def('$', " ### ", "## ##", "## ##", "## ##", "## ##", "## ##", " ### ")
        def('&', "     ", " # # ", "#####", "#####", " ### ", "  #  ", "     ")
        def(' ', "     ", "     ", "     ", "     ", "     ", "     ", "     ")

        tdef('0', "#### ## ## ####"); tdef('1', " # ##  #  # ###"); tdef('2', "###  #####  ###")
        tdef('3', "###  # ##  ####"); tdef('4', "# ## ####  #  #"); tdef('5', "####  ###  ####")
        tdef('6', "####  #### ####"); tdef('7', "###  #  #  #  #"); tdef('8', "#### ##### ####")
        tdef('9', "#### ####  ####"); tdef('U', "# ## ## ## ####"); tdef('P', "#### #####  #  ")
    }
}
