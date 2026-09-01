package me.erista.hshop.thor.download

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.erista.hshop.thor.data.DownloadStatus
import me.erista.hshop.thor.data.DownloadTask
import me.erista.hshop.thor.data.SettingsRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ThorDownloadManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun enqueueDownload(
        id: String,
        titleName: String,
        productCode: String,
        downloadUrl: String,
        targetDirectory: String,
        skipAutoConvert: Boolean = false
    ) {
        android.util.Log.d("ThorDownloadManager", "enqueueDownload: title='$titleName', url='$downloadUrl', dir='$targetDirectory', skipConvert=$skipAutoConvert")
        val targetDir = File(targetDirectory)
        if (!targetDir.exists()) {
            val created = targetDir.mkdirs()
            android.util.Log.d("ThorDownloadManager", "Target dir created: $created ($targetDirectory)")
        }

        val sanitizedTitle = titleName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        val fileName = if (productCode.isNotEmpty()) "$sanitizedTitle [$productCode].cia" else "$sanitizedTitle.cia"
        val targetFile = File(targetDir, fileName)

        val task = DownloadTask(
            id = id,
            titleName = titleName,
            productCode = productCode,
            downloadUrl = downloadUrl,
            targetFilePath = targetFile.absolutePath,
            status = DownloadStatus.QUEUED
        )

        _tasks.value = _tasks.value.filter { it.id != id } + task
        startDownload(task, skipAutoConvert)
    }

    private fun startDownload(task: DownloadTask, skipAutoConvert: Boolean = false) {
        val job = scope.launch {
            android.util.Log.d("ThorDownloadManager", "Starting download: ${task.downloadUrl} -> ${task.targetFilePath}")
            updateTask(task.id) { it.copy(status = DownloadStatus.CONNECTING) }
            val file = File(task.targetFilePath)
            file.parentFile?.mkdirs()

            try {
                val request = Request.Builder()
                    .url(task.downloadUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://hshop.erista.me/")
                    .build()

                val response = client.newCall(request).execute()
                android.util.Log.d("ThorDownloadManager", "HTTP response: code=${response.code}, headers=${response.headers}")
                if (!response.isSuccessful) {
                    updateTask(task.id) {
                        it.copy(status = DownloadStatus.FAILED, errorMessage = "HTTP error: ${response.code}")
                    }
                    return@launch
                }

                val body = response.body ?: run {
                    updateTask(task.id) {
                        it.copy(status = DownloadStatus.FAILED, errorMessage = "Empty response body")
                    }
                    return@launch
                }

                val contentLength = body.contentLength()
                updateTask(task.id) {
                    it.copy(
                        status = DownloadStatus.DOWNLOADING,
                        totalBytes = if (contentLength > 0) contentLength else 0L
                    )
                }

                var bytesRead = 0L
                var lastTime = System.currentTimeMillis()
                var lastBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int

                        while (input.read(buffer).also { read = it } != -1) {
                            if (!isActive) {
                                updateTask(task.id) { it.copy(status = DownloadStatus.CANCELLED) }
                                return@launch
                            }

                            output.write(buffer, 0, read)
                            bytesRead += read

                            val currentTime = System.currentTimeMillis()
                            val timeDelta = currentTime - lastTime
                            if (timeDelta >= 500) {
                                val bytesDelta = bytesRead - lastBytes
                                val speed = if (timeDelta > 0) (bytesDelta * 1000) / timeDelta else 0L
                                val progress = if (contentLength > 0) bytesRead.toFloat() / contentLength else 0f

                                updateTask(task.id) {
                                    it.copy(
                                        bytesDownloaded = bytesRead,
                                        speedBytesPerSec = speed,
                                        progress = progress
                                    )
                                }

                                lastTime = currentTime
                                lastBytes = bytesRead
                            }
                        }
                    }
                }

                updateTask(task.id) {
                    it.copy(
                        bytesDownloaded = bytesRead,
                        progress = 1.0f,
                        speedBytesPerSec = 0L
                    )
                }

                // Check auto-convert setting
                val settingsRepo = SettingsRepository(context)
                val currentSettings = settingsRepo.settings.value

                if (!skipAutoConvert && currentSettings.autoConvertTo3ds && file.name.endsWith(".cia", ignoreCase = true)) {
                    updateTask(task.id) {
                        it.copy(
                            status = DownloadStatus.CONVERTING,
                            progress = 0f,
                            errorMessage = "Starting decryption..."
                        )
                    }

                    val outputCciFile = File(file.parentFile, file.nameWithoutExtension + ".cci")
                    val cia3ds = io.github.cia3ds.jni.Cia3ds(context)

                    val converted = cia3ds.decryptCiaTo3ds(
                        ciaFile = file,
                        outputCciFile = outputCciFile,
                        deleteSource = currentSettings.autoRemoveDownloadedCia,
                        onProgress = { progressPercent, message ->
                            updateTask(task.id) {
                                it.copy(
                                    progress = progressPercent / 100f,
                                    errorMessage = message
                                )
                            }
                        }
                    )

                    if (converted) {
                        var finalFilePath = outputCciFile.absolutePath

                        if (currentSettings.autoCompressToZcci) {
                            updateTask(task.id) {
                                it.copy(
                                    status = DownloadStatus.CONVERTING,
                                    progress = 0f,
                                    errorMessage = "Compressing to .ZCCI..."
                                )
                            }

                            val outputZcciFile = File(outputCciFile.parentFile, outputCciFile.nameWithoutExtension + ".zcci")
                            val compressed = me.erista.hshop.thor.compressor.ZcciCompressor.compressCciToZcci(
                                inputFile = outputCciFile,
                                outputFile = outputZcciFile,
                                onProgress = { progress, msg ->
                                    updateTask(task.id) {
                                        it.copy(
                                            progress = progress,
                                            errorMessage = msg
                                        )
                                    }
                                }
                            )

                            if (compressed) {
                                finalFilePath = outputZcciFile.absolutePath
                                outputCciFile.delete() // Remove uncompressed .cci to save storage
                            }
                        }

                        updateTask(task.id) {
                            it.copy(
                                status = DownloadStatus.COMPLETED,
                                convertedFilePath = finalFilePath,
                                progress = 1.0f,
                                errorMessage = null
                            )
                        }
                    } else {
                        // Fallback: keep source .cia
                        updateTask(task.id) {
                            it.copy(
                                status = DownloadStatus.COMPLETED,
                                progress = 1.0f,
                                errorMessage = null
                            )
                        }
                    }
                } else {
                    updateTask(task.id) {
                        it.copy(
                            status = DownloadStatus.COMPLETED,
                            progress = 1.0f,
                            errorMessage = null
                        )
                    }
                }

            } catch (e: CancellationException) {
                updateTask(task.id) { it.copy(status = DownloadStatus.CANCELLED) }
            } catch (e: Exception) {
                updateTask(task.id) {
                    it.copy(status = DownloadStatus.FAILED, errorMessage = e.localizedMessage)
                }
            } finally {
                activeJobs.remove(task.id)
            }
        }

        activeJobs[task.id] = job
    }

    fun decryptExistingCia(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        val file = File(task.targetFilePath)
        decryptCiaFile(file, taskId, task.titleName)
    }

    fun decryptCiaFile(file: File, id: String = file.nameWithoutExtension, titleName: String = file.nameWithoutExtension) {
        if (!file.exists() || !file.name.endsWith(".cia", ignoreCase = true)) return

        val existingTask = _tasks.value.find { it.id == id }
        val task = existingTask ?: DownloadTask(
            id = id,
            titleName = titleName,
            productCode = "",
            downloadUrl = "",
            targetFilePath = file.absolutePath,
            totalBytes = file.length(),
            bytesDownloaded = file.length(),
            status = DownloadStatus.CONVERTING,
            progress = 0f,
            errorMessage = "Starting decryption..."
        )

        if (existingTask == null) {
            _tasks.value = _tasks.value + task
        } else {
            updateTask(id) {
                it.copy(
                    status = DownloadStatus.CONVERTING,
                    progress = 0f,
                    errorMessage = "Starting decryption..."
                )
            }
        }

        scope.launch {
            val settingsRepo = SettingsRepository(context)
            val currentSettings = settingsRepo.settings.value
            val outputCciFile = File(file.parentFile, file.nameWithoutExtension + ".cci")
            val cia3ds = io.github.cia3ds.jni.Cia3ds(context)

            val converted = cia3ds.decryptCiaTo3ds(
                ciaFile = file,
                outputCciFile = outputCciFile,
                deleteSource = currentSettings.autoRemoveDownloadedCia,
                onProgress = { progressPercent, message ->
                    updateTask(id) {
                        it.copy(
                            progress = progressPercent / 100f,
                            errorMessage = message
                        )
                    }
                }
            )

            if (converted) {
                updateTask(id) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        convertedFilePath = outputCciFile.absolutePath,
                        progress = 1.0f,
                        errorMessage = null
                    )
                }
            } else {
                updateTask(id) {
                    it.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 1.0f,
                        errorMessage = "Decryption finished"
                    )
                }
            }
        }
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        updateTask(id) { it.copy(status = DownloadStatus.CANCELLED) }
    }

    fun clearCompleted() {
        _tasks.value = _tasks.value.filter {
            it.status != DownloadStatus.COMPLETED && it.status != DownloadStatus.CANCELLED
        }
    }

    private fun updateTask(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) transform(it) else it
        }
    }
}
