package me.erista.hshop.thor.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.util.LruCache
import me.erista.hshop.model.GameTdbMetadata
import me.erista.hshop.scraper.ArtworkResolver
import java.io.File
import java.io.FileOutputStream

class GameTdbRepository(private val context: Context) {

    private val cache = LruCache<String, GameTdbMetadata>(256)
    private var db: SQLiteDatabase? = null

    init {
        initDatabase()
    }

    @Synchronized
    private fun initDatabase() {
        if (db != null && db?.isOpen == true) return
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.parentFile?.exists()!!) {
                dbFile.parentFile?.mkdirs()
            }

            // Check if we need to copy or overwrite from assets
            var needsCopy = !dbFile.exists() || dbFile.length() == 0L
            if (!needsCopy) {
                val assetSize = getAssetSize(DB_NAME)
                if (assetSize > 0 && assetSize != dbFile.length()) {
                    needsCopy = true
                }
            }

            if (needsCopy) {
                copyDatabaseFromAssets(dbFile)
            }

            if (dbFile.exists() && dbFile.length() > 0) {
                db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
                Log.i(TAG, "GameTDB database opened successfully from ${dbFile.path}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize GameTDB database", e)
        }
    }

    private fun getAssetSize(assetName: String): Long {
        return try {
            context.assets.openFd(assetName).use { it.length }
        } catch (_: Exception) {
            try {
                context.assets.open(assetName).use { it.available().toLong() }
            } catch (_: Exception) {
                -1L
            }
        }
    }

    private fun copyDatabaseFromAssets(destFile: File) {
        try {
            context.assets.open(DB_NAME).use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var length: Int
                    while (input.read(buffer).also { length = it } > 0) {
                        output.write(buffer, 0, length)
                    }
                    output.flush()
                }
            }
            Log.i(TAG, "GameTDB database extracted to ${destFile.path} (${destFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying GameTDB asset", e)
        }
    }

    /**
     * Primary entry point for metadata lookup:
     * 1. Query by Title ID (exact, foolproof match for 3DS titles)
     * 2. Query by Product Code / GameTDB ID (e.g. JB7E)
     * 3. Fallback to Title name match
     */
    fun findMetadata(
        titleId: String? = null,
        productCode: String? = null,
        fallbackTitle: String? = null
    ): GameTdbMetadata? {
        if (!titleId.isNullOrBlank()) {
            val meta = findMetadataByTitleId(titleId)
            if (meta != null) return meta
        }

        if (!productCode.isNullOrBlank()) {
            val gameId = ArtworkResolver.extractGameId(productCode)
            if (gameId != null) {
                val meta = findMetadataByGameId(gameId)
                if (meta != null) return meta
            }
        }

        if (!fallbackTitle.isNullOrBlank()) {
            return findMetadataByTitle(fallbackTitle)
        }

        return null
    }

    fun findMetadataByProductCode(productCode: String, fallbackTitle: String? = null): GameTdbMetadata? {
        return findMetadata(productCode = productCode, fallbackTitle = fallbackTitle)
    }

    fun findMetadataByTitleId(titleId: String): GameTdbMetadata? {
        val cleanTid = titleId.trim().uppercase()
        if (cleanTid.isEmpty()) return null

        synchronized(cache) {
            val cached = cache.get("tid:$cleanTid")
            if (cached != null) return cached
        }

        val database = db ?: run {
            initDatabase()
            db ?: return null
        }

        return try {
            database.rawQuery(
                "SELECT $COLUMNS FROM games WHERE title_id = ? LIMIT 1",
                arrayOf(cleanTid)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val meta = cursorToMetadata(cursor)
                    synchronized(cache) {
                        cache.put("tid:$cleanTid", meta)
                        if (meta.gameId.isNotEmpty()) cache.put(meta.gameId, meta)
                    }
                    meta
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying GameTDB for Title ID $cleanTid", e)
            null
        }
    }

    fun findMetadataByGameId(gameId: String): GameTdbMetadata? {
        val cleanId = gameId.trim().uppercase()
        if (cleanId.length != 4) return null

        synchronized(cache) {
            val cached = cache.get(cleanId)
            if (cached != null) return cached
        }

        val database = db ?: run {
            initDatabase()
            db ?: return null
        }

        return try {
            database.rawQuery(
                "SELECT $COLUMNS FROM games WHERE id = ? LIMIT 1",
                arrayOf(cleanId)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val meta = cursorToMetadata(cursor)
                    synchronized(cache) {
                        cache.put(cleanId, meta)
                        if (meta.titleId.isNotEmpty()) cache.put("tid:${meta.titleId}", meta)
                    }
                    meta
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying GameTDB for $cleanId", e)
            null
        }
    }

    fun findMetadataByTitle(title: String): GameTdbMetadata? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return null

        synchronized(cache) {
            val cached = cache.get("title:$cleanTitle")
            if (cached != null) return cached
        }

        val database = db ?: run {
            initDatabase()
            db ?: return null
        }

        return try {
            var result: GameTdbMetadata? = database.rawQuery(
                "SELECT $COLUMNS FROM games WHERE title = ? LIMIT 1",
                arrayOf(cleanTitle)
            ).use { cursor ->
                if (cursor.moveToFirst()) cursorToMetadata(cursor) else null
            }

            if (result == null) {
                result = database.rawQuery(
                    "SELECT $COLUMNS FROM games WHERE name LIKE ? LIMIT 1",
                    arrayOf("$cleanTitle%")
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursorToMetadata(cursor) else null
                }
            }

            if (result != null) {
                synchronized(cache) {
                    cache.put("title:$cleanTitle", result)
                    if (result.gameId.isNotEmpty()) cache.put(result.gameId, result)
                    if (result.titleId.isNotEmpty()) cache.put("tid:${result.titleId}", result)
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error querying GameTDB for title $cleanTitle", e)
            null
        }
    }

    private fun cursorToMetadata(cursor: Cursor): GameTdbMetadata {
        return GameTdbMetadata(
            gameId = cursor.getString(0) ?: "",
            titleId = cursor.getString(1) ?: "",
            name = cursor.getString(2) ?: "",
            title = cursor.getString(3) ?: "",
            synopsis = cursor.getString(4) ?: "",
            developer = cursor.getString(5) ?: "",
            publisher = cursor.getString(6) ?: "",
            releaseDate = cursor.getString(7) ?: "",
            genre = cursor.getString(8) ?: "",
            ratingType = cursor.getString(9) ?: "",
            ratingValue = cursor.getString(10) ?: "",
            ratingDescriptors = cursor.getString(11) ?: "",
            players = cursor.getString(12) ?: "",
            wifiFeatures = cursor.getString(13) ?: "",
            languages = cursor.getString(14) ?: "",
            region = cursor.getString(15) ?: "",
            firmware = cursor.getString(16) ?: "",
            trimmedSize = cursor.getLong(17),
            card = cursor.getString(18) ?: ""
        )
    }

    companion object {
        private const val TAG = "GameTdbRepository"
        private const val DB_NAME = "gametdb.db"
        private const val COLUMNS = "id, title_id, name, title, synopsis, developer, publisher, release_date, genre, rating_type, rating_val, rating_desc, players, wifi_features, languages, region, firmware, trimmed_size, card"
    }
}

