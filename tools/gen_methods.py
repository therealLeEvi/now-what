# -*- coding: utf-8 -*-
"""Regenerates methods.json from wiki-checked rates.

Convention (see README): xpPerHour is the rate at the BOTTOM of the bracket,
not the level-99 ceiling. Wilderness methods carry risk=PVP.
"""
import io, json, collections

W = "https://oldschool.runescape.wiki/w/"
OUT = []


def m(skill, lo, hi, name, xp, notes=None, url=None, tick=False, pvp=False, quests=None,
      attention=None, activity=None, xp_each=None, consumes=None):
    d = collections.OrderedDict()
    d["skill"] = skill
    d["minLevel"] = lo
    d["maxLevel"] = hi
    d["name"] = name
    d["xpPerHour"] = xp
    if notes:
        d["notes"] = notes
    d["wikiUrl"] = url or (W + skill.capitalize() + "_training")
    if quests:
        d["quests"] = quests
    if activity:
        # An activities.json id. The method takes that activity's gear and
        # supplies at load time rather than repeating them here.
        d["activity"] = activity
    if xp_each or consumes:
        # The xp of ONE action and what that action uses up, so the panel can
        # turn a level range into a shopping list. Both or neither: a count
        # needs the divisor, and a divisor with nothing to count is dead weight.
        assert xp_each and consumes, (name, xp_each, consumes)
        d["xpPerAction"] = xp_each
        d["consumes"] = [collections.OrderedDict(
            [("item", item)] + ([("each", each)] if each != 1 else []))
            for item, each in consumes]
    if tick:
        d["intensity"] = "TICK_MANIPULATION"
    if pvp:
        d["risk"] = "PVP"
    if attention:
        # AFK / ORDINARY / DEMANDING. Omitted means "not judged yet", which the
        # panel renders as no label at all - see TrainingMethod.Attention. Tick
        # manipulation methods are left out on purpose: they already say so.
        assert attention in ("AFK", "ORDINARY", "DEMANDING"), attention
        assert not tick, "a tick-manipulation method already carries its own label"
        d["attention"] = attention
    OUT.append(d)


# ---------------------------------------------------------------- AGILITY
A = W + "Agility_training"
HS = W + "Hallowed_Sepulchre"
m("AGILITY", 1, 20, "Gnome Stronghold / Draynor rooftop", 9000, None, A)
m("AGILITY", 20, 47, "Brimhaven Agility Arena", 30000,
  "Tickets buy Agility xp in bulk; the rate climbs toward 50k by level 40", A)
m("AGILITY", 30, 99, "Agility Pyramid", 25000,
  "Slower, but the only course that pays you while you run it", A)
m("AGILITY", 47, 62, "Wilderness Agility Course", 50000,
  "Fastest in this bracket, but player killers camp the obstacles. Bring nothing you mind losing.",
  A, pvp=True)
m("AGILITY", 52, 62, "Hallowed Sepulchre (floor 1)", 45000,
  "Requires Sins of the Father; the safe answer to the same bracket as the Wilderness course", HS)
m("AGILITY", 60, 70, "Seers' Village rooftop", 50000, "Marks of Grace toward the graceful outfit", A)
m("AGILITY", 62, 72, "Hallowed Sepulchre (floor 2)", 56000, None, HS)
m("AGILITY", 70, 80, "Pollnivneach rooftop", 47000, "Marks of Grace toward the graceful outfit", A)
m("AGILITY", 72, 87, "Hallowed Sepulchre (floors 3-4)", 69000,
  "Reaches roughly 80k once floor 4 opens at 77", HS)
m("AGILITY", 75, 99, "Prifddinas rooftop", 60000, "Requires Song of the Elves", A)
m("AGILITY", 80, 90, "Rellekka rooftop", 50000, "Marks of Grace toward the graceful outfit", A)
m("AGILITY", 87, 99, "Hallowed Sepulchre (floor 5)", 98000,
  "Fastest Agility in the game and entirely safe", HS)
m("AGILITY", 90, 99, "Ardougne rooftop", 66000,
  "Best rooftop rate; the Ardougne hard diary speeds it up further", A)

