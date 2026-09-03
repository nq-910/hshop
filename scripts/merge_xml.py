#!/usr/bin/env python3
"""
Merges GameTDB XML (3dstdb.xml) with 3DS Releases XML (3dsreleases.xml)
to produce a complete, unified 3DS XML metadata catalog with:
- Rich GameTDB synopses, developers, genres, ratings, player counts
- Exact 16-character Title IDs, minimum firmware, trimmed ROM sizes, and card types
"""

import xml.etree.ElementTree as ET
import gzip
import os
import sys
import time

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
        code = serial.replace("CTR-P-", "").replace("CTR-N-", "").replace("CTR-", "").strip()[:4]
        tid = (r.findtext("titleid") or "").strip()
        name = (r.findtext("name") or "").strip()
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
            "name": name
        }
        all_releases.append(data)
        if code and code != "N/A" and len(code) == 4 and code not in code_to_rel:
            code_to_rel[code] = data
        if name and name.lower() not in name_to_rel:
            name_to_rel[name.lower()] = data

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
            matched_tids.add(rel["titleid"])

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

    # Append any remaining releases
    appended_count = 0
    for rel in all_releases:
        tid = rel["titleid"]
        code = rel["code"]
        gid = code if (code and code != "N/A" and code not in existing_ids) else tid
        if gid and gid not in existing_ids and tid not in matched_tids:
            existing_ids.add(gid)
            matched_tids.add(tid)
            appended_count += 1

            game_el = ET.SubElement(root, "game", {"name": rel["name"]})

            id_el = ET.SubElement(game_el, "id")
            id_el.text = gid

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

    print(f"[*] Writing unified XML to {output_xml_path}...")
    tdb_tree.write(output_xml_path, encoding="UTF-8", xml_declaration=True)

    if output_gz_path:
        print(f"[*] Compressing to {output_gz_path}...")
        with open(output_xml_path, "rb") as f_in, gzip.open(output_gz_path, "wb", compresslevel=9) as f_out:
            f_out.writelines(f_in)

    print(f"[✓] Complete in {time.time() - t0:.2f}s!")

if __name__ == "__main__":
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    gametdb_xml = os.path.join(repo_root, "..", "hshop", "app", "src", "main", "assets", "3dstdb.xml")
    releases_xml = os.path.join(repo_root, "data", "3dsreleases.xml")
    out_xml = os.path.join(repo_root, "data", "3dsdb.xml")
    out_gz = os.path.join(repo_root, "data", "3dsdb.xml.gz")

    merge_xmls(gametdb_xml, releases_xml, out_xml, out_gz)
