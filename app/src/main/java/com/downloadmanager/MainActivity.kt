package com.downloadmanager

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME    = "dm_prefs"
        const val KEY_MODE      = "mode"
        const val KEY_DM        = "dm"
        const val MODE_EXTERNAL = "external"
        const val MODE_BROWSER  = "browser"

        val STATIC_LOCKED_DISPLAY = listOf(
            "mega.nz"        to "JS decryption",
            "mega.co.nz"     to "JS decryption",
            "mediafire.com"  to "JS-assembled URLs",
            "1fichier.com"   to "Login + CAPTCHA",
            "testfile.org"   to "Anti-hotlink / one-time URLs",
        )

        // ── Default managed extensions (comprehensive, future-proof) ──
        val DEFAULT_EXTENSIONS = linkedSetOf(
            // Archives
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst", "tgz", "cab", "lzh", "lz4",
            // Apps / Installers
            "apk", "xapk", "apks", "exe", "msi", "dmg", "deb", "rpm", "appimage", "ipa", "pkg",
            // Documents
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp",
            "rtf", "txt", "csv", "epub", "mobi", "djvu",
            // Images
            "jpg", "jpeg", "png", "gif", "bmp", "svg", "webp", "ico", "tiff", "tif",
            "heic", "heif", "avif", "jxl", "psd", "raw",
            // Audio
            "mp3", "flac", "aac", "ogg", "wav", "wma", "m4a", "opus", "aiff", "ape",
            // Video
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts",
            "mpg", "mpeg", "vob", "rmvb",
            // Fonts
            "ttf", "otf", "woff", "woff2",
            // Disk Images
            "iso", "img", "bin", "vmdk", "vhd",
            // Torrents
            "torrent",
            // Subtitles
            "srt", "sub", "ass", "vtt",
            // Data / Code
            "json", "xml", "sql", "db", "sqlite",
            // Other
            "jar", "war", "crx", "xpi", "swf"
        )
    }

    data class DmInfo(
        val key: String, val label: String,
        val pkg: String, val colorHex: String
    )

    private val DM_LIST = listOf(
        DmInfo("auto",     "Auto",    "",                                    "#8B949E"),
        DmInfo("idm_plus", "IDM+",   "idm.internet.download.manager.plus",  "#3FB950"),
        DmInfo("idm",      "IDM",    "idm.internet.download.manager",       "#58A6FF"),
        DmInfo("adm_pro",  "ADM Pro","com.dv.adm.pay",                      "#BC8CFF"),
        DmInfo("adm",      "ADM",    "com.dv.adm",                          "#D29922"),
        DmInfo("fdm",      "FDM",    "org.freedownloadmanager.fdm",         "#F85149"),
        DmInfo("one_dm",   "1DM+",   "nextapp.downloader",                  "#F0883E")
    )

    private lateinit var statusText:           TextView
    private lateinit var externalCard:         MaterialCardView
    private lateinit var browserCard:          MaterialCardView
    private lateinit var externalBadge:        TextView
    private lateinit var browserBadge:         TextView
    private lateinit var homeSection:          View
    private lateinit var historySection:       View
    private lateinit var historyListContainer: LinearLayout
    private lateinit var tabLayout:            TabLayout
    private lateinit var dmSection:            LinearLayout
    private lateinit var dmListContainer:      LinearLayout

    private var currentDm: String = "auto"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        bindViews()
        setupTabs()
        setupModeActions()
        initDefaultExtensions()

        if (!getPrefs().contains(KEY_MODE)) writeMode(MODE_EXTERNAL)
        currentDm = getPrefs().getString(KEY_DM, "auto") ?: "auto"

        showSection("home")
        updateUi()
    }

    private fun initDefaultExtensions() {
        val prefs = getPrefs()
        if (!prefs.contains(ModeProvider.MANAGED_EXT_KEY)) {
            prefs.edit()
                .putStringSet(ModeProvider.MANAGED_EXT_KEY, DEFAULT_EXTENSIONS.toMutableSet())
                .putStringSet(ModeProvider.SKIPPED_EXT_KEY, mutableSetOf())
                .apply()
        }
    }

    private fun bindViews() {
        statusText           = findViewById(R.id.statusText)
        externalCard         = findViewById(R.id.externalCard)
        browserCard          = findViewById(R.id.browserCard)
        externalBadge        = findViewById(R.id.externalBadge)
        browserBadge         = findViewById(R.id.browserBadge)
        homeSection          = findViewById(R.id.homeSection)
        historySection       = findViewById(R.id.historySection)
        historyListContainer = findViewById(R.id.historyListContainer)
        tabLayout            = findViewById(R.id.tabLayout)
        dmSection            = findViewById(R.id.dmSection)
        dmListContainer      = findViewById(R.id.dmListContainer)
    }

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_home))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_history))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showSection(if (tab.position == 0) "home" else "history")
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupModeActions() {
        externalCard.setOnClickListener {
            writeMode(MODE_EXTERNAL); updateUi()
            Toast.makeText(this, R.string.toast_external_active, Toast.LENGTH_SHORT).show()
        }
        browserCard.setOnClickListener {
            writeMode(MODE_BROWSER); updateUi()
            Toast.makeText(this, R.string.toast_browser_active, Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.historyClear).setOnClickListener {
            clearHistory(); refreshHistoryList()
            Toast.makeText(this, R.string.toast_history_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSection(which: String) {
        homeSection.visibility    = if (which == "home")    View.VISIBLE else View.GONE
        historySection.visibility = if (which == "history") View.VISIBLE else View.GONE
        if (which == "history") refreshHistoryList()
    }

    private fun updateUi() {
        val mode = readMode()
        statusText.text = getString(R.string.status_active_mode, mode.uppercase())

        val activeStroke = Color.parseColor("#3B82F6")
        val idleStroke   = Color.parseColor("#30363D")

        if (mode == MODE_EXTERNAL) {
            externalCard.strokeColor = activeStroke
            browserCard.strokeColor  = idleStroke
            externalBadge.text       = getString(R.string.badge_active)
            browserBadge.text        = ""
            dmSection.visibility     = View.VISIBLE
            buildDmCards()
            addQuickAddBar()
            addBlacklistButton()
            addSectionLabel("ext_header", "📋  FILE EXTENSIONS")
            addExtQuickAddBar()
            addExtensionButton()
        } else {
            externalCard.strokeColor = idleStroke
            browserCard.strokeColor  = activeStroke
            externalBadge.text       = ""
            browserBadge.text        = getString(R.string.badge_active)
            dmSection.visibility     = View.GONE
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  DM PICKER — Compact Material cards
    // ═══════════════════════════════════════════════════════════════

    private fun buildDmCards() {
        dmListContainer.removeAllViews()
        for (dm in DM_LIST) {
            val installed = dm.key == "auto" || isPackageInstalled(dm.pkg)
            dmListContainer.addView(createDmCard(dm, currentDm == dm.key, installed))
        }
    }

    private fun createDmCard(dm: DmInfo, isSelected: Boolean, isInstalled: Boolean): View {
        val dmColor      = Color.parseColor(dm.colorHex)
        val surfaceDark  = Color.parseColor("#0D1117")
        val surfaceHover = Color.parseColor("#161B22")
        val borderIdle   = Color.parseColor("#21262D")

        val card = MaterialCardView(this).apply {
            radius = dp(16).toFloat()
            cardElevation = if (isSelected) 2f else 0f
            setCardBackgroundColor(if (isSelected) surfaceHover else surfaceDark)
            strokeColor = if (isSelected) dmColor else borderIdle
            strokeWidth = if (isSelected) dp(2) else dp(1)
            isClickable = isInstalled
            isFocusable = isInstalled
            alpha = if (isInstalled) 1f else 0.38f
            val outValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            foreground = ContextCompat.getDrawable(context, outValue.resourceId)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
            if (isInstalled) setOnClickListener { onDmSelected(dm) }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(14), dp(16), dp(14))
        }

        // Accent pill
        row.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(3), dp(30)).apply {
                marginStart = dp(4); marginEnd = dp(14)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(3).toFloat()
                setColor(if (isSelected) dmColor else Color.TRANSPARENT)
            }
        })

        // Icon container
        val iconSize = dp(44)
        val iconContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = dp(14) }
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#111318"))
                setStroke(dp(1), if (isSelected) colorWithAlpha(dmColor, 90) else borderIdle)
            }
        }
        if (isInstalled && dm.key != "auto") {
            iconContainer.addView(ImageView(this).apply {
                val s = dp(32)
                layoutParams = FrameLayout.LayoutParams(s, s).apply { gravity = Gravity.CENTER }
                scaleType = ImageView.ScaleType.FIT_CENTER
                try { setImageDrawable(packageManager.getApplicationIcon(dm.pkg)) } catch (_: Exception) {}
            })
        } else {
            iconContainer.addView(TextView(this).apply {
                text = if (dm.key == "auto") "⚡" else dm.label.first().uppercase()
                textSize = if (dm.key == "auto") 18f else 16f
                setTextColor(if (isSelected) dmColor else Color.parseColor("#636E7B"))
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            })
        }
        row.addView(iconContainer)

        // Labels
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        labels.addView(TextView(this).apply {
            text = dm.label; textSize = 15f
            setTextColor(if (isInstalled) Color.WHITE else Color.parseColor("#636E7B"))
            setTypeface(null, Typeface.BOLD)
        })
        labels.addView(TextView(this).apply {
            textSize = 11f; setTextColor(Color.parseColor("#484F58"))
            setPadding(0, dp(2), 0, 0)
            text = when {
                !isInstalled     -> getString(R.string.dm_not_installed)
                dm.key == "auto" -> getString(R.string.dm_auto_sub)
                else             -> dm.pkg
            }
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(labels)

        // Radio dot
        val dotOuter = dp(20)
        row.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dotOuter, dotOuter).apply { marginStart = dp(8) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT)
                setStroke(dp(2), if (isSelected) dmColor else Color.parseColor("#30363D"))
            }
            if (isSelected) {
                addView(View(this@MainActivity).apply {
                    val d = dp(10)
                    layoutParams = FrameLayout.LayoutParams(d, d).apply { gravity = Gravity.CENTER }
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(dmColor) }
                })
            }
        })

        card.addView(row)
        return card
    }

    private fun onDmSelected(dm: DmInfo) {
        currentDm = dm.key
        getPrefs().edit().putString(KEY_DM, dm.key).commit()
        buildDmCards()
        Toast.makeText(this, getString(R.string.toast_dm_selected, dm.label), Toast.LENGTH_SHORT).show()
    }

    // ═══════════════════════════════════════════════════════════════
    //  DOMAIN QUICK-ADD BAR
    // ═══════════════════════════════════════════════════════════════

    private fun addQuickAddBar() {
        replaceByTag("quick_add_bar") {
            LinearLayout(this).apply {
                tag = "quick_add_bar"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(6), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(Color.parseColor("#0D1117"))
                    setStroke(dp(1), Color.parseColor("#21262D"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6); bottomMargin = dp(4) }

                addView(TextView(this@MainActivity).apply {
                    text = "🛡\uFE0F"; textSize = 14f; setPadding(0, 0, dp(10), 0)
                })

                val input = EditText(this@MainActivity).apply {
                    hint = "Paste failed URL or domain..."
                    textSize = 13f; setTextColor(Color.WHITE)
                    setHintTextColor(Color.parseColor("#484F58"))
                    inputType = InputType.TYPE_TEXT_VARIATION_URI
                    setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_DONE
                    background = null
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(0, dp(4), dp(8), dp(4))
                }
                addView(input)

                val doAdd = {
                    val text = input.text.toString().trim()
                    if (text.isNotBlank()) {
                        val cleaned = cleanDomain(text)
                        if (cleaned.isEmpty()) {
                            Toast.makeText(this@MainActivity, "Invalid domain or URL", Toast.LENGTH_SHORT).show()
                        } else if (STATIC_LOCKED_DISPLAY.any { it.first == cleaned }) {
                            Toast.makeText(this@MainActivity, "$cleaned is built-in", Toast.LENGTH_SHORT).show()
                        } else {
                            val prefs = getPrefs()
                            val domains = (prefs.getStringSet(ModeProvider.BLACKLIST_KEY, mutableSetOf())
                                ?: mutableSetOf()).toMutableSet()
                            if (domains.contains(cleaned)) {
                                Toast.makeText(this@MainActivity, "$cleaned already in list", Toast.LENGTH_SHORT).show()
                            } else {
                                domains.add(cleaned)
                                // Also remove from disabled if it was there
                                val disabled = (prefs.getStringSet(ModeProvider.BLACKLIST_DISABLED_KEY, mutableSetOf())
                                    ?: mutableSetOf()).toMutableSet()
                                disabled.remove(cleaned)
                                prefs.edit()
                                    .putStringSet(ModeProvider.BLACKLIST_KEY, domains)
                                    .putStringSet(ModeProvider.BLACKLIST_DISABLED_KEY, disabled)
                                    .commit()
                                Toast.makeText(this@MainActivity,
                                    "✅ $cleaned → future downloads stay in Chrome",
                                    Toast.LENGTH_LONG).show()
                                input.text.clear()
                                hideKeyboard(input)
                                addBlacklistButton()
                            }
                        }
                    }
                }

                addView(TextView(this@MainActivity).apply {
                    text = "ADD"; textSize = 12f
                    setTextColor(Color.parseColor("#58A6FF"))
                    setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        setColor(Color.parseColor("#161B22"))
                        setStroke(dp(1), Color.parseColor("#30363D"))
                    }
                    setOnClickListener { doAdd() }
                })

                input.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) { doAdd(); true } else false
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  BLACKLIST BUTTON + DIALOG (with toggleable checkboxes)
    // ═══════════════════════════════════════════════════════════════

    private fun addBlacklistButton() {
        replaceByTag("blacklist_link") {
            val prefs = getPrefs()
            val allDomains = prefs.getStringSet(ModeProvider.BLACKLIST_KEY, emptySet()) ?: emptySet()
            val disabled = prefs.getStringSet(ModeProvider.BLACKLIST_DISABLED_KEY, emptySet()) ?: emptySet()
            val activeCount = allDomains.size - disabled.intersect(allDomains).size
            val label = if (allDomains.isEmpty()) "" else " ($activeCount active / ${allDomains.size} total)"

            TextView(this).apply {
                tag = "blacklist_link"
                text = "⚙\uFE0F  Manage Browser-Only Sites$label"
                textSize = 13f; setTextColor(Color.parseColor("#58A6FF"))
                setPadding(dp(4), dp(12), 0, dp(8))
                setOnClickListener { showBlacklistDialog() }
            }
        }
    }

    private fun showBlacklistDialog() {
        val prefs = getPrefs()
        val userDomains = (prefs.getStringSet(ModeProvider.BLACKLIST_KEY, mutableSetOf())
            ?: mutableSetOf()).toMutableSet()
        val disabledDomains = (prefs.getStringSet(ModeProvider.BLACKLIST_DISABLED_KEY, mutableSetOf())
            ?: mutableSetOf()).toMutableSet()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(8))
        }

        root.addView(TextView(this).apply {
            text = "🛡\uFE0F Browser-Only Sites"; textSize = 18f
            setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "☑ = browser handles  ☐ = DM tries\nAdd sites that fail with download managers."
            textSize = 12f; setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, 0, 0, dp(16))
        })

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320))
        }
        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(listContainer)
        root.addView(scrollView)

        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
            .setView(root).create()

        fun rebuildList() {
            listContainer.removeAllViews()

            listContainer.addView(makeSectionHeader("Built-in (always active)"))
            for ((domain, reason) in STATIC_LOCKED_DISPLAY) {
                listContainer.addView(makeDomainRow(domain, reason,
                    isStatic = true, isActive = true, onToggle = null, onRemove = null))
            }

            listContainer.addView(makeSectionHeader("Your custom sites"))
            if (userDomains.isEmpty()) {
                listContainer.addView(TextView(this).apply {
                    text = "No custom sites added yet"; textSize = 13f
                    setTextColor(Color.parseColor("#484F58"))
                    setPadding(dp(4), dp(8), 0, dp(8))
                    setTypeface(null, Typeface.ITALIC)
                })
            } else {
                for (domain in userDomains.sorted()) {
                    val isActive = !disabledDomains.contains(domain)
                    listContainer.addView(makeDomainRow(domain, "Custom rule",
                        isStatic = false, isActive = isActive,
                        onToggle = { checked ->
                            if (checked) disabledDomains.remove(domain)
                            else disabledDomains.add(domain)
                            prefs.edit().putStringSet(ModeProvider.BLACKLIST_DISABLED_KEY, disabledDomains).commit()
                            rebuildList()
                        },
                        onRemove = {
                            userDomains.remove(domain)
                            disabledDomains.remove(domain)
                            prefs.edit()
                                .putStringSet(ModeProvider.BLACKLIST_KEY, userDomains)
                                .putStringSet(ModeProvider.BLACKLIST_DISABLED_KEY, disabledDomains)
                                .commit()
                            rebuildList()
                            Toast.makeText(this, "Removed: $domain", Toast.LENGTH_SHORT).show()
                        }
                    ))
                }
            }
        }

        root.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "+ Add Site"; setTextColor(Color.parseColor("#58A6FF"))
            strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#30363D"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            setOnClickListener {
                showAddDomainDialog { newDomain ->
                    val cleaned = cleanDomain(newDomain)
                    if (cleaned.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Invalid domain", Toast.LENGTH_SHORT).show()
                    } else if (STATIC_LOCKED_DISPLAY.any { it.first == cleaned }) {
                        Toast.makeText(this@MainActivity, "$cleaned is built-in", Toast.LENGTH_SHORT).show()
                    } else if (userDomains.contains(cleaned)) {
                        Toast.makeText(this@MainActivity, "$cleaned already added", Toast.LENGTH_SHORT).show()
                    } else {
                        userDomains.add(cleaned)
                        disabledDomains.remove(cleaned)
                        prefs.edit()
                            .putStringSet(ModeProvider.BLACKLIST_KEY, userDomains)
                            .putStringSet(ModeProvider.BLACKLIST_DISABLED_KEY, disabledDomains)
                            .commit()
                        rebuildList()
                        Toast.makeText(this@MainActivity, "Added: $cleaned", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        root.addView(MaterialButton(this).apply {
            text = "Done"; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#238636")); cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(8) }
            setOnClickListener { dialog.dismiss(); updateUi() }
        })

        rebuildList()
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(Color.parseColor("#0D1117"))
            setStroke(dp(1), Color.parseColor("#30363D"))
        })
        dialog.show()
    }

    private fun makeDomainRow(
        domain: String, subtitle: String,
        isStatic: Boolean, isActive: Boolean,
        onToggle: ((Boolean) -> Unit)?, onRemove: (() -> Unit)?
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(10), dp(8), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor(if (isActive) "#161B22" else "#0D1117"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }

        row.addView(CheckBox(this).apply {
            isChecked = isActive; isEnabled = !isStatic
            setPadding(0, 0, dp(2), 0)
            if (onToggle != null) {
                setOnCheckedChangeListener { _, checked -> onToggle(checked) }
            }
        })

        row.addView(TextView(this).apply {
            text = "🌐"; textSize = 14f; setPadding(0, 0, dp(10), 0)
        })

        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        labels.addView(TextView(this).apply {
            text = domain; textSize = 14f
            setTextColor(if (isActive) Color.WHITE else Color.parseColor("#636E7B"))
        })
        labels.addView(TextView(this).apply {
            text = subtitle; textSize = 10f
            setTextColor(Color.parseColor("#636E7B"))
        })
        row.addView(labels)

        if (!isStatic && onRemove != null) {
            row.addView(TextView(this).apply {
                text = "✕"; textSize = 16f
                setTextColor(Color.parseColor("#F85149"))
                setPadding(dp(12), dp(4), dp(4), dp(4))
                setOnClickListener { onRemove() }
            })
        } else {
            row.addView(TextView(this).apply {
                text = "🔒"; textSize = 12f; setPadding(dp(8), 0, dp(4), 0)
            })
        }
        return row
    }

    // ═══════════════════════════════════════════════════════════════
    //  EXTENSION QUICK-ADD BAR
    // ═══════════════════════════════════════════════════════════════

    private fun addExtQuickAddBar() {
        replaceByTag("ext_quick_add") {
            LinearLayout(this).apply {
                tag = "ext_quick_add"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(6), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(Color.parseColor("#0D1117"))
                    setStroke(dp(1), Color.parseColor("#21262D"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4); bottomMargin = dp(4) }

                addView(TextView(this@MainActivity).apply {
                    text = "📦"; textSize = 14f; setPadding(0, 0, dp(10), 0)
                })

                val input = EditText(this@MainActivity).apply {
                    hint = "Add extension (e.g. .xyz)..."
                    textSize = 13f; setTextColor(Color.WHITE)
                    setHintTextColor(Color.parseColor("#484F58"))
                    inputType = InputType.TYPE_TEXT_VARIATION_URI
                    setSingleLine(true); imeOptions = EditorInfo.IME_ACTION_DONE
                    background = null
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(0, dp(4), dp(8), dp(4))
                }
                addView(input)

                val doAdd = {
                    val raw = input.text.toString().trim().lowercase()
                        .removePrefix(".").replace(Regex("[^a-z0-9]"), "")
                    if (raw.isNotBlank()) {
                        val prefs = getPrefs()
                        val managed = (prefs.getStringSet(ModeProvider.MANAGED_EXT_KEY, mutableSetOf())
                            ?: mutableSetOf()).toMutableSet()
                        if (managed.contains(raw)) {
                            Toast.makeText(this@MainActivity, ".$raw already in list", Toast.LENGTH_SHORT).show()
                        } else {
                            managed.add(raw)
                            prefs.edit().putStringSet(ModeProvider.MANAGED_EXT_KEY, managed).commit()
                            Toast.makeText(this@MainActivity, "✅ .$raw added (captured by DM)",
                                Toast.LENGTH_SHORT).show()
                            input.text.clear()
                            hideKeyboard(input)
                            addExtensionButton()
                        }
                    }
                }

                addView(TextView(this@MainActivity).apply {
                    text = "+ ADD"; textSize = 12f
                    setTextColor(Color.parseColor("#3FB950"))
                    setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        setColor(Color.parseColor("#161B22"))
                        setStroke(dp(1), Color.parseColor("#30363D"))
                    }
                    setOnClickListener { doAdd() }
                })

                input.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) { doAdd(); true } else false
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  EXTENSION BUTTON + DIALOG (toggleable chip grid)
    // ═══════════════════════════════════════════════════════════════

    private fun addExtensionButton() {
        replaceByTag("ext_link") {
            val prefs = getPrefs()
            val managed = prefs.getStringSet(ModeProvider.MANAGED_EXT_KEY, emptySet()) ?: emptySet()
            val skipped = prefs.getStringSet(ModeProvider.SKIPPED_EXT_KEY, emptySet()) ?: emptySet()
            val activeCount = managed.size - skipped.intersect(managed).size
            val skipCount = skipped.intersect(managed).size
            val label = if (managed.isEmpty()) ""
            else " ($activeCount captured" + (if (skipCount > 0) ", $skipCount skipped)" else ")")

            TextView(this).apply {
                tag = "ext_link"
                text = "📋  Manage Extensions$label"
                textSize = 13f; setTextColor(Color.parseColor("#3FB950"))
                setPadding(dp(4), dp(8), 0, dp(12))
                setOnClickListener { showExtensionDialog() }
            }
        }
    }

    private fun showExtensionDialog() {
        val prefs = getPrefs()
        val managed = (prefs.getStringSet(ModeProvider.MANAGED_EXT_KEY, mutableSetOf())
            ?: mutableSetOf()).toMutableSet()
        val skipped = (prefs.getStringSet(ModeProvider.SKIPPED_EXT_KEY, mutableSetOf())
            ?: mutableSetOf()).toMutableSet()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(8))
        }

        root.addView(TextView(this).apply {
            text = "📋 File Extensions"; textSize = 18f
            setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Green = captured by DM · Gray = stays in browser\nTap to toggle · Add custom extensions below"
            textSize = 11f; setTextColor(Color.parseColor("#8B949E"))
            setPadding(0, 0, 0, dp(12))
        })

        // Search bar
        val searchInput = EditText(this).apply {
            hint = "🔍 Filter extensions..."
            textSize = 13f; setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#484F58"))
            setSingleLine(true); background = null
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(LinearLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#161B22"))
                setStroke(dp(1), Color.parseColor("#21262D"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            addView(searchInput)
        })

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(340))
        }
        val gridContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(gridContainer)
        root.addView(scrollView)

        val dialog = AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
            .setView(root).create()

        fun rebuildGrid(filter: String = "") {
            gridContainer.removeAllViews()
            val filtered = managed.sorted().filter {
                filter.isEmpty() || it.contains(filter.lowercase().removePrefix("."))
            }
            val grouped = filtered.groupBy { categorizeExtension(it) }
            val catOrder = listOf("📦 Archives", "📱 Apps", "📄 Documents", "🖼️ Images",
                "🎵 Audio", "🎬 Video", "🔤 Fonts", "💿 Disk Images",
                "🧲 Torrents", "💬 Subtitles", "📎 Other")

            for (cat in catOrder) {
                val exts = grouped[cat] ?: continue
                gridContainer.addView(makeSectionHeader(cat))

                var chipRow: LinearLayout? = null
                for ((i, ext) in exts.withIndex()) {
                    if (i % 4 == 0) {
                        chipRow = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT)
                        }
                        gridContainer.addView(chipRow)
                    }
                    val isActive = !skipped.contains(ext)
                    chipRow?.addView(createExtChip(ext, isActive) { nowChecked ->
                        if (nowChecked) skipped.remove(ext) else skipped.add(ext)
                        prefs.edit().putStringSet(ModeProvider.SKIPPED_EXT_KEY, skipped).commit()
                        rebuildGrid(searchInput.text.toString())
                    })
                }
                // Fill remaining cells in last row
                if (chipRow != null && exts.size % 4 != 0) {
                    val remaining = 4 - (exts.size % 4)
                    for (j in 0 until remaining) {
                        chipRow.addView(View(this).apply {
                            layoutParams = LinearLayout.LayoutParams(0, dp(1), 1f)
                        })
                    }
                }
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { rebuildGrid(s?.toString() ?: "") }
        })

        // Add extension button
        root.addView(MaterialButton(this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "+ Add Extension"; setTextColor(Color.parseColor("#3FB950"))
            strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#30363D"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            setOnClickListener {
                showAddExtDialog { ext ->
                    val cleaned = ext.lowercase().removePrefix(".").replace(Regex("[^a-z0-9]"), "")
                    if (cleaned.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Invalid extension", Toast.LENGTH_SHORT).show()
                    } else if (managed.contains(cleaned)) {
                        Toast.makeText(this@MainActivity, ".$cleaned already exists", Toast.LENGTH_SHORT).show()
                    } else {
                        managed.add(cleaned)
                        prefs.edit().putStringSet(ModeProvider.MANAGED_EXT_KEY, managed).commit()
                        rebuildGrid(searchInput.text.toString())
                        Toast.makeText(this@MainActivity, "Added: .$cleaned", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        root.addView(MaterialButton(this).apply {
            text = "Done"; setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#238636")); cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8); bottomMargin = dp(8) }
            setOnClickListener { dialog.dismiss(); updateUi() }
        })

        rebuildGrid()
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(Color.parseColor("#0D1117"))
            setStroke(dp(1), Color.parseColor("#30363D"))
        })
        dialog.show()
    }

    private fun createExtChip(ext: String, isChecked: Boolean, onToggle: (Boolean) -> Unit): View {
        val green     = Color.parseColor("#3FB950")
        val grayText  = Color.parseColor("#484F58")
        val greenBg   = Color.parseColor("#0D2818")
        val grayBg    = Color.parseColor("#161B22")
        val greenBdr  = Color.parseColor("#238636")
        val grayBdr   = Color.parseColor("#21262D")

        return TextView(this).apply {
            text = ".$ext"
            textSize = 11f
            setTextColor(if (isChecked) green else grayText)
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(7), dp(4), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(if (isChecked) greenBg else grayBg)
                setStroke(dp(1), if (isChecked) greenBdr else grayBdr)
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            setOnClickListener { onToggle(!isChecked) }
        }
    }

    private fun categorizeExtension(ext: String): String {
        return when (ext.lowercase()) {
            in setOf("zip","rar","7z","tar","gz","bz2","xz","zst","tgz","cab","lzh","lz4") -> "📦 Archives"
            in setOf("apk","xapk","apks","exe","msi","dmg","deb","rpm","appimage","ipa","pkg") -> "📱 Apps"
            in setOf("pdf","doc","docx","xls","xlsx","ppt","pptx","odt","ods","odp",
                "rtf","txt","csv","epub","mobi","djvu") -> "📄 Documents"
            in setOf("jpg","jpeg","png","gif","bmp","svg","webp","ico","tiff","tif",
                "heic","heif","avif","jxl","psd","raw") -> "🖼️ Images"
            in setOf("mp3","flac","aac","ogg","wav","wma","m4a","opus","aiff","ape") -> "🎵 Audio"
            in setOf("mp4","mkv","avi","mov","wmv","flv","webm","m4v","3gp","ts",
                "mpg","mpeg","vob","rmvb") -> "🎬 Video"
            in setOf("ttf","otf","woff","woff2") -> "🔤 Fonts"
            in setOf("iso","img","bin","vmdk","vhd") -> "💿 Disk Images"
            in setOf("torrent") -> "🧲 Torrents"
            in setOf("srt","sub","ass","vtt") -> "💬 Subtitles"
            else -> "📎 Other"
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  INPUT DIALOGS
    // ═══════════════════════════════════════════════════════════════

    private fun showAddDomainDialog(onAdd: (String) -> Unit) {
        val inputContainer = FrameLayout(this).apply { setPadding(dp(24), dp(16), dp(24), 0) }
        val input = EditText(this).apply {
            hint = "example.com"; textSize = 15f; setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#484F58"))
            inputType = InputType.TYPE_TEXT_VARIATION_URI; setSingleLine(true)
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#161B22"))
                setStroke(dp(1), Color.parseColor("#30363D"))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        inputContainer.addView(input)
        AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
            .setTitle("Add Browser-Only Site")
            .setMessage("Downloads from this domain will stay in Chrome.")
            .setView(inputContainer)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) onAdd(text)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddExtDialog(onAdd: (String) -> Unit) {
        val inputContainer = FrameLayout(this).apply { setPadding(dp(24), dp(16), dp(24), 0) }
        val input = EditText(this).apply {
            hint = ".xyz"; textSize = 15f; setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#484F58"))
            inputType = InputType.TYPE_TEXT_VARIATION_URI; setSingleLine(true)
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#161B22"))
                setStroke(dp(1), Color.parseColor("#30363D"))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        inputContainer.addView(input)
        AlertDialog.Builder(this, androidx.appcompat.R.style.Theme_AppCompat_Dialog)
            .setTitle("Add Extension")
            .setMessage("This extension will be captured and forwarded to your download manager.")
            .setView(inputContainer)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) onAdd(text)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ═══════════════════════════════════════════════════════════════
    //  SHARED UI HELPERS
    // ═══════════════════════════════════════════════════════════════

    private fun makeSectionHeader(title: String): View {
        return TextView(this).apply {
            text = title.uppercase(); textSize = 10f
            setTextColor(Color.parseColor("#636E7B"))
            setTypeface(null, Typeface.BOLD); letterSpacing = 0.08f
            setPadding(dp(4), dp(14), 0, dp(6))
        }
    }

    private fun addSectionLabel(tag: String, label: String) {
        replaceByTag(tag) {
            TextView(this).apply {
                this.tag = tag
                text = label; textSize = 11f
                setTextColor(Color.parseColor("#636E7B"))
                setTypeface(null, Typeface.BOLD); letterSpacing = 0.06f
                setPadding(dp(4), dp(18), 0, dp(4))
            }
        }
    }

    /** Replace a tagged view in dmSection, or add new one */
    private fun replaceByTag(viewTag: String, builder: () -> View) {
        val existing = dmSection.findViewWithTag<View>(viewTag)
        existing?.let { dmSection.removeView(it) }
        dmSection.addView(builder())
    }

    private fun cleanDomain(raw: String): String {
        var d = raw.trim().lowercase()
        d = d.removePrefix("https://").removePrefix("http://")
        val slashIdx = d.indexOf('/')
        if (slashIdx > 0) d = d.substring(0, slashIdx)
        val colonIdx = d.lastIndexOf(':')
        if (colonIdx > 0) d = d.substring(0, colonIdx)
        d = d.removePrefix("www.").removePrefix("*.")
        val parts = d.split(".")
        if (parts.size > 2) {
            val twoPartTlds = setOf("co.uk","co.in","co.jp","com.au","com.br",
                "co.za","org.uk","net.au","ac.uk","gov.uk")
            val lastTwo = parts.takeLast(2).joinToString(".")
            d = if (twoPartTlds.contains(lastTwo)) parts.takeLast(3).joinToString(".")
            else lastTwo
        }
        return if (d.contains('.') && !d.contains(' ')) d else ""
    }

    // ═══════════════════════════════════════════════════════════════
    //  HISTORY
    // ═══════════════════════════════════════════════════════════════

    private fun refreshHistoryList() {
        historyListContainer.removeAllViews()
        val lines = readHistoryLines()
        if (lines.isEmpty()) {
            historyListContainer.addView(TextView(this).apply {
                text = getString(R.string.history_empty); textSize = 14f
                setTextColor(Color.parseColor("#8B949E"))
                setPadding(0, 32, 0, 0); gravity = Gravity.CENTER
            })
            return
        }
        for (line in lines) addHistoryCard(parseHistoryLine(line))
    }

    private fun addHistoryCard(data: Map<String, String>) {
        val typeColor = when {
            data["filename"]?.lowercase()?.endsWith(".apk") == true -> "#3FB950"
            data["mime"]?.contains("video") == true                  -> "#58A6FF"
            data["mime"]?.contains("audio") == true                  -> "#BC8CFF"
            data["mime"]?.contains("image") == true                  -> "#D29922"
            else                                                      -> "#3B82F6"
        }
        val dmColor = DM_LIST.find { it.key == data["dm"] }?.colorHex ?: "#8B949E"
        val dmValue = data["dm"] ?: ""
        val isBlocked = dmValue.startsWith("Blocked") || dmValue == "Cancelled" || dmValue == "No DM"
        val cardBorderColor = if (isBlocked) "#F85149" else "#30363D"

        val card = MaterialCardView(this).apply {
            radius = 20f; cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#161B22"))
            strokeColor = Color.parseColor(cardBorderColor); strokeWidth = 1
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, 14) }
            setOnClickListener { showHistoryDetails(data) }
        }

        val inner = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        inner.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(6, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor(if (isBlocked) "#F85149" else typeColor))
        })

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(TextView(this).apply {
            text = data["filename"].takeUnless { it.isNullOrBlank() }
                ?: getString(R.string.history_item_unknown)
            textSize = 15f; setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD); maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        val dmLabel = if (isBlocked) dmValue
        else DM_LIST.find { it.key == data["dm"] }?.label ?: data["dm"]?.takeUnless { it.isNullOrBlank() }

        if (!dmLabel.isNullOrBlank() && dmLabel != "pending") {
            val badgeColor = if (isBlocked) "#F85149" else dmColor
            topRow.addView(TextView(this).apply {
                text = dmLabel; textSize = 10f; setTextColor(Color.WHITE)
                gravity = Gravity.CENTER; setPadding(10, 3, 10, 3)
                background = GradientDrawable().apply {
                    cornerRadius = 20f; setColor(Color.parseColor(badgeColor))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = 8 }
            })
        }

        content.addView(topRow)
        content.addView(TextView(this).apply {
            text = data["time"].orEmpty(); textSize = 12f
            setTextColor(Color.parseColor("#8B949E")); setPadding(0, 4, 0, 0)
        })

        inner.addView(content); card.addView(inner)
        historyListContainer.addView(card)
    }

    private fun showHistoryDetails(data: Map<String, String>) {
        val view      = layoutInflater.inflate(R.layout.dialog_info, null)
        val titleView = view.findViewById<TextView>(R.id.dialogTitle)
        val msgView   = view.findViewById<TextView>(R.id.dialogMessage)
        val okButton  = view.findViewById<MaterialButton>(R.id.dialogOk)

        titleView.text = data["filename"].takeUnless { it.isNullOrBlank() }
            ?: getString(R.string.history_item_title)
        val dmLabel = DM_LIST.find { it.key == data["dm"] }?.label ?: data["dm"].orEmpty()
        msgView.text = android.text.Html.fromHtml(
            getString(R.string.history_details_format,
                data["time"].orEmpty(), data["filename"].orEmpty(),
                data["mime"].orEmpty(), dmLabel,
                data["url"].orEmpty(), data["referer"].orEmpty()),
            android.text.Html.FROM_HTML_MODE_LEGACY)

        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        okButton.setOnClickListener { dialog.dismiss() }
        msgView.setOnLongClickListener {
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newPlainText("URL", data["url"]))
            Toast.makeText(this, "URL copied", Toast.LENGTH_SHORT).show(); true
        }
        dialog.show()
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════

    private fun dp(dp: Int): Int = (dp * resources.displayMetrics.density + 0.5f).toInt()
    private fun colorWithAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    private fun hideKeyboard(v: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(v.windowToken, 0)
    }
    private fun getPrefs() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    private fun writeMode(mode: String) = getPrefs().edit().putString(KEY_MODE, mode).commit()
    private fun readMode(): String {
        val m = getPrefs().getString(KEY_MODE, MODE_EXTERNAL) ?: MODE_EXTERNAL
        return if (m == MODE_BROWSER) MODE_BROWSER else MODE_EXTERNAL
    }
    private fun isPackageInstalled(pkg: String): Boolean =
        try { packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
    private fun parseHistoryLine(line: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        line.split("|").forEach { val idx = it.indexOf("=")
            if (idx > 0) map[dec(it.substring(0, idx))] = dec(it.substring(idx + 1)) }
        return map
    }
    private fun dec(value: String): String = value
        .replace("%3D", "=").replace("%7C", "|").replace("%0A", "\n")
        .replace("%0D", "\r").replace("%09", "\t").replace("%25", "%")
    private fun readHistoryLines(): List<String> = try {
        val file = File(filesDir, "history.txt")
        if (!file.exists()) emptyList() else file.readLines().filter { it.isNotBlank() }.reversed()
    } catch (_: Exception) { emptyList() }
    private fun clearHistory() {
        try { File(filesDir, "history.txt").writeText("") } catch (_: Exception) {}
    }
}