# ---------------------------------------------------------------- THIEVING
T = W + "Thieving_training"
BJ = W + "Blackjack"
m("THIEVING", 1, 36, "Pickpocketing men, farmers and master farmers", 20000,
  "Quests skip most of this bracket outright", T)
m("THIEVING", 36, 45, "Stealing from Aldarin villa chests", 60000,
  "Bring lockpicks and stamina potions", T)
m("THIEVING", 45, 65, "Blackjacking bandits (Pollnivneach)", 99000,
  "Very click-intensive; the rate more than doubles by level 65", BJ)
m("THIEVING", 49, 65, "Stealing artefacts (Port Piscarilius)", 150000,
  "Needs Piscarilius favour; far calmer than blackjacking", T)
m("THIEVING", 55, 60, "Pickpocketing Knights of Ardougne", 86000,
  "Safe and low-setup. Over 55-99: ~200 dodgy necklaces, ~16k hitpoints healed, ~7.6M coins.",
  T)
m("THIEVING", 60, 65, "Pickpocketing Knights of Ardougne", 98000,
  None, T)
m("THIEVING", 65, 70, "Pickpocketing Knights of Ardougne", 109000,
  None, T)
m("THIEVING", 70, 75, "Pickpocketing Knights of Ardougne", 124000,
  None, T)
m("THIEVING", 75, 80, "Pickpocketing Knights of Ardougne", 141000,
  None, T)
m("THIEVING", 80, 85, "Pickpocketing Knights of Ardougne", 159000,
  None, T)
m("THIEVING", 85, 90, "Pickpocketing Knights of Ardougne", 182000,
  None, T)
m("THIEVING", 90, 95, "Pickpocketing Knights of Ardougne", 209000,
  None, T)
m("THIEVING", 95, 99, "Pickpocketing Knights of Ardougne", 240000,
  None, T)
m("THIEVING", 65, 99, "Blackjacking menaphite thugs", 230000,
  "Fastest safe method outside the Wilderness, at high click intensity", BJ)
m("THIEVING", 82, 99, "Pickpocketing vyres (Darkmeyer)", 120000,
  "Requires Sins of the Father; good profit alongside the xp", T)
m("THIEVING", 84, 99, "Rogues' Castle chests", 300000,
  "The fastest Thieving there is. Player killers camp the castle, and you lose what you carry.",
  T, pvp=True)
m("THIEVING", 91, 99, "Pyramid Plunder", 270000,
  "Nearly as fast as Rogues' Castle with no PvP exposure at all", W + "Pyramid_Plunder")

# ---------------------------------------------------------------- FARMING
F = W + "Farming_training"
# Farming is paced by real-world growth timers, not by hours at the keyboard,
# so every entry leaves xpPerHour null: a tree run is worth ~2.4M xp per hour
# of *actual clicking*, and printing that as an hourly rate would have the
# planner claim 99 Farming in an afternoon.
m("FARMING", 1, 15, "Quests and allotment runs", None,
  "Quests cover much of it; allotments fill the rest until oak trees open at 15.",
  F)
m("FARMING", 15, 99, "Tree runs", None,
  "A few minutes of clicking for a big lump of xp, then a wait. Oak 15, willow 30, maple 45, yew 60, magic 75.",
  F)
m("FARMING", 35, 99, "Hardwood tree runs (Fossil Island)", None,
  "Teak 35, mahogany 55, redwood 90. Fold these into the same tree run.",
  F)
m("FARMING", 32, 99, "Herb runs", None,
  "Far less xp than a tree run, but among the best passive income in the game.",
  F)

# ---------------------------------------------------------------- MAGIC
MG = W + "Magic_training"
m("MAGIC", 1, 21, "Enchanting jewellery / spells on crabs", 32000, None, MG)
m("MAGIC", 21, 55, "Low Level Alchemy", 60000,
  "Stationary and cheap; the standard way through the 20s and 30s", MG)
m("MAGIC", 43, 55, "Superheat Item", 97000,
  "Gives Smithing xp at the same time, but you pay for the ore", MG)
