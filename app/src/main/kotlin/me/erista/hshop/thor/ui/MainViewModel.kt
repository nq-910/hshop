package me.erista.hshop.thor.ui

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.erista.hshop.model.*
import me.erista.hshop.scraper.HShopScraper
import me.erista.hshop.thor.data.AppSettings
import me.erista.hshop.thor.data.AppTheme
import me.erista.hshop.thor.data.DownloadStatus
import me.erista.hshop.thor.data.DownloadTask
import me.erista.hshop.thor.data.LocalFileType
import me.erista.hshop.thor.data.LocalRomItem
import me.erista.hshop.thor.data.SettingsRepository
import me.erista.hshop.thor.download.AutoDownloadResolver
import me.erista.hshop.thor.download.ThorDownloadManager
import coil.imageLoader
import coil.request.ImageRequest
import java.io.File

enum class BottomTab {
    BROWSE,
    LIBRARY,
    DOWNLOADS,
    SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val scraper = HShopScraper()
    private val settingsRepo = SettingsRepository(application)
    private val downloadManager = ThorDownloadManager(application)

    val settings: StateFlow<AppSettings> = settingsRepo.settings
    val downloadTasks: StateFlow<List<DownloadTask>> = downloadManager.tasks

    private val _localRoms = MutableStateFlow<List<LocalRomItem>>(emptyList())
    val localRoms: StateFlow<List<LocalRomItem>> = _localRoms.asStateFlow()

    private val _selectedLocalRom = MutableStateFlow<LocalRomItem?>(null)
    val selectedLocalRom: StateFlow<LocalRomItem?> = _selectedLocalRom.asStateFlow()

    private val _selectedLocalFilter = MutableStateFlow("ALL")
    val selectedLocalFilter: StateFlow<String> = _selectedLocalFilter.asStateFlow()

    private val _selectedDownloadTaskId = MutableStateFlow<String?>(null)
    val selectedDownloadTaskId: StateFlow<String?> = _selectedDownloadTaskId.asStateFlow()

    private val _settingsScrollEvent = MutableSharedFlow<Float>(extraBufferCapacity = 5)
    val settingsScrollEvent: SharedFlow<Float> = _settingsScrollEvent.asSharedFlow()

    private val _launchLocalRomEvent = MutableSharedFlow<LocalRomItem>(extraBufferCapacity = 1)
    val launchLocalRomEvent: SharedFlow<LocalRomItem> = _launchLocalRomEvent.asSharedFlow()

    private val _isScanningLocalRoms = MutableStateFlow(false)
    val isScanningLocalRoms: StateFlow<Boolean> = _isScanningLocalRoms.asStateFlow()

    private val _selectedTab = MutableStateFlow(BottomTab.BROWSE)
    val selectedTab: StateFlow<BottomTab> = _selectedTab.asStateFlow()

    // Focus state: true = Nav keys navigate Bottom Navigation tabs; false = Nav keys navigate screen content
    private val _isBottomBarFocused = MutableStateFlow(true)
    val isBottomBarFocused: StateFlow<Boolean> = _isBottomBarFocused.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow(HShopCategory.GAMES)
    val selectedCategory: StateFlow<HShopCategory> = _selectedCategory.asStateFlow()

    private val _selectedSubcategory = MutableStateFlow<String?>(null)
    val selectedSubcategory: StateFlow<String?> = _selectedSubcategory.asStateFlow()

    private val _subcategories = MutableStateFlow<List<HShopSubcategory>>(emptyList())
    val subcategories: StateFlow<List<HShopSubcategory>> = _subcategories.asStateFlow()

    private val appUpdater = me.erista.hshop.thor.updater.AppUpdater(application)

    private val _availableUpdate = MutableStateFlow<me.erista.hshop.thor.updater.AppUpdateInfo?>(null)
    val availableUpdate: StateFlow<me.erista.hshop.thor.updater.AppUpdateInfo?> = _availableUpdate.asStateFlow()

    private val _titles = MutableStateFlow<List<HShopTitleSummary>>(emptyList())
    val titles: StateFlow<List<HShopTitleSummary>> = _titles.asStateFlow()

    private val _selectedTitleDetail = MutableStateFlow<HShopTitleDetail?>(null)
    val selectedTitleDetail: StateFlow<HShopTitleDetail?> = _selectedTitleDetail.asStateFlow()

    private val _turnstileTarget = MutableStateFlow<HShopTitleDetail?>(null)
    val turnstileTarget: StateFlow<HShopTitleDetail?> = _turnstileTarget.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _canLoadMore = MutableStateFlow(true)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()

    private val _updateCheckStatus = MutableStateFlow<String?>(null)
    val updateCheckStatus: StateFlow<String?> = _updateCheckStatus.asStateFlow()

    // Non-null when a task just ran out of storage; holds the title name for the dialog.
    private val _outOfStorageTitleName = MutableStateFlow<String?>(null)
    val outOfStorageTitleName: StateFlow<String?> = _outOfStorageTitleName.asStateFlow()

