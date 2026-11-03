# Wiki RECOMMENDED stats per fight, gathered from oldschool.runescape.wiki
# (the /Strategies page "Suggested skills"/"Recommendations" section unless NOTES says otherwise).
# Soft gate only. Minimum/budget figures used where the wiki gives several.
# Hard requirements already in activities.json are not repeated.

def S(skill, level):
    return ("SKILL_LEVEL", skill, level)

def C(skills, total):
    return ("COMBINED_SKILL_LEVEL", skills, total)

def ANY(*opts):
    return ("ANY_OF", list(opts))

MELEE = "ATTACK,STRENGTH"

RECOMMENDED = {
    # --- Slayer / single bosses ---
    "pvm_zulrah": [ANY(S("MAGIC", 75), S("RANGED", 80)), S("DEFENCE", 70), S("HITPOINTS", 80), S("PRAYER", 45)],
    "pvm_vorkath": [ANY(S("RANGED", 85), C(MELEE, 165)), S("DEFENCE", 75), S("PRAYER", 70)],
    "pvm_abyssal_sire": [ANY(C(MELEE, 180), S("RANGED", 90), S("MAGIC", 92)), S("DEFENCE", 90), S("HITPOINTS", 90), S("PRAYER", 70)],
    "pvm_kraken": [S("MAGIC", 75), S("DEFENCE", 70), S("HITPOINTS", 75)],
    "pvm_cerberus": [C(MELEE, 170), S("DEFENCE", 85), S("PRAYER", 70)],
    "pvm_thermonuclear_smoke_devil": [ANY(C(MELEE, 180), S("MAGIC", 75)), S("HITPOINTS", 90), S("PRAYER", 49)],
    "pvm_alchemical_hydra": [S("RANGED", 75), S("HITPOINTS", 85), S("PRAYER", 74)],
    "pvm_grotesque_guardians": [C(MELEE, 150), S("DEFENCE", 70), S("HITPOINTS", 75), S("PRAYER", 70)],
    "pvm_barrows": [C(MELEE, 100), S("MAGIC", 50), S("DEFENCE", 50), S("PRAYER", 43)],
    # --- God Wars ---
    "pvm_general_graardor": [ANY(S("ATTACK", 80), S("RANGED", 80)), S("DEFENCE", 70), S("HITPOINTS", 80), S("PRAYER", 44)],
    "pvm_kreearra": [S("DEFENCE", 70), S("HITPOINTS", 70), S("PRAYER", 44)],
    "pvm_commander_zilyana": [S("RANGED", 80), S("DEFENCE", 80), S("HITPOINTS", 70), S("PRAYER", 44)],
    "pvm_kril_tsutsaroth": [ANY(C(MELEE, 170), S("RANGED", 85)), S("DEFENCE", 80), S("HITPOINTS", 80), S("PRAYER", 70)],
    "pvm_nex": [ANY(S("RANGED", 90), C(MELEE, 180)), S("DEFENCE", 80), S("HITPOINTS", 90), S("PRAYER", 70)],
    # --- Skilling-ish bosses ---
    "pvm_zalcano": [],
    "pvm_hespori": [C(MELEE, 120), S("HITPOINTS", 60)],
    # --- Raids ---
    "pvm_chambers_of_xeric": [C(MELEE, 160), S("RANGED", 80), S("MAGIC", 80), S("PRAYER", 77)],
    "pvm_theatre_of_blood": [C(MELEE, 150), S("DEFENCE", 75), S("HITPOINTS", 75), S("PRAYER", 75)],
    "pvm_tombs_of_amascut": [C(MELEE, 140), S("RANGED", 70), S("MAGIC", 70), S("PRAYER", 77)],
    # --- Desert Treasure II and later quest bosses ---
    "pvm_duke_sucellus": [C(MELEE, 150), S("DEFENCE", 70), S("HITPOINTS", 80)],
    "pvm_the_leviathan": [S("RANGED", 75), S("DEFENCE", 70), S("HITPOINTS", 80)],
    "pvm_the_whisperer": [S("MAGIC", 75), S("HITPOINTS", 80)],
    "pvm_vardorvis": [C(MELEE, 165), S("DEFENCE", 80), S("HITPOINTS", 85)],
    "pvm_yama": [ANY(C(MELEE, 160), S("MAGIC", 82)), S("DEFENCE", 75), S("PRAYER", 63)],
    "pvm_phantom_muspah": [S("RANGED", 85), S("DEFENCE", 75), S("PRAYER", 74)],
    "pvm_doom_of_mokhaiotl": [S("RANGED", 90), S("PRAYER", 43)],
    "pvm_maggot_king": [ANY(S("RANGED", 85), S("MAGIC", 75), C(MELEE, 165)), S("DEFENCE", 70), S("PRAYER", 74)],
    "pvm_amoxliatl": [C(MELEE, 120), S("DEFENCE", 60), S("HITPOINTS", 60), S("PRAYER", 37)],
    "pvm_the_hueycoatl": [C(MELEE, 120), S("DEFENCE", 60), S("HITPOINTS", 70), S("PRAYER", 43)],
    "pvm_moons_of_peril": [C(MELEE, 140), S("DEFENCE", 70), S("HITPOINTS", 75), S("PRAYER", 34)],
    "pvm_royal_titans": [ANY(C(MELEE, 140), S("RANGED", 70)), S("MAGIC", 75), S("DEFENCE", 70), S("HITPOINTS", 70)],
    "pvm_fortis_colosseum": [C(MELEE, 190), S("RANGED", 95), S("DEFENCE", 95), S("PRAYER", 85)],
    "pvm_gemstone_crab": [],
    "pvm_araxxor": [ANY(C(MELEE, 170), S("RANGED", 80)), S("PRAYER", 43)],
    "pvm_the_nightmare": [C(MELEE, 170), S("DEFENCE", 85), S("HITPOINTS", 85), S("PRAYER", 70)],
    "pvm_phosanis_nightmare": [C(MELEE, 190), S("DEFENCE", 85), S("HITPOINTS", 95), S("PRAYER", 70)],
    "pvm_shellbane_gryphon": [C(MELEE, 130), S("PRAYER", 43)],
    # --- Classic bosses ---
    "pvm_corporeal_beast": [ANY(C(MELEE, 188), S("RANGED", 90)), S("HITPOINTS", 85), S("PRAYER", 70)],
    "pvm_kalphite_queen": [ANY(C(MELEE, 150), S("RANGED", 75)), S("DEFENCE", 70), S("HITPOINTS", 80), S("PRAYER", 70)],
    "pvm_giant_mole": [ANY(C(MELEE, 120), S("RANGED", 60), S("MAGIC", 60)), S("DEFENCE", 60), S("HITPOINTS", 60), S("PRAYER", 43)],
    "pvm_sarachnis": [C(MELEE, 130), S("DEFENCE", 65), S("HITPOINTS", 65), S("PRAYER", 43)],
    "pvm_dagannoth_kings": [C(MELEE, 140), S("RANGED", 70), S("MAGIC", 75), S("DEFENCE", 60)],
    "pvm_scurrius": [ANY(C(MELEE, 100), S("RANGED", 50), S("MAGIC", 50)), S("DEFENCE", 50), S("HITPOINTS", 50), S("PRAYER", 43)],
    # --- Wilderness ---
    "pvm_chaos_fanatic": [S("RANGED", 75), S("DEFENCE", 70), S("PRAYER", 37)],
    "pvm_crazy_archaeologist": [S("MAGIC", 75), S("PRAYER", 40)],
    "pvm_scorpia": [S("MAGIC", 70), S("PRAYER", 40)],
    "pvm_king_black_dragon": [ANY(C(MELEE, 160), S("RANGED", 80)), S("HITPOINTS", 70), S("PRAYER", 70)],
    "pvm_chaos_elemental": [ANY(C(MELEE, 160), S("RANGED", 75)), S("DEFENCE", 70), S("PRAYER", 37)],
    "pvm_revenant_maledictus": [ANY(C(MELEE, 120), S("RANGED", 60)), S("DEFENCE", 40), S("PRAYER", 45)],
    "pvm_vetion": [C(MELEE, 140), S("DEFENCE", 70), S("HITPOINTS", 75), S("PRAYER", 70)],
    "pvm_venenatis": [ANY(C(MELEE, 155), S("RANGED", 75)), S("DEFENCE", 70), S("HITPOINTS", 70)],
    "pvm_callisto": [ANY(S("RANGED", 75), S("MAGIC", 79)), S("DEFENCE", 70), S("HITPOINTS", 60), S("PRAYER", 44)],
    "pvm_revenants": [ANY(C(MELEE, 120), S("RANGED", 60)), S("DEFENCE", 40), S("PRAYER", 45)],
    "pvm_lava_dragons": [S("MAGIC", 70), S("PRAYER", 43)],
    "pvm_green_dragons": [ANY(C(MELEE, 140), S("RANGED", 70)), S("DEFENCE", 70), S("HITPOINTS", 70)],
    "pvm_zombie_pirates": [S("ATTACK", 60), S("PRAYER", 37)],
    # --- Challenges ---
    "pvm_the_gauntlet": [ANY(C(MELEE, 165), S("RANGED", 85)), S("MAGIC", 80), S("HITPOINTS", 85), S("PRAYER", 77)],
    "pvm_the_inferno": [S("RANGED", 90), S("DEFENCE", 90), S("HITPOINTS", 90), S("PRAYER", 80)],
    # --- Low level / misc bosses ---
    "pvm_obor": [ANY(S("RANGED", 60), C(MELEE, 100)), S("DEFENCE", 50), S("PRAYER", 40)],
    "pvm_bryophyta": [ANY(S("RANGED", 60), S("MAGIC", 59), C(MELEE, 100)), S("DEFENCE", 40), S("PRAYER", 37)],
    "pvm_brutus": [],
    "pvm_mad_angel": [C(MELEE, 120), S("PRAYER", 43)],
    "pvm_skotizo": [C(MELEE, 150), S("DEFENCE", 75), S("HITPOINTS", 75)],
    "pvm_deranged_archaeologist": [S("MAGIC", 50), S("PRAYER", 40)],
    "pvm_the_fractured_archive": [ANY(C(MELEE, 185), S("RANGED", 95)), S("DEFENCE", 75), S("HITPOINTS", 95)],
    "pvm_tormented_demons": [C(MELEE, 150), S("RANGED", 70), S("HITPOINTS", 75), S("PRAYER", 70)],
    "pvm_demonic_gorillas": [C(MELEE, 165), S("RANGED", 85), S("DEFENCE", 80), S("PRAYER", 70)],
    # --- Slayer monsters / money makers ---
    "pvm_frost_dragons": [ANY(C(MELEE, 140), S("RANGED", 70), S("MAGIC", 75)), S("DEFENCE", 70), S("HITPOINTS", 70), S("PRAYER", 44)],
    "pvm_rune_dragons": [ANY(C(MELEE, 180), S("RANGED", 90)), S("DEFENCE", 90), S("PRAYER", 70)],
    "pvm_adamant_dragons": [ANY(C(MELEE, 180), S("RANGED", 90)), S("DEFENCE", 90), S("PRAYER", 70)],
    "pvm_brutal_black_dragons": [ANY(C(MELEE, 140), S("RANGED", 70), S("MAGIC", 75)), S("DEFENCE", 70), S("HITPOINTS", 70), S("PRAYER", 44)],
    "pvm_lizardman_shamans": [ANY(C(MELEE, 160), S("RANGED", 60)), S("DEFENCE", 20), S("PRAYER", 40)],
    "pvm_vyrewatch_sentinels": [C(MELEE, 120), S("PRAYER", 43)],
    "pvm_skeletal_wyverns": [ANY(C(MELEE, 150), S("RANGED", 70), S("MAGIC", 75))],
    "pvm_urium_shades": [C(MELEE, 160), S("DEFENCE", 80)],
    "pvm_spitting_wyverns": [C(MELEE, 160), S("DEFENCE", 80), S("PRAYER", 70)],
    "pvm_kurasks": [C(MELEE, 150), S("DEFENCE", 75)],
    "pvm_hydras": [C(MELEE, 180), S("DEFENCE", 90), S("PRAYER", 70)],
    "pvm_basilisk_knights": [ANY(C(MELEE, 160), S("RANGED", 75))],
    "pvm_gargoyles": [C(MELEE, 140), S("DEFENCE", 70)],
    "pvm_venators": [C(MELEE, 120), S("PRAYER", 43)],
    "pvm_cave_horrors": [ANY(S("MAGIC", 70), S("RANGED", 50)), S("DEFENCE", 70)],
    "pvm_wyrms": [ANY(S("RANGED", 70), S("MAGIC", 70)), S("PRAYER", 37)],
    "pvm_drakes": [S("RANGED", 70), S("PRAYER", 40)],
    # --- Combat minigames ---
    "minigame_fight_caves": [S("RANGED", 75), S("DEFENCE", 70), S("HITPOINTS", 75), S("PRAYER", 60)],
    "minigame_barbarian_assault": [S("RANGED", 50), S("DEFENCE", 40), S("HITPOINTS", 60)],
    "minigame_pest_control": [ANY(C(MELEE, 120), S("RANGED", 60), S("MAGIC", 60)), S("DEFENCE", 60), S("HITPOINTS", 75)],
}