m("MAGIC", 55, 99, "High Level Alchemy (passive)", 78000,
  "Done alongside another activity rather than as a session of its own", MG)
m("MAGIC", 55, 80, "Tele-alching at Camelot", 144000,
  "Alch between teleports; profitable and needs no combat", MG)
m("MAGIC", 70, 99, "Ice Bursting maniacal monkeys", 310000,
  "Requires Monkey Madness II; roughly 400k once Ice Barrage unlocks at 94", MG)
m("MAGIC", 70, 99, "Ice Bursting zombie pirates", 330000,
  "As fast as maniacal monkeys, and it pays for itself. You are a target the whole time.",
  MG, pvp=True)
m("MAGIC", 80, 99, "Stun-alching", 256000,
  "Fastest non-combat Magic, at a punishing click rate", MG)
m("MAGIC", 86, 99, "Plank Make", 90000,
  "Near-AFK and profitable; requires Lunar Diplomacy", MG)

# ---------------------------------------------------------------- SMITHING
S = W + "Smithing_training"
BF = W + "Blast_Furnace"
m("SMITHING", 1, 40, "Smithing bronze through steel at an anvil", 14000,
  "Rate climbs steeply as better bars unlock", S)
m("SMITHING", 15, 99, "Giants' Foundry", 98000,
  "Requires Sleeping Giants; low cost and low attention", W + "Giants%27_Foundry")
m("SMITHING", 15, 99, "Blast Furnace (iron through rune bars)", 75000,
  "Slower than gold but turns a profit instead of costing", BF)
m("SMITHING", 20, 99, "Dart tips and nails", 13000,
  "Near-zero attention; usually breaks even", S)
m("SMITHING", 35, 99, "Steel cannonballs", 14000,
  "The AFK option — slow, but you can run it while doing something else", S)
m("SMITHING", 40, 99, "Blast Furnace (gold bars, goldsmith gauntlets)", 380000,
  "By far the fastest Smithing; needs goldsmith gauntlets, ice gloves and Family Crest", BF)

# ---------------------------------------------------------------- RANGED
R = W + "Ranged_training"
m("RANGED", 1, 45, "Dwarf multicannon on ogres or similar", 40000,
  "Fastest option before chinning unlocks", R)
m("RANGED", 1, 99, "Gemstone crab", 30000,
  "Fully idle; far slower than chinning but costs nothing and needs no attention", R)
m("RANGED", 45, 55, "Chinning maniacal monkeys (grey chinchompas)", 290000,
  "Requires Monkey Madness II chapter II; chins are consumed, so this is expensive", R)
m("RANGED", 55, 65, "Chinning maniacal monkeys (red chinchompas)", 430000,
  "Expensive — budget for the chins before you start", R)
m("RANGED", 65, 99, "Chinning maniacal monkeys (black chinchompas)", 690000,
  "The fastest xp/hr in the game, priced accordingly. Near 1M/hr at 99 with Rigour and void.", R)
m("RANGED", 70, 99, "Nightmare Zone (venator bow)", 140000,
  "Largely AFK, and a fraction of the cost of chinning", W + "Nightmare_Zone")
m("RANGED", 80, 99, "Venator bow at maniacal monkeys", 140000,
  "Cheaper alternative to chins at the same spot", R)

# ---------------------------------------------------------------- MINING
MI = W + "Mining_training"
m("MINING", 1, 15, "Copper and tin ore", 15000, None, MI)
m("MINING", 15, 45, "Iron ore (power mining)", 45000,
  "Drop the ore rather than banking it; Varrock armour helps", MI)
m("MINING", 30, 99, "Motherlode Mine", 35000,
  "Low attention, and pays in nuggets and Prospector pieces. Reaches ~62k with upgrades.",
  W + "Motherlode_Mine")
m("MINING", 40, 99, "Gem rocks (Shilo Village)", 46000,
  "Requires the medium Karamja diary; the gems are worth real money", MI)
