# Generates activities.json and item-sources.json.
#
# Skill / quest-point / combat-level requirements come from the OSRS Wiki's
# consolidated "Quests/Skill requirements" table. Quest-to-quest prerequisite
# links are NOT tabulated anywhere fetchable, so those are hand-entered below
# and are the part most worth reviewing.
import io, json, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from diary_data import DIARY_RAW, REGION_LABELS, DIARY_WIKI_PAGE, EXTRA_ACTIVITIES

WIKI_SKILLS = """
The Dig Site | AGILITY:10, HERBLORE:10, THIEVING:25
The Lost Tribe | AGILITY:13, MINING:17, THIEVING:13
Tai Bwo Wannai Trio | AGILITY:15, FISHING:5
Troll Stronghold | AGILITY:15
The Depths of Despair | AGILITY:18
Mountain Daughter | AGILITY:20
Death to the Dorgeshuun | AGILITY:23, THIEVING:23
Ghosts Ahoy | AGILITY:25, COOKING:20
The Grand Tree | AGILITY:25
In Search of the Myreque | AGILITY:25, MAGIC:7, MINING:15
Watchtower | AGILITY:25, HERBLORE:14, MAGIC:14, MINING:40, THIEVING:15
Darkness of Hallowvale | AGILITY:26, CONSTRUCTION:5, CRAFTING:32, MAGIC:33, MINING:20, STRENGTH:40, THIEVING:22
Troll Romance | AGILITY:28
Cold War | AGILITY:30, CONSTRUCTION:34, CRAFTING:30, HUNTER:10, THIEVING:15
Shilo Village | AGILITY:32, CRAFTING:20
Horror from the Deep | AGILITY:35
One Small Favour | AGILITY:36, CRAFTING:25, HERBLORE:18, MINING:40, SMITHING:30
Land of the Goblins | AGILITY:38, FISHING:40, HERBLORE:48, THIEVING:45
Royal Trouble | AGILITY:40, SLAYER:40
Cabin Fever | AGILITY:42, RANGED:40, SMITHING:50
A Taste of Hope | AGILITY:45, ATTACK:40, HERBLORE:40, SLAYER:38, CRAFTING:48
The Path of Glouphrie | AGILITY:45, RANGED:47, SLAYER:56, STRENGTH:60
The Heart of Darkness | AGILITY:46, MINING:55, SLAYER:48, THIEVING:48
Recipe for Disaster - Freeing King Awowogei | AGILITY:48, COOKING:70, CRAFTING:48
Legends' Quest | AGILITY:50, CRAFTING:50, HERBLORE:45, MAGIC:56, MINING:52, PRAYER:42, SMITHING:50, STRENGTH:50, THIEVING:50, WOODCUTTING:50, QUEST:107
Sins of the Father | AGILITY:52, ATTACK:50, CRAFTING:56, FLETCHING:60, MAGIC:49, MINING:40, PRAYER:50, WOODCUTTING:62, SLAYER:50
A Kingdom Divided | AGILITY:54, CRAFTING:38, HERBLORE:50, MAGIC:35, MINING:42, THIEVING:52, WOODCUTTING:52
Monkey Madness II | AGILITY:55, CRAFTING:70, FIREMAKING:60, SLAYER:69, THIEVING:55, HUNTER:60
Regicide | AGILITY:56
Grim Tales | AGILITY:59, FARMING:45, HERBLORE:52, THIEVING:58, WOODCUTTING:71
Dragon Slayer II | AGILITY:60, CONSTRUCTION:50, CRAFTING:62, HITPOINTS:50, MAGIC:75, MINING:68, SMITHING:70, THIEVING:60, QUEST:200
The Curse of Arrav | AGILITY:61, MINING:64, RANGED:62, SLAYER:37, STRENGTH:58, THIEVING:62
Beneath Cursed Sands | AGILITY:62, CRAFTING:55, FIREMAKING:55
While Guthix Sleeps | AGILITY:66, FIREMAKING:66, HERBLORE:65, HUNTER:62, MAGIC:67, THIEVING:72, FARMING:65, QUEST:180
Making Friends with My Arm | AGILITY:68, CONSTRUCTION:35, FIREMAKING:66, MINING:72
Secrets of the North | AGILITY:69, HUNTER:56, THIEVING:64
Song of the Elves | AGILITY:70, CONSTRUCTION:70, HERBLORE:70, HUNTER:70, MINING:70, SMITHING:70, WOODCUTTING:70, FARMING:70
Another Slice of H.A.M. | ATTACK:15, PRAYER:25
Holy Grail | ATTACK:20
Dream Mentor | COMBAT:85
The Eyes of Glouphrie | CONSTRUCTION:5, MAGIC:46
Tower of Life | CONSTRUCTION:10
The Fremennik Isles | CONSTRUCTION:20
Getting Ahead | CONSTRUCTION:26, CRAFTING:30
The Great Brain Robbery | CONSTRUCTION:30, CRAFTING:16, PRAYER:50
Scrambled! | CONSTRUCTION:38, SMITHING:35
Desert Treasure II - The Fallen Empire | CONSTRUCTION:60, FIREMAKING:75, HERBLORE:62, MAGIC:75, RUNECRAFT:60, THIEVING:70
Recipe for Disaster - Another Cook's Quest | COOKING:10
Recipe for Disaster - Freeing Evil Dave | COOKING:25
Big Chompy Bird Hunting | COOKING:30, FLETCHING:5, RANGED:30
Recipe for Disaster - Freeing the Lumbridge Guide | COOKING:40
Recipe for Disaster - Freeing Skrach Uglogwee | COOKING:41, FIREMAKING:20
Heroes' Quest | COOKING:53, FISHING:53, HERBLORE:25, MINING:50, QUEST:55
Swan Song | COOKING:62, CRAFTING:40, FISHING:62, FIREMAKING:42, MAGIC:66, SMITHING:45, QUEST:100
The Giant Dwarf | CRAFTING:12, FIREMAKING:16, MAGIC:33, THIEVING:14
A Ruff Situation | CRAFTING:15
Animal Magnetism | CRAFTING:19, RANGED:30, SLAYER:18, WOODCUTTING:35
Creature of Fenkenstrain | CRAFTING:20, THIEVING:25
Elemental Workshop I | CRAFTING:20, MINING:20, SMITHING:20
The Golem | CRAFTING:20, THIEVING:25
Shades of Mort'ton | CRAFTING:20, FIREMAKING:5, HERBLORE:15
Tears of Guthix | CRAFTING:20, FIREMAKING:49, MINING:20, QUEST:43
In Aid of the Myreque | CRAFTING:25, MAGIC:7, MINING:15, RUNECRAFT:30
Shadow of the Storm | CRAFTING:30
The Slug Menace | CRAFTING:30, RUNECRAFT:30, SLAYER:30, THIEVING:30
Lost City | CRAFTING:31, WOODCUTTING:36
Haunted Mine | CRAFTING:35
Enlightened Journey | CRAFTING:36, FARMING:30, FIREMAKING:20, QUEST:20
Family Crest | CRAFTING:40, MAGIC:59, MINING:40, SMITHING:40
Rum Deal | CRAFTING:42, FARMING:40, PRAYER:47, SLAYER:42, FISHING:50
The Hand in the Sand | CRAFTING:49, THIEVING:17
Enakhra's Lament | CRAFTING:50, FIREMAKING:45, MAGIC:39, PRAYER:43
Lunar Diplomacy | CRAFTING:61, DEFENCE:40, HERBLORE:5, MAGIC:65, MINING:60, WOODCUTTING:55, FIREMAKING:49
The Fremennik Exiles | CRAFTING:65, FISHING:60, RUNECRAFT:55, SMITHING:60, SLAYER:60
Rag and Bone Man II | DEFENCE:20, SLAYER:40
Between a Rock... | DEFENCE:30, MINING:40, SMITHING:50
King's Ransom | DEFENCE:65, MAGIC:45
Forgettable Tale... | FARMING:17, COOKING:22
The Garden of Death | FARMING:20
Garden of Tranquillity | FARMING:25
My Arm's Big Adventure | FARMING:29, WOODCUTTING:10
Fairytale II - Cure a Queen | FARMING:49, HERBLORE:57, THIEVING:40
Sea Slug | FIREMAKING:30
Olaf's Quest | FIREMAKING:40, WOODCUTTING:50
Desert Treasure I | FIREMAKING:50, MAGIC:50, SLAYER:10, THIEVING:53
Fishing Contest | FISHING:10
The Tourist Trap | FLETCHING:10, SMITHING:20
Devious Minds | FLETCHING:50, RUNECRAFT:50, SMITHING:65
Jungle Potion | HERBLORE:3
Zogre Flesh Eaters | HERBLORE:8, RANGED:30, SMITHING:4
Eadgar's Ruse | HERBLORE:31
The Ascent of Arceuus | HUNTER:12
Eagles' Peak | HUNTER:27
Defender of Varrock | HUNTER:52, SMITHING:55
Elemental Workshop II | MAGIC:20, SMITHING:30
Spirits of the Elid | MAGIC:33, MINING:37, RANGED:37, THIEVING:37
The Knight's Sword | MINING:10
Tale of the Righteous | MINING:10, STRENGTH:16
Scorpion Catcher | PRAYER:31
Black Knights' Fortress | QUEST:12
Below Ice Mountain | QUEST:16
Wanted! | QUEST:32
Dragon Slayer I | QUEST:32
Recipe for Disaster - Freeing Sir Amik Varze | QUEST:107
Recipe for Disaster - Defeating the Culinaromancer | QUEST:175
Underground Pass | RANGED:25
Temple of Ikov | RANGED:40, THIEVING:42
Mourning's End Part I | RANGED:60, THIEVING:50
Temple of the Eye | RUNECRAFT:10
What Lies Below | RUNECRAFT:35
Sleeping Giants | SMITHING:15
Tribal Totem | THIEVING:21
The Feud | THIEVING:30
"""

