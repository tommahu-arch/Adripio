// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.floatingbutton

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A small round button drawn over other apps (needs "Display over other apps"), owned by the keyboard service.
 * Tap, double tap and long press each run one [FloatingButtonAction]; dragging moves the button.
 * It is shown while the setting is on and the permission is granted, and removed otherwise.
 */
class FloatingButtonController(private val ime: InputMethodService) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs = ime.prefs()
    private val windowManager = ime.getSystemService(WindowManager::class.java)
    private var button: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val sizePx = (SIZE_DP * ime.resources.displayMetrics.density).roundToInt()

    fun onCreate() {
        prefs.registerOnSharedPreferenceChangeListener(this)
        update()
    }

    fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        remove()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == Settings.PREF_FLOATING_BUTTON_ENABLED) update()
    }

    /** Adds or removes the button to match the setting and the permission. Cheap, may be called often. */
    fun update() {
        val wanted = prefs.getBoolean(Settings.PREF_FLOATING_BUTTON_ENABLED, Defaults.PREF_FLOATING_BUTTON_ENABLED)
            && canDrawOverlays(ime)
        if (wanted && button == null) add()
        else if (!wanted && button != null) remove()
    }

    /** Puts the button back inside the screen, e.g. after rotation. */
    fun reposition() {
        val view = button ?: return
        val lp = params ?: return
        applyPosition(lp)
        try { windowManager.updateViewLayout(view, lp) } catch (e: IllegalArgumentException) { Log.w(TAG, "reposition", e) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun add() {
        val lp = WindowManager.LayoutParams(
            sizePx, sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        applyPosition(lp)
        val view = createView()
        try {
            windowManager.addView(view, lp)
        } catch (e: Exception) { // e.g. BadTokenException or SecurityException when the permission was just revoked
            Log.w(TAG, "could not add floating button", e)
            return
        }
        button = view
        params = lp
    }

    private fun remove() {
        val view = button ?: return
        button = null
        params = null
        try { windowManager.removeView(view) } catch (e: IllegalArgumentException) { Log.w(TAG, "remove", e) }
    }

    private fun createView(): View {
        val density = ime.resources.displayMetrics.density
        val view = ImageView(ime)
        view.setImageResource(R.drawable.ic_settings_floating_button)
        view.setColorFilter(Color.WHITE)
        val pad = (12 * density).roundToInt()
        view.setPadding(pad, pad, pad, pad)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xCC202020.toInt())
            setStroke(density.roundToInt().coerceAtLeast(1), 0x66FFFFFF)
        }
        view.contentDescription = ime.getString(R.string.floating_button_title)

        val detector = GestureDetector(ime, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            // without a double tap action there is nothing to wait for, so tap right away
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (action(Settings.PREF_FLOATING_BUTTON_DOUBLE_TAP, Defaults.PREF_FLOATING_BUTTON_DOUBLE_TAP) == FloatingButtonAction.NONE)
                    run(Settings.PREF_FLOATING_BUTTON_TAP, Defaults.PREF_FLOATING_BUTTON_TAP)
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (action(Settings.PREF_FLOATING_BUTTON_DOUBLE_TAP, Defaults.PREF_FLOATING_BUTTON_DOUBLE_TAP) != FloatingButtonAction.NONE)
                    run(Settings.PREF_FLOATING_BUTTON_TAP, Defaults.PREF_FLOATING_BUTTON_TAP)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                run(Settings.PREF_FLOATING_BUTTON_DOUBLE_TAP, Defaults.PREF_FLOATING_BUTTON_DOUBLE_TAP)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (action(Settings.PREF_FLOATING_BUTTON_LONG_PRESS, Defaults.PREF_FLOATING_BUTTON_LONG_PRESS) == FloatingButtonAction.NONE)
                    return
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                run(Settings.PREF_FLOATING_BUTTON_LONG_PRESS, Defaults.PREF_FLOATING_BUTTON_LONG_PRESS)
            }
        })

        val slop = ViewConfiguration.get(ime).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        view.setOnTouchListener { _, ev ->
            val lp = params ?: return@setOnTouchListener false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startX = lp.x; startY = lp.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging && hypot(ev.rawX - downX, ev.rawY - downY) > slop) {
                        dragging = true
                        val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                        detector.onTouchEvent(cancel) // no tap or long press once it is a drag
                        cancel.recycle()
                    }
                    if (dragging) {
                        val (maxX, maxY) = maxPosition()
                        lp.x = (startX + ev.rawX - downX).roundToInt().coerceIn(0, maxX)
                        lp.y = (startY + ev.rawY - downY).roundToInt().coerceIn(0, maxY)
                        try { windowManager.updateViewLayout(view, lp) } catch (e: IllegalArgumentException) { Log.w(TAG, "drag", e) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) savePosition(lp)
            }
            if (!dragging) detector.onTouchEvent(ev)
            true
        }
        return view
    }

    private fun action(key: String, default: String) = FloatingButtonAction.fromPref(prefs.getString(key, default), default)

    private fun run(key: String, default: String) {
        when (action(key, default)) {
            FloatingButtonAction.TOGGLE_KEYBOARD ->
                if (ime.isInputViewShown) ime.requestHideSelf(0) else ime.requestShowSelf(0)
            FloatingButtonAction.TOGGLE_FLOATING -> KeyboardSwitcher.getInstance().toggleFloatingKeyboard()
            FloatingButtonAction.IME_PICKER -> ime.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            FloatingButtonAction.NONE -> {}
        }
    }

    // position is stored as a fraction of the free space, so it survives rotation and different screen sizes
    private fun maxPosition(): Pair<Int, Int> {
        val dm = ime.resources.displayMetrics
        return (dm.widthPixels - sizePx).coerceAtLeast(0) to (dm.heightPixels - sizePx).coerceAtLeast(0)
    }

    private fun applyPosition(lp: WindowManager.LayoutParams) {
        val (maxX, maxY) = maxPosition()
        lp.x = (prefs.getFloat(Settings.PREF_FLOATING_BUTTON_POS_X, 1f).coerceIn(0f, 1f) * maxX).roundToInt()
        lp.y = (prefs.getFloat(Settings.PREF_FLOATING_BUTTON_POS_Y, 0.5f).coerceIn(0f, 1f) * maxY).roundToInt()
    }

    private fun savePosition(lp: WindowManager.LayoutParams) {
        val (maxX, maxY) = maxPosition()
        prefs.edit {
            putFloat(Settings.PREF_FLOATING_BUTTON_POS_X, if (maxX == 0) 1f else lp.x.toFloat() / maxX)
            putFloat(Settings.PREF_FLOATING_BUTTON_POS_Y, if (maxY == 0) 0.5f else lp.y.toFloat() / maxY)
        }
    }

    companion object {
        private const val SIZE_DP = 48
        private val TAG = FloatingButtonController::class.java.simpleName

        fun canDrawOverlays(context: android.content.Context) =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || AndroidSettings.canDrawOverlays(context)
    }
}
