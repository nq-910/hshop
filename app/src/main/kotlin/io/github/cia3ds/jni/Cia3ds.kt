package io.github.cia3ds.jni

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class Cia3ds(private val context: Context) {

    companion object {
        private const val TAG = "Cia3dsJni"

        init {
            try {
                System.loadLibrary("cia3ds")
                Log.d(TAG, "libcia3ds.so loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load libcia3ds.so: ${e.localizedMessage}")
            }
        }
    }

    private fun ensureSeedDb(): File {
        val target = File(context.filesDir, "seeddb.bin")
        if (!target.exists() || target.length() == 0L) {
            try {
                context.assets.open("seeddb.bin").use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract seeddb.bin from assets: ${e.localizedMessage}")
            }
        }
        return target
    }

    external fun nativeVersion(): String

    external fun nativeCancel()

    external fun nativeDecryptCia(
        inFd: Int,
        outFd: Int,
        seedDbPath: String,
        tmpDir: String,
        originalName: String,
        wantCci: Boolean,
        progressCallback: NativeProgressCallback?,
        logCallback: NativeLogCallback?,
        seedFetcher: NativeSeedFetcherCallback?
    ): Int

    suspend fun decryptCiaTo3ds(
        ciaFile: File,
        outputCciFile: File,
        deleteSource: Boolean = false,
        onProgress: (progress: Int, message: String) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (!ciaFile.exists()) {
            Log.e(TAG, "Source CIA not found: ${ciaFile.absolutePath}")
            return@withContext false
        }

        outputCciFile.parentFile?.mkdirs()
        if (outputCciFile.exists()) {
            outputCciFile.delete()
        }

        val seedDb = ensureSeedDb()
        val seedDbPath = if (seedDb.exists()) seedDb.absolutePath else ""
        val tmpWorkDir = File(context.cacheDir, "cia3ds-work")

        // Pre-clean any stale work left by a previous crash before we start,
        // so we never have two extractions' worth of data on disk at once.
        if (tmpWorkDir.exists()) {
            tmpWorkDir.deleteRecursively()
            Log.d(TAG, "Pre-cleaned stale work dir: ${tmpWorkDir.absolutePath}")
        }
        tmpWorkDir.mkdirs()
        val tmpDir = tmpWorkDir.absolutePath

        // Pre-flight check: Internal cache storage for decryption scratch space
        val cacheUsable = context.cacheDir.usableSpace
        val estimatedCacheNeeded = ciaFile.length() + (30L * 1024 * 1024)
        if (cacheUsable in 1 until estimatedCacheNeeded) {
            Log.e(TAG, "Not enough internal cache storage for decryption: $cacheUsable bytes free, needs ~$estimatedCacheNeeded bytes")
            throw java.io.IOException("Not enough internal storage for decryption cache (ENOSPC). ${me.erista.hshop.thor.util.StorageUtils.formatSize(cacheUsable)} free, needs ~${me.erista.hshop.thor.util.StorageUtils.formatSize(estimatedCacheNeeded)}.")
        }

        // Pre-flight check: Target drive storage for output .CCI
        val targetUsable = outputCciFile.parentFile?.usableSpace ?: 0L
        val estimatedOutputNeeded = (ciaFile.length() * 1.05).toLong() + (50L * 1024 * 1024)
        if (targetUsable in 1 until estimatedOutputNeeded) {
            Log.e(TAG, "Not enough target drive storage for output CCI: $targetUsable bytes free, needs ~$estimatedOutputNeeded bytes")
            throw java.io.IOException("Not enough storage on target drive (ENOSPC). ${me.erista.hshop.thor.util.StorageUtils.formatSize(targetUsable)} free, needs ~${me.erista.hshop.thor.util.StorageUtils.formatSize(estimatedOutputNeeded)}.")
        }

        var inPfd: ParcelFileDescriptor? = null
        var outPfd: ParcelFileDescriptor? = null

        try {
            inPfd = ParcelFileDescriptor.open(ciaFile, ParcelFileDescriptor.MODE_READ_ONLY)
            outPfd = ParcelFileDescriptor.open(outputCciFile, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE)

            Log.d(TAG, "Calling nativeDecryptCia: inFd=${inPfd.fd}, outFd=${outPfd.fd}, tmpDir=$tmpDir, name=${ciaFile.name}")

            val fetcher = io.github.cia3ds.seed.SeedFetcher(context)

            val result = nativeDecryptCia(
                inFd = inPfd.fd,
                outFd = outPfd.fd,
                seedDbPath = seedDbPath,
                tmpDir = tmpDir,
                originalName = ciaFile.name,
                wantCci = true,
                progressCallback = object : NativeProgressCallback {
                    override fun onProgress(progress: Int, message: String) {
                        onProgress(progress, message)
                    }
                },
                logCallback = object : NativeLogCallback {
                    override fun onLine(line: String) {
                        Log.d(TAG, "[Native] $line")
                    }
                },
                seedFetcher = object : NativeSeedFetcherCallback {
                    override fun onFetch(titleId: String): ByteArray? {
                        return kotlinx.coroutines.runBlocking {
                            fetcher.fetch(titleId) { line ->
                                Log.d(TAG, "[SeedFetcher] $line")
                            }
                        }
                    }
                }
            )

            Log.d(TAG, "nativeDecryptCia finished with code: $result, output size: ${outputCciFile.length()} bytes")
            val success = result == 0 && outputCciFile.exists() && outputCciFile.length() > 0
            if (success && deleteSource) {
                ciaFile.delete()
            } else if (!success && outputCciFile.exists()) {
                outputCciFile.delete() // Clean up partial/failed output file
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Exception during nativeDecryptCia: ${e.localizedMessage}", e)
            if (outputCciFile.exists()) {
                outputCciFile.delete()
            }
            if (e is java.io.IOException) throw e
            false
        } finally {
            try { inPfd?.close() } catch (_: Exception) {}
            try { outPfd?.close() } catch (_: Exception) {}
            // Clean up intermediate work files to free cache storage
            tmpWorkDir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}