# Quests with no skill/QP requirement of their own, but that matter as links
# in a chain (or as sensible early goals).
NO_REQ_QUESTS = [
    "Cook's Assistant", "Demon Slayer", "Romeo & Juliet", "Shield of Arrav",
    "Sheep Shearer", "The Restless Ghost", "Ernest the Chicken", "Vampyre Slayer",
    "Imp Catcher", "Prince Ali Rescue", "Doric's Quest", "Witch's Potion",
    "Goblin Diplomacy", "Pirate's Treasure", "Misthalin Mystery", "Rune Mysteries",
    "X Marks the Spot", "Druidic Ritual", "Witch's House", "Merlin's Crystal",
    "Tree Gnome Village", "Waterfall Quest", "Plague City", "Biohazard",
    "Priest in Peril", "Nature Spirit", "Gertrude's Cat", "Icthlarin's Little Helper",
    "A Tail of Two Cats", "Death Plateau", "The Fremennik Trials", "Fight Arena",
    "Monkey Madness I", "Roving Elves", "Sheep Herder", "Mourning's End Part II",
    "Bone Voyage", "Client of Kourend", "Alfred Grimhand's Barcrawl",
    "Recipe for Disaster", "Ernest the Chicken",
]

# Hand-entered. This is the part that needs a human review pass.
QUEST_PREREQS = {
    "Dragon Slayer II": ["Legends' Quest", "Dream Mentor", "A Tail of Two Cats",
                          "Animal Magnetism", "Ghosts Ahoy", "Bone Voyage", "Client of Kourend"],
    "Legends' Quest": ["Family Crest", "Heroes' Quest", "Shilo Village",
                        "Underground Pass", "Waterfall Quest"],
    "Heroes' Quest": ["Shield of Arrav", "Lost City", "Merlin's Crystal", "Dragon Slayer I"],
    "Monkey Madness I": ["The Grand Tree", "Tree Gnome Village"],
    "Monkey Madness II": ["Monkey Madness I", "Enlightened Journey",
                           "The Eyes of Glouphrie", "Troll Stronghold", "Watchtower"],
    "Underground Pass": ["Biohazard"],
    "Biohazard": ["Plague City"],
    "Regicide": ["Underground Pass"],
    "Roving Elves": ["Regicide", "Waterfall Quest"],
    "Mourning's End Part I": ["Roving Elves", "Big Chompy Bird Hunting", "Sheep Herder"],
    "Mourning's End Part II": ["Mourning's End Part I"],
    "Song of the Elves": ["Mourning's End Part II"],
    "Desert Treasure I": ["The Dig Site", "Temple of Ikov", "The Tourist Trap",
                           "Troll Stronghold", "Priest in Peril", "Waterfall Quest"],
    "Troll Stronghold": ["Death Plateau"],
    "Eadgar's Ruse": ["Druidic Ritual", "Troll Stronghold"],
    "Dream Mentor": ["Lunar Diplomacy", "Eadgar's Ruse"],
    "Lunar Diplomacy": ["The Fremennik Trials", "Lost City", "Rune Mysteries", "Shilo Village"],
    "Shilo Village": ["Jungle Potion"],
    "Jungle Potion": ["Druidic Ritual"],
    "The Dig Site": ["Druidic Ritual"],
    "Bone Voyage": ["The Dig Site"],
    "Animal Magnetism": ["The Restless Ghost", "Ernest the Chicken", "Priest in Peril"],
    "Ghosts Ahoy": ["Priest in Peril", "The Restless Ghost"],
    "Nature Spirit": ["Priest in Peril", "The Restless Ghost"],
    "A Tail of Two Cats": ["Icthlarin's Little Helper"],
    "Icthlarin's Little Helper": ["Gertrude's Cat"],
    "In Search of the Myreque": ["Nature Spirit"],
    "In Aid of the Myreque": ["In Search of the Myreque"],
    "Darkness of Hallowvale": ["In Aid of the Myreque"],
    "A Taste of Hope": ["Darkness of Hallowvale"],
    "Sins of the Father": ["A Taste of Hope"],
    "The Fremennik Isles": ["The Fremennik Trials"],
    "The Fremennik Exiles": ["The Fremennik Isles", "Lunar Diplomacy", "Mountain Daughter"],
    "Horror from the Deep": ["Alfred Grimhand's Barcrawl"],
    "Desert Treasure II - The Fallen Empire": ["Desert Treasure I", "Secrets of the North",
                                                "Below Ice Mountain", "Temple of the Eye",
                                                "Enakhra's Lament", "My Arm's Big Adventure",
                                                "The Garden of Death"],
    "Secrets of the North": ["Making Friends with My Arm"],
    "Making Friends with My Arm": ["My Arm's Big Adventure", "Swan Song"],
    "My Arm's Big Adventure": ["Eadgar's Ruse"],
    "Swan Song": ["One Small Favour", "Garden of Tranquillity"],
    "One Small Favour": ["Rune Mysteries", "Shilo Village"],
    "Garden of Tranquillity": ["Creature of Fenkenstrain"],
    "Creature of Fenkenstrain": ["Priest in Peril"],
    "King's Ransom": ["Holy Grail", "Merlin's Crystal", "Murder Mystery", "One Small Favour"],
    "Holy Grail": ["Merlin's Crystal"],
    "The Path of Glouphrie": ["The Eyes of Glouphrie", "Waterfall Quest"],
    "The Eyes of Glouphrie": ["The Grand Tree"],
    "Grim Tales": ["Witch's House"],
    "The Great Brain Robbery": ["Creature of Fenkenstrain", "Cabin Fever"],
    "Cabin Fever": ["Pirate's Treasure", "Rum Deal"],
    "Rum Deal": ["Zogre Flesh Eaters", "Priest in Peril"],
    "Zogre Flesh Eaters": ["Big Chompy Bird Hunting", "Jungle Potion"],
    "Recipe for Disaster - Freeing King Awowogei": ["Monkey Madness I", "Recipe for Disaster"],
    "Recipe for Disaster - Freeing Sir Amik Varze": ["Recipe for Disaster", "Heroes' Quest"],
    "Recipe for Disaster - Defeating the Culinaromancer": ["Recipe for Disaster - Freeing King Awowogei",
                                                            "Recipe for Disaster - Freeing Sir Amik Varze"],
    "While Guthix Sleeps": ["Defender of Varrock", "King's Ransom", "Dream Mentor",
                             "Temple of Ikov", "Tears of Guthix"],
    "Defender of Varrock": ["Family Crest", "Demon Slayer"],
    "The Curse of Arrav": ["Defender of Varrock"],
    "A Kingdom Divided": ["The Ascent of Arceuus", "The Depths of Despair",
                           "The Forsaken Tower", "Tale of the Righteous", "The Queen of Thieves"],
    "Beneath Cursed Sands": ["Contact!"],
    "Land of the Goblins": ["Another Slice of H.A.M."],
    "Another Slice of H.A.M.": ["Death to the Dorgeshuun", "The Dig Site"],
    "Death to the Dorgeshuun": ["The Lost Tribe"],
    "The Lost Tribe": ["Goblin Diplomacy", "Rune Mysteries"],
    "The Heart of Darkness": ["Sins of the Father", "A Kingdom Divided"],
    "Tears of Guthix": ["Temple of Ikov", "Waterfall Quest"],
    "Troll Romance": ["Troll Stronghold"],
    "Haunted Mine": ["Priest in Peril"],
    "Shades of Mort'ton": ["Priest in Peril"],
    "Enakhra's Lament": [],
    "Tai Bwo Wannai Trio": ["Jungle Potion"],
    "Fairytale II - Cure a Queen": ["Fairytale I - Growing Pains"],
    "Royal Trouble": ["Throne of Miscellania"],
    "Scorpion Catcher": ["Alfred Grimhand's Barcrawl"],
    "Olaf's Quest": ["The Fremennik Trials"],
    "Mountain Daughter": [],
    "The Slug Menace": ["Wanted!", "Sea Slug"],
    "Devious Minds": ["Wanted!", "Troll Stronghold", "Doric's Quest"],
    "Wanted!": ["Recruitment Drive", "The Lost Tribe", "Priest in Peril"],
    "Getting Ahead": ["Perilous Moons"],
    "The Forsaken Tower": ["Client of Kourend"],
    "The Queen of Thieves": ["Client of Kourend"],
    "The Depths of Despair": ["Client of Kourend"],
    "The Ascent of Arceuus": ["Client of Kourend"],
    "Tale of the Righteous": ["Client of Kourend"],
    "Temple of the Eye": ["Rune Mysteries"],
    "What Lies Below": ["Rune Mysteries"],
}

