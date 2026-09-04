#!/usr/bin/env python3
"""
Merges GameTDB XML (3dstdb.xml) with 3DS Releases XML (3dsreleases.xml)
to produce a complete, unified 3DS XML metadata catalog with:
- Rich GameTDB synopses, developers, genres, ratings, player counts
- Exact 16-character Title IDs, minimum firmware, trimmed ROM sizes, and card types
- Proper 4-character ID resolution across CTR-, KTR-, and BBB- serials
- Prioritization of full retail/eShop releases over updates and demos
"""

import xml.etree.ElementTree as ET
import gzip
import os
import sys
import time

def extract_game_id(serial: str) -> str:
    """
    Extracts the 4-character Product Code ID from any 3DS serial.
    Handles standard (CTR-P-AGRP -> AGRP, CTR-AGRP -> AGRP),
    New 3DS (KTR-CAFP -> CAFP, KTR-BD3E -> BD3E),
    and Virtual Console (BBB-PKBL -> PKBL).
    """
    s = (serial or "").strip()
    if not s or s == "N/A":
        return ""
    if "-" in s:
        code = s.split("-")[-1].strip()[:4]
    else:
        code = s.strip()[:4]
    return code.upper() if len(code) == 4 and code.isalnum() else ""

def get_release_priority(release_type: str, title_id: str) -> int:
    """
    Computes sorting priority for matching:
    Standard full games (00040000) take precedence over demos (00040002) and updates (0004000E).
    Type 1 (Cartridge) and Type 4 (eShop) take precedence over Type 2 (Demo) and Type 3 (Update).
    Lower number = higher priority.
    """
    tid = (title_id or "").upper()
    if tid.startswith("00040000"):
        tid_prio = 0
    elif tid.startswith("00040002"):
        tid_prio = 20
    elif tid.startswith("0004000E"):
        tid_prio = 30
    else:
        tid_prio = 10

    t = release_type.strip() if release_type else "1"
    t_prio = 1 if t == "1" else (2 if t == "4" else (3 if t == "2" else (4 if t == "3" else 5)))
    return tid_prio + t_prio

