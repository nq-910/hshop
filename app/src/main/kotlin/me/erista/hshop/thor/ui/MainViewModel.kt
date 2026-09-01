package me.erista.hshop.thor.ui

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.erista.hshop.model.*
import me.erista.hshop.scraper.HShopScraper
import me.erista.hshop.thor.data.AppSettings
import me.erista.hshop.thor.data.AppTheme
import me.erista.hshop.thor.data.DownloadStatus
import me.erista.hshop.thor.data.DownloadTask
import me.erista.hshop.thor.data.SettingsRepository
import me.erista.hshop.thor.download.AutoDownloadResolver
import me.erista.hshop.thor.download.ThorDownloadManager
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

    private val _localRoms = MutableStateFlow<List<me.erista.hshop.thor.data.LocalRomItem>>(emptyList())
    val localRoms: StateFlow<List<me.erista.hshop.thor.data.LocalRomItem>> = _localRoms.asStateFlow()

    private val _isScanningLocalRoms = MutableStateFlow(false)
    val isScanningLocalRoms: StateFlow<Boolean> = _isScanningLocalRoms.asStateFlow()

    private val _selectedTab = MutableStateFlow(BottomTab.BROWSE)
    val selectedTab: StateFlow<BottomTab> = _selectedTab.asStateFlow()

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

    init {
        loadCategory(HShopCategory.GAMES)
        checkForAppUpdates(silent = true)
        refreshLocalRoms()
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
            } else if (!silent) {
                _updateCheckStatus.value = "You are running the latest version (v0.0.1-beta)."
            }
        }
    }

    fun dismissUpdateDialog() {
        _availableUpdate.value = null
    }

    fun refreshLocalRoms() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanningLocalRoms.value = true
            val currentSettings = settings.value
            val scanDirectories = mutableListOf<File>()

            // 1. Primary ROMs Download Path
            val primaryDir = File(currentSettings.downloadPath)
            if (primaryDir.exists() && primaryDir.isDirectory) scanDirectories.add(primaryDir)

            // 2. Updates & DLC Path
            val updateDir = File(currentSettings.updateDlcPath)
            if (updateDir.exists() && updateDir.isDirectory) scanDirectories.add(updateDir)

            // 3. Common Thor handheld ROM locations (/sdcard/ROMs/n3ds, /sdcard/ROMs/3DS)
            val n3dsDir = File(Environment.getExternalStorageDirectory(), "ROMs/n3ds")
            if (n3dsDir.exists() && n3dsDir.isDirectory && !scanDirectories.contains(n3dsDir)) {
                scanDirectories.add(n3dsDir)
            }

            val items = mutableListOf<me.erista.hshop.thor.data.LocalRomItem>()

            for (dir in scanDirectories) {
                dir.walkTopDown().maxDepth(3).filter { it.isFile }.forEach { file ->
                    val ext = file.extension.lowercase()
                    val type = when (ext) {
                        "cci" -> me.erista.hshop.thor.data.LocalFileType.CCI
                        "zcci" -> me.erista.hshop.thor.data.LocalFileType.ZCCI
                        "3ds" -> me.erista.hshop.thor.data.LocalFileType.THREE_DS
                        "cia" -> me.erista.hshop.thor.data.LocalFileType.CIA
                        else -> null
                    }

                    if (type != null) {
                        val baseName = file.nameWithoutExtension
                        val prodCodeMatch = Regex("\\[([A-Z0-9-]+)\\]").find(baseName)
                        val prodCode = prodCodeMatch?.groupValues?.get(1) ?: ""
                        val cleanName = baseName.replace(Regex("\\[[A-Z0-9-]+\\]"), "").trim()

                        val isUpdateDlc = type == me.erista.hshop.thor.data.LocalFileType.CIA &&
                                (file.absolutePath.contains("Updates_DLC", ignoreCase = true) ||
                                        prodCode.startsWith("CTR-U-") ||
                                        prodCode.startsWith("CTR-M-"))

                        val sizeMb = file.length() / (1024f * 1024f)
                        val sizeStr = if (sizeMb >= 1024f) String.format("%.2f GB", sizeMb / 1024f) else String.format("%.1f MB", sizeMb)

                        items.add(
                            me.erista.hshop.thor.data.LocalRomItem(
                                file = file,
                                name = cleanName.ifEmpty { file.name },
                                productCode = prodCode,
                                fileType = type,
                                sizeBytes = file.length(),
                                sizeString = sizeStr,
                                lastModified = file.lastModified(),
                                isDecrypted = type == me.erista.hshop.thor.data.LocalFileType.CCI || type == me.erista.hshop.thor.data.LocalFileType.THREE_DS || type == me.erista.hshop.thor.data.LocalFileType.ZCCI,
                                isUpdateOrDlc = isUpdateDlc
                            )
                        )
                    }
                }
            }

            _localRoms.value = items.distinctBy { it.file.absolutePath }.sortedByDescending { it.lastModified }
            _isScanningLocalRoms.value = false
        }
    }

    fun selectLocalRom(item: me.erista.hshop.thor.data.LocalRomItem) {
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

    fun selectTab(tab: BottomTab) {
        _selectedTab.value = tab
        if (tab == BottomTab.LIBRARY) {
            refreshLocalRoms()
        }
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

    fun selectTitle(summary: HShopTitleSummary) {
        viewModelScope.launch {
            _statusMessage.value = "Loading ${summary.name}..."
            try {
                val detail = scraper.fetchTitleDetail(summary.id)
                _selectedTitleDetail.value = detail
                _statusMessage.value = "Loaded ${summary.name}"
            } catch (e: Exception) {
                _selectedTitleDetail.value = HShopTitleDetail(
                    id = summary.id,
                    name = summary.name,
                    categorySlug = summary.categorySlug,
                    subcategorySlug = summary.subcategorySlug,
                    titleId = summary.titleId,
                    productCode = summary.productCode,
                    version = summary.version,
                    sizeString = summary.sizeString,
                    contentType = summary.contentType,
                    addedDate = "N/A",
                    updatedDate = "N/A",
                    downloadCount = 0L,
                    artwork = summary.artwork
                )
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

    fun handleButtonB() {
        if (_selectedTab.value != BottomTab.BROWSE) {
            selectTab(BottomTab.BROWSE)
        }
    }

    fun handleButtonY() {
        val tabs = BottomTab.values()
        val nextIndex = (_selectedTab.value.ordinal + 1) % tabs.size
        selectTab(tabs[nextIndex])
    }

    fun handleButtonX() {
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
}