WIKI = "https://oldschool.runescape.wiki/w/"
SKILL_NAMES = {"ATTACK","DEFENCE","STRENGTH","HITPOINTS","RANGED","PRAYER","MAGIC","COOKING",
               "WOODCUTTING","FLETCHING","FISHING","FIREMAKING","CRAFTING","SMITHING","MINING",
               "HERBLORE","AGILITY","THIEVING","SLAYER","FARMING","RUNECRAFT","HUNTER",
               "CONSTRUCTION","SAILING"}

def slug(name):
    return re.sub(r'[^a-z0-9]+', '_', name.lower()).strip('_')

def wiki_url(name):
    return WIKI + name.replace(' ', '_')

# ---- merge wiki skill data per quest ----
quests = {}
for line in WIKI_SKILLS.strip().split('\n'):
    if '|' not in line:
        continue
    name, reqs = [p.strip() for p in line.split('|', 1)]
    entry = quests.setdefault(name, {'skills': {}, 'qp': 0, 'combat': 0})
    if reqs.lower() == 'none':
        continue
    for part in reqs.split(','):
        part = part.strip()
        if ':' not in part:
            continue
        k, v = part.rsplit(':', 1)
        k, v = k.strip().upper(), int(v)
        if k == 'HUNTING':
            k = 'HUNTER'
        if k == 'QUEST':
            entry['qp'] = max(entry['qp'], v)
        elif k == 'COMBAT':
            entry['combat'] = max(entry['combat'], v)
        elif k in SKILL_NAMES:
            entry['skills'][k] = max(entry['skills'].get(k, 0), v)
        else:
            print("WARN unknown skill token:", k, file=sys.stderr)

for q in NO_REQ_QUESTS:
    quests.setdefault(q, {'skills': {}, 'qp': 0, 'combat': 0})
# any quest named as a prerequisite must exist as an activity, or the resolver
# reports it as an unresolved reference
for target, prereqs in QUEST_PREREQS.items():
    quests.setdefault(target, {'skills': {}, 'qp': 0, 'combat': 0})
    for pr in prereqs:
        quests.setdefault(pr, {'skills': {}, 'qp': 0, 'combat': 0})

# ---- diary requirement cleaning ----
# The wiki marks some entries "(started)" or "Partial completion of"; the
# requirement model has no way to say "begun but not finished", so those
# become full quest requirements. Over-requiring beats dropping the step.
NOT_A_QUEST = {
    "none", "all quests", "defeat the penance queen",
    "completion of barbarian assault", "knight waves training grounds",
}

def clean_quest(raw):
    n = raw.strip().strip('"')
    n = re.sub(r"\s*\((started|partial|fully completed|completion required|"
               r"Awowogei subquest)\)\s*$", "", n, flags=re.I)
    n = re.sub(r"^(started|completion of|partial completion of)\s+", "", n, flags=re.I)
    return n.strip()

DIARY = {}
for region, tiers in DIARY_RAW.items():
    DIARY[region] = {}
    for tier, (skills_raw, quests_raw) in tiers.items():
        skills = {}
        for part in skills_raw.split(","):
            part = part.strip()
            if ":" not in part:
                continue
            k, v = part.rsplit(":", 1)
            k = k.strip().upper().replace("DEFENSE", "DEFENCE")
            v = v.strip()
            if not v.isdigit():
                continue          # the wiki table has literal "none" cells
            if k in SKILL_NAMES:
                skills[k] = max(skills.get(k, 0), int(v))
            else:
                print("WARN diary skill token:", region, tier, k, file=sys.stderr)
        qs = []
        for q in quests_raw.split(";"):
            name = clean_quest(q)
            if not name or name.lower() in NOT_A_QUEST:
                continue
            qs.append(name)
        DIARY[region][tier] = (skills, qs)

for region in DIARY:
    for tier in DIARY[region]:
        for q in DIARY[region][tier][1]:
            quests.setdefault(q, {"skills": {}, "qp": 0, "combat": 0})
for extra in EXTRA_ACTIVITIES:
    quests.setdefault(extra, {"skills": {}, "qp": 0, "combat": 0})

activities = []
for name in sorted(quests):
    data = quests[name]
    reqs = []
    for skill in sorted(data['skills']):
        reqs.append({"type": "SKILL_LEVEL", "value": skill, "threshold": data['skills'][skill]})
    if data['qp']:
        reqs.append({"type": "QUEST_POINTS", "value": "QUEST_POINTS", "threshold": data['qp']})
    if data['combat']:
        reqs.append({"type": "COMBAT_LEVEL", "value": "COMBAT_LEVEL", "threshold": data['combat']})
    for pr in QUEST_PREREQS.get(name, []):
        reqs.append({"type": "QUEST_COMPLETE", "value": pr})
    activities.append({
        "id": "quest_" + slug(name),
        "name": name,
        "category": "QUEST",
        "description": "Quest",
        "reasonTemplate": "You meet the requirements for this quest",
        "wikiUrl": wiki_url(name),
        "priority": 5,
        "requirements": reqs,
    })

