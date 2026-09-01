package me.erista.hshop.thor.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("hshop_thor_settings", Context.MODE_PRIVATE)

    private val defaultDownloadPath = File(Environment.getExternalStorageDirectory(), "ROMs/3DS").absolutePath

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val path = prefs.getString("download_path", defaultDownloadPath) ?: defaultDownloadPath
        val themeName = prefs.getString("app_theme", AppTheme.THOR_AMOLED.name) ?: AppTheme.THOR_AMOLED.name
        val theme = try {
            AppTheme.valueOf(themeName)
        } catch (e: Exception) {
            AppTheme.THOR_AMOLED
        }
        val wifiOnly = prefs.getBoolean("wifi_only", false)
        val autoCreate = prefs.getBoolean("auto_create", true)

        val autoRemoveCia = prefs.getBoolean("auto_remove_cia", false)
        val autoConvert3ds = prefs.getBoolean("auto_convert_3ds", true)
        val regions = prefs.getStringSet("allowed_regions", emptySet()) ?: emptySet()

        return AppSettings(
            downloadPath = path,
            theme = theme,
            downloadOverWifiOnly = wifiOnly,
            autoCreateFolders = autoCreate,
            autoRemoveDownloadedCia = autoRemoveCia,
            autoConvertTo3ds = autoConvert3ds,
            allowedRegions = regions
        )
    }

    fun setDownloadPath(path: String) {
        val cleanPath = path.trim()
        prefs.edit().putString("download_path", cleanPath).apply()
        _settings.value = _settings.value.copy(downloadPath = cleanPath)
    }

    fun setTheme(theme: AppTheme) {
        prefs.edit().putString("app_theme", theme.name).apply()
        _settings.value = _settings.value.copy(theme = theme)
    }

    fun setDownloadOverWifiOnly(wifiOnly: Boolean) {
        prefs.edit().putBoolean("wifi_only", wifiOnly).apply()
        _settings.value = _settings.value.copy(downloadOverWifiOnly = wifiOnly)
    }

    fun setAutoCreateFolders(autoCreate: Boolean) {
        prefs.edit().putBoolean("auto_create", autoCreate).apply()
        _settings.value = _settings.value.copy(autoCreateFolders = autoCreate)
    }

    fun setAutoRemoveDownloadedCia(autoRemove: Boolean) {
        prefs.edit().putBoolean("auto_remove_cia", autoRemove).apply()
        _settings.value = _settings.value.copy(autoRemoveDownloadedCia = autoRemove)
    }

    fun setAutoConvertTo3ds(autoConvert: Boolean) {
        prefs.edit().putBoolean("auto_convert_3ds", autoConvert).apply()
        _settings.value = _settings.value.copy(autoConvertTo3ds = autoConvert)
    }

    fun setAllowedRegions(regions: Set<String>) {
        prefs.edit().putStringSet("allowed_regions", regions).apply()
        _settings.value = _settings.value.copy(allowedRegions = regions)
    }

    fun toggleAllowedRegion(regionSlug: String) {
        val current = _settings.value.allowedRegions.toMutableSet()
        if (current.contains(regionSlug)) {
            current.remove(regionSlug)
        } else {
            current.add(regionSlug)
        }
        setAllowedRegions(current)
    }
}
