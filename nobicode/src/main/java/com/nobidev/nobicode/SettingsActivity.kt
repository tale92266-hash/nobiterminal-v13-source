package com.nobidev.nobicode

import android.os.Bundle
import android.view.LayoutInflater
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nobidev.nobicode.databinding.NobicActivitySettingsBinding
import com.nobidev.nobicode.preferences.EditorPreferences
import com.nobidev.nobicode.theme.ThemeManager
import com.nobidev.nobicode.theme.Themes

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: NobicActivitySettingsBinding
    private lateinit var prefs: EditorPreferences

    private lateinit var categorySections: List<CategorySection>

    /** Currently open category screen, or null while the category list is shown. */
    private var currentCategory: CategorySection? = null

    /** Search is debounced (performance): typing never filters on every keystroke. */
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    private var searchQuery: String = ""
    private val keyCategory = "nobi_settings_open_category"

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(keyCategory, currentCategory?.name)
    }

    private data class CategorySection(
        val name: String,
        val card: View,
        val header: View,
        val body: View,
        val chevron: ImageView
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v15: follow the Nobi time-aware terminal theme for editor Settings UI.
        // v15: (Theme.NobiCode is fixed-dark, so this bridge takes visible effect
        // v15: only if that theme ever becomes DayNight; it is harmless until then.)
        nobiDesiredNightMode()?.let { delegate.localNightMode = it }
        binding = NobicActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = EditorPreferences(this)

        // Drill-in: toolbar back returns from a category screen to the
        // category list; only on the list itself does it close Settings.
        binding.settingsToolbar.setNavigationOnClickListener {
            if (currentCategory != null) showCategoryList() else finish()
        }
        // System back follows the same rule (list -> close, detail -> list).
        onBackPressedDispatcher.addCallback(this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentCategory != null) {
                        showCategoryList()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            })

        // Keep the header below the status bar on edge-to-edge displays
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsToolbar) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, sb.top, v.paddingRight, v.paddingBottom)
            insets
        }

        loadPreferences()
        setupListeners()
        setupSettingsSearch()
        setupCategoryCards()
        // Rotation / process recreation re-opens the same category screen.
        savedInstanceState?.getString(keyCategory)?.let { name ->
            categorySections.firstOrNull { it.name == name }?.let { showCategory(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        // v15: re-apply the Nobi terminal theme if it changed while Settings was open.
        nobiDesiredNightMode()?.let { mode ->
            if (delegate.localNightMode != mode) delegate.localNightMode = mode
        }
    }

    /**
     * v15: night mode following the Nobi time-aware terminal theme, or null
     * when the time-theme pref is off (upstream behavior is then kept).
     */
    private fun nobiDesiredNightMode(): Int? {
        val prefs = getSharedPreferences("nobi_v7", MODE_PRIVATE)
        if (!prefs.getBoolean("time_theme", false)) return null
        var wanted = prefs.getString("time_theme_applied", "") ?: ""
        if (wanted.isEmpty()) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            wanted = if (hour >= 7 && hour < 19) "daylight" else "dark"
        }
        return when (wanted) {
            "daylight" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else -> null
        }
    }

    private fun setupSettingsSearch() {
        binding.settingsSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                // Debounce: wait until typing pauses instead of filtering
                // the whole list on every keystroke (performance rule).
                pendingSearch?.let { searchHandler.removeCallbacks(it) }
                val runnable = Runnable { applySearch() }
                pendingSearch = runnable
                searchHandler.postDelayed(runnable, 250)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun applySearch() {
        if (!::categorySections.isInitialized) return
        val current = currentCategory
        if (current == null) applySearchToList() else applySearchToDetail(current)
    }

    /**
     * Drill-in navigation (v10): the category list is one screen, and tapping
     * a category opens that category's own screen — no accordion expand /
     * collapse on a single screen. All rows stay the same views (state is
     * preserved, nothing is re-inflated), only visibility changes.
     */
    private fun setupCategoryCards() {
        categorySections = listOf(
            CategorySection("General", binding.cardGeneral, binding.cardGeneralHeader, binding.bodyGeneral, binding.chevronGeneral),
            CategorySection("Appearance", binding.cardAppearance, binding.cardAppearanceHeader, binding.bodyAppearance, binding.chevronAppearance),
            CategorySection("Editor", binding.cardEditor, binding.cardEditorHeader, binding.bodyEditor, binding.chevronEditor),
            CategorySection("Coding assistance", binding.cardCoding, binding.cardCodingHeader, binding.bodyCoding, binding.chevronCoding),
            CategorySection("Files & saving", binding.cardFiles, binding.cardFilesHeader, binding.bodyFiles, binding.chevronFiles)
        )
        for (section in categorySections) {
            section.header.setOnClickListener { showCategory(section) }
        }
        showCategoryList()
    }

    /** Category list screen: all 5 categories, bodies hidden, footer visible. */
    private fun showCategoryList() {
        currentCategory = null
        binding.settingsToolbar.title = "Settings"
        binding.settingsStripImage.visibility = View.VISIBLE
        binding.settingsSearch.visibility = View.VISIBLE
        binding.btnResetSettings.visibility = View.VISIBLE
        binding.versionText.visibility = View.VISIBLE
        for (section in categorySections) {
            // Restore every row first: detail search may have hidden some.
            val body = section.body as ViewGroup
            for (i in 0 until body.childCount) body.getChildAt(i).visibility = View.VISIBLE
            section.card.visibility = View.VISIBLE
            section.header.visibility = View.VISIBLE
            section.body.visibility = View.GONE
            section.chevron.rotation = 0f
        }
        applySearchToList()
    }

    /** One category's dedicated screen: only its options, with a back path. */
    private fun showCategory(section: CategorySection) {
        currentCategory = section
        binding.settingsToolbar.title = section.name
        binding.settingsStripImage.visibility = View.GONE
        binding.btnResetSettings.visibility = View.GONE
        binding.versionText.visibility = View.GONE
        for (other in categorySections) {
            if (other === section) {
                other.card.visibility = View.VISIBLE
                other.header.visibility = View.GONE
                other.body.visibility = View.VISIBLE
            } else {
                other.card.visibility = View.GONE
            }
        }
        applySearchToDetail(section)
    }

    /** List search: a category shows if its name or any of its rows match. */
    private fun applySearchToList() {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            for (section in categorySections) section.card.visibility = View.VISIBLE
            return
        }
        for (section in categorySections) {
            var anyMatch = section.name.lowercase().contains(q)
            val body = section.body as ViewGroup
            for (i in 0 until body.childCount) {
                if (collectRowText(body.getChildAt(i)).lowercase().contains(q)) {
                    anyMatch = true
                    break
                }
            }
            section.card.visibility = if (anyMatch) View.VISIBLE else View.GONE
        }
    }

    /** Detail search: only the open category's rows are filtered. */
    private fun applySearchToDetail(section: CategorySection) {
        val q = searchQuery.trim().lowercase()
        val body = section.body as ViewGroup
        for (i in 0 until body.childCount) {
            val row = body.getChildAt(i)
            row.visibility = if (q.isEmpty()
                || collectRowText(row).lowercase().contains(q)) View.VISIBLE else View.GONE
        }
    }

    private fun collectRowText(v: View): String {
        val sb = StringBuilder()
        if (v is TextView) sb.append(v.text).append(' ')
        if (v is ViewGroup) for (i in 0 until v.childCount) sb.append(collectRowText(v.getChildAt(i)))
        return sb.toString()
    }

    private fun loadPreferences() {
        binding.themeSubtitle.text = ThemeManager(this).current.displayName
        binding.fontSizeSubtitle.text = "${prefs.fontSize.toInt()} sp"
        binding.switchWordWrap.isChecked = prefs.isWordWrap
        binding.switchLineNumbers.isChecked = prefs.showLineNumbers
        binding.switchFolding.isChecked = prefs.codeFolding
        binding.switchStickyScroll.isChecked = prefs.stickyScroll
        binding.switchMinimap.isChecked = prefs.minimap
        binding.switchHighlightLine.isChecked = prefs.highlightCurrentLine
        binding.switchIndentGuides.isChecked = prefs.indentGuides
        binding.switchTrimWhitespace.isChecked = prefs.trimTrailingWhitespace
        binding.switchAutoIndentPaste.isChecked = prefs.autoIndentOnPaste
        binding.switchFontLigatures.isChecked = prefs.fontLigatures
        binding.lineSpacingSubtitle.text = "${(prefs.lineSpacing * 100).toInt()}%"
        binding.switchKeepScreenOn.isChecked = prefs.keepScreenOn
        binding.switchTypewriterMode.isChecked = prefs.typewriterMode
        binding.switchSymbolsBar.isChecked = prefs.symbolsBar
        binding.switchHideHiddenFiles.isChecked = prefs.hideHiddenFiles
        binding.switchInsertFinalNewline.isChecked = prefs.insertFinalNewline
        binding.switchFormatOnSave.isChecked = prefs.formatOnSave
        binding.switchShowWhitespace.isChecked = prefs.showWhitespace
        binding.switchRainbowBrackets.isChecked = prefs.rainbowBrackets
        binding.switchSyntaxHighlight.isChecked = prefs.syntaxHighlight
        binding.switchAutoIndent.isChecked = prefs.autoIndent
        binding.switchPinchZoom.isChecked = prefs.pinchZoom
        binding.switchEmmet.isChecked = prefs.emmet
        binding.switchAutoClose.isChecked = prefs.autoCloseBrackets
        binding.switchAutoSave.isChecked = prefs.autoSave
        binding.switchReadOnly.isChecked = prefs.readOnly
        binding.tabSizeSubtitle.text = "${prefs.tabSize} Spaces"
        binding.lineGuideSubtitle.text = if (prefs.lineGuideColumn > 0) "${prefs.lineGuideColumn} columns" else "Off"
    }

    private fun setupListeners() {
        binding.rowTheme.setOnClickListener {
            // v7 (46): plugin themes appear alongside the built-ins.
            val themeList = Themes.allWithPlugins()
            val themes = themeList.map { it.displayName }.toTypedArray()
            val themeManager = ThemeManager(this)
            val checkedItem = themeList.indexOfFirst { it.id == themeManager.currentThemeId }
                .coerceAtLeast(0)
            MaterialAlertDialogBuilder(this)
                .setTitle("Select Theme")
                .setSingleChoiceItems(themes, checkedItem) { dialog, which ->
                    val chosen = themeList[which]
                    themeManager.current = chosen
                    prefs.themeName = chosen.displayName
                    binding.themeSubtitle.text = chosen.displayName
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.rowFontSize.setOnClickListener {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_slider, null)
            val seekBar = view.findViewById<SeekBar>(R.id.slider)
            val valueText = view.findViewById<TextView>(R.id.valueText)
            seekBar.max = 30
            seekBar.min = 8
            seekBar.progress = prefs.fontSize.toInt()
            valueText.text = "${prefs.fontSize.toInt()} sp"
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                    valueText.text = "$v sp"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
            MaterialAlertDialogBuilder(this)
                .setTitle("Font size")
                .setView(view)
                .setPositiveButton("OK") { _, _ ->
                    prefs.fontSize = seekBar.progress.toFloat()
                    binding.fontSizeSubtitle.text = "${seekBar.progress} sp"
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.switchWordWrap.setOnCheckedChangeListener { _, isChecked ->
            prefs.isWordWrap = isChecked
        }

        binding.switchLineNumbers.setOnCheckedChangeListener { _, isChecked ->
            prefs.showLineNumbers = isChecked
        }

        binding.switchFolding.setOnCheckedChangeListener { _, isChecked ->
            prefs.codeFolding = isChecked
        }

        binding.switchStickyScroll.setOnCheckedChangeListener { _, isChecked ->
            prefs.stickyScroll = isChecked
        }

        binding.switchMinimap.setOnCheckedChangeListener { _, isChecked ->
            prefs.minimap = isChecked
        }

        binding.switchHighlightLine.setOnCheckedChangeListener { _, isChecked ->
            prefs.highlightCurrentLine = isChecked
        }

        binding.switchIndentGuides.setOnCheckedChangeListener { _, isChecked ->
            prefs.indentGuides = isChecked
        }

        binding.switchTrimWhitespace.setOnCheckedChangeListener { _, isChecked ->
            prefs.trimTrailingWhitespace = isChecked
        }

        binding.switchAutoIndentPaste.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoIndentOnPaste = isChecked
        }

        binding.switchFontLigatures.setOnCheckedChangeListener { _, isChecked ->
            prefs.fontLigatures = isChecked
        }

        binding.switchKeepScreenOn.setOnCheckedChangeListener { _, isChecked ->
            prefs.keepScreenOn = isChecked
        }

        binding.switchTypewriterMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.typewriterMode = isChecked
        }

        binding.switchSymbolsBar.setOnCheckedChangeListener { _, isChecked ->
            prefs.symbolsBar = isChecked
        }

        binding.switchHideHiddenFiles.setOnCheckedChangeListener { _, isChecked ->
            prefs.hideHiddenFiles = isChecked
        }

        binding.switchInsertFinalNewline.setOnCheckedChangeListener { _, isChecked ->
            prefs.insertFinalNewline = isChecked
        }

        binding.switchFormatOnSave.setOnCheckedChangeListener { _, isChecked ->
            prefs.formatOnSave = isChecked
        }

        binding.rowLineSpacing.setOnClickListener {
            val options = arrayOf("100% (Compact)", "115% (Normal)", "130% (Relaxed)", "150% (Loose)")
            val values = floatArrayOf(1.0f, 1.15f, 1.3f, 1.5f)
            val currentIdx = values.indexOfFirst { it == prefs.lineSpacing }.coerceAtLeast(0)
            MaterialAlertDialogBuilder(this)
                .setTitle("Line spacing")
                .setSingleChoiceItems(options, currentIdx) { dialog, which ->
                    prefs.lineSpacing = values[which]
                    binding.lineSpacingSubtitle.text = "${(values[which] * 100).toInt()}%"
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.switchShowWhitespace.setOnCheckedChangeListener { _, isChecked ->
            prefs.showWhitespace = isChecked
        }
        binding.switchRainbowBrackets.setOnCheckedChangeListener { _, isChecked ->
            prefs.rainbowBrackets = isChecked
        }

        binding.switchAutoClose.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoCloseBrackets = isChecked
        }

        binding.switchSyntaxHighlight.setOnCheckedChangeListener { _, isChecked ->
            prefs.syntaxHighlight = isChecked
        }

        binding.switchAutoIndent.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoIndent = isChecked
        }

        binding.switchPinchZoom.setOnCheckedChangeListener { _, isChecked ->
            prefs.pinchZoom = isChecked
        }

        binding.switchEmmet.setOnCheckedChangeListener { _, isChecked ->
            prefs.emmet = isChecked
        }

        binding.switchAutoSave.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoSave = isChecked
        }

        binding.switchReadOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.readOnly = isChecked
        }

        try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            binding.versionText.text = "NobiIDE v${pi.versionName} (${pi.longVersionCode})"
        } catch (_: Exception) { }
        binding.versionText.setOnLongClickListener {
            val clip = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clip.setPrimaryClip(android.content.ClipData.newPlainText("version", binding.versionText.text))
            android.widget.Toast.makeText(this, "Version copied", android.widget.Toast.LENGTH_SHORT).show()
            true
        }
        binding.versionText.setOnClickListener {
            val density = resources.displayMetrics.density
            val pad = (20 * density).toInt()
            val aboutView = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                addView(android.widget.ImageView(context).apply {
                    setImageResource(R.drawable.nobi_img_about_banner)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        (140 * density).toInt()
                    )
                })
                addView(android.widget.TextView(context).apply {
                    text = "A fast, simple code editor for Android.\n\nMade with care for developers who code on the go."
                    setPadding(pad, pad, pad, 0)
                })
            }
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("NobiIDE")
                .setView(aboutView)
                .setPositiveButton(R.string.close, null)
                .show()
        }
        binding.btnResetSettings.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Reset settings?")
                .setMessage("All settings will return to their defaults.")
                .setPositiveButton("Reset") { _, _ ->
                    prefs.clearAll()
                    recreate()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.rowTabSize.setOnClickListener {
            val sizes = arrayOf("2 Spaces", "4 Spaces", "8 Spaces")
            val currentIdx = when (prefs.tabSize) { 2 -> 0; 8 -> 2; else -> 1 }
            MaterialAlertDialogBuilder(this)
                .setTitle("Tab Size")
                .setSingleChoiceItems(sizes, currentIdx) { dialog, which ->
                    val size = when (which) { 0 -> 2; 2 -> 8; else -> 4 }
                    prefs.tabSize = size
                    binding.tabSizeSubtitle.text = "$size Spaces"
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.rowLineGuide.setOnClickListener {
            val options = arrayOf("Off", "80 columns", "100 columns", "120 columns")
            val currentIdx = when (prefs.lineGuideColumn) { 80 -> 1; 100 -> 2; 120 -> 3; else -> 0 }
            MaterialAlertDialogBuilder(this)
                .setTitle("Line length guide")
                .setSingleChoiceItems(options, currentIdx) { dialog, which ->
                    val col = when (which) { 1 -> 80; 2 -> 100; 3 -> 120; else -> 0 }
                    prefs.lineGuideColumn = col
                    binding.lineGuideSubtitle.text = if (col > 0) "$col columns" else "Off"
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