# ---- PVM ----
# Slayer levels and quest unlocks are hard gates and accurate. Gear/stat advice
# that is a recommendation rather than a gate lives in the description.
PVM = [
    ("pvm_zulrah", "Zulrah", [("QUEST_COMPLETE", "Regicide", 0)],
     "Solo boss. Recommended 75+ Ranged/Magic with a blowpipe or trident and good switches."),
    ("pvm_vorkath", "Vorkath", [("QUEST_COMPLETE", "Dragon Slayer II", 0)],
     "Solo dragon boss. Recommended 80+ Ranged, dragonfire protection and an antifire."),
    ("pvm_abyssal_sire", "Abyssal Sire", [("SKILL_LEVEL", "SLAYER", 85)],
     "Slayer boss. Requires 85 Slayer to damage."),
    ("pvm_kraken", "Kraken", [("SKILL_LEVEL", "SLAYER", 87)],
     "Slayer boss, Magic only. Trident recommended."),
    ("pvm_cerberus", "Cerberus", [("SKILL_LEVEL", "SLAYER", 91)],
     "Slayer boss. Requires 91 Slayer; prayer switching heavy."),
    ("pvm_thermonuclear_smoke_devil", "Thermonuclear smoke devil", [("SKILL_LEVEL", "SLAYER", 93)],
     "Slayer boss. Requires 93 Slayer."),
    ("pvm_alchemical_hydra", "Alchemical Hydra", [("SKILL_LEVEL", "SLAYER", 95),
                                                   ("QUEST_COMPLETE", "A Kingdom Divided", 0)],
     "Slayer boss. Requires 95 Slayer."),
    ("pvm_grotesque_guardians", "Grotesque Guardians", [("SKILL_LEVEL", "SLAYER", 75)],
     "Slayer boss on a gargoyle task. Requires 75 Slayer and a brittle key."),
    ("pvm_barrows", "Barrows", [("QUEST_COMPLETE", "Priest in Peril", 0)],
     "Low-requirement money maker and a good first 'real' PVM routine."),
    # One entry per boss rather than one for the dungeon: the four rooms have
    # different entry requirements, different killcount, and completely
    # different gear, so a single "God Wars Dungeon bosses" row could not say
    # anything specific enough to act on.
    ("pvm_general_graardor", "General Graardor", [("SKILL_LEVEL", "STRENGTH", 70)],
     "Bandos boss. 70 Strength and a hammer to enter the room, 40 Bandos killcount."),
    ("pvm_kreearra", "Kree'arra", [("SKILL_LEVEL", "RANGED", 70)],
     "Armadyl boss. 70 Ranged and a mithril grapple to enter, 40 Armadyl killcount. Ranged only."),
    ("pvm_commander_zilyana", "Commander Zilyana", [("SKILL_LEVEL", "AGILITY", 70)],
     "Saradomin boss. 70 Agility (not boostable) to enter, 40 Saradomin killcount."),
    ("pvm_kril_tsutsaroth", "K'ril Tsutsaroth", [("SKILL_LEVEL", "HITPOINTS", 70)],
     "Zamorak boss. 70 Hitpoints to cross the river, 40 Zamorak killcount."),
    ("pvm_zalcano", "Zalcano", [("QUEST_COMPLETE", "Song of the Elves", 0),
                                 ("SKILL_LEVEL", "MINING", 70),
                                 ("SKILL_LEVEL", "SMITHING", 70)],
     "Group skilling boss in Prifddinas."),
    ("pvm_hespori", "Hespori", [("SKILL_LEVEL", "FARMING", 65)],
     "Farming boss, one kill per seed planted."),
    ("pvm_chambers_of_xeric", "Chambers of Xeric", [("SKILL_LEVEL", "RANGED", 75),
                                                     ("SKILL_LEVEL", "HERBLORE", 78)],
     "Raid. No hard gate, but 75+ combat stats and 78 Herblore for overloads is the practical floor."),
    ("pvm_theatre_of_blood", "Theatre of Blood", [("SKILL_LEVEL", "RANGED", 80),
                                                   ("SKILL_LEVEL", "MAGIC", 80)],
     "Raid. No hard gate; high combat stats and strong gear expected."),
    ("pvm_tombs_of_amascut", "Tombs of Amascut", [("QUEST_COMPLETE", "Beneath Cursed Sands", 0)],
     "Raid with scalable difficulty, so it is approachable earlier than the other two."),
]


