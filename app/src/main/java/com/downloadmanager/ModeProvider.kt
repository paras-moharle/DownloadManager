package com.downloadmanager

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * ContentProvider that exposes all app settings to the Xposed Hook
 * running inside Chrome's process (cross-process, single query).
 *
 * Paths:
 *   /config     → consolidated: mode + active blacklist + skipped extensions
 *   /settings   → mode only (legacy compat)
 *   /blacklist  → active blacklist only (legacy compat)
 *
 * The Hook uses /config for a single efficient query per download.
 */
class ModeProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.downloadmanager.mode"

        // SharedPreferences keys
        const val BLACKLIST_KEY          = "browser_only_domains"
        const val BLACKLIST_DISABLED_KEY = "browser_only_domains_disabled"
        const val MANAGED_EXT_KEY        = "managed_extensions"
        const val SKIPPED_EXT_KEY        = "skipped_extensions"
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri, projection: Array<out String>?,
        selection: String?, selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val prefs = context?.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            ?: return null

        return when (uri.lastPathSegment) {

            // ── Consolidated config (v5 — single query, lower RAM) ──
            "config" -> {
                val cursor = MatrixCursor(arrayOf("mode", "blacklist", "skipped_ext"))

                val mode = prefs.getString(MainActivity.KEY_MODE, MainActivity.MODE_EXTERNAL)
                    ?: MainActivity.MODE_EXTERNAL

                // Active blacklist = all domains minus disabled ones
                val allDomains = prefs.getStringSet(BLACKLIST_KEY, emptySet()) ?: emptySet()
                val disabledDomains = prefs.getStringSet(BLACKLIST_DISABLED_KEY, emptySet()) ?: emptySet()
                val activeDomains = allDomains - disabledDomains

                // Skipped extensions (unchecked by user)
                val skippedExt = prefs.getStringSet(SKIPPED_EXT_KEY, emptySet()) ?: emptySet()

                cursor.addRow(arrayOf(
                    mode,
                    activeDomains.joinToString(","),
                    skippedExt.joinToString(",")
                ))
                cursor
            }

            // ── Legacy: mode only ──
            "settings" -> {
                val cursor = MatrixCursor(arrayOf("mode"))
                val mode = prefs.getString(MainActivity.KEY_MODE, MainActivity.MODE_EXTERNAL)
                    ?: MainActivity.MODE_EXTERNAL
                cursor.addRow(arrayOf(mode))
                cursor
            }

            // ── Legacy: blacklist only ──
            "blacklist" -> {
                val cursor = MatrixCursor(arrayOf("domains"))
                val allDomains = prefs.getStringSet(BLACKLIST_KEY, emptySet()) ?: emptySet()
                val disabledDomains = prefs.getStringSet(BLACKLIST_DISABLED_KEY, emptySet()) ?: emptySet()
                val activeDomains = allDomains - disabledDomains
                cursor.addRow(arrayOf(activeDomains.joinToString(",")))
                cursor
            }

            else -> null
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
