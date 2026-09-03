# 🎮 Metadata & Box Art Pipeline

This document details the metadata unification, offline SQLite schema, Title ID resolution precedence, and multi-tier box art CDN architecture implemented in **hShop Thor**.

---

## 1. Metadata Sources & Unification

To provide complete, offline, zero-latency metadata and high-fidelity box art on the AYN Thor dual displays, hShop Thor combines two comprehensive databases:

1. **GameTDB (3DS Dataset)**: Rich community metadata including synopses, genres, developer, publisher, release dates, player counts, rating badges, and high-res cover scans.
2. **3DS Scene Releases Database (`3dsreleases.xml`)**: Complete release records with exact 16-character hexadecimal Title IDs (e.g. `0004000000037500`), minimum console firmware requirements (e.g. `FW 11.4.0E`), trimmed ROM byte sizes, card types, and release groups.

### Merged Dataset Statistics
* **Total Catalogued Titles**: 4,889 unique titles.
* **Indexed Title IDs**: 2,672+ titles enriched with exact 16-character Title IDs.
* **Firmware Constraints**: 2,672+ entries enriched with minimum system firmware.
* **Unified Master XML**: Saved as [app/src/main/assets/3dstdb.xml](file:///home/hpnquoc/R/projects/personal_project/thor/hshop/app/src/main/assets/3dstdb.xml) (13 MB) and released on [thor-3ds-db](https://github.com/nq-910/thor-3ds-db) as `3dsdb.xml` / `3dsdb.xml.gz`.

---

## 2. Offline SQLite Architecture (`gametdb.db`)

Rather than parsing a 13 MB XML document into memory at runtime, `scripts/generate_gametdb.py` compiles the XML into a lean, highly optimized SQLite database stored at [app/src/main/assets/gametdb.db](file:///home/hpnquoc/R/projects/personal_project/thor/hshop/app/src/main/assets/gametdb.db) (~3.8 MB).

### Database Schema
```sql
CREATE TABLE games (
    id TEXT PRIMARY KEY,          -- 4-6 char GameTDB ID (e.g. CTR-P-EKJA / EKJA)
    title_id TEXT,               -- 16-character hex Title ID (e.g. 0004000000055D00)
    name TEXT NOT NULL,          -- Title Name
    developer TEXT,              -- Developer studio
    publisher TEXT,              -- Publishing entity
    date TEXT,                   -- Release date (YYYY-MM-DD)
    genre TEXT,                  -- Comma-separated genres
    rating_type TEXT,            -- CERO, ESRB, PEGI, etc.
    rating_value TEXT,           -- Rating age / classification
    players INTEGER,             -- Supported player count
    synopsis TEXT,               -- Extended game description
    firmware TEXT,               -- Minimum firmware requirement (e.g. 6.1.0E)
    trimmed_size INTEGER,        -- Exact trimmed ROM size in bytes
    card TEXT                    -- Card type / cartridge generation
);

CREATE INDEX idx_games_title_id ON games(title_id);
CREATE INDEX idx_games_name ON games(name);
```

### Resolution Precedence (`GameTdbRepository.kt`)
When a title is selected in the catalog or library, metadata resolution follows a strict 3-tier precedence hierarchy:

```mermaid
flowchart TD
    Start([Query Title Metadata]) --> CheckTID{Title ID provided?}
    CheckTID -- Yes --> QueryTID[Query idx_games_title_id]
    CheckTID -- No --> CheckPC
    QueryTID --> FoundTID{Found?}
    FoundTID -- Yes --> ReturnMeta([Return Exact Metadata & Box Art])
    FoundTID -- No --> CheckPC{Product Code provided?}
    CheckPC -- Yes --> QueryPC[Query games.id / Product Code]
    CheckPC -- No --> QueryName
    QueryPC --> FoundPC{Found?}
    FoundPC -- Yes --> ReturnMeta
    FoundPC -- No --> QueryName[Query Cleaned Title Name with LIKE fallback]
    QueryName --> End([Return Best-Effort Match or Null])
```

1. **Exact Title ID Lookup (`title_id`)**: Completely unambiguous. Avoids regional product code mismatches and identical game names across platforms.
2. **Product Code Lookup (`id`)**: Fallback matching `CTR-P-XXXX`, `CTR-N-XXXX`, or 4-character disc IDs.
3. **Normalized Title Search (`name`)**: Strips region tags (`(USA)`, `[EUR]`, `(En,Ja)`), edition suffixes, and punctuation for resilient fallback matching.

---

## 3. Multi-Tier Box Art CDN Pipeline

Cover artwork resolution is managed by `ArtworkResolver.kt` in `core-scraper`. To ensure zero broken images and low bandwidth consumption, artwork is fetched across a tiered fallback ladder:

```
[Level 1: Local Disk Cache]
    └── Coil Disk Cache (256 MB in cacheDir/image_cache)
[Level 2: High-Speed WebP CDN]
    └── https://cdn.jsdelivr.net/gh/nq-910/thor-3ds-db@main/covers/{gameId}.webp
[Level 3: Primary GameTDB Server]
    └── https://art.gametdb.com/3ds/coverM/{region}/{gameId}.jpg
[Level 4: High-Res GameTDB HQ Fallback]
    └── https://art.gametdb.com/3ds/coverHQ/{region}/{gameId}.jpg
```

### The `thor-3ds-db` Repository
Hosted at [https://github.com/nq-910/thor-3ds-db](https://github.com/nq-910/thor-3ds-db), this companion repository provides:
* **3,492 Optimized WebP Covers**: Losslessly compressed to ~50 KB per cover (down from 300+ KB JPEGs).
* **Global jsDelivr Edge Distribution**: Instant worldwide CDN loading with automatic Brotli/HTTP3 delivery.
* **Automated CI/CD Verification**: GitHub Actions workflow validates SQLite integrity, builds compressed XML `.gz` artifacts, and produces GitHub Releases automatically.

---

## 4. Dual-Screen Presentation Details

On the AYN Thor's top display (`1080x1920` scaled), the metadata is displayed via `TopScreenContent.kt`:
* **Dynamic Header Typography**: Automatically downscales title font size (`26sp` ➔ `23sp` ➔ `20sp`) for verbose localization titles (e.g. *Pokémon Mystery Dungeon: Gates to Infinity*).
* **Responsive Chip Flow**: `FlowRow` renders compact badges for:
  - Developer & Publisher (with `maxLines = 1` and `TextOverflow.Ellipsis`).
  - Release Date (`YYYY-MM-DD`).
  - Supported Player Count.
  - **Firmware Requirement Badge** (`FW <version>`, highlighted in primary accent).
* **Synopsis Modal**: Gamepads trigger synopsis expansion via **Button X**; users can read multi-paragraph local descriptions with full gamepad stick scrolling.
