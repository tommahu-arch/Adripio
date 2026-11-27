// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.dictionary.DictionaryInfoUtils
import helium314.keyboard.latin.dictionary.MissingDictionaryDialog
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.settings.SettingsSubtype
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.mainLayoutName
import helium314.keyboard.latin.utils.mainLayoutNameOrQwerty
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.ListPickerDialog
import helium314.keyboard.settings.dialogs.TextInputDialog
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.latin.utils.previewDark
import java.util.Locale

@Composable
fun LanguageScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    val sortedSubtypes = remember(b?.value) { getSortedSubtypes(ctx) }
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val enabledSubtypes = SubtypeSettings.getEnabledSubtypes()
    SearchScreen(
        onClickBack = onClickBack,
        title = {
            Column {
                Text(stringResource(R.string.language_and_layouts_title))
                Text(stringResource(
                    R.string.text_tap_languages),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        filteredItems = { term ->
            sortedSubtypes.filter { subtype ->
                subtype.displayName().replace("(", "")
                    .splitOnWhitespace().any { it.startsWith(term, true) }
            }
        },
        itemContent = { SubtypeRow(it, it in enabledSubtypes) }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubtypeRow(subtype: InputMethodSubtype, isEnabled: Boolean) {
    val ctx = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showPickerDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val currentLayout = subtype.mainLayoutNameOrQwerty()
    val isCustomLayout = LayoutUtilsCustom.isCustomLayout(currentLayout)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { SettingsDestination.navigateTo(SettingsDestination.Subtype + subtype.toSettingsSubtype().toPref()) },
                onLongClick = { showMenu = true },
            )
            .padding(vertical = 6.dp, horizontal = 16.dp)
    ) {
        var showNoDictDialog by remember { mutableStateOf(false) }
        Column(modifier = Modifier.weight(1f)) {
            Text(subtype.displayName(), style = MaterialTheme.typography.bodyLarge)
            val description = if (SubtypeSettings.isAdditionalSubtype(subtype)) {
                val secondaryLocales = subtype.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)?.split(Separators.KV)
                    ?.joinToString(", ") { it.constructLocale().localizedDisplayName(ctx.resources) }
                stringResource(R.string.custom_subtype) + (secondaryLocales?.let { "\n$it" } ?: "")
            } else null
            if (description != null)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = {
                if (it && !dictsAvailable(subtype.locale(), ctx))
                    showNoDictDialog = true
                if (it) SubtypeSettings.addEnabledSubtype(ctx.prefs(), subtype)
                else SubtypeSettings.removeEnabledSubtype(ctx, subtype)
            }
        )
        if (showNoDictDialog)
            MissingDictionaryDialog({ showNoDictDialog = false }, subtype.locale())
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.create_another_layout)) },
                onClick = { showMenu = false; showPickerDialog = true }
            )
            if (isCustomLayout)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename_custom_template)) },
                    onClick = { showMenu = false; showRenameDialog = true }
                )
            if (SubtypeSettings.isAdditionalSubtype(subtype))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_custom_subtype)) },
                    onClick = { showMenu = false; showDeleteDialog = true }
                )
        }
    }
    if (showPickerDialog) {
        val locale = subtype.locale()
        val used = SubtypeSettings.getAllAvailableSubtypes().filter { it.locale() == locale }
            .mapTo(HashSet()) { it.mainLayoutNameOrQwerty() }
        val available = (LayoutUtils.getAvailableLayouts(LayoutType.MAIN, ctx, locale)
            + LayoutUtilsCustom.getLayoutFiles(LayoutType.MAIN, ctx, locale).map { it.name })
            .filter { it !in used }.distinct()
        ListPickerDialog(
            onDismissRequest = { showPickerDialog = false },
            items = available + CUSTOM_ENTRY,
            title = { Text(stringResource(R.string.create_another_layout)) },
            getItemName = {
                if (it == CUSTOM_ENTRY) stringResource(R.string.create_custom_layout_entry)
                else if (LayoutUtilsCustom.isCustomLayout(it)) LayoutUtilsCustom.getDisplayName(it)
                else SubtypeLocaleUtils.getLayoutDisplayNameInSystemLocale(it, locale)
            },
            showRadioButtons = false,
            onItemSelected = {
                if (it == CUSTOM_ENTRY) showCreateDialog = true
                else addSubtypeWithLayout(ctx, subtype.toSettingsSubtype(), it)
            }
        )
    }
    if (showCreateDialog)
        LayoutNameDialog(
            initialName = "",
            title = stringResource(R.string.create_custom_layout_entry),
            locale = subtype.locale(),
            onDismissRequest = { showCreateDialog = false },
            onConfirmed = { createLayoutFromTemplate(ctx, subtype.toSettingsSubtype(), it) }
        )
    if (showRenameDialog)
        LayoutNameDialog(
            initialName = LayoutUtilsCustom.getDisplayName(currentLayout),
            title = stringResource(R.string.rename_custom_template),
            locale = subtype.locale(),
            onDismissRequest = { showRenameDialog = false },
            onConfirmed = { renameCustomLayout(ctx, currentLayout, it, subtype.locale()) }
        )
    if (showDeleteDialog)
        ConfirmationDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_custom_subtype_confirm, subtype.displayName())) },
            confirmButtonText = stringResource(R.string.delete),
            onConfirmed = { deleteCustomSubtype(ctx, subtype) }
        )
}

