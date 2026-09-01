package me.erista.hshop.thor.ui.bottom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import me.erista.hshop.model.HShopCategory
import me.erista.hshop.model.HShopTitleSummary
import me.erista.hshop.thor.data.DownloadStatus
import me.erista.hshop.thor.ui.BottomTab
import me.erista.hshop.thor.ui.MainViewModel
import me.erista.hshop.thor.ui.download.DownloadsScreenContent
import me.erista.hshop.thor.ui.download.TurnstileDownloadDialog
import me.erista.hshop.thor.ui.settings.SettingsScreenContent
import me.erista.hshop.thor.ui.theme.*

@Composable
fun BottomScreenContent(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val downloadTasks by viewModel.downloadTasks.collectAsState()
    val turnstileTarget by viewModel.turnstileTarget.collectAsState()

    val activeDownloadsCount = downloadTasks.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.CONNECTING || it.status == DownloadStatus.QUEUED
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    BottomTab.BROWSE -> BrowseTabContent(viewModel = viewModel)
                    BottomTab.LIBRARY -> me.erista.hshop.thor.ui.local.LocalLibraryScreenContent(viewModel = viewModel)
                    BottomTab.DOWNLOADS -> DownloadsScreenContent(viewModel = viewModel)
                    BottomTab.SETTINGS -> SettingsScreenContent(viewModel = viewModel)
                }
            }

            // Bottom Navigation Bar
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                NavigationBarItem(
                    selected = selectedTab == BottomTab.BROWSE,
                    onClick = { viewModel.selectTab(BottomTab.BROWSE) },
                    icon = { Icon(imageVector = Icons.Default.Explore, contentDescription = "Browse") },
                    label = { Text("Browse") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == BottomTab.LIBRARY,
                    onClick = { viewModel.selectTab(BottomTab.LIBRARY) },
                    icon = { Icon(imageVector = Icons.Default.FolderSpecial, contentDescription = "Library") },
                    label = { Text("Library") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == BottomTab.DOWNLOADS,
                    onClick = { viewModel.selectTab(BottomTab.DOWNLOADS) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeDownloadsCount > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text(activeDownloadsCount.toString(), color = Color.Black)
                                    }
                                }
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Download, contentDescription = "Downloads")
                        }
                    },
                    label = { Text("Downloads") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == BottomTab.SETTINGS,
                    onClick = { viewModel.selectTab(BottomTab.SETTINGS) },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        // Turnstile Captcha Resolver Modal
        turnstileTarget?.let { target ->
            TurnstileDownloadDialog(
                detail = target,
                onDismiss = { viewModel.dismissTurnstileDialog() },
                onDownloadUrlResolved = { directUrl ->
                    viewModel.onDownloadUrlResolved(directUrl)
                }
            )
        }

        // App Update Notification Modal
        val availableUpdate by viewModel.availableUpdate.collectAsState()
        availableUpdate?.let { updateInfo ->
            me.erista.hshop.thor.ui.updater.UpdateAvailableDialog(
                updateInfo = updateInfo,
                onDismiss = { viewModel.dismissUpdateDialog() }
            )
        }
    }
}

@Composable
private fun BrowseTabContent(viewModel: MainViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedSubcategory by viewModel.selectedSubcategory.collectAsState()
    val subcategories by viewModel.subcategories.collectAsState()
    val titles by viewModel.titles.collectAsState()
    val selectedTitleDetail by viewModel.selectedTitleDetail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val canLoadMore by viewModel.canLoadMore.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 4.dp)
    ) {
        // 1. Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search 3DS ROMs, Updates, DLC...", color = Color.Gray) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search() })
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Categories Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(HShopCategory.entries) { cat ->
                val isSelected = cat == selectedCategory
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.loadCategory(cat) },
                    label = { Text(cat.displayName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = Color.Black,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = Color.White
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        // 3. Region Filter Chips (filtered by allowedRegions setting if configured)
        val settings by viewModel.settings.collectAsState()
        val allowedRegions = settings.allowedRegions
        val visibleSubcategories = remember(subcategories, allowedRegions) {
            if (allowedRegions.isEmpty()) subcategories
            else subcategories.filter { allowedRegions.contains(it.slug) }
        }

        if (visibleSubcategories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    val isAll = selectedSubcategory == null
                    SuggestionChip(
                        onClick = { viewModel.filterBySubcategory(null) },
                        label = { Text("All Regions", fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isAll) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                            labelColor = if (isAll) MaterialTheme.colorScheme.secondary else Color.Gray
                        )
                    )
                }
                items(visibleSubcategories) { sub ->
                    val isSelected = sub.slug == selectedSubcategory
                    SuggestionChip(
                        onClick = { viewModel.filterBySubcategory(sub.slug) },
                        label = { Text(sub.name, fontSize = 12.sp) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                            labelColor = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Gray
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Loading Indicator or Title List
        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()

            LaunchedEffect(selectedTitleDetail?.id) {
                val index = titles.indexOfFirst { it.id == selectedTitleDetail?.id }
                if (index >= 0) {
                    listState.animateScrollToItem(index)
                }
            }

            // Infinite scroll detection: trigger loadMore when reaching within 5 items of the end
            val shouldLoadMore by remember {
                derivedStateOf {
                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisibleItem >= titles.size - 6
                }
            }

            LaunchedEffect(shouldLoadMore, canLoadMore, isLoadingMore) {
                if (shouldLoadMore && canLoadMore && !isLoadingMore && titles.isNotEmpty()) {
                    viewModel.loadMoreTitles()
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(titles) { title ->
                    val isSelected = title.id == selectedTitleDetail?.id
                    TitleListItem(
                        title = title,
                        isSelected = isSelected,
                        onClick = { viewModel.selectTitle(title) }
                    )
                }

                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleListItem(
    title: HShopTitleSummary,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail Cover
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                val thumbUrl = title.artwork?.thumbnailCoverUrl
                    ?: title.artwork?.primaryCoverUrl
                    ?: title.artwork?.fallbackUrls?.firstOrNull()

                AsyncImage(
                    model = thumbUrl,
                    contentDescription = title.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title.productCode.ifEmpty { "CTR" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title.sizeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (title.subcategorySlug.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = title.subcategorySlug.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Select Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
