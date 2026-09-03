#!/usr/bin/env python3
"""
Generates an optimized SQLite database (gametdb.db) from GameTDB 3dstdb.xml.
Includes all 4,785 titles with developers, publishers, release dates, genres,
ratings, player counts, localized synopses, and indexed search columns.
"""

import xml.etree.ElementTree as ET
import sqlite3
import os
import sys
import time

def generate_gametdb_db(xml_path: str, db_path: str):
    if not os.path.exists(xml_path):
        print(f"Error: XML file not found at {xml_path}", file=sys.stderr)
        sys.exit(1)

    if os.path.exists(db_path):
        os.remove(db_path)

    print(f"Parsing XML from {xml_path}...")
    t0 = time.time()
    tree = ET.parse(xml_path)
    root = tree.getroot()
    print(f"Parsed XML in {time.time() - t0:.2f}s. Extracting games...")

    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    # Create schema
    cur.execute("""
    CREATE TABLE games (
        id TEXT PRIMARY KEY,
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
        region TEXT
    )
    """)

    # First pass: collect game records and build a title-to-synopsis mapping
    # for titles that might have a synopsis under a sibling regional release
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
        if canonical_title and best_syn and canonical_title not in title_synopsis_cache:
            title_synopsis_cache[canonical_title.lower()] = best_syn

    rows = []
    for game in root.findall("game"):
        gid = (game.findtext("id") or "").strip()
        if not gid:
            continue

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

        # If synopsis is still empty, check if another regional release of the same title has it
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

        rows.append((
            gid,
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
            region
        ))

    cur.executemany("""
    INSERT INTO games VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, rows)

    # Create indices for instant lookups
    print("Creating indices...")
    cur.execute("CREATE INDEX idx_games_title ON games(title COLLATE NOCASE)")
    cur.execute("CREATE INDEX idx_games_name ON games(name COLLATE NOCASE)")

    # Optimize and vacuum
    conn.commit()
    cur.execute("VACUUM")
    conn.close()

    db_size = os.path.getsize(db_path)
    print(f"Generated {db_path} successfully!")
    print(f"Total game records: {len(rows)}")
    print(f"Database file size: {db_size / (1024 * 1024):.2f} MB ({db_size} bytes)")

if __name__ == "__main__":
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    default_xml = os.path.join(repo_root, "app", "src", "main", "assets", "3dstdb.xml")
    default_db = os.path.join(repo_root, "app", "src", "main", "assets", "gametdb.db")

    xml_file = sys.argv[1] if len(sys.argv) > 1 else default_xml
    db_file = sys.argv[2] if len(sys.argv) > 2 else default_db

    generate_gametdb_db(xml_file, db_file)