m("MINING", 45, 99, "3-tick granite power-mining", 120000,
  "Requires precise drop-and-reclick timing every game tick; very click intensive", MI, tick=True)
m("MINING", 45, 99, "Calcified rocks (Lovakengj)", 40000,
  "Requires Lovakengj favour; the ore feeds the Blast Mine minigame itself", MI)
m("MINING", 60, 99, "Crashed stars", 26000,
  "Very low effort; stardust charges the celestial ring", MI)
m("MINING", 70, 99, "Volcanic Mine", 84000,
  "Group minigame requiring Bone Voyage; fastest normal-pace Mining", W + "Volcanic_Mine")
m("MINING", 75, 99, "Blast Mine", 65000,
  "Profitable solo minigame; needs dynamite, a chisel and a tinderbox", W + "Blast_Mine")
m("MINING", 92, 99, "Amethyst", 22000, "Slow but profitable and nearly AFK", MI)

# ---------------------------------------------------------------- CRAFTING
C = W + "Crafting_training"
m("CRAFTING", 1, 20, "Leather crafting (gloves through chaps)", 60000, None, C)
m("CRAFTING", 20, 54, "Cutting gems (sapphire through diamond)", 139000,
  "Rate roughly doubles as better gems unlock; a jeweller's chisel adds another ~10%", C)
m("CRAFTING", 54, 63, "Crafting battlestaves", 245000,
  "Fast and close to break-even if you alch the staves", C)
m("CRAFTING", 61, 99, "Superglass Make", 108000,
  "Requires Lunar Diplomacy; profitable and calmer than d'hide", C)
m("CRAFTING", 63, 99, "Dragonhide bodies", 313000,
  "Fastest normal-pace Crafting; a costume needle removes the thread juggling", C)

# ---------------------------------------------------------------- WOODCUTTING
WC = W + "Woodcutting_training"
CAM = W + "Camphor_tree"
IRON = W + "Ironwood_tree"
ROSE = W + "Rosewood_tree"
m("WOODCUTTING", 1, 15, "Regular trees", 13000, None, WC, attention="ORDINARY")
m("WOODCUTTING", 15, 35, "Oak trees", 40000, None, WC, attention="ORDINARY")
m("WOODCUTTING", 35, 65, "Teak trees (regular pace)", 55000,
  "Solid through the mid game; falls off past 65 without tick manipulation", WC,
  attention="ORDINARY")
m("WOODCUTTING", 35, 41, "1.5-tick teaks (Fossil Island)", 93000,
  "Tick-perfect chopping, and the fastest Woodcutting there is. Reaches ~235k by 99.", WC, tick=True)
m("WOODCUTTING", 41, 50, "1.5-tick teaks (Fossil Island)", 115000,
  None, WC, tick=True)
m("WOODCUTTING", 50, 61, "1.5-tick teaks (Fossil Island)", 126000,
  None, WC, tick=True)
m("WOODCUTTING", 61, 71, "1.5-tick teaks (Fossil Island)", 173000,
  None, WC, tick=True)
m("WOODCUTTING", 71, 80, "1.5-tick teaks (Fossil Island)", 194000,
  None, WC, tick=True)
m("WOODCUTTING", 80, 90, "1.5-tick teaks (Fossil Island)", 208000,
  None, WC, tick=True)
m("WOODCUTTING", 90, 99, "1.5-tick teaks (Fossil Island)", 222000,
  None, WC, tick=True)
m("WOODCUTTING", 45, 65, "Maple trees", 48000, "The Kandarin medium diary improves this", WC,
  attention="ORDINARY")
m("WOODCUTTING", 62, 90, "Blisterwood tree", 69000,
  "No banking at all — the logs are consumed as you chop", WC, attention="AFK")
m("WOODCUTTING", 65, 99, "Sulliuscep (Fossil Island)", 80000,
  "Needs Bone Voyage, and you walk a six-spot cycle across the Tar Swamp", WC,
  attention="ORDINARY")
m("WOODCUTTING", 66, 90, "Camphor trees (The Great Conch)", 70000,
  "Needs 45 Sailing and Troubled Tortugans started; a plain tree, no cycle to walk", CAM,
  attention="ORDINARY")