private const val CUSTOM_ENTRY = "\u0000custom"

@Composable
private fun LayoutNameDialog(
    initialName: String,
    title: String,
    locale: java.util.Locale,
    onDismissRequest: () -> Unit,
    onConfirmed: (name: String) -> Unit,
) {
    val ctx = LocalContext.current
    TextInputDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = onConfirmed,
        title = { Text(title) },
        initialText = initialName,
        textInputLabel = { Text(stringResource(R.string.custom_layout_name)) },
        checkTextValid = { name ->
            name.isNotBlank() && LayoutUtilsCustom.getLayoutName(name.trim(), LayoutType.MAIN, locale).let { layoutName ->
                !LayoutUtilsCustom.getLayoutFile(layoutName, LayoutType.MAIN, ctx).exists()
            }
        },
    )
}

/** Copies the main layout of [base] into a new custom layout called [name], and adds and enables a subtype using it. */
private fun createLayoutFromTemplate(ctx: Context, base: SettingsSubtype, name: String) {
    val layoutName = LayoutUtilsCustom.getLayoutName(name.trim(), LayoutType.MAIN, base.locale)
    val baseLayout = base.mainLayoutName() ?: SubtypeLocaleUtils.QWERTY
    val content = if (LayoutUtilsCustom.isCustomLayout(baseLayout))
        LayoutUtilsCustom.getLayoutFile(baseLayout, LayoutType.MAIN, ctx).readText()
    else LayoutUtils.getContentWithPlus(baseLayout, base.locale, ctx)
    LayoutUtilsCustom.getLayoutFile(layoutName, LayoutType.MAIN, ctx).writeText(content)
    LayoutUtilsCustom.onLayoutFileChanged()
    val newSubtype = base.withLayout(LayoutType.MAIN, layoutName)
    SubtypeUtilsAdditional.changeAdditionalSubtype(newSubtype, newSubtype, ctx)
    SubtypeSettings.addEnabledSubtype(ctx.prefs(), newSubtype.toAdditionalSubtype())
    KeyboardSwitcher.getInstance().setThemeNeedsReload()
    (ctx.getActivity() as? SettingsActivity)?.prefChanged()
}

private fun addSubtypeWithLayout(ctx: Context, base: SettingsSubtype, layoutName: String) {
    var newSubtype = base.withLayout(LayoutType.MAIN, layoutName)
    // the PC AZERTY layout is designed for a PC style bottom row
    if (layoutName == "pcazerty_fr") newSubtype = newSubtype.withLayout(LayoutType.FUNCTIONAL, "functional_keys_pc")
    SubtypeUtilsAdditional.changeAdditionalSubtype(newSubtype, newSubtype, ctx)
    SubtypeSettings.addEnabledSubtype(ctx.prefs(), newSubtype.toAdditionalSubtype())
    (ctx.getActivity() as? SettingsActivity)?.prefChanged()
}

