// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.floatingbutton

import androidx.annotation.StringRes
import helium314.keyboard.latin.R

/** What a gesture on the floating button can do. [name] is what gets stored in the preferences. */
enum class FloatingButtonAction(@StringRes val label: Int) {
    /** show the keyboard if it is hidden, hide it if it is shown */
    TOGGLE_KEYBOARD(R.string.floating_button_action_toggle_keyboard),
    /** switch the keyboard between fixed (full width) and floating (movable window) */
    TOGGLE_FLOATING(R.string.floating_button_action_toggle_floating),
    /** the system "choose keyboard" menu */
    IME_PICKER(R.string.floating_button_action_ime_picker),
    NONE(R.string.action_none);

    companion object {
        fun fromPref(value: String?, default: String): FloatingButtonAction =
            entries.firstOrNull { it.name == value } ?: entries.first { it.name == default }
    }
}
