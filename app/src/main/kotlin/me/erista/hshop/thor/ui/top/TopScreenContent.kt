package me.erista.hshop.thor.ui.top

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.erista.hshop.model.HShopTitleDetail
import me.erista.hshop.model.RelatedContentSummary
import me.erista.hshop.thor.data.DownloadStatus
import me.erista.hshop.thor.data.DownloadTask
import me.erista.hshop.thor.data.SettingsRepository
import me.erista.hshop.thor.ui.MainViewModel
import me.erista.hshop.thor.ui.download.OutOfStorageDialog
import me.erista.hshop.thor.ui.BottomTab
import me.erista.hshop.thor.util.StorageUtils
import android.os.Environment
import java.io.File

@Composable
fun TopScreenContent(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val selectedDetail by viewModel.selectedTitleDetail.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    val downloadTasks by viewModel.downloadTasks.collectAsState()
    val outOfStorageTitleName by viewModel.outOfStorageTitleName.collectAsState()

    val currentTask = selectedDetail?.let { detail ->
        downloadTasks.find { it.id == detail.id }
    }

    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val settings by settingsRepo.settings.collectAsState()
    val downloadDir = remember(settings.downloadPath) { File(settings.downloadPath) }

    val cleanTitle = remember(selectedDetail) {
        selectedDetail?.let { detail ->
            val clean = detail.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            val code = detail.productCode?.trim()
            if (!code.isNullOrEmpty()) "$clean [$code]" else clean
        }
    }

    val existingCci = remember(cleanTitle, downloadDir, downloadTasks) {
        cleanTitle?.let { File(downloadDir, "$it.cci").takeIf { f -> f.exists() && f.length() > 0 } }
    }
    val existingZcci = remember(cleanTitle, downloadDir, downloadTasks) {
        cleanTitle?.let { File(downloadDir, "$it.zcci").takeIf { f -> f.exists() && f.length() > 0 } }
    }
    val existing3ds = remember(cleanTitle, downloadDir, downloadTasks) {
        cleanTitle?.let { File(downloadDir, "$it.3ds").takeIf { f -> f.exists() && f.length() > 0 } }
    }
    val existingCia = remember(cleanTitle, downloadDir, downloadTasks) {
        cleanTitle?.let { File(downloadDir, "$it.cia").takeIf { f -> f.exists() && f.length() > 0 } }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    val activeRomFile = existingZcci ?: existingCci ?: existing3ds ?: existingCia ?: currentTask?.convertedFilePath?.let { File(it).takeIf { f -> f.exists() } } ?: currentTask?.targetFilePath?.let { File(it).takeIf { f -> f.exists() } }

    if (showDeleteConfirm && activeRomFile != null && selectedDetail != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ROM File?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to permanently delete \"${selectedDetail?.name}\"?\n\nFile: ${activeRomFile.name} (${String.format("%.1f MB", activeRomFile.length() / (1024f * 1024f))})",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLocalRomFile(activeRomFile)
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    outOfStorageTitleName?.let { title ->
        OutOfStorageDialog(
            titleName = title,
            onDismiss = { viewModel.dismissOutOfStorageDialog() }
        )
    }

    val selectedTab by viewModel.selectedTab.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (selectedTab == BottomTab.SETTINGS) {
            TopSettingsDashboard(
                viewModel = viewModel,
                settings = settings,
                modifier = Modifier.fillMaxSize()
            )
        } else if (selectedDetail == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Select a title on the bottom touchscreen",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = statusMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else {
            selectedDetail?.let { detail ->
                DetailView(
                    detail = detail,
                    currentTask = currentTask,
                    existingCci = existingCci,
                    existingZcci = existingZcci,
                    existing3ds = existing3ds,
                    existingCia = existingCia,
                    activeRomFile = activeRomFile,
                    onDownloadClick = { viewModel.requestDownload(detail) },
                    onDownloadRelatedClick = { rel -> viewModel.requestRelatedDownload(rel) },
                    onCancelClick = { currentTask?.let { viewModel.cancelDownload(it.id) } },
                    onDeleteClick = { showDeleteConfirm = true },
                    onDecryptClick = {
                        if (existingCia != null) {
                            viewModel.decryptCiaFile(existingCia, detail.id, detail.name)
                        } else if (currentTask != null) {
                            viewModel.decryptExistingCia(currentTask.id)
                        }
                    }
                )
            }
        }

        // Top Status Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isLoading) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AYN THOR • hShop Browser",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = statusMsg,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun DetailView(
    detail: HShopTitleDetail,
    currentTask: DownloadTask?,
    existingCci: java.io.File?,
    existingZcci: java.io.File?,
    existing3ds: java.io.File?,
    existingCia: java.io.File?,
    activeRomFile: java.io.File?,
    onDownloadClick: () -> Unit,
    onDownloadRelatedClick: (RelatedContentSummary) -> Unit,
    onCancelClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDecryptClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 56.dp, start = 24.dp, end = 24.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Left Column: High-Res Boxart, Quick Badges & Download Button
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            ) {
                val context = LocalContext.current
                val thumbUrl = detail.artwork?.thumbnailCoverUrl
                    ?: detail.artwork?.primaryCoverUrl
                val coverUrl = detail.artwork?.highResCoverUrl
                    ?: detail.artwork?.primaryCoverUrl
                    ?: detail.artwork?.fallbackUrls?.firstOrNull()

                val imageRequest = remember(coverUrl, thumbUrl) {
                    val builder = ImageRequest.Builder(context)
                        .data(coverUrl)
                        .crossfade(true)
                    if (!thumbUrl.isNullOrBlank() && thumbUrl != coverUrl) {
                        builder.placeholderMemoryCacheKey(thumbUrl)
                    }
                    builder.build()
                }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = detail.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Legitimacy & Type Badge
            Surface(
                color = if (detail.contentType.contains("Legit", ignoreCase = true)) Color(0xFF1B5E20) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${detail.contentType} • ${detail.sizeString}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Download Action Button or Active Download Progress
            val context = androidx.compose.ui.platform.LocalContext.current

            if (currentTask != null && (currentTask.status == DownloadStatus.DOWNLOADING || currentTask.status == DownloadStatus.CONNECTING)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentTask.progressPercent}% • ${currentTask.speedString}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = onCancelClick,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = Color.Red,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { currentTask.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Black.copy(alpha = 0.4f)
                        )
                    }
                }
            } else if (currentTask != null && currentTask.status == DownloadStatus.CONVERTING) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Processing: ${currentTask.progressPercent}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { currentTask.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = Color.Black.copy(alpha = 0.4f)
                        )
                    }
                }
            } else if (activeRomFile != null) {
                val isDecrypted = existingZcci != null || existingCci != null || existing3ds != null || (currentTask?.convertedFilePath != null && currentTask.status == DownloadStatus.COMPLETED)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDecrypted) {
                        Button(
                            onClick = { me.erista.hshop.thor.util.GameLauncher.launchGame(context, activeRomFile.absolutePath) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onDecryptClick,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Decrypt", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete ROM",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                Button(
                    onClick = onDownloadClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download .CIA", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Right Column: Title Info, Technical Specs & Related Updates/DLC
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Game Name
            Text(
                text = detail.name,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Category Breadcrumbs
            Text(
                text = "${detail.categorySlug.uppercase()} ➔ ${detail.subcategorySlug.uppercase()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Specs Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpecCard(title = "Title ID", value = detail.titleId, modifier = Modifier.weight(1f))
                SpecCard(title = "Product Code", value = detail.productCode, modifier = Modifier.weight(1f))
                SpecCard(title = "Version", value = detail.version, modifier = Modifier.weight(0.8f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SHA-256 Hash & Seed
            if (!detail.sha256.isNullOrEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "SHA-256 HASH",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = detail.sha256 ?: "N/A",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Related Content (Filtered: Updates & DLC only, excluding Demos)
            val filteredRelated = detail.relatedContent.filter { rel ->
                !rel.relationType.contains("Demo", ignoreCase = true) &&
                        !rel.name.contains("Demo", ignoreCase = true) &&
                        !rel.productCode.startsWith("CTR-T-", ignoreCase = true)
            }

            if (filteredRelated.isNotEmpty()) {
                Text(
                    text = "UPDATES & DLC (${filteredRelated.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredRelated) { rel ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.width(240.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rel.relationType.ifEmpty { "Update / DLC" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = rel.sizeString,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = rel.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Version: v${rel.version} • ${rel.productCode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { onDownloadRelatedClick(rel) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.fillMaxWidth().height(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Download .CIA", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value.ifEmpty { "N/A" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TopSettingsDashboard(
    viewModel: MainViewModel,
    settings: me.erista.hshop.thor.data.AppSettings,
    modifier: Modifier = Modifier
) {
    val internalFree = remember { Environment.getDataDirectory().usableSpace }
    val romDirFree = remember(settings.downloadPath) { StorageUtils.getUsableSpace(settings.downloadPath) }
    val cacheSize = remember { viewModel.getAppCacheSizeBytes() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Banner
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Settings & System Overview",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "AYN Thor Dual-Screen Handheld Console",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Real-time Storage Overview Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Internal Storage", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${StorageUtils.formatSize(internalFree)} Free",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("ROM Storage", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${StorageUtils.formatSize(romDirFree)} Free",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Cache Usage", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = StorageUtils.formatSize(cacheSize),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Quick Controller Guide Card
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Controller Navigation Guide",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "• [D-Pad / Left Stick] Navigate through titles, tabs, and filter options\n" +
                               "• [A Button] Select title / Enter tab content / Start download\n" +
                               "• [B Button] Return to bottom tab bar\n" +
                               "• [X Button] Quick Decrypt (.cia) or Compress (.zcci) on Library item\n" +
                               "• [L1 / R1] Cycle format filters (Library) or Categories (Browse)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}