# ---- What to wear and what to bring, per boss ----
# Kept separate from the PVM table above because it is a different kind of fact
# with a different source: requirements come from the quest/slayer tables, this
# comes from each boss's /Strategies page. Budget kits are listed first and on
# purpose - someone who has only just met the requirements is not holding a
# twisted bow, and a panel that names only best-in-slot is no use to them.
PVM_PREP = {
    "pvm_zulrah": {
        "setups": [
            ("Ranged (budget)", ["Void ranger helm", "amulet of fury", "Ava's accumulator",
                                 "black d'hide body and chaps", "magic shortbow (i)",
                                 "amethyst arrows", "void knight gloves", "Aranea boots",
                                 "ring of recoil"]),
            ("Magic (budget)", ["Mystic hat", "amulet of glory", "skill cape",
                                "mystic robe top and bottom", "trident of the seas",
                                "book of darkness", "barrows gloves", "wizard boots",
                                "ring of recoil"]),
            ("Ranged (best in slot)", ["Masori mask, body and chaps (f)",
                                       "twisted bow or toxic blowpipe", "Zaryte vambraces",
                                       "Dizana's quiver", "necklace of rupture",
                                       "ring of suffering (ri)", "seeking dragon arrows"]),
            ("Magic (best in slot)", ["Ancestral hat, top and bottom",
                                      "Tumeken's shadow or sanguinesti staff",
                                      "occult necklace", "imbued god cape", "tome of fire",
                                      "magus ring"]),
        ],
        "supplies": ["Extended anti-venom+ (4) - not optional here",
                     "4+ cooked karambwan for combo eating",
                     "Sharks or dark crabs to fill the rest",
                     "1-3 super restores or prayer potions",
                     "Ranging or magic potion to match your setup",
                     "Zul-andra teleport scroll to get back",
                     "Ring of dueling to bank at Ferox"],
        "helper": "Zulrah Helper",
        "helper_id": "zulrah-helper",
    },
    "pvm_vorkath": {
        "setups": [
            ("Ranged (budget)", ["Slayer helmet or Void ranger helm", "salve amulet (ei)",
                                 "Ava's accumulator", "black d'hide body and chaps",
                                 "rune crossbow with ruby and diamond bolts (e)",
                                 "dragonfire ward or anti-dragon shield", "barrows gloves"]),
            ("Ranged (best in slot)", ["Masori mask, body and chaps (f)",
                                       "dragon hunter crossbow",
                                       "ruby and diamond dragon bolts (e)", "salve amulet (ei)",
                                       "Dizana's quiver", "Zaryte vambraces", "dragonfire ward"]),
            ("Melee (best in slot)", ["Torva full helm, platebody and platelegs",
                                      "dragon hunter lance", "avernic defender",
                                      "salve amulet (ei)", "infernal cape", "ferocious gloves"]),
        ],
        "supplies": ["Extended super antifire, or super antifire plus Protect from Magic",
                     "Extended anti-venom+ - skip it only with a serpentine helm",
                     "3-4 super restores or prayer potions",
                     "Manta rays, dark crabs or anglerfish",
                     "Rune pouch: dust, chaos and law runes for Crumble Undead",
                     "Teleport to Rellekka"],
    },
    "pvm_alchemical_hydra": {
        "setups": [
            ("Ranged (budget)", ["Slayer helmet (i)", "amulet of fury", "Ava's assembler",
                                 "black d'hide body and chaps", "toxic blowpipe",
                                 "book of law", "barrows gloves"]),
            ("Ranged (best in slot)", ["Slayer helmet (i)", "Masori body and chaps (f)",
                                       "twisted bow", "twisted buckler",
                                       "seeking dragon arrows", "Zaryte vambraces",
                                       "Dizana's quiver", "necklace of anguish", "lightbearer"]),
            ("Melee (best in slot)", ["Slayer helmet (i)", "Torva platebody and platelegs",
                                      "scythe of vitur or dragon hunter lance",
                                      "avernic defender", "amulet of rancour",
                                      "infernal cape", "ferocious gloves", "ultor ring"]),
        ],
        "supplies": ["4-20 prayer potions or super restores - prayer bonus matters more than accuracy here",
                     "4-10 cooked moonlight antelope, or food plus antidote++",
                     "Divine ranging or divine combat potion",
                     "Zaryte crossbow with ruby bolts (e) for specs, if you have one",
                     "House tablet or ring of dueling to bank",
                     "Bonecrusher necklace and bracelet of slaughter are optional quality of life"],
    },
    "pvm_general_graardor": {
        "setups": [
            ("Melee (budget)", ["Helm of neitiznot", "amulet of fury", "fire cape",
                                "Bandos chestplate and tassets", "abyssal whip or dragon scimitar",
                                "dragon defender", "barrows gloves", "dragon boots"]),
            ("Melee (best in slot)", ["Torva full helm, platebody and platelegs",
                                      "scythe of vitur or ghrazi rapier", "avernic defender",
                                      "amulet of rancour", "infernal cape", "ferocious gloves",
                                      "ultor ring"]),
        ],
        "supplies": ["A hammer, elder maul or Imcando hammer - needed to enter the room",
                     "Any Bandos item to stop the bodyguards attacking you on the way in",
                     "Getting into the dungeon needs 60 Strength or 60 Agility and Troll Stronghold started, plus a rope the first time",
                     "40 killcount for this faction (15 with Grandmaster combat achievements)",
                     "Prayer potions or super restores",
                     "Saradomin brews or high-healing food",
                     "A special attack weapon",
                     "Games necklace or house tablet to get out"],
    },
    "pvm_kreearra": {
        "setups": [
            ("Ranged (budget)", ["Void ranger helm or Armadyl helmet", "amulet of fury",
                                 "Ava's assembler", "black d'hide or Armadyl body and chaps",
                                 "rune crossbow with ruby and diamond bolts (e)",
                                 "odium ward", "barrows gloves"]),
            ("Ranged (best in slot)", ["Masori mask, body and chaps (f)",
                                       "twisted bow or Zaryte crossbow", "Zaryte vambraces",
                                       "Dizana's quiver", "necklace of anguish",
                                       "venator ring", "seeking dragon arrows"]),
        ],
        "supplies": ["A crossbow with a mithril grapple - needed to enter the room",
                     "Any Armadyl item to stop the bodyguards attacking you on the way in",
                     "Getting into the dungeon needs 60 Strength or 60 Agility and Troll Stronghold started, plus a rope the first time",
                     "40 killcount for this faction (15 with Grandmaster combat achievements)",
                     "Prayer potions or super restores",
                     "Saradomin brews or high-healing food",
                     "A special attack weapon",
                     "Games necklace or house tablet to get out"],
    },
    "pvm_commander_zilyana": {
        "setups": [
            ("Melee (budget)", ["Helm of neitiznot", "amulet of fury", "fire cape",
                                "Bandos chestplate and tassets", "abyssal whip",
                                "dragon defender", "barrows gloves", "dragon boots"]),
            ("Melee (best in slot)", ["Torva full helm, platebody and platelegs",
                                      "scythe of vitur", "avernic defender",
                                      "amulet of rancour", "infernal cape",
                                      "ferocious gloves", "ultor ring"]),
        ],
        "supplies": ["Two ropes the first time you go, for the shortcut",
                     "Any Saradomin item to stop the bodyguards attacking you on the way in",
                     "Getting into the dungeon needs 60 Strength or 60 Agility and Troll Stronghold started, plus a rope the first time",
                     "40 killcount for this faction (15 with Grandmaster combat achievements)",
                     "Prayer potions or super restores",
                     "Saradomin brews or high-healing food",
                     "A special attack weapon",
                     "Games necklace or house tablet to get out"],
    },
    "pvm_kril_tsutsaroth": {
        "setups": [
            ("Melee (budget)", ["Helm of neitiznot", "amulet of fury", "fire cape",
                                "Bandos chestplate and tassets", "abyssal whip",
                                "dragon defender", "barrows gloves", "dragon boots"]),
            ("Melee (best in slot)", ["Torva full helm, platebody and platelegs",
                                      "scythe of vitur", "avernic defender",
                                      "amulet of rancour", "infernal cape",
                                      "ferocious gloves", "ultor ring"]),
        ],
        "supplies": ["Any Zamorak item to stop the bodyguards attacking you on the way in",
                     "Protect from Melee, and watch for K'ril's magic attack",
                     "Getting into the dungeon needs 60 Strength or 60 Agility and Troll Stronghold started, plus a rope the first time",
                     "40 killcount for this faction (15 with Grandmaster combat achievements)",
                     "Prayer potions or super restores",
                     "Saradomin brews or high-healing food",
                     "A special attack weapon",
                     "Games necklace or house tablet to get out"],
    },
    "pvm_tombs_of_amascut": {
        "setups": [
            ("Melee (best to worst)", ["Head: Torva full helm, Neitiznot faceguard, serpentine helm",
                                       "Body/legs: Torva, Bandos, fighter torso",
                                       "Weapon: Osmumten's fang, keris partisan, ghrazi rapier",
                                       "Shield: avernic then dragon defender",
                                       "Neck: rancour, torture, blood fury",
                                       "Hands: ferocious then barrows gloves",
                                       "Boots: primordial then dragon boots"]),
            ("Ranged (best to worst)", ["Head/body/legs: Masori (f), Masori, crystal",
                                        "Weapon: twisted bow, Zaryte crossbow, bow of faerdhinen",
                                        "Shield: twisted buckler, dragonfire ward, odium ward",
                                        "Ammo: seeking dragon arrows or ruby dragon bolts (e)",
                                        "Neck: rupture then anguish",
                                        "Cape: Dizana's quiver, Ava's assembler, Ava's accumulator"]),
            ("Magic (best to worst)", ["Head/body/legs: ancestral then virtus",
                                       "Weapon: Tumeken's shadow, eye of Ayak, sanguinesti staff, trident of the swamp",
                                       "Shield: Elidinis' ward (f), arcane spirit shield",
                                       "Neck: occult necklace",
                                       "Hands: confliction gauntlets then tormented bracelet"]),
        ],
        "supplies": ["Saradomin brews and super restores",
                     "Combat potion matching your style",
                     "Smelling salts - take Power or Chaos early for them",
                     "Blessed crystal scarabs, silk dressings and tears of Elidinis from the chests",
                     "Stamina potions",
                     "Everything in each row runs best to worst - anything on the row is doable"],
    },
    "pvm_theatre_of_blood": {
        "setups": [
            ("Melee (best to worst)", ["Head: Torva or oathplate helm, Neitiznot faceguard, Blood Moon helm",
                                       "Body/legs: Torva or oathplate, Bandos, fighter torso",
                                       "Weapon: scythe of vitur, soulreaper axe or blade of saeldor, abyssal tentacle",
                                       "Shield: avernic then dragon defender",
                                       "Neck: rancour, torture, blood fury",
                                       "Boots: primordial then dragon boots",
                                       "Ring: ultor, bellator, berserker (i)"]),
            ("Ranged (best to worst)", ["Void ranger helm with elite void top and robe",
                                        "Weapon: twisted bow plus toxic blowpipe",
                                        "Shield: twisted buckler",
                                        "Neck: rupture then anguish",
                                        "Cape: Dizana's quiver then Ava's assembler"]),
            ("Magic (best to worst)", ["Head/body/legs: ancestral or virtus, or void mage",
                                       "Weapon: eye of Ayak, kodai wand, nightmare staff",
                                       "Shield: Elidinis' ward (f)",
                                       "Neck: occult necklace",
                                       "Hands: confliction gauntlets then tormented bracelet"]),
        ],
        "supplies": ["Prayer potions, super restores and Saradomin brews",
                     "Sharks or anglerfish",
                     "Stamina potions",
                     "Rune pouch for barrage and thralls",
                     "Special weapons: dragon claws, elder maul, Bandos godsword or crystal halberd",
                     "Everything in each row runs best to worst - anything on the row is doable"],
    },
    "pvm_chambers_of_xeric": {
        "setups": [
            ("Melee (best to worst)", ["Head: Torva or oathplate helm, Neitiznot faceguard, Inquisitor's great helm",
                                       "Weapon: scythe of vitur, dragon hunter lance or soulreaper axe, ghrazi rapier or blade of saeldor",
                                       "Shield: avernic then dragon defender",
                                       "Neck: rancour, torture, blood fury or fury",
                                       "Hands: ferocious then barrows gloves",
                                       "Boots: primordial then Aranea boots"]),
            ("Ranged (best to worst)", ["Head/body/legs: Masori (f), Masori, Armadyl",
                                        "Weapon: twisted bow plus blowpipe, then dragon hunter or Zaryte crossbow",
                                        "Neck: rupture then anguish",
                                        "Hands: Zaryte vambraces then barrows gloves",
                                        "Boots: pegasian boots"]),
            ("Magic (best to worst)", ["Head/body/legs: ancestral then virtus",
                                       "Weapon: Tumeken's shadow, eye of Ayak, dragon hunter wand",
                                       "Shield: Elidinis' ward (f), tome of earth or fire",
                                       "Neck: occult then fury",
                                       "Hands: confliction gauntlets then tormented bracelet"]),
        ],
        "supplies": ["Prepot a stamina before you go in",
                     "Saradomin brews, super restores and sanfew serums",
                     "Potions matching the style you are bringing",
                     "The raid builds most of its own supplies from the rooms you get",
                     "Gear beyond this depends on which rooms your raid rolls - check the wiki page per room"],
    },
    "pvm_abyssal_sire": {
        "setups": [
            ("Melee (budget)", ["Black mask (i)", "amulet of glory", "fire cape",
                                "fighter torso", "Bandos tassets", "abyssal whip or rapier",
                                "dragon defender", "barrows gloves", "dragon boots",
                                "berserker ring (i)"]),
            ("Melee (best in slot)", ["Slayer helmet (i)", "Torva platebody and platelegs",
                                      "emberlight", "avernic defender", "amulet of rancour",
                                      "infernal cape", "ferocious gloves", "lightbearer"]),
        ],
        "supplies": ["Scorching bow, or any ranged weapon, for the respiratory systems",
                     "A staff to autocast Ancient Magicks for the spawn phase",
                     "Super combat potion",
                     "Several super restores",
                     "Anglerfish or similar high-healing food",
                     "Special attack weapon",
                     "House teleport tablet for quick banking",
                     "Bracelet of slaughter to extend the task"],
    },
    "pvm_kraken": {
        "setups": [
            ("Magic (budget)", ["Black mask (i)", "amulet of glory", "god cape",
                                "Ahrim's robetop and robeskirt", "trident of the swamp",
                                "mage's book", "barrows gloves", "infinity boots",
                                "ring of suffering (i)"]),
            ("Magic (best in slot)", ["Slayer helmet (i)", "Ancestral robe top and bottom",
                                      "Tumeken's shadow or eye of Ayak", "Elidinis' ward (f)",
                                      "occult necklace", "imbued god cape",
                                      "confliction gauntlets", "magus ring"]),
        ],
        "supplies": ["Fishing explosives to disturb the whirlpools - bring plenty",
                     "8-10 prayer potions (4)",
                     "10+ cooked karambwan or better food",
                     "Magic boost: saturated heart or ancient brew",
                     "Rune pouch with your spell runes",
                     "A teleport out"],
    },
    "pvm_grotesque_guardians": {
        "setups": [
            ("Melee (budget)", ["Slayer helmet (i)", "amulet of fury", "fire cape",
                                "fighter torso", "Bandos tassets", "abyssal tentacle",
                                "dragon defender", "barrows gloves", "dragon boots",
                                "berserker ring (i)"]),
            ("Melee (best in slot)", ["Slayer helmet (i)", "Torva platebody and platelegs",
                                      "scythe of vitur", "avernic defender",
                                      "amulet of rancour", "infernal cape",
                                      "ferocious gloves", "ultor ring"]),
        ],
        "supplies": ["Rock hammer or granite hammer - you cannot finish them without it",
                     "Brittle key (one-off, unlocks the roof permanently)",
                     "An active gargoyle Slayer task",
                     "Super restores rather than prayer potions",
                     "4+ anglerfish",
                     "Divine super combat potion",
                     "Slayer ring to teleport back"],
    },
    "pvm_barrows": {
        "setups": [
            ("Magic (budget)", ["Helm of neitiznot", "amulet of air", "god cape",
                                "mystic robe top and bottom", "staff of air",
                                "rune kiteshield", "combat bracelet", "climbing boots",
                                "ring of dueling"]),
            ("Magic (stronger)", ["Ancestral hat, top and bottom", "trident of the swamp",
                                  "occult necklace", "imbued god cape", "Elidinis' ward (f)",
                                  "tormented bracelet", "eternal boots", "magus ring"]),
        ],
        "supplies": ["Spade - you cannot enter the crypts without it",
                     "4-5 prayer potions",
                     "Sharks or similar food",
                     "Runes for air spells or Iban blast",
                     "Barrows teleport tablet and a house tablet",
                     "Combat or ranging potion if you are meleeing the brothers"],
    },
    "pvm_thermonuclear_smoke_devil": {
        "setups": [
            ("Magic (budget)", ["Facemask or slayer helmet", "amulet of glory", "god cape",
                                "Ahrim's robetop and robeskirt", "trident of the swamp",
                                "mage's book", "barrows gloves", "infinity boots",
                                "seers ring (i)"]),
            ("Melee (budget)", ["Facemask or slayer helmet", "amulet of fury", "fire cape",
                                "Bandos chestplate and tassets", "abyssal tentacle",
                                "dragon defender", "barrows gloves", "dragon boots",
                                "berserker ring (i)"]),
            ("Magic (best in slot)", ["Slayer helmet (i)", "Virtus robe top and bottom",
                                      "Tumeken's shadow", "Elidinis' ward (f)",
                                      "occult necklace", "imbued god cape",
                                      "confliction gauntlets", "magus ring"]),
        ],
        "supplies": ["A facemask, gas mask or slayer helmet must be worn or the smoke damages you",
                     "5+ super restores",
                     "Dark crabs or similar food",
                     "Saturated heart or other boost",
                     "Special attack weapon",
                     "Rune pouch and a house teleport tablet"],
    },
    "pvm_zalcano": {
        "setups": [
            ("Skilling kit", ["Best pickaxe you can wield - crystal, dragon or infernal",
                              "Varrock armour (tier 4 mines extra tephra)",
                              "or full graceful for the run energy",
                              "prospector helmet", "dragonbone necklace for prayer bonus",
                              "mining cape (t) or max cape", "regen bracelet",
                              "devout or graceful boots"]),
        ],
        "supplies": ["1-3 stamina potions depending on your Agility",
                     "8 doses of Saradomin brew, or ordinary food if you are paying attention",
                     "Teleport crystal to leave",
                     "Keep inventory slots free for tephra and loot"],
    },
    "pvm_hespori": {
        "setups": [
            ("Melee", ["Dragon scimitar or better", "a special attack weapon such as a dragon dagger",
                       "any Strength-bonus gear", "60+ melee stats is the practical floor"]),
            ("Magic", ["Fire spells - Hespori takes 100% extra damage from them",
                       "59 Magic for Fire Blast", "40 Prayer for Protect from Magic"]),
        ],
        "supplies": ["Hespori seed - no seed, no fight",
                     "Rake, seed dibber and spade",
                     "A ranged weapon such as darts or a blowpipe to clear the flowers fast",
                     "High-healing food",
                     "Antipoison",
                     "Prayer potion if the fight runs long"],
    },
    "pvm_cerberus": {
        "setups": [
            ("Melee (budget)", ["Slayer helmet (i)", "amulet of fury", "fire cape",
                                "Bandos or Torag's body and legs",
                                "abyssal whip or Saradomin sword", "dragon defender",
                                "barrows gloves", "spectral spirit shield if you own one"]),
            ("Melee (best in slot)", ["Slayer helmet (i)", "Inquisitor's hauberk and plateskirt",
                                      "scythe of vitur",
                                      "avernic defender or spectral spirit shield",
                                      "amulet of rancour", "infernal cape", "ferocious gloves",
                                      "ultor ring"]),
            ("Ranged (best in slot)", ["Slayer helmet (i)", "Masori body and chaps (f)",
                                       "twisted bow", "necklace of rupture", "Dizana's quiver",
                                       "Zaryte vambraces", "lightbearer"]),
        ],
        "supplies": ["6-9 prayer potions or super restores - the souls drain prayer fast",
                     "1-2 divine super combat potions",
                     "12-14 dark crabs or sharks",
                     "A spec weapon: elder maul, dragon claws or Zaryte crossbow",
                     "Rune pouch for thralls if you have Arceuus unlocked",
                     "House teleport tablet"],
    },
}