m("WOODCUTTING", 90, 99, "Camphor trees (The Great Conch)", 82000, None, CAM)
m("WOODCUTTING", 77, 99, "Bloodwood trees", 130000,
  "Fastest without tick manipulation, but a two-tick cycle across three trees to reach it; needs The Blood Moon Rises",
  WC, attention="DEMANDING")
m("WOODCUTTING", 77, 99, "Engorged bloodwood tree", 70000,
  "The calm way to tap bloodwood: one tree, far less input, roughly half the rate", WC,
  attention="AFK")
m("WOODCUTTING", 80, 99, "Ironwood trees (Sunbleak Island)", 66000,
  "Needs 72 Sailing; a steady rate for very little input - each tree lasts four minutes", IRON,
  attention="AFK")
m("WOODCUTTING", 88, 99, "Redwood trees", 55000,
  "Slower than bloodwood but completely AFK-friendly", WC, attention="AFK")
m("WOODCUTTING", 92, 99, "Rosewood trees (Drumstick Isle)", 85000,
  "Needs 79 Sailing; drop the logs, there is no bank on the isle", ROSE, attention="ORDINARY")

# ---------------------------------------------------------------- FISHING
FI = W + "Fishing_training"
BAF = W + "Barbarian_Fishing"
m("FISHING", 1, 20, "Net fishing (shrimp and anchovies)", 10000, None, FI)
m("FISHING", 20, 47, "Fly fishing (trout and salmon)", 25000,
  "Rate climbs toward 50k by the top of the bracket", FI)
m("FISHING", 47, 99, "Drift net fishing (Fossil Island)", 53000,
  "Trains Hunter at the same time; requires Bone Voyage", W + "Drift_net_fishing")
m("FISHING", 58, 99, "Barbarian Fishing", 37000,
  "Passive Agility and Strength xp alongside the Fishing", BAF)
m("FISHING", 58, 99, "Barbarian Fishing (3-tick)", 68000,
  "Tick-perfect swapping between fishing and a second action", BAF, tick=True)
m("FISHING", 71, 99, "2-tick harpooning (swordfish and tuna)", 78000,
  "Fastest Fishing in the game with a crystal harpoon", FI, tick=True)
m("FISHING", 78, 99, "Leechfin fishing", 109000,
  "Requires The Blood Moon Rises; fastest normal-pace Fishing but very click heavy", FI)
m("FISHING", 82, 99, "Minnows", 40000, "Profitable, and needs the full angler's outfit", FI)
m("FISHING", 85, 99, "Dark crabs", 40000,
  "Taken for the profit, not the xp. You can be killed for the catch.",
  FI, pvp=True)

# ---------------------------------------------------------------- MELEE
ML = W + "Pay-to-play_melee_training"
for sk in ("ATTACK", "STRENGTH", "DEFENCE"):
    m(sk, 1, 30, "Low-level monsters (chickens, cows, Al Kharid warriors)", 12000,
      "Quests skip most of this bracket", ML)
    m(sk, 20, 99, "Sand or ammonite crabs", 30000,
      "Fully AFK — the default when you want to do something else at the same time", ML)
    m(sk, 30, 55, "Gemstone crab (Tlati Rainforest)", 50000,
      "Infinite health and low accuracy, so no food is needed", ML)
    m(sk, 40, 99, "Slayer tasks", 35000,
      "Slower per hour, but it trains Slayer at the same time and pays for itself in drops", W + "Slayer")
    m(sk, 55, 75, "Sulphur naguas (Neypotzli)", 75000,
      "Requires 48 Slayer; restores prayer and the drops are worth keeping", ML)
    m(sk, 60, 99, "Nightmare Zone", 60000,
      "The most AFK method in the game — minutes between clicks", W + "Nightmare_Zone")
    m(sk, 75, 99, "Maniacal monkeys (Ape Atoll)", 200000,
      "Requires Monkey Madness II; prayer potion drops sustain Piety", ML)

