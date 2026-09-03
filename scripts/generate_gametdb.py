#!/usr/bin/env python3
"""
Generates an optimized SQLite database (gametdb.db) by merging:
1. GameTDB (3dstdb.xml): synopses, developers, ratings, genres, players, release dates
2. 3DS Releases (3dsreleases.xml): exact 16-character Title IDs, firmware, trimmed sizes, languages
"""

import xml.etree.ElementTree as ET
import sqlite3
import os
import sys
import time

def generate_gametdb_db(gametdb_xml: str, releases_xml: str, db_path: str):
    if not os.path.exists(gametdb_xml):
        print(f"Error: GameTDB XML file not found at {gametdb_xml}", file=sys.stderr)
        sys.exit(1)

    if os.path.exists(db_path):
        os.remove(db_path)

    # 1. Parse 3dsreleases.xml if available
    releases_by_code = {}
    releases_by_name = {}
    all_releases = []

    if os.path.exists(releases_xml):
        print(f"[*] Parsing 3DS Releases from {releases_xml}...")
        t0 = time.time()
        rel_tree = ET.parse(releases_xml)
        for r in rel_tree.getroot().findall("release"):
            serial = (r.findtext("serial") or "").strip()
            code = serial.replace("CTR-P-", "").replace("CTR-N-", "").replace("CTR-", "").strip()[:4]
            tid = (r.findtext("titleid") or "").strip()
            name = (r.findtext("name") or "").strip()
            firmware = (r.findtext("firmware") or "").strip()
            languages = (r.findtext("languages") or "").strip()
            trimmed_size = int(r.findtext("trimmedsize") or 0)
            card = (r.findtext("card") or "").strip()
            publisher = (r.findtext("publisher") or "").strip()
            region = (r.findtext("region") or "").strip()

            rel_data = {
                "serial": serial,
                "code": code,
                "title_id": tid,
                "name": name,
                "firmware": firmware,
                "languages": languages,
                "trimmed_size": trimmed_size,
                "card": card,
                "publisher": publisher,
                "region": region
            }
            all_releases.append(rel_data)

            if code and code != "N/A" and len(code) == 4 and code not in releases_by_code:
                releases_by_code[code] = rel_data
            if name and name.lower() not in releases_by_name:
                releases_by_name[name.lower()] = rel_data

        print(f"[✓] Loaded {len(all_releases)} releases ({len(releases_by_code)} mapped by code) in {time.time() - t0:.2f}s")
    else:
        print(f"[!] 3DS Releases XML not found at {releases_xml}, continuing with GameTDB only.")

    # 2. Parse GameTDB 3dstdb.xml
    print(f"[*] Parsing GameTDB from {gametdb_xml}...")
    t0 = time.time()
    tree = ET.parse(gametdb_xml)
    root = tree.getroot()
    print(f"[✓] Parsed GameTDB XML in {time.time() - t0:.2f}s. Merging metadata...")

    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    cur.execute("""
    CREATE TABLE games (
        id TEXT PRIMARY KEY,
        title_id TEXT,
        name TEXT,
        title TEXT,
        synopsis TEXT,
        developer TEXT,
        publisher TEXT,
        release_date TEXT,
        genre TEXT,
        rating_type TEXT,
        rating_val TEXT,
        rating_desc TEXT,
        players TEXT,
        wifi_features TEXT,
        languages TEXT,
        region TEXT,
        firmware TEXT,
        trimmed_size INTEGER,
        card TEXT
    )
    """)

    # Build title-to-synopsis cache for regional fallbacks
    title_synopsis_cache = {}
    for game in root.findall("game"):
        en_syn = ""
        any_syn = ""
        en_title = ""
        first_title = ""
        for loc in game.findall("locale"):
            title = (loc.findtext("title") or "").strip()
            syn = (loc.findtext("synopsis") or "").strip()
            if loc.get("lang") == "EN":
                en_title = title
                en_syn = syn
            if not first_title and title:
                first_title = title
            if not any_syn and syn:
                any_syn = syn

        canonical_title = en_title or first_title
        best_syn = en_syn or any_syn
        if canonical_title and best_syn and canonical_title.lower() not in title_synopsis_cache:
            title_synopsis_cache[canonical_title.lower()] = best_syn

    rows = []
    matched_release_ids = set()
    inserted_ids = set()

    for game in root.findall("game"):
        gid = (game.findtext("id") or "").strip()
        if not gid or gid in inserted_ids:
            continue
        inserted_ids.add(gid)

        raw_name = (game.get("name") or "").strip()
        en_title = ""
        en_syn = ""
        first_title = ""
        first_syn = ""

        for loc in game.findall("locale"):
            t = (loc.findtext("title") or "").strip()
            s = (loc.findtext("synopsis") or "").strip()
            if loc.get("lang") == "EN":
                en_title = t
                en_syn = s
            if not first_title and t:
                first_title = t
            if not first_syn and s:
                first_syn = s

        title = en_title or first_title or raw_name
        synopsis = en_syn or first_syn

        if not synopsis and title.lower() in title_synopsis_cache:
            synopsis = title_synopsis_cache[title.lower()]

        developer = (game.findtext("developer") or "").strip()
        publisher = (game.findtext("publisher") or "").strip()

        date_el = game.find("date")
        release_date = ""
        if date_el is not None and date_el.get("year"):
            y = date_el.get("year", "")
            m = (date_el.get("month", "") or "").zfill(2)
            d = (date_el.get("day", "") or "").zfill(2)
            if y and m and d and m != "00" and d != "00":
                release_date = f"{y}-{m}-{d}"
            elif y and m and m != "00":
                release_date = f"{y}-{m}"
            else:
                release_date = y

        genre = (game.findtext("genre") or "").strip()

        rating_el = game.find("rating")
        rating_type = ""
        rating_val = ""
        rating_desc = ""
        if rating_el is not None:
            rating_type = (rating_el.get("type") or "").strip()
            rating_val = (rating_el.get("value") or "").strip()
            desc_items = [d.text.strip() for d in rating_el.findall("descriptor") if d.text and d.text.strip()]
            rating_desc = ", ".join(desc_items)

        input_el = game.find("input")
        players = (input_el.get("players") or "").strip() if input_el is not None else ""

        wifi_el = game.find("wi-fi")
        wifi_features = ""
        if wifi_el is not None:
            feat_items = [f.text.strip() for f in wifi_el.findall("feature") if f.text and f.text.strip()]
            wifi_features = ", ".join(feat_items)

        languages = (game.findtext("languages") or "").strip()
        region = (game.findtext("region") or "").strip()

        # Match with 3dsreleases
        rel = releases_by_code.get(gid) or releases_by_name.get(title.lower()) or releases_by_name.get(raw_name.lower())
        title_id = ""
        firmware = ""
        trimmed_size = 0
        card = ""

        if rel:
            title_id = rel.get("title_id", "")
            firmware = rel.get("firmware", "")
            trimmed_size = rel.get("trimmed_size", 0)
            card = rel.get("card", "")
            if not languages and rel.get("languages"):
                languages = rel.get("languages", "")
            if not publisher and rel.get("publisher"):
                publisher = rel.get("publisher", "")
            matched_release_ids.add(rel["title_id"])

        rows.append((
            gid,
            title_id,
            raw_name,
            title,
            synopsis,
            developer,
            publisher,
            release_date,
            genre,
            rating_type,
            rating_val,
            rating_desc,
            players,
            wifi_features,
            languages,
            region,
            firmware,
            trimmed_size,
            card
        ))

    # Append any remaining releases from 3dsreleases.xml that weren't in GameTDB
    unmatched_releases = 0
    for rel in all_releases:
        tid = rel.get("title_id", "")
        code = rel.get("code", "")
        entry_id = code if (code and code != "N/A" and code not in inserted_ids) else tid
        if entry_id and entry_id not in inserted_ids and tid not in matched_release_ids:
            inserted_ids.add(entry_id)
            unmatched_releases += 1
            rows.append((
                entry_id,
                tid,
                rel.get("name", ""),
                rel.get("name", ""),
                "", # synopsis
                "", # developer
                rel.get("publisher", ""),
                "", # release_date
                "", # genre
                "", # rating_type
                "", # rating_val
                "", # rating_desc
                "", # players
                "", # wifi_features
                rel.get("languages", ""),
                rel.get("region", ""),
                rel.get("firmware", ""),
                rel.get("trimmed_size", 0),
                rel.get("card", "")
            ))

    print(f"[*] Inserting {len(rows)} merged records into SQLite ({unmatched_releases} additional scene releases)...")
    cur.executemany("""
    INSERT INTO games VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, rows)

    # Indices for instant lookups
    print("[*] Creating indices on (id, title_id, title, name)...")
    cur.execute("CREATE INDEX idx_games_title_id ON games(title_id)")
    cur.execute("CREATE INDEX idx_games_title ON games(title COLLATE NOCASE)")
    cur.execute("CREATE INDEX idx_games_name ON games(name COLLATE NOCASE)")

    conn.commit()
    cur.execute("VACUUM")
    conn.close()

    db_size = os.path.getsize(db_path)
    print(f"[✓] Generated {db_path} successfully!")
    print(f"    Total records: {len(rows)}")
    print(f"    Database size: {db_size / (1024 * 1024):.2f} MB ({db_size} bytes)")

if __name__ == "__main__":
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    gametdb_xml = os.path.join(repo_root, "app", "src", "main", "assets", "3dstdb.xml")
    releases_xml = os.path.join(repo_root, "scripts", "3dsreleases.xml")
    default_db = os.path.join(repo_root, "app", "src", "main", "assets", "gametdb.db")

    generate_gametdb_db(gametdb_xml, releases_xml, default_db)