for pid, name, reqs, desc in PVM:
    out = []
    for t, v, thr in reqs:
        r = {"type": t, "value": v}
        if thr:
            r["threshold"] = thr
        out.append(r)
    entry = {
        "id": pid, "name": name, "category": "PVM", "description": desc,
        "reasonTemplate": "Your stats support this now",
        "wikiUrl": wiki_url(name.split(' bosses')[0]), "priority": 6, "requirements": out,
    }
    prep = PVM_PREP.get(pid)
    if prep:
        entry["setups"] = [{"name": n, "items": items} for n, items in prep["setups"]]
        entry["supplies"] = prep["supplies"]
        entry["strategyUrl"] = wiki_url(name.split(' bosses')[0]) + "/Strategies"
        if prep.get("helper"):
            entry["helperPlugin"] = prep["helper"]
            if prep.get("helper_id"):
                entry["helperPluginId"] = prep["helper_id"]
    activities.append(entry)

# ---- Minigames / unlocks ----
MISC = [
    ("minigame_barbarian_assault", "Barbarian Assault", "MINIGAME", [("COMBAT_LEVEL", "COMBAT_LEVEL", 40)],
     "Team minigame; the source of Void knight equipment."),
    ("minigame_pest_control", "Pest Control", "MINIGAME", [("COMBAT_LEVEL", "COMBAT_LEVEL", 40)],
     "Void knight equipment and cheap combat experience."),
    ("minigame_wintertodt", "Wintertodt", "MINIGAME", [("SKILL_LEVEL", "FIREMAKING", 50)],
     "Safe Firemaking training with useful supply drops."),
    ("minigame_tempoross", "Tempoross", "MINIGAME", [("SKILL_LEVEL", "FISHING", 35)],
     "Safe Fishing training, source of the fish barrel and tackle box."),
    ("minigame_guardians_of_the_rift", "Guardians of the Rift", "MINIGAME", [("SKILL_LEVEL", "RUNECRAFT", 27)],
     "Runecraft training and the main source of Runecraft pets and outfits."),
    ("minigame_fight_caves", "Fight Caves", "MINIGAME", [("SKILL_LEVEL", "RANGED", 70)],
     "Solo wave minigame for the fire cape; 70+ Ranged and a good prayer book expected."),
    ("unlock_warriors_guild", "Warriors' Guild entry", "SKILLING",
     [("ANY_OF", None, 0)], "Dragon defender unlock."),
    ("skilling_herb_runs", "Start Farming herb runs", "SKILLING", [("SKILL_LEVEL", "FARMING", 32)],
     "Low-effort passive income and fast Farming experience."),
    ("skilling_birdhouse_runs", "Start birdhouse runs", "SKILLING", [("SKILL_LEVEL", "HUNTER", 5)],
     "Roughly five minutes every 50 minutes for passive Hunter experience."),
    ("skilling_graceful_set", "Buy a Graceful outfit", "SKILLING", [("SKILL_LEVEL", "AGILITY", 30)],
     "Marks of grace from rooftop courses; weight reduction that pays off everywhere."),
]

