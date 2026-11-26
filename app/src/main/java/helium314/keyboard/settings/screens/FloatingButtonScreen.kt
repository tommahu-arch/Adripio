// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.floatingbutton.FloatingButtonAction
import helium314.keyboard.latin.floatingbutton.FloatingButtonController
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.SwitchPreference

@Composable
fun FloatingButtonScreen(onClickBack: () -> Unit) {
    val ctx = LocalContext.current
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val enabled = ctx.prefs().getBoolean(Settings.PREF_FLOATING_BUTTON_ENABLED, Defaults.PREF_FLOATING_BUTTON_ENABLED)
    val items = listOf(
        Settings.PREF_FLOATING_BUTTON_ENABLED,
        // switched on but permission was taken away (or never given): point to where it is granted
        if (enabled && !FloatingButtonController.canDrawOverlays(ctx)) PREF_FLOATING_BUTTON_PERMISSION else null,
        R.string.floating_button_gestures_category,
        Settings.PREF_FLOATING_BUTTON_TAP,
        Settings.PREF_FLOATING_BUTTON_DOUBLE_TAP,
        Settings.PREF_FLOATING_BUTTON_LONG_PRESS,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.floating_button_title),
        settings = items
    )
}

private const val PREF_FLOATING_BUTTON_PERMISSION = "floating_button_permission"

private fun openOverlayPermission(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) { // a few devices have no such page
        Log.w("FloatingButtonScreen", "cannot open overlay permission page", e)
    }
}

private fun actionItems(context: Context, includeNone: Boolean = true) =
    FloatingButtonAction.entries
        .filter { includeNone || it != FloatingButtonAction.NONE }
        .map { context.getString(it.label) to it.name }

fun createFloatingButtonSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_FLOATING_BUTTON_ENABLED,
        R.string.floating_button_enable, R.string.floating_button_enable_summary)
    { setting ->
        val ctx = LocalContext.current
        SwitchPreference(
            setting, Defaults.PREF_FLOATING_BUTTON_ENABLED,
            allowCheckedChange = { on ->
                // turning on needs the permission: send the user to grant it, then they switch it on again
                if (on && !FloatingButtonController.canDrawOverlays(ctx)) {
                    openOverlayPermission(ctx)
                    false
                } else true
            }
        )
    },
    Setting(context, PREF_FLOATING_BUTTON_PERMISSION,
        R.string.floating_button_permission_missing, R.string.floating_button_permission_missing_summary)
    { setting ->
        val ctx = LocalContext.current
        Preference(name = setting.title, description = setting.description, onClick = { openOverlayPermission(ctx) })
    },
    Setting(context, Settings.PREF_FLOATING_BUTTON_TAP, R.string.floating_button_tap) {
        ListPreference(it, actionItems(LocalContext.current, includeNone = false), Defaults.PREF_FLOATING_BUTTON_TAP)
    },
    Setting(context, Settings.PREF_FLOATING_BUTTON_DOUBLE_TAP, R.string.floating_button_double_tap, R.string.floating_button_double_tap_summary) {
        ListPreference(it, actionItems(LocalContext.current), Defaults.PREF_FLOATING_BUTTON_DOUBLE_TAP)
    },
    Setting(context, Settings.PREF_FLOATING_BUTTON_LONG_PRESS, R.string.floating_button_long_press) {
        ListPreference(it, actionItems(LocalContext.current), Defaults.PREF_FLOATING_BUTTON_LONG_PRESS)
    },
)