WILDERNESS = [
    "pvm_callisto",                 # Callisto's Den, level 40 Wilderness
    "pvm_venenatis",                # Silk Chasm, level 35 Wilderness
    "pvm_vetion",                   # Vet'ion's Rest, level 35 Wilderness
    "pvm_scorpia",                  # Scorpion Pit, level 53-55 Wilderness
    "pvm_chaos_elemental",          # deep Wilderness, multi-combat
    "pvm_chaos_fanatic",            # level 42 Wilderness
    "pvm_crazy_archaeologist",      # level 23 Wilderness
    "pvm_king_black_dragon",        # lair entrance is in the Wilderness (level 42 walk)
    "pvm_corporeal_beast",          # Corporeal Beast's Lair is in level 21 Wilderness
    "pvm_revenants",                # Revenant Caves, level 17-40 Wilderness
    "pvm_revenant_maledictus",      # Revenant Caves
    "pvm_lava_dragons",             # Lava Dragon Isle, level 36-42 Wilderness
    "pvm_green_dragons",            # mainly Wilderness (Myths' Guild alternative after DS2)
    "pvm_zombie_pirates",           # Chaos Temple, level 11 Wilderness
]

NOTES = {
    "pvm_zalcano": "No recommended combat stats on the wiki; it is a Mining/Smithing boss whose hard requirements already cover the skills.",
    "pvm_gemstone_crab": "No recommended stats on the wiki; it is a combat training boss meant for any level.",
    "pvm_brutus": "No recommended stats on the wiki; the main page says it is designed for low-level players and can be fought almost straight after starting.",
    "pvm_alchemical_hydra": "Strategies page has no stats section; taken from Money_making_guide/Killing_the_Alchemical_Hydra (Ranged 75+, Prayer 74+, Hitpoints 85+).",
    "pvm_kreearra": "Wiki lists Ranged 70 (=hard requirement, 80 ideal) and Combat 85 'required'; combat level cannot be expressed in these forms, so it is left out.",
    "pvm_commander_zilyana": "Wiki also suggests Combat 100+, which cannot be expressed and is left out.",
    "pvm_general_graardor": "Strength 70 is both the hard requirement and the wiki minimum, so melee is gated on Attack 80 only.",
    "pvm_kril_tsutsaroth": "Used the 'Attacker' column (lower) rather than 'Tank'.",
    "pvm_theatre_of_blood": "Used the Entry Mode minimum (75 in all combat stats); Normal/Hard mode suggest 90+ melee, 95 HP. Ranged/Magic 75 omitted as the hard requirement is already 80.",
    "pvm_chambers_of_xeric": "Raid needs all styles, so melee, Ranged and Magic are all required rather than ANY_OF. Defence 70 and HP 80 also listed but trimmed to keep 4.",
    "pvm_tombs_of_amascut": "All styles listed (70 each); trimmed Defence 70 and HP 75 to keep 4. Difficulty scales with invocations.",
    "pvm_fortis_colosseum": "Wiki minimum for a first completion is 95 in all combat stats (Magic 94, Prayer 85); Magic 94 and HP 95 trimmed to keep 4.",
    "pvm_the_gauntlet": "Wiki lists all styles (Att 80, Str 85, Def 80, Rng 85, Mag 80, HP 85, Pray 77); chose melee-or-ranged plus Magic. Judgement call.",
    "pvm_tormented_demons": "Wiki says 'high stats in at least 2 combat styles'; expressed as melee AND ranged. Magic 82 (Dark Demonbane) left out.",
    "pvm_demonic_gorillas": "Gorillas need melee and ranged switches, so both are required.",
    "pvm_dagannoth_kings": "Each king is weak to a different style, so melee, Ranged and Magic are all required.",
    "pvm_yama": "Wiki lists Magic 82 and Att/Str 80 without clearly saying either/or; expressed as ANY_OF. Prayer 63 is the wiki's stated acceptable minimum (Mystic Vigour).",
    "pvm_maggot_king": "Wiki lists Ranged 85 (if ranged), Magic 75 (if magic) and Att 80/Str 85 untagged; treated as three alternatives. Prayer 74/Defence 70 are for Rigour ('highly recommended').",
    "pvm_doom_of_mokhaiotl": "Prayer 43 is the wiki minimum; it highly recommends 74 Prayer + 70 Defence for Rigour.",
    "pvm_cerberus": "Melee is the main style on the wiki; Ranged 75 is listed too but appears to be a secondary switch, so it is omitted.",
    "pvm_venenatis": "Magic 94+ also listed, omitted.",
    "pvm_green_dragons": "Strategies page suggests 70 melee/Ranged/Def/HP; the activity description currently says 80+ combat or 60+ Ranged, which does not match. Wilderness flag reflects the main spots; Myths' Guild is a safe alternative.",
    "pvm_corporeal_beast": "Melee column used for HP 85 / Prayer 70 (ranged column wants HP 90 / Prayer 74); Defence 94 applies to melee only so it was left out. Lair is in level 21 Wilderness but a games necklace teleports straight there.",
    "pvm_king_black_dragon": "Lair itself is not Wilderness, but the walk in passes level 42 Wilderness, so it is flagged.",
    "pvm_bryophyta": "From the page text: ranged 60 Ranged/40 Def/37 Prayer, magic 59 Magic/37 Prayer, melee at least 50 Att/Str/Def.",
    "pvm_obor": "From the page text: ranged 60 Ranged/50 Def/40 Prayer, melee 50 Att/Str/Def/40 Prayer.",
    "pvm_cave_horrors": "Money making guide wording is muddled ('Magic 70, Attack, Defence or Ranged 50 (along with 70 Defence)'); interpreted as Magic 70 or Ranged 50, plus Defence 70.",
    "pvm_gargoyles": "Money making guide says Attack/Strength/Defence '70/80+'; used 70.",
    "pvm_urium_shades": "From Money_making_guide/Killing_urium_shades: Attack/Strength/Defence 80+.",
    "pvm_kurasks": "From Money_making_guide/Killing_kurasks.",
    "pvm_hydras": "From Money_making_guide/Killing_hydras: Attack/Strength/Defence 90+, Prayer 70+.",
    "pvm_spitting_wyverns": "From Money_making_guide/Killing_Spitting_Wyvern.",
    "pvm_wyrms": "From Money_making_guide/Killing_Wyrms_(Slayer).",
    "pvm_drakes": "From Money_making_guide/Killing_Drakes; Prayer 40 for Protect from Missiles (74 for Rigour).",
    "pvm_zombie_pirates": "From Money_making_guide/Killing_zombie_pirates_(Budget).",
    "pvm_vyrewatch_sentinels": "Wiki lists Prayer 43 as a requirement (Protect from Melee); activities.json lacks it, so it is kept here.",
    "pvm_skeletal_wyverns": "Per-style lists; only the offensive stat is common to all, so a single ANY_OF.",
    "pvm_basilisk_knights": "Per-style lists (melee also wants 80 Def/HP, 70 Prayer); only the offensive ANY_OF is kept.",
    "pvm_lizardman_shamans": "Defence 20 is the wiki's minimum (60 recommended).",
    "pvm_revenants": "Same Suggested skills section as Revenant maledictus (Revenants/Strategies).",
    "pvm_revenant_maledictus": "Uses Revenants/Strategies (the activity's strategyUrl).",
    "minigame_fight_caves": "TzHaar Fight Cave/Strategies table; used the standard 'Ranged only' column. Pures (Def <20) and early-game irons (61 Rng, 50 Def, 43 Pray) are listed lower.",
    "minigame_barbarian_assault": "Barbarian Assault/Strategies: the non-solo attacker figures (80+ if solo attacking).",
    "minigame_pest_control": "Pest Control/Strategies 'Recommended skills'.",
}
