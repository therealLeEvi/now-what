# Per-region, per-tier Achievement Diary requirements, read from each region's
# own OSRS Wiki page. The wiki lists these cumulatively (a Hard tier entry
# repeats the Medium skills), which is kept as-is: it is what you actually
# need in hand to finish that tier.
#
# "(started)" / "(partial)" markers in the wiki mean you only need to have
# begun that quest. There is no way to express that in the requirement model,
# so those are kept as full quest requirements — slightly over-requiring is
# better than dropping the step entirely, but it is a known inaccuracy.
DIARY_RAW = {
"ARDOUGNE": {
 "EASY":   ("THIEVING:5", "Biohazard; Rune Mysteries"),
 "MEDIUM": ("AGILITY:39, STRENGTH:38, RANGED:21, FARMING:31, MAGIC:51, FIREMAKING:50, THIEVING:38",
            "Enlightened Journey; The Hand in the Sand; Rune Mysteries; Tower of Life; Underground Pass; Fairytale II - Cure a Queen (started); Sea Slug (started); Watchtower (partial)"),
 "HARD":   ("MINING:52, STRENGTH:50, AGILITY:56, SMITHING:68, HERBLORE:45, FISHING:53, RANGED:60, THIEVING:72, COOKING:53, PRAYER:42, CRAFTING:50, FIREMAKING:50, MAGIC:66, FLETCHING:5, WOODCUTTING:50, RUNECRAFT:65, FARMING:70, CONSTRUCTION:50, HUNTER:59",
            "Enlightened Journey; The Hand in the Sand; Legends' Quest; Monkey Madness I; Mourning's End Part II; Rune Mysteries; Tower of Life; Watchtower; Fairytale II - Cure a Queen (started); Sea Slug (started)"),
 "ELITE":  ("MINING:52, STRENGTH:50, AGILITY:90, SMITHING:91, HERBLORE:45, FISHING:81, RANGED:60, THIEVING:82, COOKING:91, PRAYER:42, CRAFTING:50, FIREMAKING:50, MAGIC:94, FLETCHING:69, WOODCUTTING:50, RUNECRAFT:65, FARMING:85, CONSTRUCTION:50, HUNTER:59",
            "Desert Treasure I; Enlightened Journey; The Hand in the Sand; Haunted Mine; Legends' Quest; Monkey Madness I; Mourning's End Part II; Rune Mysteries; Tower of Life; Watchtower; Fairytale II - Cure a Queen (started); Sea Slug (started)"),
},
"DESERT": {
 "EASY":   ("HUNTER:5", "Icthlarin's Little Helper (started)"),
 "MEDIUM": ("AGILITY:30, SLAYER:22, HUNTER:47, THIEVING:25, HERBLORE:36, CONSTRUCTION:20, WOODCUTTING:35",
            "The Golem; Eagles' Peak; Spirits of the Elid; Enakhra's Lament"),
 "HARD":   ("THIEVING:65, MINING:45, MAGIC:68, AGILITY:70, SLAYER:65, FIREMAKING:60, SMITHING:68, ATTACK:50",
            "The Feud; Desert Treasure I; Dream Mentor"),
 "ELITE":  ("COOKING:85, MAGIC:94, FLETCHING:95, CONSTRUCTION:78, THIEVING:91, PRAYER:85",
            "Contact!; Desert Treasure I; Dream Mentor; Eagles' Peak; Enakhra's Lament; The Feud; The Golem; Spirits of the Elid"),
},
"FALADOR": {
 "EASY":   ("AGILITY:5, MINING:10, SMITHING:13, CONSTRUCTION:16", "Doric's Quest; The Knight's Sword"),
 "MEDIUM": ("AGILITY:42, MINING:40, SMITHING:13, DEFENCE:20, RANGED:19, THIEVING:40, COOKING:20, PRAYER:10, CRAFTING:40, FIREMAKING:49, MAGIC:37, FLETCHING:1, WOODCUTTING:30, SLAYER:32, FARMING:23, CONSTRUCTION:16",
            "Doric's Quest; The Knight's Sword; Recruitment Drive; Skippy and the Mogres"),
 "HARD":   ("AGILITY:59, MINING:60, SMITHING:13, DEFENCE:50, HERBLORE:52, FISHING:53, RANGED:19, THIEVING:58, COOKING:53, PRAYER:70, CRAFTING:40, FIREMAKING:49, MAGIC:37, FLETCHING:1, WOODCUTTING:71, RUNECRAFT:56, SLAYER:72, FARMING:45, CONSTRUCTION:16",
            "Doric's Quest; Grim Tales; Heroes' Quest; The Knight's Sword; Skippy and the Mogres; The Slug Menace"),
 "ELITE":  ("AGILITY:80, MINING:60, SMITHING:13, DEFENCE:50, HERBLORE:81, FISHING:53, RANGED:19, THIEVING:58, COOKING:53, PRAYER:70, CRAFTING:40, FIREMAKING:49, MAGIC:37, FLETCHING:1, WOODCUTTING:75, RUNECRAFT:88, SLAYER:72, FARMING:91, CONSTRUCTION:16",
            "Doric's Quest; Grim Tales; Heroes' Quest; The Knight's Sword; Skippy and the Mogres; The Slug Menace"),
},
"FREMENNIK": {
 "EASY":   ("CRAFTING:23, FIREMAKING:15, HUNTER:11, MINING:20, SMITHING:20, THIEVING:5, WOODCUTTING:15", "The Fremennik Trials"),
 "MEDIUM": ("AGILITY:35, CONSTRUCTION:37, CRAFTING:31, DEFENCE:30, FIREMAKING:40, HUNTER:45, MINING:40, SMITHING:50, THIEVING:42, WOODCUTTING:50",
            "Between a Rock...; Eagles' Peak; Horror from the Deep"),
 "HARD":   ("AGILITY:35, COOKING:53, CRAFTING:61, DEFENCE:40, FIREMAKING:49, FISHING:53, HERBLORE:66, HUNTER:55, MAGIC:72, MINING:70, SMITHING:60, THIEVING:75, WOODCUTTING:56",
            "Between a Rock...; Eadgar's Ruse; Eagles' Peak; Horror from the Deep; Lunar Diplomacy; Throne of Miscellania"),
 "ELITE":  ("AGILITY:80, CRAFTING:80, HUNTER:55, MINING:70, RANGED:70, RUNECRAFT:82, SLAYER:83, STRENGTH:70, THIEVING:75",
            "Between a Rock...; Eadgar's Ruse; Eagles' Peak; Horror from the Deep; Lunar Diplomacy; Throne of Miscellania"),
},
"KANDARIN": {
 "EASY":   ("FISHING:16, FARMING:13", "Elemental Workshop I (started)"),
 "MEDIUM": ("AGILITY:36, STRENGTH:22, RANGED:39, HERBLORE:48, FISHING:46, COOKING:43, MAGIC:45, FLETCHING:50, FARMING:26, MINING:30, WOODCUTTING:36, THIEVING:47",
            "Alfred Grimhand's Barcrawl; Waterfall Quest (started); Fairytale II - Cure a Queen (started); Elemental Workshop II (partial)"),
 "HARD":   ("FISHING:70, AGILITY:60, FLETCHING:70, WOODCUTTING:60, FIREMAKING:65, MAGIC:56, THIEVING:53, SMITHING:75, DEFENCE:70, PRAYER:70, CONSTRUCTION:50, MINING:30, RANGED:40, COOKING:43, STRENGTH:50, ATTACK:20",
            "Desert Treasure I (started); Fairytale II - Cure a Queen (started); Elemental Workshop II (partial)"),
 "ELITE":  ("SMITHING:90, HERBLORE:86, FISHING:76, COOKING:80, FIREMAKING:85, CRAFTING:85, MAGIC:87, FARMING:79, AGILITY:60, THIEVING:53, RANGED:40, CONSTRUCTION:50",
            "Family Crest; Lunar Diplomacy"),
},
"KARAMJA": {
 "EASY":   ("MINING:40, AGILITY:15", "none"),
 "MEDIUM": ("MINING:40, AGILITY:32, HERBLORE:3, FISHING:65, COOKING:16, CRAFTING:20, WOODCUTTING:50, FARMING:27, HUNTER:41", "The Grand Tree; Shilo Village"),
 "HARD":   ("MINING:52, STRENGTH:50, AGILITY:53, SMITHING:40, HERBLORE:25, FISHING:65, RANGED:42, THIEVING:50, COOKING:53, CRAFTING:50, MAGIC:59, WOODCUTTING:50, RUNECRAFT:44, SLAYER:50, FARMING:27, HUNTER:41",
            "The Grand Tree; Tai Bwo Wannai Trio"),
 "ELITE":  ("HERBLORE:87, RUNECRAFT:91, FARMING:72", "The Grand Tree; Tai Bwo Wannai Trio"),
},
"KOUREND": {
 "EASY":   ("MINING:15, HERBLORE:12, FISHING:20, THIEVING:25, CONSTRUCTION:25", "Druidic Ritual"),
 "MEDIUM": ("MINING:42, STRENGTH:16, AGILITY:49, HERBLORE:12, FISHING:43, THIEVING:25, CRAFTING:31, FIREMAKING:50, WOODCUTTING:50, FARMING:45, HUNTER:53",
            "The Ascent of Arceuus; The Depths of Despair; The Forsaken Tower; The Queen of Thieves; Tale of the Righteous"),
 "HARD":   ("MINING:65, STRENGTH:16, AGILITY:49, SMITHING:70, DEFENCE:40, HERBLORE:31, FISHING:43, THIEVING:49, CRAFTING:61, FIREMAKING:50, MAGIC:66, FLETCHING:25, WOODCUTTING:60, SLAYER:62, FARMING:74, HUNTER:53",
            "The Ascent of Arceuus; The Depths of Despair; Dream Mentor; The Forsaken Tower; The Queen of Thieves; Tale of the Righteous"),
 "ELITE":  ("MINING:65, STRENGTH:16, AGILITY:49, SMITHING:70, DEFENCE:40, HERBLORE:31, FISHING:82, THIEVING:49, COOKING:84, CRAFTING:61, FIREMAKING:50, MAGIC:90, FLETCHING:40, WOODCUTTING:90, RUNECRAFT:77, SLAYER:95, FARMING:85, HUNTER:53",
            "The Ascent of Arceuus; The Depths of Despair; Dream Mentor; The Forsaken Tower; The Queen of Thieves; Tale of the Righteous"),
},
"LUMBRIDGE": {
 "EASY":   ("MINING:15, FISHING:15, FIREMAKING:15, WOODCUTTING:15, RUNECRAFT:5, SLAYER:7", "Cook's Assistant; Rune Mysteries"),
 "MEDIUM": ("AGILITY:20, STRENGTH:19, FISHING:30, RANGED:50, THIEVING:38, CRAFTING:38, WOODCUTTING:30, RUNECRAFT:23, HUNTER:42, MAGIC:31",
            "Animal Magnetism; Cook's Assistant; Rune Mysteries; Lost City"),
 "HARD":   ("MINING:50, STRENGTH:19, AGILITY:46, SMITHING:40, HERBLORE:25, FISHING:53, RANGED:50, THIEVING:53, COOKING:70, PRAYER:52, CRAFTING:70, FIREMAKING:65, MAGIC:60, WOODCUTTING:57, RUNECRAFT:59, SLAYER:18, FARMING:63, HUNTER:42",
            "Animal Magnetism; Another Slice of H.A.M.; Recipe for Disaster; Rune Mysteries; Tears of Guthix"),
 "ELITE":  ("ATTACK:50, HITPOINTS:50, MINING:72, STRENGTH:70, AGILITY:70, SMITHING:88, DEFENCE:65, HERBLORE:70, FISHING:62, RANGED:70, THIEVING:78, COOKING:72, PRAYER:52, CRAFTING:70, FIREMAKING:75, MAGIC:75, FLETCHING:70, WOODCUTTING:75, RUNECRAFT:76, SLAYER:74, FARMING:70, CONSTRUCTION:70, HUNTER:70",
            "none"),
},
"MORYTANIA": {
 "EASY":   ("CRAFTING:15, COOKING:12, SLAYER:15, FARMING:23", "Nature Spirit"),
 "MEDIUM": ("HUNTER:29, AGILITY:40, WOODCUTTING:45, COOKING:40, HERBLORE:22, FISHING:50, SMITHING:35",
            "Cabin Fever; Dwarf Cannon; Ghosts Ahoy; Lair of Tarn Razorlor; In Aid of the Myreque (started)"),
 "HARD":   ("MAGIC:66, CONSTRUCTION:50, AGILITY:71, FARMING:47, WOODCUTTING:50, FIREMAKING:50, SLAYER:58, MINING:55, PRAYER:70, DEFENCE:70",
            "Desert Treasure I; Dwarf Cannon; Ghosts Ahoy; In Aid of the Myreque; Lair of Tarn Razorlor; The Great Brain Robbery (started)"),
 "ELITE":  ("FISHING:96, STRENGTH:76, FIREMAKING:80, MAGIC:83, CRAFTING:84, SLAYER:85",
            "Desert Treasure I; Dwarf Cannon; Ghosts Ahoy; In Aid of the Myreque; Lair of Tarn Razorlor; Lunar Diplomacy; Shades of Mort'ton; The Great Brain Robbery (started)"),
},
"VARROCK": {
 "EASY":   ("AGILITY:13, CRAFTING:8, FISHING:20, RUNECRAFT:9, THIEVING:5", "Rune Mysteries"),
 "MEDIUM": ("AGILITY:30, FIREMAKING:40, FISHING:20, HERBLORE:10, MAGIC:25, THIEVING:25, FARMING:25",
            "The Dig Site; Enlightened Journey; Garden of Tranquillity; Gertrude's Cat; Rune Mysteries; A Soul's Bane; Tree Gnome Village"),
 "HARD":   ("AGILITY:51, FARMING:68, FIREMAKING:60, HERBLORE:10, HUNTER:66, MAGIC:54, PRAYER:52, RANGED:40, SMITHING:20, THIEVING:53, WOODCUTTING:60",
            "Desert Treasure I; Enlightened Journey; Garden of Tranquillity; Gertrude's Cat; Rune Mysteries; A Soul's Bane; Tree Gnome Village"),
 "ELITE":  ("COOKING:95, CRAFTING:61, FIREMAKING:60, FLETCHING:81, HERBLORE:90, MAGIC:86, PRAYER:52, RANGED:40, RUNECRAFT:78, SMITHING:89, THIEVING:53, WOODCUTTING:60",
            "Desert Treasure I; Dream Mentor; Enlightened Journey; Garden of Tranquillity; Gertrude's Cat; A Soul's Bane; Tree Gnome Village"),
},
"WESTERN": {
 "EASY":   ("MINING:15, FLETCHING:20, HUNTER:9, COOKING:30, RANGED:30", "Big Chompy Bird Hunting; Rune Mysteries"),
 "MEDIUM": ("AGILITY:37, MINING:40, SMITHING:30, HERBLORE:18, FISHING:46, RANGED:30, COOKING:42, CRAFTING:25, FIREMAKING:35, MAGIC:46, FLETCHING:20, WOODCUTTING:35, HUNTER:31, CONSTRUCTION:5",
            "Big Chompy Bird Hunting; Eagles' Peak; The Eyes of Glouphrie; The Grand Tree; Tree Gnome Village; One Small Favour (partial)"),
 "HARD":   ("AGILITY:56, MINING:70, SMITHING:45, HERBLORE:18, FISHING:62, RANGED:70, THIEVING:75, COOKING:70, CRAFTING:40, FIREMAKING:50, MAGIC:66, FLETCHING:20, WOODCUTTING:50, FARMING:68, CONSTRUCTION:65, HUNTER:69",
            "Big Chompy Bird Hunting; Eagles' Peak; Roving Elves; The Eyes of Glouphrie; Swan Song; Monkey Madness I (partial); Regicide (started); Mourning's End Part I (started)"),
 "ELITE":  ("AGILITY:85, MINING:70, SMITHING:45, HERBLORE:18, FISHING:62, RANGED:70, THIEVING:85, COOKING:70, CRAFTING:40, FIREMAKING:50, MAGIC:66, FLETCHING:85, WOODCUTTING:50, SLAYER:93, FARMING:75, CONSTRUCTION:65, HUNTER:69, PRAYER:22, ATTACK:42, STRENGTH:42, DEFENCE:42, HITPOINTS:42",
            "Eagles' Peak; Roving Elves; The Eyes of Glouphrie; Swan Song; Underground Pass; Regicide (started); Mourning's End Part I (started)"),
},
"WILDERNESS": {
 "EASY":   ("MAGIC:21, AGILITY:15, MINING:15", "Enter the Abyss"),
 "MEDIUM": ("MINING:55, WOODCUTTING:61, AGILITY:52, MAGIC:60, SMITHING:50, SLAYER:50", "Enter the Abyss"),
 "HARD":   ("AGILITY:64, MINING:55, MAGIC:66, SMITHING:75, FISHING:53, HUNTER:67, SLAYER:68", "Death Plateau; Enter the Abyss; Mage Arena I"),
 "ELITE":  ("FISHING:85, COOKING:90, MINING:85, SMITHING:90, THIEVING:84, WOODCUTTING:75, FIREMAKING:75, MAGIC:96, SLAYER:83, AGILITY:64", "Death Plateau; Desert Treasure I; Enter the Abyss; Mage Arena I"),
},
}

