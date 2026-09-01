package me.erista.hshop.cli

import kotlinx.coroutines.runBlocking
import me.erista.hshop.model.HShopCategory
import me.erista.hshop.scraper.ArtworkResolver
import me.erista.hshop.scraper.CitraRomsScraper
import me.erista.hshop.scraper.HShopScraper

fun main(args: Array<String>) = runBlocking {
    val scraper = HShopScraper()
    val citraScraper = CitraRomsScraper()

    println("==========================================================")
    println("        hShop Scraper & Artwork Engine (AYN Thor)         ")
    println("==========================================================")

    val command = args.getOrNull(0)?.lowercase() ?: "demo"

    when (command) {
        "categories" -> {
            println("\n[1] Fetching hShop categories and subcategories...")
            for (cat in HShopCategory.entries) {
                println("\n📂 Category: ${cat.displayName} (${cat.slug})")
                val subcats = scraper.fetchSubcategories(cat)
                subcats.forEach { sub ->
                    println("  └── 🏷️ ${sub.name.padEnd(20)} | Titles: ${sub.titleCount.toString().padStart(5)} | Size: ${sub.sizeString}")
                }
            }
        }

        "search" -> {
            val query = args.getOrNull(1) ?: "mario"
            println("\n[2] Searching hShop for: '$query'...")
            val result = scraper.searchTitles(query = query, count = 10)
            println("Found ${result.totalCount} results. Showing first ${result.titles.size}:")
            result.titles.forEachIndexed { index, t ->
                println("\n  #${index + 1} 🎮 ${t.name}")
                println("      ID: ${t.id} | TitleID: ${t.titleId} | ProductCode: ${t.productCode}")
                println("      Category: ${t.categorySlug} -> ${t.subcategorySlug} | Size: ${t.sizeString} | Version: ${t.version}")
                println("      Primary Cover: ${t.artwork?.primaryCoverUrl}")
                println("      HQ Cover:      ${t.artwork?.highResCoverUrl}")
                println("      Full Wrap:     ${t.artwork?.fullCoverWrapUrl}")
            }
        }

        "detail" -> {
            val id = args.getOrNull(1) ?: "632"
            println("\n[3] Fetching title detail for ID: $id...")
            val detail = scraper.fetchTitleDetail(id)
            println("Title: ${detail.name}")
            println("Path: ${detail.categorySlug} -> ${detail.subcategorySlug}")
            println("Title ID: ${detail.titleId} | Product Code: ${detail.productCode}")
            println("Version: ${detail.version} | Size: ${detail.sizeString}")
            println("Downloads: ${detail.downloadCount} | Added: ${detail.addedDate}")
            println("Seed: ${detail.seed}")
            println("SHA-256: ${detail.sha256}")
            println("Primary Cover: ${detail.artwork?.primaryCoverUrl}")
            println("HQ Cover:      ${detail.artwork?.highResCoverUrl}")
            println("Full Wrap:     ${detail.artwork?.fullCoverWrapUrl}")
            if (detail.relatedContent.isNotEmpty()) {
                println("Related Content (${detail.relatedContent.size}):")
                detail.relatedContent.forEach { r ->
                    println("  - [${r.relationType}] ${r.name} (ID: ${r.id}, Size: ${r.sizeString})")
                }
            }
        }

        "citra" -> {
            println("\n[4] Scraping ROM list from citra-emulator.com/3ds-roms...")
            val roms = citraScraper.fetchRomList()
            println("Found ${roms.size} curated ROMs / Hacks on Citra site:")
            roms.take(5).forEach { r ->
                println("  - 🕹️ ${r.title} | Image: ${r.imageUrl}")
            }
        }

        else -> {
            println("\n>>> Running Full Demo Workflow <<<\n")

            println("[Step 1] Searching for 'Mario' on hShop...")
            val marioSearch = scraper.searchTitles("mario", count = 3)
            marioSearch.titles.forEach { t ->
                println("  ⭐ ${t.name} (ID: ${t.id}, Code: ${t.productCode}, Size: ${t.sizeString})")
                println("     Cover: ${t.artwork?.primaryCoverUrl}")
                println("     HQ:    ${t.artwork?.highResCoverUrl}")
            }

            println("\n[Step 2] Fetching Title Detail for ID: 632 (Pokemon Ultra Sun)...")
            val ultraSun = scraper.fetchTitleDetail("632")
            println("  Name: ${ultraSun.name}")
            println("  Title ID: ${ultraSun.titleId}")
            println("  Product Code: ${ultraSun.productCode}")
            println("  Primary Cover: ${ultraSun.artwork?.primaryCoverUrl}")
            println("  HQ Cover: ${ultraSun.artwork?.highResCoverUrl}")

            println("\n✅ All scraper workflows executed successfully!")
        }
    }
}