# ---------------------------------------------------------------- HITPOINTS
m("HITPOINTS", 1, 99, "Comes naturally from any combat training", None,
  "Not trained directly — gained automatically from Attack/Strength/Defence/Ranged/Magic combat xp",
  W + "Hitpoints")

# ---------------------------------------------------------------- PRAYER
P = W + "Prayer_training"
GA = W + "Gilded_altar"
m("PRAYER", 1, 30, "Burying bones / Ectofuntus", 25000,
  "Quests cover most of this bracket faster than burying does", P)
m("PRAYER", 30, 99, "Gilded altar with dragon bones", 643000,
  "Needs 75 Construction and your own or a friend's altar; the standard safe method", GA,
  xp_each=252, consumes=[("Dragon bones", 1)])
m("PRAYER", 30, 99, "Libation bowl with blessed bone shards", 670000,
  "Slightly cheaper than a gilded altar, at the cost of preparing the shards first", P,
  xp_each=5, consumes=[("Blessed bone shards", 1)])
m("PRAYER", 30, 99, "Chaos Temple altar", 500000,
  "Half the bone cost, from a 50% save chance. Bring one inventory at a time and expect to die.",
  W + "Chaos_Temple", pvp=True)
m("PRAYER", 70, 99, "Gilded altar with superior dragon bones", 1340000,
  "The fastest Prayer in the game and the most expensive by a wide margin", GA,
  xp_each=525, consumes=[("Superior dragon bones", 1)])

# ---------------------------------------------------------------- COOKING
CO = W + "Cooking_training"
m("COOKING", 1, 40, "Cooking shrimp, trout and salmon", 30000, None, CO)
m("COOKING", 30, 99, "Karambwan at the Hosidius kitchen", 141000,
  "Requires Tai Bwo Wannai Trio; the kitchen's burn reduction does the work", CO)
m("COOKING", 30, 99, "1-tick karambwan", 464000,
  "Tick-perfect cooking; the fastest Cooking in the game, near 1M/hr by level 99", CO, tick=True)
m("COOKING", 40, 68, "Cooking lobsters", 120000, "Cheap, and profitable at most prices", CO)
m("COOKING", 68, 99, "Jugs of wine", 490000,
  "Fastest normal-pace Cooking, and wine never burns", CO)
m("COOKING", 84, 99, "Cooking anglerfish", 305000,
  "Fast and turns a profit rather than costing one", CO)

# ---------------------------------------------------------------- FLETCHING
FL = W + "Fletching_training"
m("FLETCHING", 1, 20, "Arrow shafts and shortbows", 10000, None, FL)
m("FLETCHING", 1, 99, "Darts", 21000,
  "Zero-time training — fletch while doing something else entirely", FL)
m("FLETCHING", 20, 40, "Oak and willow longbows", 18000, "Roughly break-even", FL)
m("FLETCHING", 40, 99, "Battlestaves (celastrus bark)", 110000,
  "Fastest Fletching for most of the game; the bark comes from your own farming patch", FL,
  xp_each=80, consumes=[("Celastrus bark", 1)])
m("FLETCHING", 70, 85, "Yew longbows (u)", 75000,
  "Cheaper than battlestaves and can turn a profit strung", FL,
  xp_each=75, consumes=[("Yew logs", 1)])
m("FLETCHING", 92, 99, "Redwood shields", 168000, "Fastest late-game option", FL,
  xp_each=216, consumes=[("Redwood logs", 2)])

# ---------------------------------------------------------------- FIREMAKING
FM = W + "Firemaking_training"
m("FIREMAKING", 1, 30, "Burning regular and oak logs", 45000, None, FM)
m("FIREMAKING", 30, 50, "Burning willow and maple logs", 107000,
  "Burn in a line next to a bank", FM)
m("FIREMAKING", 50, 99, "Wintertodt", 161000,
  "Pays in supply crates and pyromancer pieces and gives passive Woodcutting xp; ~320k by level 99",
  W + "Wintertodt", activity="minigame_wintertodt")
m("FIREMAKING", 75, 99, "Burning magic logs", 451000,
  "Much faster than Wintertodt, but you are buying every log", FM)
