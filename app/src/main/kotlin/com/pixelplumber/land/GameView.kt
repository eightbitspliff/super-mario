package com.pixelplumber.land

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.pixelplumber.core.Game
import com.pixelplumber.core.Input
import com.pixelplumber.core.SCREEN_H
import com.pixelplumber.core.SCREEN_W
import com.pixelplumber.core.Synth
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Zeigt das Spiel an und stellt Touch-Steuerung im Handheld-Stil bereit:
 * Steuerkreuz links, A (Springen) und B (Rennen/Feuer) rechts, START zum Pausieren.
 * Unterstützt außerdem Gamepads und Tastaturen.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {
    private val synth = Synth(22050)
    private val save = PrefsSave(context)
    private val game = Game(synth, save)
    private val audio = AudioOut(synth)

    private val lock = Any()
    private val touch = Input()
    private val keys = Input()
    private val stick = Input()
    private val frameInput = Input()
    private val shown = Input()

    @Volatile private var resumed = false
    @Volatile private var hasSurface = false
    @Volatile private var loopRun = false
    private var thread: Thread? = null

    private val bmp = Bitmap.createBitmap(SCREEN_W, SCREEN_H, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(SCREEN_W * SCREEN_H)
    private val palette = intArrayOf(0xFFE0F8D0.toInt(), 0xFF88C070.toInt(), 0xFF346856.toInt(), 0xFF081820.toInt())
    private val bmpPaint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val dst = Rect()
    private val bezel = RectF()

    private val dp = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    // Layout der Bedienelemente
    private var overlay = false
    private var dpadX = 0f
    private var dpadY = 0f
    private var dpadR = 0f
    private var aX = 0f
    private var aY = 0f
    private var bX = 0f
    private var bY = 0f
    private var btnR = 0f
    private val startRect = RectF()
    private val soundRect = RectF()

    private val colBody = 0xFFD3CFC7.toInt()
    private val colBezel = 0xFF5B5E6E.toInt()
    private val colDpad = 0xFF2C2C30.toInt()
    private val colBtn = 0xFF9C2359.toInt()
    private val colLabel = 0xFF2D3A8C.toInt()

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        synth.enabled = save.soundOn
    }

    // ------------------------------------------------------------ Lebenszyklus
    fun resume() {
        resumed = true
        audio.start()
        if (hasSurface) startLoop()
    }

    fun pause() {
        resumed = false
        stopLoop()
        audio.stop()
        synchronized(lock) { game.autoPause() }
    }

    fun onBack(): Boolean = synchronized(lock) { game.onBack() }

    override fun surfaceCreated(h: SurfaceHolder) {
        hasSurface = true
        if (resumed) startLoop()
    }

    override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hh: Int) = layoutControls(w, hh)

    override fun surfaceDestroyed(h: SurfaceHolder) {
        hasSurface = false
        stopLoop()
    }

    private fun startLoop() {
        if (loopRun) return
        loopRun = true
        thread = Thread(this, "game").apply { start() }
    }

    private fun stopLoop() {
        loopRun = false
        thread?.let { if (it !== Thread.currentThread()) it.join(1000) }
        thread = null
    }

    override fun run() {
        val step = 1_000_000_000L / 60
        var last = System.nanoTime()
        var acc = 0L
        while (loopRun) {
            val now = System.nanoTime()
            acc += now - last
            last = now
            if (acc > step * 4) acc = step * 4
            var updated = false
            while (acc >= step) {
                synchronized(lock) {
                    frameInput.set(touch); frameInput.or(keys); frameInput.or(stick)
                    shown.set(frameInput)
                    game.update(frameInput)
                }
                acc -= step
                updated = true
            }
            if (updated) draw() else try { Thread.sleep(2) } catch (_: InterruptedException) {}
        }
    }

    // ------------------------------------------------------------ Zeichnen
    private fun draw() {
        val c = try { holder.lockCanvas() } catch (_: Exception) { null } ?: return
        try {
            val px = synchronized(lock) { game.render().px.copyOf() }
            for (i in pixels.indices) pixels[i] = palette[px[i].toInt()]
            bmp.setPixels(pixels, 0, SCREEN_W, 0, 0, SCREEN_W, SCREEN_H)
            c.drawColor(if (overlay) Color.BLACK else colBody)
            if (!overlay) {
                paint.color = colBezel
                c.drawRoundRect(bezel, 10 * dp, 10 * dp, paint)
            }
            c.drawBitmap(bmp, null, dst, bmpPaint)
            drawControls(c)
        } finally {
            holder.unlockCanvasAndPost(c)
        }
    }

    private fun drawControls(c: Canvas) {
        val alpha = if (overlay) 0x70 else 0xFF
        fun col(v: Int) = (v and 0x00FFFFFF) or (alpha shl 24)

        // Steuerkreuz
        val arm = dpadR * 0.36f
        paint.color = col(colDpad)
        c.drawRoundRect(RectF(dpadX - dpadR, dpadY - arm, dpadX + dpadR, dpadY + arm), arm * 0.35f, arm * 0.35f, paint)
        c.drawRoundRect(RectF(dpadX - arm, dpadY - dpadR, dpadX + arm, dpadY + dpadR), arm * 0.35f, arm * 0.35f, paint)
        paint.color = col(0xFF55555C.toInt())
        val hi = arm * 0.9f
        if (shown.left) c.drawCircle(dpadX - dpadR + hi, dpadY, arm * 0.5f, paint)
        if (shown.right) c.drawCircle(dpadX + dpadR - hi, dpadY, arm * 0.5f, paint)
        if (shown.up) c.drawCircle(dpadX, dpadY - dpadR + hi, arm * 0.5f, paint)
        if (shown.down) c.drawCircle(dpadX, dpadY + dpadR - hi, arm * 0.5f, paint)

        // A / B
        textPaint.textSize = btnR * 0.9f
        for ((x, y, label, down) in listOf(Btn(aX, aY, "A", shown.a), Btn(bX, bY, "B", shown.b))) {
            paint.color = col(if (down) 0xFF6E1640.toInt() else colBtn)
            c.drawCircle(x, y, btnR, paint)
            textPaint.color = if (overlay) Color.argb(0xB0, 255, 255, 255) else colLabel
            c.drawText(label, x, y + btnR * 1.75f, textPaint)
            if (overlay) c.drawText(label, x, y + textPaint.textSize * 0.33f, textPaint)
        }

        // START und Ton
        textPaint.textSize = startRect.height() * 0.55f
        for ((r, label, down) in listOf(Triple(startRect, "START", shown.start), Triple(soundRect, if (synth.enabled) "TON AN" else "TON AUS", false))) {
            paint.color = col(if (down) 0xFF3A3A40.toInt() else 0xFF77777F.toInt())
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, paint)
            textPaint.color = if (overlay) Color.argb(0xC0, 255, 255, 255) else Color.WHITE
            c.drawText(label, r.centerX(), r.centerY() + textPaint.textSize * 0.36f, textPaint)
        }
    }

    private data class Btn(val x: Float, val y: Float, val label: String, val down: Boolean)

    private fun layoutControls(w: Int, h: Int) {
        val portrait = h > w
        if (portrait) {
            val scale = if (w >= SCREEN_W * 2) (w * 0.92f / SCREEN_W).toInt() else 1
            val sw = SCREEN_W * scale
            val sh = SCREEN_H * scale
            val left = (w - sw) / 2
            val top = (h * 0.05f).toInt() + (8 * dp).toInt()
            dst.set(left, top, left + sw, top + sh)
            val pad = 12 * dp
            bezel.set(left - pad, top - pad, left + sw + pad, top + sh + pad)
            val areaTop = bezel.bottom + 10 * dp
            val areaH = h - areaTop
            dpadR = min(w * 0.19f, areaH * 0.3f)
            dpadX = w * 0.26f
            dpadY = areaTop + areaH * 0.42f
            btnR = dpadR * 0.46f
            aX = w * 0.84f; aY = dpadY - btnR * 0.7f
            bX = w * 0.62f; bY = dpadY + btnR * 0.7f
            val sw2 = 70 * dp; val sh2 = 24 * dp
            val sy = min(h - sh2 - 16 * dp, dpadY + dpadR + 28 * dp)
            startRect.set(w * 0.58f - sw2 / 2, sy, w * 0.58f + sw2 / 2, sy + sh2)
            soundRect.set(w * 0.36f - sw2 / 2, sy, w * 0.36f + sw2 / 2, sy + sh2)
            overlay = false
        } else {
            val scale = max(1, (h * 0.96f / SCREEN_H).toInt())
            var sw = SCREEN_W * scale
            var sh = SCREEN_H * scale
            if (sw > w) { sw = w; sh = w * SCREEN_H / SCREEN_W }
            val left = (w - sw) / 2
            val top = (h - sh) / 2
            dst.set(left, top, left + sw, top + sh)
            bezel.set(left - 6 * dp, top - 6 * dp, left + sw + 6 * dp, top + sh + 6 * dp)
            val side = left.toFloat()
            overlay = side < h * 0.32f
            dpadR = if (overlay) h * 0.17f else min(side * 0.4f, h * 0.2f)
            dpadX = if (overlay) dpadR * 1.35f else side / 2
            dpadY = h * 0.62f
            btnR = dpadR * 0.5f
            val rightCenter = if (overlay) w - dpadR * 1.4f else w - side / 2
            aX = rightCenter + btnR * 1.15f; aY = h * 0.56f
            bX = rightCenter - btnR * 1.15f; bY = h * 0.68f
            if (aX + btnR > w - 4 * dp) { val d = aX + btnR - (w - 4 * dp); aX -= d; bX -= d }
            val sw2 = 64 * dp; val sh2 = 22 * dp
            startRect.set(rightCenter - sw2 / 2, h * 0.14f, rightCenter + sw2 / 2, h * 0.14f + sh2)
            val lc = dpadX
            soundRect.set(lc - sw2 / 2, h * 0.14f, lc + sw2 / 2, h * 0.14f + sh2)
        }
    }

    // ------------------------------------------------------------ Eingabe
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val t = Input()
        val action = e.actionMasked
        if (action != MotionEvent.ACTION_CANCEL) {
            for (i in 0 until e.pointerCount) {
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) && i == e.actionIndex) continue
                applyTouch(t, e.getX(i), e.getY(i))
            }
        }
        if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)) {
            val i = e.actionIndex
            if (soundRect.contains(e.getX(i), e.getY(i))) {
                synth.enabled = !synth.enabled
                save.soundOn = synth.enabled
            }
        }
        synchronized(lock) { touch.set(t) }
        return true
    }

    private fun applyTouch(t: Input, x: Float, y: Float) {
        val dx = x - dpadX
        val dy = y - dpadY
        if (hypot(dx, dy) <= dpadR * 1.45f) {
            val dead = dpadR * 0.22f
            if (dx < -dead) t.left = true
            if (dx > dead) t.right = true
            if (dy > dpadR * 0.42f) t.down = true
            if (dy < -dpadR * 0.42f) t.up = true
            return
        }
        val da = hypot(x - aX, y - aY)
        val db = hypot(x - bX, y - bY)
        when {
            da <= btnR * 1.4f && da <= db -> t.a = true
            db <= btnR * 1.4f -> t.b = true
            startRect.contains(x, y) -> t.start = true
        }
    }

    fun onKey(e: KeyEvent): Boolean {
        val down = when (e.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        synchronized(lock) {
            when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> keys.left = down
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> keys.right = down
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> keys.up = down
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> keys.down = down
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_DPAD_CENTER -> keys.a = down
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_SHIFT_LEFT -> keys.b = down
                KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_P -> keys.start = down
                else -> return false
            }
        }
        return true
    }

    fun onJoystick(e: MotionEvent): Boolean {
        if (e.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK || e.action != MotionEvent.ACTION_MOVE) return false
        val x = e.getAxisValue(MotionEvent.AXIS_X) + e.getAxisValue(MotionEvent.AXIS_HAT_X)
        val y = e.getAxisValue(MotionEvent.AXIS_Y) + e.getAxisValue(MotionEvent.AXIS_HAT_Y)
        synchronized(lock) {
            stick.left = x < -0.4f; stick.right = x > 0.4f
            stick.up = y < -0.5f; stick.down = y > 0.5f
        }
        return true
    }
}
