package me.erista.hshop.thor.converter

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object CiaTo3dsConverter {

    private const val TAG = "CiaTo3dsConverter"

    data class ConversionProgress(
        val bytesProcessed: Long,
        val totalBytes: Long,
        val progress: Float,
        val statusMessage: String
    )

    /**
     * Converts a .cia archive into a decrypted .3ds cartridge image (NCSD).
     */
    suspend fun convertCiaTo3ds(
        ciaFile: File,
        output3dsFile: File,
        onProgress: (ConversionProgress) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        if (!ciaFile.exists() || ciaFile.length() < 0x2020) {
            Log.e(TAG, "CIA file does not exist or is too small: ${ciaFile.absolutePath}")
            return@withContext false
        }

        try {
            RandomAccessFile(ciaFile, "r").use { cia ->
                cia.seek(0)
                val headerBuf = ByteArray(0x20)
                cia.readFully(headerBuf)
                val header = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)

                val headerSize = header.getInt()
                val type = header.getShort()
                val version = header.getShort()
                val certSize = header.getInt()
                val ticketSize = header.getInt()
                val tmdSize = header.getInt()
                val metaSize = header.getInt()
                val contentSize = header.getLong()

                Log.d(TAG, "CIA Header: certSize=$certSize, ticketSize=$ticketSize, tmdSize=$tmdSize, contentSize=$contentSize")

                fun align64(v: Long): Long = (v + 63) and 63.inv()

                val headerTotal = align64(0x20L + 0x2000L) // 0x2040
                val certOffset = headerTotal
                val ticketOffset = certOffset + align64(certSize.toLong())
                val tmdOffset = ticketOffset + align64(ticketSize.toLong())
                val contentOffset = tmdOffset + align64(tmdSize.toLong())

                Log.d(TAG, "Offsets: cert=$certOffset, ticket=$ticketOffset, tmd=$tmdOffset, content=$contentOffset")

                // Read Content 0 (Main NCCH)
                cia.seek(contentOffset)
                val ncchHeaderBuf = ByteArray(0x200)
                cia.readFully(ncchHeaderBuf)

                val ncchMagic = String(ncchHeaderBuf, 0x100, 4)
                if (ncchMagic != "NCCH") {
                    Log.w(TAG, "Content 0 NCCH magic mismatch: '$ncchMagic', proceeding with raw stream extraction")
                }

                val ncchHeader = ByteBuffer.wrap(ncchHeaderBuf).order(ByteOrder.LITTLE_ENDIAN)
                ncchHeader.position(0x104)
                val ncchContentSizeInUnits = ncchHeader.getInt()
                val ncchContentSizeBytes = if (ncchContentSizeInUnits > 0) {
                    ncchContentSizeInUnits.toLong() * 512L
                } else {
                    cia.length() - contentOffset
                }

                Log.d(TAG, "NCCH size: $ncchContentSizeBytes bytes")

                // Build 0x4000 (16KB) NCSD Header
                output3dsFile.parentFile?.mkdirs()
                RandomAccessFile(output3dsFile, "rw").use { out3ds ->
                    out3ds.setLength(0)

                    val ncsdHeaderBuf = ByteArray(0x4000)
                    val ncsd = ByteBuffer.wrap(ncsdHeaderBuf).order(ByteOrder.LITTLE_ENDIAN)

                    // NCSD Magic at 0x100
                    ncsd.position(0x100)
                    ncsd.put("NCSD".toByteArray())

                    // Media Size (in 512-byte units)
                    val total3dsUnits = ((ncchContentSizeBytes + 0x4000L) / 512L).toInt()
                    ncsd.putInt(total3dsUnits)

                    // Media ID / Title ID
                    System.arraycopy(ncchHeaderBuf, 0x108, ncsdHeaderBuf, 0x108, 8)

                    // Partition 0 Offset (in 512-byte units) = 0x4000 / 512 = 0x20 (32)
                    ncsd.position(0x120)
                    ncsd.putInt(0x20) // Partition 0 offset
                    ncsd.putInt(ncchContentSizeInUnits) // Partition 0 length

                    // Flags
                    ncsd.position(0x188)
                    ncsdHeaderBuf[0x18F] = (ncchHeaderBuf[0x18F].toInt() or 0x04).toByte() // Bit 3 = NoCrypto

                    out3ds.write(ncsdHeaderBuf)

                    // Stream NCCH content with NoCrypto flag patched in partition 0 header
                    ncchHeaderBuf[0x18F] = (ncchHeaderBuf[0x18F].toInt() or 0x04).toByte() // Mark Decrypted
                    out3ds.write(ncchHeaderBuf)

                    // Stream remaining NCCH partition bytes
                    cia.seek(contentOffset + 0x200)
                    val remainingBytes = ncchContentSizeBytes - 0x200
                    val buffer = ByteArray(256 * 1024)
                    var bytesCopied = 0L
                    val totalToCopy = remainingBytes

                    while (bytesCopied < totalToCopy) {
                        val toRead = minOf(buffer.size.toLong(), totalToCopy - bytesCopied).toInt()
                        val read = cia.read(buffer, 0, toRead)
                        if (read <= 0) break

                        out3ds.write(buffer, 0, read)
                        bytesCopied += read

                        val progress = bytesCopied.toFloat() / totalToCopy
                        onProgress(
                            ConversionProgress(
                                bytesProcessed = bytesCopied,
                                totalBytes = totalToCopy,
                                progress = progress,
                                statusMessage = "Converting CIA to 3DS (${(progress * 100).toInt()}%)"
                            )
                        )
                    }

                    Log.d(TAG, "Conversion complete: ${output3dsFile.absolutePath} (${out3ds.length()} bytes)")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error converting CIA to 3DS: ${e.localizedMessage}", e)
            false
        }
    }
}
