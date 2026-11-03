# tools

Scripts that generate the bundled content database. They are not part of the
build — the plugin ships the generated JSON, not these — but the JSON should
be regenerated from here rather than hand-edited, so the provenance of each
field stays traceable.

## Regenerating the content database

Run from the project root (the directory containing `build.gradle`):

```
python tools/gen_data.py
python tools/gen_methods.py
```

That rewrites both `src/main/resources/com/nextstep/activities.json` and
`item-sources.json`. Then run `./gradlew test` — `ContentDatabaseTest` is what
catches a dangling quest reference or a bad skill name, and those failures are
otherwise invisible at runtime.

### gen_data.py

Builds every activity and item source. Contains, in order:

- `WIKI_SKILLS` — skill, quest-point and combat-level requirements per quest,
  transcribed from the OSRS Wiki's consolidated `Quests/Skill requirements`
  table. Wiki-derived; trust it.
- `NO_REQ_QUESTS` — quests with no requirements of their own that still
  matter as links in a chain.
- `QUEST_PREREQS` — **hand-entered.** The wiki does not tabulate quest-to-quest
  prerequisites anywhere fetchable, so this is the part most likely to be
  wrong and the first place to look when a chain comes out odd.
- `PVM`, `MISC`, `COLLECTION`, `CA_TIERS` — bosses, minigames, collection log
  goals and Combat Achievement tiers.
- `ITEMS` — every `gameItemId` was read out of RuneLite's own `ItemID` class
  rather than recalled. Do the same when adding more; two ids written from
  memory during the first pass were wrong.

### gen_methods.py

Builds `methods.json` — every training method, its level bracket, its xp/hr and
its notes. Rewrites that file in place; its output is byte-identical to what
ships, so regenerating is safe and is the right way to change the data.

Conventions it encodes, all of which came from play testing rather than the
wiki's framing:

- `xpPerHour` is the rate **at the bottom of the bracket**, not the method's
  level-99 ceiling. Quoting the ceiling makes a method look better than it will
  be for the player reading the step, and it can flip the ranking.
- A method whose rate climbs steeply gets **several brackets** following the
  real curve rather than one average — see the 1.5-tick teak and Knights of
  Ardougne rows. `SkillPathPlanner` merges adjacent segments that resolve to
  the same methods, so fine brackets cost nothing in readability.
- A bracketed method carries its **note on the first bracket only**. Writing it
  on every bracket used to print the same paragraph under all nine Knights rows.
  `TrainingMethodDatabase` now copies a note down the brackets of its method at
  load time, and the panel's repeat check keeps it to one appearance — so a plan
  starting part-way up a method (Thieving from 76, above the 55–60 row that
  holds the Knights note) still shows it. Keep writing it once.
- Notes are one short sentence. The panel is 240px wide and the time estimate
  is rendered separately, so a note does not need to restate the rate — and a
  Wilderness method does not need to say "Wilderness", because the option
  carries a tag that does.
- `risk: PVP` for anything in the Wilderness, and only methods that are
  actually recommended somewhere get an entry at all.

### diary_data.py

All 48 Achievement Diary tiers, read from each region's own wiki page (the
summary `Achievement Diary` page only has aggregate maxima across all twelve
regions and is useless for this).

Two known inaccuracies, both deliberate:

- The wiki lists tier requirements cumulatively, so a Hard entry repeats
  Medium's skills. Kept as-is — it is what you need in hand to finish that
  tier.
- Entries the wiki marks "(started)" or "Partial completion of" mean you only
  need to have *begun* that quest. There is no way to say that in the
  requirement model, so they became full quest requirements. Over-requiring
  beats dropping the step, but a `QUEST_STARTED` requirement type would fix
  it properly.

Diary activity ids are `REGION_TIER` and must keep matching what
`DiaryTracker` writes into `PlayerState`, or completion stops reading across.
Same applies to `CA_<TIER>` and `CombatAchievementTracker`.

## MakeIcon.java

Regenerates both icons as pixel art:

```
java tools/MakeIcon.java src/main/resources/com/nextstep/icon.png      # 16x16 sidebar nav button
java tools/MakeIcon.java icon.png 3                                    # 48x48 plugin hub listing
```

Drawn pixel by pixel rather than with antialiased Graphics2D, which turns to
mush at that size on RuneLite's dark sidebar.

The hub icon is the same art scaled by whole pixels (nearest-neighbour), not a
second drawing, so the listing and the sidebar stay recognisably one icon and
the pixel edges stay hard. The hub allows up to 48x72 at the repository root.