/** Removes the additional [subtype], and its custom layout file if no other subtype uses it. */
private fun deleteCustomSubtype(ctx: Context, subtype: InputMethodSubtype) {
    val layout = subtype.mainLayoutName()
    SubtypeUtilsAdditional.removeAdditionalSubtype(ctx, subtype)
    SubtypeSettings.reloadEnabledSubtypes(ctx)
    if (layout != null && LayoutUtilsCustom.isCustomLayout(layout)
        && SubtypeSettings.getAllAvailableSubtypes().none { it.mainLayoutName() == layout }) {
        LayoutUtilsCustom.getLayoutFile(layout, LayoutType.MAIN, ctx).delete()
        LayoutUtilsCustom.onLayoutFileChanged()
    }
    KeyboardSwitcher.getInstance().setThemeNeedsReload()
    (ctx.getActivity() as? SettingsActivity)?.prefChanged()
}

private fun renameCustomLayout(ctx: Context, oldLayout: String, newName: String, locale: java.util.Locale) {
    val newLayout = LayoutUtilsCustom.getLayoutName(newName.trim(), LayoutType.MAIN, locale)
    if (newLayout == oldLayout) return
    val oldFile = LayoutUtilsCustom.getLayoutFile(oldLayout, LayoutType.MAIN, ctx)
    LayoutUtilsCustom.getLayoutFile(newLayout, LayoutType.MAIN, ctx).writeText(oldFile.readText())
    oldFile.delete()
    LayoutUtilsCustom.onLayoutFileChanged()
    SubtypeSettings.onRenameLayout(LayoutType.MAIN, oldLayout, newLayout, ctx)
    KeyboardSwitcher.getInstance().setThemeNeedsReload()
    (ctx.getActivity() as? SettingsActivity)?.prefChanged()
}

private fun dictsAvailable(locale: Locale, context: Context): Boolean {
    if (locale.language == SubtypeLocaleUtils.NO_LANGUAGE) return true // incorrect, but we don't want to show the dialog for "no language"
    val (dicts, hasInternal) = getUserAndInternalDictionaries(context, locale)
    return hasInternal || dicts.isNotEmpty()
}

// sorting by display name is still slow, even with the cache... but probably good enough
private fun getSortedSubtypes(context: Context): List<InputMethodSubtype> {
    val availableSubtypes = SubtypeSettings.getAllAvailableSubtypes()
    val availableLocales = availableSubtypes.mapTo(HashSet()) { it.locale() }
    val systemLocales = SubtypeSettings.getSystemLocales().map {
        LocaleUtils.getBestMatch(it, availableLocales) { st -> st }
    }
    val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
    val localesWithDictionary = DictionaryInfoUtils.getCacheDirectories(context).mapNotNull { dir ->
        if (dir.list()?.any { it.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX) } == true)
            dir.name.constructLocale()
        else null
    }

    val defaultAdditionalSubtypes = Defaults.PREF_ADDITIONAL_SUBTYPES.split(Separators.SETS).map {
        it.substringBefore(Separators.SET) to (it.substringAfter(Separators.SET) + ",AsciiCapable,EmojiCapable,isAdditionalSubtype")
    }
    fun isDefaultSubtype(subtype: InputMethodSubtype): Boolean =
        defaultAdditionalSubtypes.any { it.first == subtype.locale().language && it.second == subtype.extraValue }

    val subtypeSortComparator = compareBy<InputMethodSubtype>(
        { it !in enabledSubtypes },
        { it.locale() !in localesWithDictionary },
        { it.locale() !in systemLocales },
        { !(SubtypeSettings.isAdditionalSubtype(it) && !isDefaultSubtype(it) ) },
        {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) it.languageTag == SubtypeLocaleUtils.NO_LANGUAGE
            else it.locale == SubtypeLocaleUtils.NO_LANGUAGE
        },
        { it.displayName() }
    )
    return availableSubtypes.sortedWith(subtypeSortComparator)
}

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            LanguageScreen { }
        }
    }
}