for mid, name, cat, reqs, desc in MISC:
    out = []
    for t, v, thr in reqs:
        if t == "ANY_OF":
            out.append({"type": "ANY_OF", "anyOf": [
                {"type": "SKILL_LEVEL", "value": "ATTACK", "threshold": 99},
                {"type": "SKILL_LEVEL", "value": "STRENGTH", "threshold": 99},
                {"type": "COMBINED_SKILL_LEVEL", "value": "ATTACK,STRENGTH", "threshold": 130},
            ]})
            continue
        r = {"type": t, "value": v}
        if thr:
            r["threshold"] = thr
        out.append(r)
    activities.append({
        "id": mid, "name": name, "category": cat, "description": desc,
        "reasonTemplate": "Available to you now",
        "wikiUrl": wiki_url(name), "priority": 4, "requirements": out,
    })

# ---- Achievement Diaries ----
TIER_ORDER = ["EASY", "MEDIUM", "HARD", "ELITE"]
for region in sorted(DIARY):
    label = REGION_LABELS[region]
    page = DIARY_WIKI_PAGE.get(region, label.replace(" ", "_") + "_Diary")
    for tier in TIER_ORDER:
        skills, qs = DIARY[region][tier]
        reqs = [{"type": "SKILL_LEVEL", "value": k, "threshold": skills[k]} for k in sorted(skills)]
        reqs += [{"type": "QUEST_COMPLETE", "value": q} for q in qs]
        activities.append({
            # id must match what DiaryTracker writes into PlayerState
            "id": region + "_" + tier,
            "name": label + " " + tier.capitalize() + " Diary",
            "category": "DIARY",
            "description": "Achievement Diary tier",
            "reasonTemplate": "You meet the requirements for this diary tier",
            "wikiUrl": WIKI + page,
            "priority": 5,
            "requirements": reqs,
        })

# ---- Combat Achievements ----
# NOTE: nothing populates completed CA tiers in PlayerState yet, so these can
# be planned toward but never report as already done.
CA_TIERS = [
    ("EASY", 40, "Entry-level tasks; mostly low-level bosses and basic mechanics."),
    ("MEDIUM", 70, "Requires comfortable mid-level bossing."),
    ("HARD", 90, "Expects solid gear and prayer switching."),
    ("ELITE", 100, "High-end bossing, including raids."),
    ("MASTER", 110, "Endgame content with tight mechanical requirements."),
    ("GRANDMASTER", 120, "The hardest tasks in the game."),
]
for tier, combat, desc in CA_TIERS:
    activities.append({
        # id matches what CombatAchievementTracker writes into PlayerState
        "id": "CA_" + tier,
        "name": "Combat Achievements: " + tier.capitalize() + " tier",
        "category": "COMBAT_ACHIEVEMENT",
        "description": desc,
        "reasonTemplate": "Your combat level supports this tier",
        "wikiUrl": WIKI + "Combat_Achievements",
        "priority": 5,
        "requirements": [{"type": "COMBAT_LEVEL", "value": "COMBAT_LEVEL", "threshold": combat}],
    })

# ---- Collection log goals ----
# NOTE: same caveat as Combat Achievements — collection log unlocks are not
# read from the client yet, so these never report as already obtained.
COLLECTION = [
    ("collectionlog_vorkath_head", "Vorkath's head", "Vorkath",
     [("QUEST_COMPLETE", "Dragon Slayer II", 0)]),
    ("collectionlog_zulrah_pet", "Pet snakeling (Zulrah)", "Zulrah",
     [("QUEST_COMPLETE", "Regicide", 0)]),
    ("collectionlog_barrows_set", "A full Barrows set", "Barrows",
     [("QUEST_COMPLETE", "Priest in Peril", 0)]),
    ("collectionlog_abyssal_whip", "Abyssal whip", "Abyssal demons",
     [("SKILL_LEVEL", "SLAYER", 85)]),
    ("collectionlog_fire_cape", "Fire cape", "the Fight Caves",
     [("SKILL_LEVEL", "RANGED", 70)]),
    ("collectionlog_dragon_defender", "Dragon defender", "the Warriors' Guild",
     [("COMBINED_SKILL_LEVEL", "ATTACK,STRENGTH", 130)]),
]
for cid, name, source, reqs in COLLECTION:
    out = []
    for t, v, thr in reqs:
        r = {"type": t, "value": v}
        if thr:
            r["threshold"] = thr
        out.append(r)
    activities.append({
        "id": cid, "name": name, "category": "COLLECTION_LOG",
        "description": "Collection log slot from " + source,
        "reasonTemplate": "You can access this drop source now",
        "wikiUrl": wiki_url(name), "priority": 3, "requirements": out,
    })

