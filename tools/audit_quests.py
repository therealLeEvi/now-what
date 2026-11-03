# Reads every quest's requirements from its OSRS Wiki infobox and writes
# tools/quest_wiki_data.py, which gen_data.py applies over its own quest table.
#
# Why this exists: QUEST_PREREQS in gen_data.py was entered by hand, and an
# audit in November 2026 found 40-odd quests whose prerequisites did not match
# the wiki (While Guthix Sleeps listed King's Ransom, The Heart of Darkness
# listed two quests it does not need). A plan walks that graph backwards, so a
# wrong link is a wrong plan. The wiki's infobox lists the direct
# prerequisites at the second level of its bullet list, which is what this reads.
#
# usage, from the repo root:
#   ./gradlew -q questnames > <names file>
#   python tools/audit_quests.py <cache dir> <names file>
# Pages are cached in <cache dir> so a re-run does not refetch them. The names
# file is every quest RuneLite can report as finished: a wiki prerequisite the
# plugin cannot track (Barbarian Training's parts, Ancient Cavern) is dropped.
import json
import os
import pprint
import re
import sys
import time
import urllib.parse
import urllib.request

CACHE = sys.argv[1]
TRACKED = set(open(sys.argv[2], encoding="utf-8").read().splitlines()) - {""}
os.makedirs(CACHE, exist_ok=True)
SKILLS = {"ATTACK", "DEFENCE", "STRENGTH", "HITPOINTS", "RANGED", "PRAYER", "MAGIC", "COOKING",
          "WOODCUTTING", "FLETCHING", "FISHING", "FIREMAKING", "CRAFTING", "SMITHING", "MINING",
          "HERBLORE", "AGILITY", "THIEVING", "SLAYER", "FARMING", "RUNECRAFT", "HUNTER",
          "CONSTRUCTION", "SAILING"}


# RuneLite's name for each part of Recipe for Disaster, and the wiki subpage it
# lives on. The two do not match ("King Awowogei" against "Freeing King
# Awowogei"), and the plugin can only see a quest as done by RuneLite's name.
RFD_PAGES = {
    "Recipe for Disaster - Another Cook's Quest": "Recipe for Disaster/Another Cook's Quest",
    "Recipe for Disaster - Mountain Dwarf": "Recipe for Disaster/Freeing the Mountain Dwarf",
    "Recipe for Disaster - Wartface & Bentnoze": "Recipe for Disaster/Freeing the Goblin generals",
    "Recipe for Disaster - Pirate Pete": "Recipe for Disaster/Freeing Pirate Pete",
    "Recipe for Disaster - Lumbridge Guide": "Recipe for Disaster/Freeing the Lumbridge Guide",
    "Recipe for Disaster - Evil Dave": "Recipe for Disaster/Freeing Evil Dave",
    "Recipe for Disaster - Skrach Uglogwee": "Recipe for Disaster/Freeing Skrach Uglogwee",
    "Recipe for Disaster - Sir Amik Varze": "Recipe for Disaster/Freeing Sir Amik Varze",
    "Recipe for Disaster - King Awowogei": "Recipe for Disaster/Freeing King Awowogei",
    "Recipe for Disaster - Culinaromancer": "Recipe for Disaster/Defeating the Culinaromancer",
}
RFD_NAMES = {page.lower(): name for name, page in RFD_PAGES.items()}


def page_title(quest):
    return RFD_PAGES.get(quest, quest)


def quest_name(link):
    link = link.strip().replace("_", " ")
    return RFD_NAMES.get(link.lower(), link)


def wikitext(title):
    path = os.path.join(CACHE, re.sub(r"[^A-Za-z0-9]+", "_", title) + ".json")
    if not os.path.exists(path):
        url = ("https://oldschool.runescape.wiki/api.php?action=parse&prop=wikitext&format=json"
               "&formatversion=2&redirects=1&page=" + urllib.parse.quote(title))
        req = urllib.request.Request(url, headers={"User-Agent": "now-what-plugin-research"})
        open(path, "wb").write(urllib.request.urlopen(req, timeout=30).read())
        time.sleep(0.3)
    return json.load(open(path, encoding="utf-8")).get("parse", {}).get("wikitext", "")


def requirements(text):
    m = re.search(r"\|\s*requirements\s*=(.*?)\n\|\s*[a-z]+\s*=", text, re.S)
    if not m:
        return None
    skills, qp, quests = {}, 0, []
    for line in m.group(1).split("\n"):
        stripped = line.strip()
        depth = len(stripped) - len(stripped.lstrip("*"))
        body = stripped.lstrip("*").strip()
        # "130 combined, OR 99 Attack, OR 99 Strength" is an alternative, not
        # three requirements; the model has ANY_OF for that, this does not try.
        alternative = re.search(r"\bOR\b", body) is not None
        if depth == 1 and not alternative:
            for sk, lvl in re.findall(r"\{\{SCP\|([A-Za-z]+)\|(\d+)", body):
                sk = sk.upper()
                if sk == "QUEST":
                    qp = max(qp, int(lvl))
                elif sk in SKILLS:
                    skills[sk] = max(skills.get(sk, 0), int(lvl))
        if depth == 1 and re.match(r"(completion of|completed|started)\b", body, re.I):
            for q in re.findall(r"\[\[([^\]|#]+)", body):
                quests.append(quest_name(q))
        if depth == 2:
            link = re.match(r"(?:\{\{[^}]*\}\}\s*)?(?:Started\s+|Partial completion of\s+)?\[\[([^\]|#]+)",
                            body, re.I)
            if link:
                quests.append(quest_name(link.group(1)))
    return skills, qp, quests


def main():
    acts = json.load(open("src/main/resources/com/nowwhat/activities.json", encoding="utf-8"))
    names = sorted(a["name"] for a in acts if a["category"] == "QUEST")
    known = set(names) | TRACKED
    out = {}
    for name in names:
        got = requirements(wikitext(page_title(name)))
        if got is None:
            continue
        skills, qp, quests = got
        # Only quests the plugin can track: the wiki also links miniquests and
        # training (Barbarian Training) that have no completion state here.
        prereqs = [q for q in dict.fromkeys(quests) if q in known and q != name]
        if skills or qp or prereqs:
            out[name] = {"skills": skills, "qp": qp, "prereqs": prereqs}
    body = ("# Generated by tools/audit_quests.py from each quest's OSRS Wiki infobox.\n"
            "# Do not edit by hand; re-run the audit instead.\n\n"
            "QUEST_WIKI = " + pprint.pformat(out, width=100, sort_dicts=True) + "\n")
    body.encode("ascii")
    open("tools/quest_wiki_data.py", "w", encoding="ascii", newline="\n").write(body)
    print(len(names), "quests read,", len(out), "with wiki requirements")


main()