REGION_LABELS = {
 "ARDOUGNE": "Ardougne", "DESERT": "Desert", "FALADOR": "Falador",
 "FREMENNIK": "Fremennik", "KANDARIN": "Kandarin", "KARAMJA": "Karamja",
 "KOUREND": "Kourend & Kebos", "LUMBRIDGE": "Lumbridge & Draynor",
 "MORYTANIA": "Morytania", "VARROCK": "Varrock",
 "WESTERN": "Western Provinces", "WILDERNESS": "Wilderness",
}

DIARY_WIKI_PAGE = {
 "KOUREND": "Kourend_%26_Kebos_Diary",
 "LUMBRIDGE": "Lumbridge_%26_Draynor_Diary",
 "WESTERN": "Western_Provinces_Diary",
}

# Named by diaries but not quests in their own right: miniquests and similar.
# Kept as activities so the requirement graph stays closed instead of dangling.
EXTRA_ACTIVITIES = [
 "Enter the Abyss", "Mage Arena I", "Skippy and the Mogres",
 "Lair of Tarn Razorlor", "Alfred Grimhand's Barcrawl", "Knight Waves Training Grounds",
 "A Soul's Bane", "Dwarf Cannon", "Recruitment Drive",
 "Throne of Miscellania", "Contact!", "Tower of Life", "Sea Slug",
 "Fairytale I - Growing Pains", "Murder Mystery", "Perilous Moons",
]