io.open('src/main/resources/com/nextstep/activities.json', 'w', encoding='utf-8', newline='\n').write(
    json.dumps(activities, indent=2, ensure_ascii=False) + "\n")

# ---- items ----
# Every gameItemId below was read out of RuneLite's own ItemID class, not typed
# from memory. Drop estimates are rough wiki averages.
ITEMS = [
    ("TWISTED_BOW", "Twisted bow", 20997, "MONSTER_DROP", "Chambers of Xeric", 500,
     {"type": "SKILL_LEVEL", "value": "RANGED", "threshold": 75,
      "hint": "Raid-entry benchmark; CoX also wants 70s-80s melee stats and 78 Herblore for overloads."}),
    ("SCYTHE_OF_VITUR", "Scythe of vitur", 22325, "MONSTER_DROP", "Theatre of Blood", 300,
     {"type": "SKILL_LEVEL", "value": "ATTACK", "threshold": 80, "hint": "ToB expects high combat stats and strong gear."}),
    ("DRAGON_CLAWS", "Dragon claws", 13652, "MONSTER_DROP", "Chambers of Xeric", 150, None),
    ("KODAI_WAND", "Kodai wand", 21006, "MONSTER_DROP", "Chambers of Xeric", 300, None),
    ("ELDER_MAUL", "Elder maul", 21003, "MONSTER_DROP", "Chambers of Xeric", 300, None),
    ("ANCESTRAL_ROBE_TOP", "Ancestral robe top", 21021, "MONSTER_DROP", "Chambers of Xeric", 350, None),
    ("AVERNIC_DEFENDER", "Avernic defender hilt", 22322, "MONSTER_DROP", "Theatre of Blood", 60, None),
    ("TOXIC_BLOWPIPE", "Toxic blowpipe", 12926, "MONSTER_DROP", "Zulrah", 90,
     {"type": "QUEST_COMPLETE", "value": "Regicide"}),
    ("DRAGON_WARHAMMER", "Dragon warhammer", 13576, "MONSTER_DROP", "Lizardman shamans", 400, None),
    ("ARMADYL_CROSSBOW", "Armadyl crossbow", 11785, "MONSTER_DROP", "Commander Zilyana", 500, None),
    ("BANDOS_CHESTPLATE", "Bandos chestplate", 11832, "MONSTER_DROP", "General Graardor", 380, None),
    ("BANDOS_TASSETS", "Bandos tassets", 11834, "MONSTER_DROP", "General Graardor", 380, None),
    ("ARMADYL_GODSWORD", "Armadyl godsword hilt", 11802, "MONSTER_DROP", "Kree'arra", 500, None),
    ("SARADOMIN_GODSWORD", "Saradomin godsword hilt", 11806, "MONSTER_DROP", "Commander Zilyana", 500, None),
    ("PRIMORDIAL_BOOTS", "Primordial boots", 13239, "MONSTER_DROP", "Cerberus", 256,
     {"type": "SKILL_LEVEL", "value": "SLAYER", "threshold": 91}),
    ("DRAGON_HUNTER_LANCE", "Dragon hunter lance", 22978, "MONSTER_DROP", "Alchemical Hydra", 1000,
     {"type": "SKILL_LEVEL", "value": "SLAYER", "threshold": 95}),
    ("OCCULT_NECKLACE", "Occult necklace", 12002, "MONSTER_DROP", "Smoke devils", 350,
     {"type": "SKILL_LEVEL", "value": "SLAYER", "threshold": 93}),
    ("TRIDENT_OF_THE_SEAS", "Trident of the seas", 11907, "MONSTER_DROP", "Cave kraken", 512,
     {"type": "SKILL_LEVEL", "value": "SLAYER", "threshold": 87}),
    ("AMULET_OF_TORTURE", "Amulet of torture", 19553, "CRAFT", "String a zenyte and enchant it", None,
     {"type": "SKILL_LEVEL", "value": "CRAFTING", "threshold": 98}),
    ("ABYSSAL_WHIP", "Abyssal whip", 4151, "MONSTER_DROP", "Abyssal demons", 512,
     {"type": "SKILL_LEVEL", "value": "SLAYER", "threshold": 85}),
    ("DRAGON_BOOTS", "Dragon boots", 11840, "MONSTER_DROP", "Spiritual mages", 120,
     {"type": "SKILL_LEVEL", "value": "SLAYER", "threshold": 1}),
    ("FIRE_CAPE", "Fire cape", 6570, "MONSTER_DROP", "the Fight Caves", 1,
     {"type": "SKILL_LEVEL", "value": "RANGED", "threshold": 70}),
    ("DRAGON_DEFENDER", "Dragon defender", 12954, "MONSTER_DROP", "Cyclopes in the Warriors' Guild", 1, None),
    ("BARROWS_GLOVES", "Barrows gloves", 7462, "QUEST_REWARD", "Recipe for Disaster", None, None),
    ("DRAGON_SCIMITAR", "Dragon scimitar", 4587, "GE_BUY", "Cheap early melee upgrade", None,
     {"type": "QUEST_COMPLETE", "value": "Monkey Madness I"}),
    ("AMULET_OF_FURY", "Amulet of fury", 6585, "GE_BUY", "Standard all-round amulet", None, None),
    ("DRAGON_PICKAXE", "Dragon pickaxe", 11920, "MONSTER_DROP", "Venenatis, Callisto or Vet'ion", 400, None),
    ("BERSERKER_RING", "Berserker ring", 6737, "MONSTER_DROP", "Dagannoth Rex", 128, None),
    ("SEERS_RING", "Seers ring", 6731, "MONSTER_DROP", "Dagannoth Prime", 128, None),
    ("ARCHERS_RING", "Archers ring", 6733, "MONSTER_DROP", "Dagannoth Supreme", 128, None),
    ("GRACEFUL_HOOD", "Graceful hood", 11850, "SHOP", "Marks of grace, Rogues' Den", None,
     {"type": "SKILL_LEVEL", "value": "AGILITY", "threshold": 30}),
    ("RUNE_PLATEBODY", "Rune platebody", 1127, "GE_BUY", "Budget melee body", None, None),
    ("CADAVA_BERRIES", "Cadava berries", 753, "GE_BUY", None, None, None),
    ("JANGERBERRIES", "Jangerberries", 247, "GE_BUY", None, None, None),
]

items = []
for iid, display, game_id, stype, detail, kc, req in ITEMS:
    entry = {"id": iid, "displayName": display, "gameItemId": game_id, "type": stype}
    if detail:
        entry["detail"] = detail
    if kc:
        entry["estimatedKc"] = kc
    if req:
        entry["skillRequirement"] = req
    entry["wikiUrl"] = wiki_url(display)
    items.append(entry)

items.append({
    "id": "ZAMORAK_BREW", "displayName": "Zamorak brew (4)", "gameItemId": 2450, "type": "CRAFT",
    "detail": "Mix at a Herblore workbench",
    "skillRequirement": {"type": "SKILL_LEVEL", "value": "HERBLORE", "threshold": 78},
    "ingredients": [{"itemId": "CADAVA_BERRIES", "quantity": 1},
                    {"itemId": "JANGERBERRIES", "quantity": 1}],
    "wikiUrl": WIKI + "Zamorak_brew",
})

io.open('src/main/resources/com/nextstep/item-sources.json', 'w', encoding='utf-8', newline='\n').write(
    json.dumps(items, indent=2, ensure_ascii=False) + "\n")

print("activities:", len(activities), " items:", len(items))