def merge_xmls(gametdb_path: str, releases_path: str, output_xml_path: str, output_gz_path: str = None):
    t0 = time.time()
    print(f"[*] Parsing 3DS Releases from {releases_path}...")
    rel_tree = ET.parse(releases_path)
    releases = rel_tree.getroot().findall("release")

    code_to_rel = {}
    name_to_rel = {}
    all_releases = []

    for r in releases:
        serial = (r.findtext("serial") or "").strip()
        code = extract_game_id(serial)
        tid = (r.findtext("titleid") or "").strip()
        name = (r.findtext("name") or "").strip()
        rtype = (r.findtext("type") or "1").strip()
        prio = get_release_priority(rtype, tid)

        data = {
            "serial": serial,
            "code": code,
            "titleid": tid,
            "firmware": (r.findtext("firmware") or "").strip(),
            "trimmedsize": (r.findtext("trimmedsize") or "").strip(),
            "card": (r.findtext("card") or "").strip(),
            "publisher": (r.findtext("publisher") or "").strip(),
            "region": (r.findtext("region") or "").strip(),
            "languages": (r.findtext("languages") or "").strip(),
            "name": name,
            "type": rtype,
            "priority": prio
        }
        all_releases.append(data)

        if code:
            if code not in code_to_rel or prio < code_to_rel[code]["priority"]:
                code_to_rel[code] = data

        if name:
            clean_name = name.lower()
            if clean_name not in name_to_rel or prio < name_to_rel[clean_name]["priority"]:
                name_to_rel[clean_name] = data

    print(f"[✓] Loaded {len(all_releases)} releases ({len(code_to_rel)} unique 4-char codes indexed).")

    print(f"[*] Parsing GameTDB XML from {gametdb_path}...")
    tdb_tree = ET.parse(gametdb_path)
    root = tdb_tree.getroot()
    games = root.findall("game")

    matched_tids = set()
    matched_count = 0
    existing_ids = set()

    for g in games:
        gid = (g.findtext("id") or "").strip()
        if gid:
            existing_ids.add(gid)
        raw_name = (g.get("name") or "").strip()

        first_title = ""
        for loc in g.findall("locale"):
            t = (loc.findtext("title") or "").strip()
            if t and not first_title:
                first_title = t

        rel = code_to_rel.get(gid) or name_to_rel.get(first_title.lower()) or name_to_rel.get(raw_name.lower())
        if rel:
            matched_count += 1
            if rel["titleid"]:
                matched_tids.add(rel["titleid"])

            # Remove existing titleid/firmware/trimmedsize/card elements if present (for idempotency)
            for tag in ("titleid", "firmware", "trimmedsize", "card"):
                old_el = g.find(tag)
                if old_el is not None:
                    g.remove(old_el)

            if rel["titleid"]:
                tid_el = ET.Element("titleid")
                tid_el.text = rel["titleid"]
                idx = 0
                for i, child in enumerate(g):
                    if child.tag == "id":
                        idx = i + 1
                        break
                g.insert(idx, tid_el)

            if rel["firmware"]:
                fw_el = ET.Element("firmware")
                fw_el.text = rel["firmware"]
                g.append(fw_el)

            if rel["trimmedsize"]:
                ts_el = ET.Element("trimmedsize")
                ts_el.text = rel["trimmedsize"]
                g.append(ts_el)

            if rel["card"]:
                card_el = ET.Element("card")
                card_el.text = rel["card"]
                g.append(card_el)

    print(f"[✓] Enriched {matched_count} existing GameTDB games with titleid, firmware, etc.")

    # Append remaining genuine titles (strict 4-char ID, not already in existing_ids, not demo/update)
    appended_count = 0
    for rel in all_releases:
        code = rel["code"]
        tid = rel["titleid"]
        prio = rel["priority"]

        # Only append valid 4-character codes that are genuine full titles (priority < 20)
        if not code or code in existing_ids or tid in matched_tids or prio >= 20:
            continue

        existing_ids.add(code)
        if tid:
            matched_tids.add(tid)
        appended_count += 1

        game_el = ET.SubElement(root, "game", {"name": rel["name"]})

        id_el = ET.SubElement(game_el, "id")
        id_el.text = code

        if tid:
            tid_el = ET.SubElement(game_el, "titleid")
            tid_el.text = tid

        if rel["region"]:
            reg_el = ET.SubElement(game_el, "region")
            reg_el.text = rel["region"]

        if rel["languages"]:
            lang_el = ET.SubElement(game_el, "languages")
            lang_el.text = rel["languages"]

        if rel["publisher"]:
            pub_el = ET.SubElement(game_el, "publisher")
            pub_el.text = rel["publisher"]

        if rel["firmware"]:
            fw_el = ET.SubElement(game_el, "firmware")
            fw_el.text = rel["firmware"]

        if rel["trimmedsize"]:
            ts_el = ET.SubElement(game_el, "trimmedsize")
            ts_el.text = rel["trimmedsize"]

        if rel["card"]:
            card_el = ET.SubElement(game_el, "card")
            card_el.text = rel["card"]

        loc_el = ET.SubElement(game_el, "locale", {"lang": "EN"})
        t_el = ET.SubElement(loc_el, "title")
        t_el.text = rel["name"]

    print(f"[✓] Appended {appended_count} additional scene releases.")

    # Update <TDB3DS games="..." /> count attribute
    total_games = len(root.findall("game"))
    header_el = root.find("TDB3DS")
    if header_el is not None:
        header_el.set("games", str(total_games))

    print(f"[*] Formatting XML indentation for {total_games} games...")
    ET.indent(tdb_tree, space="\t")

    print(f"[*] Writing unified XML to {output_xml_path}...")
    tdb_tree.write(output_xml_path, encoding="UTF-8", xml_declaration=True)

    if output_gz_path:
        print(f"[*] Compressing to {output_gz_path}...")
        with open(output_xml_path, "rb") as f_in, gzip.open(output_gz_path, "wb", compresslevel=9) as f_out:
            f_out.writelines(f_in)

    print(f"[✓] Complete in {time.time() - t0:.2f}s! Total games: {total_games}")

if __name__ == "__main__":
    script_dir = os.path.dirname(os.path.abspath(__file__))
    hshop_root = os.path.dirname(script_dir)
    gametdb_xml = os.path.join(hshop_root, "app", "src", "main", "assets", "3dstdb.xml")
    releases_xml = os.path.join(hshop_root, "..", "thor-3ds-db", "data", "3dsreleases.xml")
    out_xml = os.path.join(hshop_root, "..", "thor-3ds-db", "data", "3dsdb.xml")
    out_gz = os.path.join(hshop_root, "..", "thor-3ds-db", "data", "3dsdb.xml.gz")

    merge_xmls(gametdb_xml, releases_xml, out_xml, out_gz)
