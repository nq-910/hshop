package me.erista.hshop.thor.compressor

import android.util.Log
import com.github.luben.zstd.Zstd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*

/**
 * Z3DS / ZCCI Compressor implementing the exact Azahar / AzaharPlus / Citra
 * seekable block-compressed format.
 */
object ZcciCompressor {

    private const val TAG = "ZcciCompressor"

    private const val Z3DS_MAGIC = "Z3DS"
    private const val Z3DS_VERSION: Byte = 1
    private const val HEADER_SIZE: Short = 0x20 // 32 bytes

    // Default frame size for CCI / 3DS cartridges: 256 KiB
    const val DEFAULT_FRAME_SIZE = 256 * 1024

    // Zstandard Seekable Format Magic Numbers
    private const val SKIPPABLE_MAGIC = 0x184D2A5E
    private const val SEEKABLE_MAGIC = 0x8F92EAB1.toInt()

    data class FrameEntry(
        val compressedSize: Int,
        val decompressedSize: Int
    )

    /**
     * Compresses a decrypted .CCI or .3DS ROM into .ZCCI format compatible with Azahar / AzaharPlus.
     */
    suspend fun compressCciToZcci(
        inputFile: File,
        outputFile: File,
        frameSize: Int = DEFAULT_FRAME_SIZE,
        compressionLevel: Int = 3,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        if (!inputFile.exists() || inputFile.length() == 0L) {
            Log.e(TAG, "Input file does not exist or is empty: ${inputFile.absolutePath}")
            return@withContext false
        }

        outputFile.parentFile?.mkdirs()
        val tempOutputFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
        if (tempOutputFile.exists()) tempOutputFile.delete()

        try {
            val totalBytes = inputFile.length()
            var processedBytes = 0L
            val frames = mutableListOf<FrameEntry>()

            // 1. Build Metadata block
            val metadataBytes = buildMetadataBlock(frameSize)
            val metadataAlignedSize = alignUp(metadataBytes.size, 0x10)

            FileInputStream(inputFile).use { fis ->
                FileOutputStream(tempOutputFile).use { fos ->
                    // 2. Write 32-byte Z3DS Header Placeholder
                    val headerBuffer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
                    headerBuffer.put(Z3DS_MAGIC.toByteArray(Charsets.US_ASCII)) // 0x00: magic "Z3DS"
                    headerBuffer.put("NCSD".toByteArray(Charsets.US_ASCII))     // 0x04: underlying_magic "NCSD"
                    headerBuffer.put(Z3DS_VERSION)                              // 0x08: version (1)
                    headerBuffer.put(0.toByte())                                // 0x09: reserved
                    headerBuffer.putShort(HEADER_SIZE)                          // 0x0A: header_size (0x20)
                    headerBuffer.putInt(metadataAlignedSize)                    // 0x0C: metadata_size
                    headerBuffer.putLong(0L)                                    // 0x10: compressed_size (placeholder)
                    headerBuffer.putLong(totalBytes)                            // 0x18: uncompressed_size
                    fos.write(headerBuffer.array())

                    // 3. Write Metadata block (aligned to 16 bytes)
                    if (metadataBytes.isNotEmpty()) {
                        fos.write(metadataBytes)
                        val padSize = metadataAlignedSize - metadataBytes.size
                        if (padSize > 0) {
                            fos.write(ByteArray(padSize))
                        }
                    }

                    // 4. Compress data frame-by-frame (256 KiB chunks)
                    val inputBuffer = ByteArray(frameSize)
                    var readBytes: Int
                    var compressedTotal = 0L

                    while (fis.read(inputBuffer).also { readBytes = it } != -1) {
                        if (!isActive) {
                            tempOutputFile.delete()
                            return@withContext false
                        }

                        val compressedChunk: ByteArray = if (readBytes == frameSize) {
                            Zstd.compress(inputBuffer, compressionLevel)
                        } else {
                            val slice = inputBuffer.copyOf(readBytes)
                            Zstd.compress(slice, compressionLevel)
                        }

                        fos.write(compressedChunk)
                        compressedTotal += compressedChunk.size

                        frames.add(
                            FrameEntry(
                                compressedSize = compressedChunk.size,
                                decompressedSize = readBytes
                            )
                        )

                        processedBytes += readBytes
                        val progress = processedBytes.toFloat() / totalBytes.toFloat()
                        val msg = "Compressing ZCCI: ${(progress * 100).toInt()}% (${frames.size} frames)"
                        onProgress(progress, msg)
                    }

                    // 5. Append Seek Table Skippable Frame at EOF
                    val seekTableSize = (frames.size * 8) + 9 // 8 bytes per frame entry + 9 bytes footer
                    val seekTableBuf = ByteBuffer.allocate(8 + seekTableSize).order(ByteOrder.LITTLE_ENDIAN)

                    seekTableBuf.putInt(SKIPPABLE_MAGIC)     // 0x184D2A5E
                    seekTableBuf.putInt(seekTableSize)        // Size of seek table payload

                    // Write each frame entry
                    for (frame in frames) {
                        seekTableBuf.putInt(frame.compressedSize)
                        seekTableBuf.putInt(frame.decompressedSize)
                    }

                    // Seek Table Footer (9 bytes)
                    seekTableBuf.putInt(frames.size)          // Number_Of_Frames (4 bytes)
                    seekTableBuf.put(0.toByte())              // Seek_Table_Descriptor (Checksum_Flag = 0) (1 byte)
                    seekTableBuf.putInt(SEEKABLE_MAGIC)       // Seekable_Magic_Number 0x8F92EAB1 (4 bytes)

                    fos.write(seekTableBuf.array())
                    compressedTotal += seekTableBuf.array().size

                    // 6. Update compressed_size in 0x20 Header
                    fos.channel.position(0x10)
                    val sizeUpdateBuf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                    sizeUpdateBuf.putLong(compressedTotal)
                    fos.write(sizeUpdateBuf.array())
                }
            }

            if (outputFile.exists()) outputFile.delete()
            tempOutputFile.renameTo(outputFile)

            val savedRatio = (1f - (outputFile.length().toFloat() / totalBytes.toFloat())) * 100f
            Log.d(TAG, "ZCCI compression complete! Saved ${String.format("%.1f", savedRatio)}% space -> ${outputFile.name}")
            onProgress(1.0f, "Compression complete (${String.format("%.1f", savedRatio)}% saved)")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing ZCCI: ${e.localizedMessage}", e)
            if (tempOutputFile.exists()) tempOutputFile.delete()
            return@withContext false
        }
    }

    private fun buildMetadataBlock(frameSize: Int): ByteArray {
        val out = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        out.put(Z3DS_VERSION) // version = 1

        fun writeItem(key: String, value: String) {
            val keyBytes = key.toByteArray(Charsets.US_ASCII)
            val valBytes = value.toByteArray(Charsets.US_ASCII)

            out.put(1.toByte())                       // type = TYPE_BINARY (1)
            out.put(keyBytes.size.toByte())           // name_len
            out.putShort(valBytes.size.toShort())     // data_len
            out.put(keyBytes)
            out.put(valBytes)
        }

        writeItem("compressor", "hShop Thor (Azahar Z3DS)")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        writeItem("date", dateFormat.format(Date()))
        writeItem("maxframesize", frameSize.toString())

        // End item (type = 0)
        out.put(0.toByte())
        out.put(0.toByte())
        out.putShort(0.toShort())

        val result = ByteArray(out.position())
        out.flip()
        out.get(result)
        return result
    }

    private fun alignUp(value: Int, alignment: Int): Int {
        val remainder = value % alignment
        return if (remainder == 0) value else value + (alignment - remainder)
    }
}