m("FIREMAKING", 90, 99, "Burning redwood logs", 519000,
  "Fastest Firemaking in the game, and the most expensive", FM)

# ---------------------------------------------------------------- HERBLORE
H = W + "Herblore_training"
m("HERBLORE", 1, 30, "Attack and antipoison potions", 62000,
  "Druidic Ritual unlocks the skill at level 3 and quests cover much of the rest of this bracket", H)
m("HERBLORE", 30, 38, "Defence potions", 187000, None, H)
m("HERBLORE", 38, 63, "Prayer potions", 218000, "Profitable as well as fast", H)
m("HERBLORE", 60, 99, "Mastering Mixology", 105000,
  "Requires Children of the Sun; slower, but it pays in reward points rather than costing gp",
  W + "Mastering_Mixology")
m("HERBLORE", 63, 69, "Super restores", 356000, None, H)
m("HERBLORE", 69, 92, "Antifire potions", 393000, None, H)
m("HERBLORE", 77, 99, "Stamina / extended antifire with tick manipulation", 535000,
  "Tick-perfect mixing; the fastest Herblore short of the very top potions", H, tick=True)
m("HERBLORE", 92, 99, "Super antifire potions", 450000, None, H)

# ---------------------------------------------------------------- SLAYER
m("SLAYER", 1, 99, "Tasks from your assigned Slayer master", None,
  "Not an hourly grind. Better masters (Nieve, Duradel) unlock better tasks and more points.",
  W + "Slayer")

# ---------------------------------------------------------------- RUNECRAFT
RC = W + "Runecraft_training"
m("RUNECRAFT", 1, 32, "Quests", None,
  "These cover 1-32 between them. What Lies Below adds 8,000 more at 35.",
  RC, quests=["Client of Kourend", "The Ascent of Arceuus", "Enter the Abyss",
              "Temple of the Eye", "The Eyes of Glouphrie", "The Slug Menace"])
m("RUNECRAFT", 1, 23, "Talisman tiaras", 36000,
  "The grind, if you would rather not quest — expensive and click-intensive", RC)
m("RUNECRAFT", 23, 25, "Lava runes", 43200,
  "No pouches yet — a binding necklace guarantees the combination", RC)
m("RUNECRAFT", 25, 50, "Lava runes", 51400,
  "With small and medium pouches", RC)
m("RUNECRAFT", 50, 75, "Lava runes", 62900,
  "With small, medium and large pouches", RC)
m("RUNECRAFT", 75, 85, "Lava runes", 77600,
  "With the giant pouch; a colossal pouch is worth about 2k/hr more here", RC)
m("RUNECRAFT", 85, 99, "Lava runes", 100800,
  "With a colossal pouch from Guardians of the Rift — the pouch matters more than the level", RC)
m("RUNECRAFT", 27, 99, "Guardians of the Rift", 30000,
  "Passive Mining and Crafting xp, plus the Raiments of the Eye and the colossal pouch",
  W + "Guardians_of_the_Rift", activity="minigame_guardians_of_the_rift")
m("RUNECRAFT", 44, 99, "Ourania (ZMI) altar", 35000,
  "No quest requirements, profitable, and no combat risk; climbs steeply once you have pouches",
  W + "Ourania_Altar")
m("RUNECRAFT", 77, 99, "Blood runes (Arceuus)", 36000,
  "Best gp/hr in the skill, with passive Mining and Crafting xp", RC)
m("RUNECRAFT", 82, 99, "Double astral runes", 42000,
  "Requires Lunar Diplomacy; strong profit alongside the xp", RC)

# ---------------------------------------------------------------- HUNTER
HU = W + "Hunter_training"
CH = W + "Chinchompa_hunting"
RU = W + "Rumour"
m("HUNTER", 1, 15, "Bird snaring (crimson swifts / Feldip weasels)", 13000, None, HU)
m("HUNTER", 9, 44, "Bird house runs (Fossil Island)", 25000,
  "Passive: a few minutes every 50. Run it alongside other training; scales with birdhouse tier.",
  W + "Bird_house_trapping")