    init {
        loadCategory(HShopCategory.GAMES)
        checkForAppUpdates(silent = true)
        refreshLocalRoms()
        // Watch for out-of-storage failures and surface them to the UI.
        viewModelScope.launch {
            downloadTasks.collect { tasks ->
                tasks.firstOrNull { it.status == DownloadStatus.OUT_OF_STORAGE }
                    ?.let { task ->
                        if (_outOfStorageTitleName.value == null) {
                            _outOfStorageTitleName.value = task.titleName
                        }
                    }
            }
        }
    }

    fun checkForAppUpdates(silent: Boolean = false) {
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            if (!silent) _updateCheckStatus.value = "Checking GitHub for updates..."
            val update = appUpdater.checkForUpdates()
            _isCheckingUpdates.value = false
            if (update != null && update.hasUpdate) {
                _availableUpdate.value = update
                _updateCheckStatus.value = "New update available: v${update.latestVersion}"
            } else if (update != null) {
                _updateCheckStatus.value = "You are running the latest version (v${update.currentVersion})."
            } else if (!silent) {
                _updateCheckStatus.value = "Could not check for updates. Check your connection."
            }
        }
    }

    fun dismissUpdateDialog() {
        _availableUpdate.value = null
    }

    fun dismissOutOfStorageDialog() {
        // Also reset the task status back to FAILED so it doesn't re-trigger.
        val titleName = _outOfStorageTitleName.value
        _outOfStorageTitleName.value = null
        if (titleName != null) {
            // Clear the OUT_OF_STORAGE status from the task so it can be retried or removed.
            val tasks = downloadTasks.value.toMutableList()
            val idx = tasks.indexOfFirst {
                it.titleName == titleName && it.status == DownloadStatus.OUT_OF_STORAGE
            }
            if (idx >= 0) {
                tasks[idx] = tasks[idx].copy(status = DownloadStatus.FAILED)
                downloadManager.updateOutOfStorageTask(tasks[idx].id)
            }
        }
    }

    fun refreshLocalRoms() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanningLocalRoms.value = true
            val currentSettings = settings.value
            val candidateDirs = mutableListOf<File>()

            // 1. Primary ROMs Download Path
            val primaryDir = File(currentSettings.downloadPath)
            if (primaryDir.exists() && primaryDir.isDirectory) candidateDirs.add(primaryDir)

            // 2. Updates & DLC Path
            val updateDir = File(currentSettings.updateDlcPath)
            if (updateDir.exists() && updateDir.isDirectory) candidateDirs.add(updateDir)

            // 3. Only check fallback paths if user has no directories configured or existing
            if (candidateDirs.isEmpty()) {
                val fallbacks = listOf(
                    File(Environment.getExternalStorageDirectory(), "ROMs/n3ds"),
                    File(Environment.getExternalStorageDirectory(), "Roms/n3ds"),
                    File(Environment.getExternalStorageDirectory(), "ROMs/3DS"),
                    File(Environment.getExternalStorageDirectory(), "Roms/3DS")
                )
                for (fb in fallbacks) {
                    if (fb.exists() && fb.isDirectory) candidateDirs.add(fb)
                }
            }

            // 1. Resolve canonical directories and deduplicate case-insensitively (fixes Android FUSE /sdcard vs /storage/emulated/0 and ROMs vs Roms)
            val canonicalDirs = candidateDirs
                .map { try { it.canonicalFile } catch (_: Exception) { it.absoluteFile } }
                .filter { it.exists() && it.isDirectory }
                .distinctBy { it.canonicalPath.lowercase() }

            // 2. Prune nested subdirectories (e.g. Updates_DLC inside ROMs/n3ds) to prevent walking the same tree twice
            val scanDirectories = canonicalDirs.filter { child ->
                canonicalDirs.none { parent ->
                    parent != child && child.canonicalPath.lowercase().startsWith(parent.canonicalPath.lowercase() + File.separator)
                }
            }

            val items = mutableListOf<LocalRomItem>()

            for (dir in scanDirectories) {
                dir.walkTopDown().maxDepth(3).filter { it.isFile }.forEach { file ->
                    val canonicalFile = try { file.canonicalFile } catch (_: Exception) { file.absoluteFile }
                    val ext = canonicalFile.extension.lowercase()
                    val type = when (ext) {
                        "cci" -> LocalFileType.CCI
                        "zcci" -> LocalFileType.ZCCI
                        "3ds" -> LocalFileType.THREE_DS
                        "cia" -> LocalFileType.CIA
                        else -> null
                    }

                    if (type != null) {
                        val baseName = canonicalFile.nameWithoutExtension
                        val prodCodeMatch = Regex("\\[([A-Z0-9-]+)\\]").find(baseName)
                        val prodCode = prodCodeMatch?.groupValues?.get(1) ?: ""
                        val cleanName = baseName.replace(Regex("\\[[A-Z0-9-]+\\]"), "").trim()

                        val isUpdateDlc = type == LocalFileType.CIA &&
                                (canonicalFile.absolutePath.contains("Updates_DLC", ignoreCase = true) ||
                                        prodCode.startsWith("CTR-U-") ||
                                        prodCode.startsWith("CTR-M-"))

                        val sizeMb = canonicalFile.length() / (1024f * 1024f)
                        val sizeStr = if (sizeMb >= 1024f) String.format("%.2f GB", sizeMb / 1024f) else String.format("%.1f MB", sizeMb)

                        items.add(
                            LocalRomItem(
                                file = canonicalFile,
                                name = cleanName.ifEmpty { canonicalFile.name },
                                productCode = prodCode,
                                fileType = type,
                                sizeBytes = canonicalFile.length(),
                                sizeString = sizeStr,
                                lastModified = canonicalFile.lastModified(),
                                isDecrypted = type == LocalFileType.CCI || type == LocalFileType.THREE_DS || type == LocalFileType.ZCCI,
                                isUpdateOrDlc = isUpdateDlc
                            )
                        )
                    }
                }
            }

            // Deduplicate by case-insensitive file name and size so physical duplicates never appear
            val sorted = items.distinctBy { "${it.file.name.lowercase()}#${it.sizeBytes}" }.sortedByDescending { it.lastModified }
            _localRoms.value = sorted
            if (_selectedLocalRom.value == null && sorted.isNotEmpty()) {
                selectLocalRom(sorted.first())
            }
            _isScanningLocalRoms.value = false
        }
    }

    fun selectLocalRom(item: LocalRomItem) {
        _selectedLocalRom.value = item
        val detail = HShopTitleDetail(
            id = item.productCode.ifEmpty { item.file.name },
            name = item.name,
            categorySlug = if (item.isUpdateOrDlc) "updates" else "games",
            subcategorySlug = "installed",
            titleId = "N/A",
            productCode = item.productCode,
            version = "Installed",
            sizeString = item.sizeString,
            contentType = item.fileType.displayName,
            addedDate = "Local Storage",
            updatedDate = "N/A",
            downloadCount = 0L,
            description = "Stored at: ${item.file.absolutePath}",
            artwork = me.erista.hshop.model.ArtworkInfo(
                primaryCoverUrl = null,
                highResCoverUrl = null,
                fallbackUrls = emptyList()
            )
        )
        _selectedTitleDetail.value = detail
        _statusMessage.value = "Selected local file: ${item.file.name}"
    }

    private var _lastBrowseDetail: HShopTitleDetail? = null

    fun selectTab(tab: BottomTab) {
        _selectedTab.value = tab
        when (tab) {
            BottomTab.BROWSE -> {
                if (_lastBrowseDetail != null) {
                    _selectedTitleDetail.value = _lastBrowseDetail
                } else if (_titles.value.isNotEmpty()) {
                    selectTitle(_titles.value.first())
                }
            }
            BottomTab.LIBRARY -> {
                val currentLocal = _selectedLocalRom.value ?: _localRoms.value.firstOrNull()
                if (currentLocal != null) {
                    selectLocalRom(currentLocal)
                }
                refreshLocalRoms()
            }
            BottomTab.DOWNLOADS -> {
                val tasks = downloadTasks.value
                val taskId = _selectedDownloadTaskId.value ?: tasks.firstOrNull()?.id
                if (taskId != null) {
                    selectDownloadTaskId(taskId)
                }
            }
            BottomTab.SETTINGS -> {
                // Top screen switches to Settings Dashboard instantly via selectedTab
            }
        }
    }

    fun setBottomBarFocused(focused: Boolean) {
        _isBottomBarFocused.value = focused
    }

    fun enterContent() {
        _isBottomBarFocused.value = false
    }

    fun exitContentToBottomBar() {
        _isBottomBarFocused.value = true
    }

    fun navigateTabNext() {
        val tabs = BottomTab.entries
        val nextIndex = (_selectedTab.value.ordinal + 1) % tabs.size
        selectTab(tabs[nextIndex])
    }

    fun navigateTabPrev() {
        val tabs = BottomTab.entries
        val prevIndex = if (_selectedTab.value.ordinal <= 0) tabs.size - 1 else _selectedTab.value.ordinal - 1
        selectTab(tabs[prevIndex])
    }

    fun setDownloadPath(path: String) {
        settingsRepo.setDownloadPath(path)
    }

    fun setUpdateDlcPath(path: String) {
        settingsRepo.setUpdateDlcPath(path)
    }

    fun setTheme(theme: AppTheme) {
        settingsRepo.setTheme(theme)
    }

    fun setAutoRemoveDownloadedCia(autoRemove: Boolean) {
        settingsRepo.setAutoRemoveDownloadedCia(autoRemove)
    }

    fun setAutoConvertTo3ds(autoConvert: Boolean) {
        settingsRepo.setAutoConvertTo3ds(autoConvert)
    }

    fun setAutoCompressToZcci(autoCompress: Boolean) {
        settingsRepo.setAutoCompressToZcci(autoCompress)
    }

    fun setAutoDownloadRelatedContent(autoDownload: Boolean) {
        settingsRepo.setAutoDownloadRelatedContent(autoDownload)
    }

    fun compressCciFile(cciFile: File, productCode: String = "", titleName: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            _statusMessage.value = "Compressing ${cciFile.name} to .ZCCI..."
            val outputZcci = File(cciFile.parentFile, cciFile.nameWithoutExtension + ".zcci")
            val success = me.erista.hshop.thor.compressor.ZcciCompressor.compressCciToZcci(
                inputFile = cciFile,
                outputFile = outputZcci,
                onProgress = { progress, msg ->
                    _statusMessage.value = msg
                }
            )

            if (success) {
                _statusMessage.value = "Compressed to ${outputZcci.name}!"
                refreshLocalRoms()
            } else {
                _statusMessage.value = "Compression failed for ${cciFile.name}"
            }
        }
    }

    fun deleteLocalRomFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileName = file.name
            if (file.exists() && file.delete()) {
                _statusMessage.value = "Deleted $fileName"
                refreshLocalRoms()
            } else {
                _statusMessage.value = "Failed to delete $fileName"
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun search() {
        val query = _searchQuery.value.trim()
        val cat = _selectedCategory.value
        val sub = _selectedSubcategory.value

        viewModelScope.launch {
            _isLoading.value = true
            _canLoadMore.value = true
            _statusMessage.value = if (query.isNotEmpty()) "Searching '$query' in ${cat.displayName}..." else "Loading ${cat.displayName}..."
            try {
                if (query.isNotEmpty()) {
                    val result = scraper.searchTitles(
                        query = query,
                        categorySlug = cat.slug,
                        subcategorySlug = sub,
                        count = 50,
                        offset = 0
                    )
                    _titles.value = result.titles
                    _canLoadMore.value = result.titles.size >= 50
                    _statusMessage.value = "Found ${result.totalCount} in ${cat.displayName}"
                } else if (sub != null) {
                    val list = scraper.fetchCategoryTitles(
                        categorySlug = cat.slug,
                        subcategorySlug = sub,
                        count = 50,
                        offset = 0
                    )
                    _titles.value = list
                    _canLoadMore.value = list.size >= 50
                    _statusMessage.value = "Loaded ${list.size} in ${cat.displayName}"
                } else {
                    val firstSub = _subcategories.value.firstOrNull()?.slug
                    if (firstSub != null) {
                        val list = scraper.fetchCategoryTitles(
                            categorySlug = cat.slug,
                            subcategorySlug = firstSub,
                            count = 50,
                            offset = 0
                        )
                        _titles.value = list
                        _canLoadMore.value = list.size >= 50
                        _statusMessage.value = "Loaded ${list.size} in ${cat.displayName}"
                    } else {
                        val result = scraper.searchTitles(
                            query = "a",
                            categorySlug = cat.slug,
                            count = 50,
                            offset = 0
                        )
                        _titles.value = result.titles
                        _canLoadMore.value = result.titles.size >= 50
                        _statusMessage.value = "Loaded ${result.totalCount} in ${cat.displayName}"
                    }
                }

                if (_titles.value.isNotEmpty()) {
                    preloadInitialArtwork(_titles.value)
                    selectTitle(_titles.value.first())
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreTitles() {
        if (_isLoading.value || _isLoadingMore.value || !_canLoadMore.value) return

        val currentList = _titles.value
        val offset = currentList.size
        val query = _searchQuery.value.trim()
        val cat = _selectedCategory.value
        val sub = _selectedSubcategory.value

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val newTitles = if (query.isNotEmpty()) {
                    val result = scraper.searchTitles(
                        query = query,
                        categorySlug = cat.slug,
                        subcategorySlug = sub,
                        count = 50,
                        offset = offset
                    )
                    result.titles
                } else if (sub != null) {
                    scraper.fetchCategoryTitles(
                        categorySlug = cat.slug,
                        subcategorySlug = sub,
                        count = 50,
                        offset = offset
                    )
                } else {
                    val firstSub = _subcategories.value.firstOrNull()?.slug
                    if (firstSub != null) {
                        scraper.fetchCategoryTitles(
                            categorySlug = cat.slug,
                            subcategorySlug = firstSub,
                            count = 50,
                            offset = offset
                        )
                    } else {
                        val result = scraper.searchTitles(
                            query = "a",
                            categorySlug = cat.slug,
                            count = 50,
                            offset = offset
                        )
                        result.titles
                    }
                }

                if (newTitles.isNotEmpty()) {
                    _titles.value = currentList + newTitles
                    preloadInitialArtwork(newTitles)
                    _canLoadMore.value = newTitles.size >= 50
                    _statusMessage.value = "Loaded ${_titles.value.size} titles"
                } else {
                    _canLoadMore.value = false
                }
            } catch (e: Exception) {
                _statusMessage.value = "Failed to load more: ${e.localizedMessage}"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun toggleAllowedRegion(slug: String) {
        settingsRepo.toggleAllowedRegion(slug)
    }

    fun loadCategory(category: HShopCategory) {
        _selectedCategory.value = category
        _selectedSubcategory.value = null
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Loading ${category.displayName}..."
            try {
                val allSubs = scraper.fetchSubcategories(category)
                _subcategories.value = allSubs

                val allowed = settings.value.allowedRegions
                val effectiveSubs = if (allowed.isEmpty()) allSubs else allSubs.filter { allowed.contains(it.slug) }

                val query = _searchQuery.value.trim()
                if (query.isNotEmpty()) {
                    val result = scraper.searchTitles(
                        query = query,
                        categorySlug = category.slug,
                        count = 50
                    )
                    _titles.value = result.titles
                    _statusMessage.value = "Found ${result.totalCount} in ${category.displayName}"
                } else {
                    val firstSub = effectiveSubs.firstOrNull()?.slug ?: allSubs.firstOrNull()?.slug
                    if (firstSub != null) {
                        _selectedSubcategory.value = firstSub
                        val list = scraper.fetchCategoryTitles(
                            categorySlug = category.slug,
                            subcategorySlug = firstSub,
                            count = 50
                        )
                        _titles.value = list
                        _statusMessage.value = "Loaded ${category.displayName}"
                    } else {
                        val result = scraper.searchTitles(
                            query = "a",
                            categorySlug = category.slug,
                            count = 50
                        )
                        _titles.value = result.titles
                        _statusMessage.value = "Loaded ${category.displayName}"
                    }
                }

                if (_titles.value.isNotEmpty()) {
                    preloadInitialArtwork(_titles.value)
                    selectTitle(_titles.value.first())
                }
            } catch (e: Exception) {
                _statusMessage.value = "Category error: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun filterBySubcategory(subcategorySlug: String?) {
        _selectedSubcategory.value = subcategorySlug
        search()
    }

    // Fast LRU in-memory cache (bounded to 300 entries) so navigating back and forth requires 0 network reloads without memory leak
    private val titleDetailCache: MutableMap<String, HShopTitleDetail> = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, HShopTitleDetail>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HShopTitleDetail>?): Boolean {
                return size > 300
            }
        }
    )
    private var loadTitleDetailJob: kotlinx.coroutines.Job? = null

    fun selectTitle(summary: HShopTitleSummary) {
        // 1. If already cached, restore instantly with ZERO reload or network delay
        val cached = titleDetailCache[summary.id]
        if (cached != null) {
            loadTitleDetailJob?.cancel()
            _selectedTitleDetail.value = cached
            _lastBrowseDetail = cached
            _statusMessage.value = "Selected ${cached.name}"
            preloadUpcomingArtwork(summary)
            return
        }

        // 2. Cancel previous in-flight detail loading job to avoid race conditions
        loadTitleDetailJob?.cancel()

        // Immediately present basic details and artwork to Top Screen
        val initialDetail = HShopTitleDetail(
            id = summary.id,
            name = summary.name,
            categorySlug = summary.categorySlug,
            subcategorySlug = summary.subcategorySlug,
            titleId = summary.titleId,
            productCode = summary.productCode,
            version = summary.version,
            sizeString = summary.sizeString,
            contentType = summary.contentType,
            addedDate = "Loading...",
            updatedDate = "Loading...",
            downloadCount = 0L,
            artwork = summary.artwork
        )
        _selectedTitleDetail.value = initialDetail
        _lastBrowseDetail = initialDetail

        // Pre-download upcoming title artwork and details ahead of the cursor
        preloadUpcomingArtwork(summary)

        loadTitleDetailJob = viewModelScope.launch {
            _statusMessage.value = "Loading ${summary.name}..."
            try {
                val detail = scraper.fetchTitleDetail(summary.id)
                titleDetailCache[summary.id] = detail
                _selectedTitleDetail.value = detail
                _lastBrowseDetail = detail
                _statusMessage.value = "Loaded ${summary.name}"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep the draft detail with artwork
            }
        }
    }

    private fun preloadInitialArtwork(titles: List<HShopTitleSummary>) {
        if (titles.isEmpty()) return
        val loader = getApplication<Application>().imageLoader
        // Pre-download thumbnails for the first 12 titles and covers for the first 4
        titles.take(12).forEachIndexed { index, item ->
            val thumb = item.artwork?.thumbnailCoverUrl
                ?: item.artwork?.primaryCoverUrl
                ?: item.artwork?.fallbackUrls?.firstOrNull()
            if (!thumb.isNullOrBlank()) {
                loader.enqueue(
                    ImageRequest.Builder(getApplication())
                        .data(thumb)
                        .build()
                )
            }
            if (index < 4) {
                val cover = item.artwork?.highResCoverUrl
                    ?: item.artwork?.primaryCoverUrl
                    ?: item.artwork?.fallbackUrls?.firstOrNull()
                if (!cover.isNullOrBlank()) {
                    loader.enqueue(
                        ImageRequest.Builder(getApplication())
                            .data(cover)
                            .build()
                    )
                }
            }
        }
    }

    private fun preloadUpcomingArtwork(currentSummary: HShopTitleSummary) {
        val list = _titles.value
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.id == currentSummary.id }
        if (currentIndex < 0) return

        val loader = getApplication<Application>().imageLoader
        val maxIndex = (currentIndex + 6).coerceAtMost(list.size - 1)
        for (i in (currentIndex + 1)..maxIndex) {
            val item = list.getOrNull(i) ?: continue
            val thumb = item.artwork?.thumbnailCoverUrl
                ?: item.artwork?.primaryCoverUrl
                ?: item.artwork?.fallbackUrls?.firstOrNull()
            if (!thumb.isNullOrBlank()) {
                loader.enqueue(
                    ImageRequest.Builder(getApplication())
                        .data(thumb)
                        .build()
                )
            }
            if (i <= currentIndex + 3) {
                val cover = item.artwork?.highResCoverUrl
                    ?: item.artwork?.primaryCoverUrl
                    ?: item.artwork?.fallbackUrls?.firstOrNull()
                if (!cover.isNullOrBlank()) {
                    loader.enqueue(
                        ImageRequest.Builder(getApplication())
                            .data(cover)
                            .build()
                    )
                }
            }
        }

        // Prefetch details for the immediate next 2 titles so navigating forward is also instant
        val nextItems = list.subList((currentIndex + 1).coerceAtMost(list.size), (currentIndex + 3).coerceAtMost(list.size))
        for (nextItem in nextItems) {
            if (!titleDetailCache.containsKey(nextItem.id)) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val d = scraper.fetchTitleDetail(nextItem.id)
                        titleDetailCache[nextItem.id] = d
                    } catch (ignored: Exception) {}
                }
            }
        }
    }

    fun requestDownload(detail: HShopTitleDetail) {
        _turnstileTarget.value = detail
        _statusMessage.value = "Verifying ${detail.name}..."
    }

    fun requestRelatedDownload(rel: RelatedContentSummary) {
        val relDetail = HShopTitleDetail(
            id = rel.id,
            name = rel.name,
            categorySlug = if (rel.relationType.contains("Update", ignoreCase = true)) "updates" else "dlc",
            subcategorySlug = "related",
            titleId = rel.titleId,
            productCode = rel.productCode,
            version = rel.version,
            sizeString = rel.sizeString,
            contentType = rel.contentType,
            addedDate = "N/A",
            updatedDate = "N/A",
            downloadCount = 0L,
            description = "Related content: ${rel.relationType}"
        )
        requestDownload(relDetail)
    }

    fun dismissTurnstileDialog() {
        _turnstileTarget.value = null
    }

    fun onDownloadUrlResolved(directUrl: String, customTarget: HShopTitleDetail? = null) {
        val target = customTarget ?: _turnstileTarget.value ?: return
        val currentSettings = settings.value

        val isUpdateOrDlc = target.categorySlug.equals("updates", ignoreCase = true) ||
                target.categorySlug.equals("dlc", ignoreCase = true) ||
                target.productCode.startsWith("CTR-U-", ignoreCase = true) ||
                target.productCode.startsWith("CTR-M-", ignoreCase = true) ||
                target.description.contains("Related content", ignoreCase = true)

        val targetDir = if (isUpdateOrDlc) currentSettings.updateDlcPath else currentSettings.downloadPath
        val skipDecryption = isUpdateOrDlc // Updates and DLC are installed as .CIA in Azahar, no decryption needed

        downloadManager.enqueueDownload(
            id = target.id,
            titleName = target.name,
            productCode = target.productCode,
            downloadUrl = directUrl,
            targetDirectory = targetDir,
            skipAutoConvert = skipDecryption
        )
        _turnstileTarget.value = null
        _statusMessage.value = "Downloading ${target.name} to $targetDir"
    }

    fun cancelDownload(id: String) {
        downloadManager.cancelDownload(id)
    }

    fun decryptExistingCia(id: String) {
        downloadManager.decryptExistingCia(id)
    }

    fun navigateTitleDown() {
        val currentList = _titles.value
        if (currentList.isEmpty()) return
        val currentId = _selectedTitleDetail.value?.id
        val currentIndex = currentList.indexOfFirst { it.id == currentId }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1).coerceAtMost(currentList.size - 1)
        selectTitle(currentList[nextIndex])

        if (nextIndex >= currentList.size - 5 && _canLoadMore.value && !_isLoadingMore.value) {
            loadMoreTitles()
        }
    }

    fun navigateTitleUp() {
        val currentList = _titles.value
        if (currentList.isEmpty()) return
        val currentId = _selectedTitleDetail.value?.id
        val currentIndex = currentList.indexOfFirst { it.id == currentId }
        val prevIndex = if (currentIndex <= 0) 0 else currentIndex - 1
        selectTitle(currentList[prevIndex])
    }

    fun navigateCategoryNext() {
        val currentCats = HShopCategory.values()
        val currentIndex = currentCats.indexOfFirst { it == _selectedCategory.value }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % currentCats.size
        loadCategory(currentCats[nextIndex])
    }

    fun navigateCategoryPrev() {
        val currentCats = HShopCategory.values()
        val currentIndex = currentCats.indexOfFirst { it == _selectedCategory.value }
        val prevIndex = if (currentIndex <= 0) currentCats.size - 1 else currentIndex - 1
        loadCategory(currentCats[prevIndex])
    }

    fun navigateSubcategoryNext() {
        val allSubs = _subcategories.value
        if (allSubs.isEmpty()) return
        val allowed = settings.value.allowedRegions
        val currentSubs = if (allowed.isEmpty()) allSubs else allSubs.filter { allowed.contains(it.slug) }
        if (currentSubs.isEmpty()) return

        val currentIndex = currentSubs.indexOfFirst { it.slug == _selectedSubcategory.value }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % currentSubs.size
        filterBySubcategory(currentSubs[nextIndex].slug)
    }

    fun navigateSubcategoryPrev() {
        val allSubs = _subcategories.value
        if (allSubs.isEmpty()) return
        val allowed = settings.value.allowedRegions
        val currentSubs = if (allowed.isEmpty()) allSubs else allSubs.filter { allowed.contains(it.slug) }
        if (currentSubs.isEmpty()) return

        val currentIndex = currentSubs.indexOfFirst { it.slug == _selectedSubcategory.value }
        val prevIndex = if (currentIndex <= 0) currentSubs.size - 1 else currentIndex - 1
        filterBySubcategory(currentSubs[prevIndex].slug)
    }

    fun handleButtonA() {
        if (_isBottomBarFocused.value) {
            enterContent()
            return
        }
        val detail = _selectedTitleDetail.value
        if (detail != null) {
            val task = downloadTasks.value.find { it.id == detail.id }
            if (task != null && task.status == DownloadStatus.COMPLETED) {
                // Already completed
            } else if (task == null || task.status == DownloadStatus.FAILED || task.status == DownloadStatus.CANCELLED) {
                requestDownload(detail)
            }
        }
    }

    fun handleButtonB(): Boolean {
        if (!_isBottomBarFocused.value) {
            exitContentToBottomBar()
            return true
        }
        if (_selectedTab.value != BottomTab.BROWSE) {
            selectTab(BottomTab.BROWSE)
            return true
        }
        return false
    }

    fun handleButtonY() {
        val tabs = BottomTab.values()
        val nextIndex = (_selectedTab.value.ordinal + 1) % tabs.size
        selectTab(tabs[nextIndex])
    }

    fun navigateLocalRomDown() {
        val list = _localRoms.value
        if (list.isEmpty()) return
        val current = _selectedLocalRom.value
        val currentIndex = list.indexOfFirst { it.file.absolutePath == current?.file?.absolutePath }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1).coerceAtMost(list.size - 1)
        selectLocalRom(list[nextIndex])
    }

    fun navigateLocalRomUp() {
        val list = _localRoms.value
        if (list.isEmpty()) return
        val current = _selectedLocalRom.value
        val currentIndex = list.indexOfFirst { it.file.absolutePath == current?.file?.absolutePath }
        val prevIndex = if (currentIndex <= 0) 0 else currentIndex - 1
        selectLocalRom(list[prevIndex])
    }

    fun syncDownloadTaskToTopScreen(taskId: String) {
        val task = downloadTasks.value.find { it.id == taskId } ?: return
        val sizeStr = me.erista.hshop.thor.util.StorageUtils.formatSize(task.totalBytes.takeIf { it > 0 } ?: (task.progress * 1000000000L).toLong())
        val detail = HShopTitleDetail(
            id = task.id,
            name = task.titleName,
            categorySlug = "downloads",
            subcategorySlug = "queue",
            titleId = "N/A",
            productCode = task.productCode,
            version = task.status.name,
            sizeString = sizeStr,
            contentType = "Download Task",
            addedDate = "Queue",
            updatedDate = "N/A",
            downloadCount = 0L,
            description = "Status: ${task.status.name} (${(task.progress * 100).toInt()}%)\nTarget: ${task.targetFilePath}",
            artwork = me.erista.hshop.model.ArtworkInfo(
                primaryCoverUrl = null,
                highResCoverUrl = null,
                fallbackUrls = emptyList()
            )
        )
        _selectedTitleDetail.value = detail
    }

    fun selectDownloadTaskId(id: String) {
        _selectedDownloadTaskId.value = id
        syncDownloadTaskToTopScreen(id)
    }

    fun navigateDownloadTaskDown() {
        val list = downloadTasks.value
        if (list.isEmpty()) return
        val currentId = _selectedDownloadTaskId.value
        val currentIndex = list.indexOfFirst { it.id == currentId }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1).coerceAtMost(list.size - 1)
        selectDownloadTaskId(list[nextIndex].id)
    }

    fun navigateDownloadTaskUp() {
        val list = downloadTasks.value
        if (list.isEmpty()) return
        val currentId = _selectedDownloadTaskId.value
        val currentIndex = list.indexOfFirst { it.id == currentId }
        val prevIndex = if (currentIndex <= 0) 0 else currentIndex - 1
        selectDownloadTaskId(list[prevIndex].id)
    }

    fun navigateContentUp() {
        when (_selectedTab.value) {
            BottomTab.BROWSE -> navigateTitleUp()
            BottomTab.LIBRARY -> navigateLocalRomUp()
            BottomTab.DOWNLOADS -> navigateDownloadTaskUp()
            BottomTab.SETTINGS -> _settingsScrollEvent.tryEmit(-350f)
        }
    }

    fun navigateContentDown() {
        when (_selectedTab.value) {
            BottomTab.BROWSE -> navigateTitleDown()
            BottomTab.LIBRARY -> navigateLocalRomDown()
            BottomTab.DOWNLOADS -> navigateDownloadTaskDown()
            BottomTab.SETTINGS -> _settingsScrollEvent.tryEmit(350f)
        }
    }

    fun selectLocalFilter(filter: String) {
        _selectedLocalFilter.value = filter
    }

    fun navigateLocalFilterNext() {
        val filters = listOf("ALL", "CCI", "ZCCI", "3DS", "CIA")
        val currentIdx = filters.indexOf(_selectedLocalFilter.value)
        val nextIdx = if (currentIdx < 0) 0 else (currentIdx + 1) % filters.size
        _selectedLocalFilter.value = filters[nextIdx]
    }

    fun navigateLocalFilterPrev() {
        val filters = listOf("ALL", "CCI", "ZCCI", "3DS", "CIA")
        val currentIdx = filters.indexOf(_selectedLocalFilter.value)
        val prevIdx = if (currentIdx <= 0) filters.size - 1 else currentIdx - 1
        _selectedLocalFilter.value = filters[prevIdx]
    }

    fun navigateContentLeft() {
        when (_selectedTab.value) {
            BottomTab.BROWSE -> navigateSubcategoryPrev()
            BottomTab.LIBRARY -> navigateLocalFilterPrev()
            BottomTab.DOWNLOADS -> { /* no-op */ }
            BottomTab.SETTINGS -> { /* no-op */ }
        }
    }

    fun navigateContentRight() {
        when (_selectedTab.value) {
            BottomTab.BROWSE -> navigateSubcategoryNext()
            BottomTab.LIBRARY -> navigateLocalFilterNext()
            BottomTab.DOWNLOADS -> { /* no-op */ }
            BottomTab.SETTINGS -> { /* no-op */ }
        }
    }

    fun navigateShoulderLeft() {
        when (_selectedTab.value) {
            BottomTab.BROWSE -> navigateCategoryPrev()
            BottomTab.LIBRARY -> navigateLocalFilterPrev()
            BottomTab.DOWNLOADS -> { /* no-op */ }
            BottomTab.SETTINGS -> { /* no-op */ }
        }
    }

    fun navigateShoulderRight() {
        when (_selectedTab.value) {
            BottomTab.BROWSE -> navigateCategoryNext()
            BottomTab.LIBRARY -> navigateLocalFilterNext()
            BottomTab.DOWNLOADS -> { /* no-op */ }
            BottomTab.SETTINGS -> { /* no-op */ }
        }
    }

    fun handleContentAction() {
        if (_isBottomBarFocused.value) {
            enterContent()
            return
        }
        when (_selectedTab.value) {
            BottomTab.BROWSE -> handleButtonA()
            BottomTab.LIBRARY -> {
                _selectedLocalRom.value?.let { rom ->
                    _launchLocalRomEvent.tryEmit(rom)
                }
            }
            BottomTab.DOWNLOADS -> {
                val taskId = _selectedDownloadTaskId.value
                if (taskId != null) {
                    val task = downloadTasks.value.find { it.id == taskId }
                    if (task != null && task.status == DownloadStatus.COMPLETED) {
                        decryptExistingCia(task.id)
                    }
                }
            }
            BottomTab.SETTINGS -> {
                // In settings, content action
            }
        }
    }

    fun handleButtonX() {
        if (_selectedTab.value == BottomTab.LIBRARY) {
            val rom = _selectedLocalRom.value ?: return
            if (rom.fileType == LocalFileType.CIA) {
                decryptCiaFile(rom.file, rom.productCode, rom.name)
            } else if (rom.fileType == LocalFileType.CCI) {
                compressCciFile(rom.file, rom.productCode, rom.name)
            }
            return
        }
        val detail = _selectedTitleDetail.value ?: return
        val task = downloadTasks.value.find { it.id == detail.id }
        if (task != null) {
            decryptExistingCia(task.id)
        }
    }

    fun decryptCiaFile(file: java.io.File, id: String, titleName: String) {
        downloadManager.decryptCiaFile(file, id, titleName)
    }

    fun clearCompletedDownloads() {
        downloadManager.clearCompleted()
    }

    fun getAppCacheSizeBytes(): Long {
        return me.erista.hshop.thor.util.StorageUtils.getDirSize(getApplication<Application>().cacheDir)
    }

    fun clearAppCache(): Long {
        val app = getApplication<Application>()
        val bytesFreed = me.erista.hshop.thor.util.StorageUtils.getDirSize(app.cacheDir)
        me.erista.hshop.thor.util.StorageUtils.clearDirectory(app.cacheDir)
        app.imageLoader.memoryCache?.clear()
        titleDetailCache.clear()
        _statusMessage.value = "Cache cleared (freed ${me.erista.hshop.thor.util.StorageUtils.formatSize(bytesFreed)})"
        return bytesFreed
    }
}
