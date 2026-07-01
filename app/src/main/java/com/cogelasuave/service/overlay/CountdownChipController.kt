package com.cogelasuave.service.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * A small, draggable "pill" drawn over other apps showing the time left in the
 * current session as MM:SS. Tapping it (a press that doesn't turn into a drag)
 * invokes [onTap] so the host can end the session early; dragging just moves it.
 *
 * Independent of [OverlayController]: this is a separate, non-modal window that
 * floats above the watched app without intercepting its touches (except on the
 * chip itself).
 */
class CountdownChipController(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var chip: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    val isShowing: Boolean get() = chip != null

    /** Shows the chip with [initialText]. [onTap] fires on a tap (not a drag). */
    fun show(initialText: String, onTap: () -> Unit) {
        if (isShowing) return

        val pad = dp(12)
        val view = TextView(context).apply {
            text = initialText
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(pad, dp(8), pad, dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(Color.parseColor("#CC222222"))
            }
        }

        val lp = buildLayoutParams()
        attachTouch(view, lp, onTap)

        runCatching { windowManager.addView(view, lp) }
            .onSuccess {
                chip = view
                params = lp
            }
    }

    /** Updates the visible countdown text; no-op if the chip isn't showing. */
    fun update(text: String) {
        chip?.text = text
    }

    fun dismiss() {
        val view = chip ?: return
        runCatching { windowManager.removeViewImmediate(view) }
        chip = null
        params = null
    }

    private fun attachTouch(
        view: View,
        lp: WindowManager.LayoutParams,
        onTap: () -> Unit,
    ) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val touchSlop = dp(8)

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(view, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) onTap()
                    true
                }
                else -> false
            }
        }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // Not focusable: the app behind stays interactive; only the chip itself
            // grabs touches.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