m("HUNTER", 15, 21, "Butterfly netting (ruby harvests)", 20000, None, HU)
m("HUNTER", 21, 39, "Box trapping (red crabs)", 31000, "Two traps until level 40", HU)
m("HUNTER", 39, 43, "Box trapping (embertailed jerboas)", 50000, "Three traps from level 40", HU)
m("HUNTER", 43, 49, "Falconry (spotted kebbits)", 60000, None, HU)
m("HUNTER", 49, 73, "Deadfall trapping (razor-backed kebbits)", 130000,
  "Fastest safe method for most of the mid game and far faster than box trapping", HU)
m("HUNTER", 63, 99, "Carnivorous (red) chinchompas", 120000,
  "The safe alternative to black chins. Needs Eagles' Peak; faster once five traps unlock at 80.", CH)
m("HUNTER", 72, 91, "Hunter rumours — Expert tier (Hunters' Guild)", 160000,
  "Safe, varied, and pays in hunter loot sacks. Novice and Adept open at 46 and 57 but are slower.", RU)
m("HUNTER", 73, 99, "Black chinchompas", 145000,
  "You lose everything you carry on death. Rates climb past 200k once five traps unlock at 80.",
  CH, pvp=True)
m("HUNTER", 77, 99, "Box trapping (rainbow crabs)", 155000, "Five traps from level 80", HU)
m("HUNTER", 80, 99, "Herbiboar (Fossil Island)", 137000,
  "Also yields herbs; no traps or supplies needed", W + "Herbiboar")
m("HUNTER", 80, 99, "Carnivorous chinchompas with tick manipulation", 200000,
  "Tick-perfect trap resetting; roughly the same rate as black chinchompas without the Wilderness", CH, tick=True)
m("HUNTER", 91, 99, "Hunter rumours — Master tier (Hunters' Guild)", 195000,
  "Requires At First Light; the fastest safe method in the skill", RU)

# ---------------------------------------------------------------- CONSTRUCTION
CN = W + "Construction_training"
m("CONSTRUCTION", 1, 33, "Planks, crude chairs and oak repair benches", 120000, None, CN)
m("CONSTRUCTION", 33, 52, "Oak larders", 480000,
  "A butler from level 40 does the banking for you", CN)
m("CONSTRUCTION", 52, 77, "Mahogany tables", 900000,
  "Needs a demon butler; among the most expensive training in the game", CN)
m("CONSTRUCTION", 74, 99, "Oak dungeon doors", 550000,
  "The budget option — meaningfully cheaper than mahogany for a modest rate loss", CN)
m("CONSTRUCTION", 77, 99, "Gnome benches (superior garden)", 1100000,
  "Fastest xp/hr of any skill in the game; needs a demon butler", CN)

# ---------------------------------------------------------------- emit
ORDER = ['AGILITY', 'THIEVING', 'FARMING', 'MAGIC', 'SMITHING', 'RANGED', 'MINING', 'CRAFTING',
         'WOODCUTTING', 'FISHING', 'ATTACK', 'STRENGTH', 'DEFENCE', 'HITPOINTS', 'PRAYER',
         'COOKING', 'FLETCHING', 'FIREMAKING', 'HERBLORE', 'SLAYER', 'RUNECRAFT', 'HUNTER',
         'CONSTRUCTION']
assert sorted(set(x["skill"] for x in OUT)) == sorted(ORDER)
OUT.sort(key=lambda x: (ORDER.index(x["skill"]), x["minLevel"], x["maxLevel"]))

body = ",\n".join("  " + json.dumps(e, indent=2, ensure_ascii=False).replace("\n", "\n  ")
                  for e in OUT)
io.open('src/main/resources/com/nextstep/methods.json', 'w', encoding='utf-8').write(
    "[\n" + body + "\n]\n")
print("wrote %d methods | %d wilderness | %d tick-manip" % (
    len(OUT),
    sum(1 for x in OUT if x.get("risk") == "PVP"),
    sum(1 for x in OUT if x.get("intensity"))))
