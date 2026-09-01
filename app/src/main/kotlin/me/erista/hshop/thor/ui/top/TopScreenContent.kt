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
import me.erista.hshop.model.HShopTitleDetail
import me.erista.hshop.thor.data.DownloadStatus
import me.erista.hshop.thor.data.DownloadTask
import me.erista.hshop.thor.data.SettingsRepository
import me.erista.hshop.thor.ui.MainViewModel
import me.erista.hshop.thor.ui.theme.*
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
    val existingCia = remember(cleanTitle, downloadDir, downloadTasks) {
        cleanTitle?.let { File(downloadDir, "$it.cia").takeIf { f -> f.exists() && f.length() > 0 } }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (selectedDetail == null) {
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
                    existingCia = existingCia,
                    onDownloadClick = { viewModel.requestDownload(detail) },
                    onCancelClick = { currentTask?.let { viewModel.cancelDownload(it.id) } },
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
    existingCia: java.io.File?,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit,
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
                val coverUrl = detail.artwork?.highResCoverUrl
                    ?: detail.artwork?.primaryCoverUrl
                    ?: detail.artwork?.fallbackUrls?.firstOrNull()

                AsyncImage(
                    model = coverUrl,
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
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Cancel, contentDescription = "Cancel", tint = Color.Red)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { if (currentTask.totalBytes > 0) currentTask.progress else 0f },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
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
                            Text(
                                text = "Converting to .3DS...",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { currentTask.progress },
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            } else if ((currentTask != null && currentTask.status == DownloadStatus.COMPLETED) || existingCci != null || existingCia != null) {
                val gamePath = existingCci?.absolutePath ?: currentTask?.convertedFilePath ?: existingCia?.absolutePath ?: currentTask?.targetFilePath ?: ""
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { me.erista.hshop.thor.util.GameLauncher.launchGame(context, gamePath) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play Game", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    if (existingCci == null && (existingCia != null || currentTask?.convertedFilePath == null)) {
                        Button(
                            onClick = onDecryptClick,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Decrypt .CCI", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
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

            // Related Content (Updates & DLC)
            if (detail.relatedContent.isNotEmpty()) {
                Text(
                    text = "RELATED CONTENT (${detail.relatedContent.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(detail.relatedContent) { rel ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.width(220.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = rel.relationType.ifEmpty { "Related" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = rel.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Size: ${rel.sizeString} • v${rel.version}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
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
