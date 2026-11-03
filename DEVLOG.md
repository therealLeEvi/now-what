# Next Step - development log

**For what the plugin is and how to install it, read [README.md](README.md).**
This file is the long-form record of how it was built: the decisions, the
reasoning behind them, and the approaches that failed. It is written for
whoever maintains this next, which is mostly a future version of the person who
wrote it. It is kept in rough chronological order, so the earliest sections
describe a much smaller program than the one in the repository now.

---

Suggests what to do next in OSRS based on live skills, quest completion,
achievement diaries, combat level, and (partially) collection log / boss KC.
Also answers "what do I need to do before I can do X" (e.g. Zulrah needs
Regicide, which needs Roving Elves, plus 70 Ranged/Magic) by walking the
requirement graph backwards.

## The prerequisite resolver

`engine/PrerequisiteResolver.java` is the new piece. The trick that makes it
work with no extra data structures: a quest is just an `Activity` with
category `QUEST`, and its own `requirements` list can include other quests
(`QUEST_COMPLETE`). So "what unlocks Zulrah" recurses through
`activities.json` the same way for every level of the chain — resolve
Zulrah's unmet requirements, and if one of them is a quest, resolve *that
quest's* unmet requirements too, and so on, until everything left is a bare
skill level / KC / combat level with nothing further to chain to.

Two things worth knowing:
- If a requirement points at a quest/diary tier that has no matching
  `Activity` entry in `activities.json`, the resolver reports it as an
  **unresolved reference** rather than silently ignoring it (see the
  `Regicide` → `Roving Elves` example in the sample data — Roving Elves
  isn't in the demo database on purpose, to show what that looks like).
  This means the tool's usefulness is directly proportional to how complete
  `activities.json` is — it can't tell you about a prerequisite it doesn't
  know exists.
- Circular requirements are detected and stopped rather than causing a
  stack overflow — this should never happen with correct game data, so if
  you see it in practice it means an error in `activities.json`, not a bug
  worth chasing in the resolver itself.

The panel now has a dropdown + "Check requirements" button that lists every
activity in the database and shows the ordered chain (including diary-tier
chains, e.g. Ardougne Medium requires Ardougne Easy first) when you look
one up.

## Status: architecture + working skeleton, not yet feature-complete

This is a real starting point, not a finished plugin. Here's exactly what's
solid and what still needs work before this is submittable to the Plugin Hub.

### Solid / ready to use
- **Skills & combat level** — `client.getRealSkillLevel(Skill)` and
  `LocalPlayer.getCombatLevel()`, both reliable, live, no caveats.
- **Quest completion** — `Quest.getState(client) == QuestState.FINISHED`,
  reliable and live.
- **Suggestion engine** (`engine/SuggestionEngine.java`) — pure Java, no
  RuneLite dependency, unit-testable as-is. This is the part most worth
  spending design time on (ranking heuristics, "almost there" logic, etc.)
  since it's independent of how the data gets in.
- **Activity schema** (`model/Activity.java`, `model/Requirement.java`,
  `activities.json`) — extensible ruleset format. Adding new content is
  editing JSON, not Java.

### Needs real work before shipping
1. **Diary varbit IDs** (`data/DiaryTracker.java`) — the bit-packing logic
   (easy=1, medium=2, hard=4, elite=8 per region varbit) is confirmed
   correct, including the known upstream bug where diary-caped accounts
   read -1 instead of 15. But the actual varbit ID per region is still a
   placeholder (`-1`) in every entry. You need to fill these in using
   RuneLite's Developer Tools → Varbit inspector while toggling diary tasks,
   or by cross-referencing an existing open-source diary-tracking plugin's
   varbit table (e.g. search the plugin hub for "Diary Requirements" or
   similar and check their source for the constants — don't guess).
2. **Boss kill counts** — there's no single clean client-side source. Two
   viable approaches:
   - Parse the "Your Zulrah kill count is: X" style chat message RuneLite
     already receives (fragile if Jagex changes wording, but zero network
     calls).
   - Query the OSRS Hiscores API (`https://secure.runescape.com/m=hiscore_oldschool/index_lite.json?player=NAME`)
     as a fallback — this requires a network call, which is fine for Plugin
     Hub review as long as it's clearly optional/toggleable (see
     `useHiscoresFallback` in the config) and only ever sends the player's
     own name.
3. **Collection log data** — only reliable once the player has opened their
   log at least once in-game (client-side cache limitation shared by the
   existing "Collection Log" plugin). The cleanest path is *not* to
   reimplement this from scratch: read it out of the existing Collection Log
   plugin's persisted `ConfigManager` group if it's installed, rather than
   re-deriving it from raw widget data yourself.
4. **Quest points total** — `Quest` enum doesn't directly expose reward QP
   in a simple summable way in all RuneLite versions; needs verification
   against the current API before the `QUEST_POINTS` requirement type is
   trustworthy.
5. **Content maintenance** — `activities.json` currently has 7 example
   entries covering each category, purely to prove the schema. Building out
   full requirement coverage (all diary tiers, all quest chains, popular
   bosses, collection log categories) is a data-entry project, arguably
   bigger than the code itself. Consider seeding this from the Wiki's
   structured quest/diary requirement tables rather than typing it by hand.

## Before you build this for real

A very similar plugin — "Goal Planner" — is currently an **open, unmerged**
Plugin Hub PR (search `runelite/plugin-hub` PR #11552). It covers overlapping
ground (goals across skills/quests/diaries/CAs/KC/collection log with
auto-seeded prerequisites). Worth reading its source for prior art on the
exact same varbit/data problems above before you re-solve them, and worth
checking whether it gets merged — if so, differentiate by leaning into
*proactive* personalized suggestions and ranking (this plugin's actual value
prop) rather than duplicating goal-tracking.

## Plugin Hub review notes
- **No network calls at all.** This originally read "no network calls by
  default; the Hiscores fallback is opt-in" — but the fallback was never
  implemented, and the setting that advertised it defaulted to *on*. The
  setting has since been deleted (see "The settings that did nothing"), so the
  claim to make in the PR description is the simpler and now true one: the
  plugin ships its content database and queries nothing.
- Don't use `client.menuAction` or automate any in-game actions — this is a
  read-only advisory panel, which keeps it clearly on the right side of
  Jagex's third-party client rules.

## Building
Requires the RuneLite plugin development environment (JDK 11, Gradle). See
RuneLite's own "Plugin Development" wiki page for project setup —
`build.gradle` here assumes you're building as an external Plugin Hub
plugin, not editing the RuneLite client source directly.

## Field report: what an actual installed competitor gets wrong

Having Goal Planner (AKAddons/runelite-goal-planner) installed and in hand
surfaced two things worth designing around:

1. **Item-goal selection has a live bug** (as of testing this) — selecting
   an item to track as a goal doesn't register. This is exactly the class
   of bug the RuneLite item-search widget is known for across multiple
   plugins built on `ItemManager`/`ItemContainer` search. Worth filing on
   their tracker rather than assuming it's user error, and worth writing
   your own item-search integration defensively (test it against items
   with punctuation/apostrophes in the name, which is a classic trigger).

2. **The real gap, and the one worth building around**: Goal Planner (and
   QuestPath) will tell you *what* you're missing — "47 Agility" — but not
   *how to actually get there*. Neither project claims to solve this for
   general skill training. This is now baked into the schema: `Requirement`
   has an optional `hint` field (see `activities.json`) carrying a short,
   concrete training-method suggestion, rendered under each unmet
   requirement in the panel's lookup result. This is the single most
   differentiated thing this plugin can do that the two more mature
   competitors don't.

   Scaling this past a handful of hand-written hints eventually wants its
   own small per-skill "best method at this level range" table (method
   name, rough XP/hr, GP cost tier) rather than a hint string per
   individual requirement — worth a dedicated `TrainingMethod` data file
   once the requirement database itself is more complete, so a method
   isn't re-typed every time the same level range shows up as a
   requirement for a different activity.

## Path planning — "set a goal, get a route there"

The panel now has three ways to set a goal, each producing an ordered,
actionable `Path` instead of a static requirement list:

- **Activity goal** — any quest/diary/pvm/etc already in `activities.json`.
  Internally still uses `PrerequisiteResolver`, but every unmet `SKILL_LEVEL`
  requirement along the chain now gets expanded into real training steps
  instead of stopping at "Agility level 47".
- **Skill goal** — pick a skill + target level. `SkillPathPlanner` walks
  `methods.json` and emits one step per method segment, switching methods
  at the level where a better one becomes available (e.g. Al Kharid course
  20–30, then Ardougne rooftop 30–52, then Seers' 52–60...) rather than
  naming a single method for the whole range.
- **Item goal** — type an item id (matching `item-sources.json`) + quantity.
  Recursively resolves `CRAFT` recipes into their ingredients (each
  ingredient is itself resolved the same way, with cycle detection) until
  everything bottoms out in a `GE_BUY`, `SHOP`, or `MONSTER_DROP` leaf step.

### Data files driving this
- `methods.json` — per-skill list of `{skill, minLevel, maxLevel, name,
  xpPerHour, notes, wikiUrl, intensity, risk}`. All 23 skills are seeded, at
  varying depth — a skill covered by two or three broad brackets is thinner
  than one whose method genuinely changes every ten levels, and thin data
  reads as confident because the panel can't tell the difference. Hunter
  was the case that proved it (see the Wilderness-risk section above).
- `item-sources.json` — per-item `{id, displayName, type, ...}`. Only 3
  items seeded, including one full `CRAFT` chain (Zamorak brew ← Cadava
  berries + Jangerberries) to prove the recursion works. Scaling this to
  useful coverage is, like `activities.json`, primarily a data-entry
  project — the OSRS Wiki's structured item/recipe data would be the
  practical source to derive it from in bulk, the same way QuestPath
  derives its quest data from Quest Helper's source rather than hand-typing
  requirements (see the earlier note on that).

### Known limitations, stated plainly
- **Item goals don't check what you already own.** There's no bank/
  inventory reading wired up yet (same open integration point noted
  earlier for collection log), so an item goal always plans for the full
  target quantity rather than "target minus what's already in the bank."
  Goal Planner's `ItemTracker` (see the earlier architecture notes on its
  bank-visibility handling) is the reference implementation to follow here.
- **Drop-rate KC estimates are rough placeholders**, not real droprate
  math — `estimatedKc` in `item-sources.json` is a single guessed number,
  not `1 / dropRate` computed from actual wiki droptable data. Treat every
  `KILL_BOSS` step's kill count as "ballpark," not a promise.
- **No cross-goal deduplication.** If two different requirements along a
  chain both need Agility training, each gets its own independent
  `SkillPathPlanner` call rather than one merged Agility training block
  spanning both — functionally correct (you'd train through both ranges
  regardless) but the output can look redundant on a long chain. Worth
  fixing by collecting all SKILL_LEVEL demands for a Path first and
  planning each skill once at the end, rather than inline per-requirement.

## Tick manipulation as a first-class preference, not a hidden default

Some training methods (tick-perfect drop-and-reclick timing, "3-ticking",
etc.) sit at the top of the xp/hr table specifically because they demand
constant precise timing against the game's tick rate — legitimate,
wiki-documented, and something a meaningful chunk of players deliberately
avoid because of how click-intensive it is. The planner treats this as data,
not an afterthought:

- `TrainingMethod.intensity` is `NORMAL` (default) or `TICK_MANIPULATION`.
- Ranges are allowed to **overlap** for the same skill — a tick-manip method
  and its calmer fallback commonly cover the identical level bracket at very
  different xp/hr (see the Mining 45–99 and Woodcutting 35–99 entries in
  `methods.json` for worked examples of both a NORMAL and a
  TICK_MANIPULATION method occupying the same range).
- `Goal.allowTickManipulation` (set from a checkbox in the panel, defaulting
  to the `allowTickManipulation` config option — off by default) controls
  whether `SkillPathPlanner` considers TICK_MANIPULATION candidates at all.
  When off, it picks the best *remaining* method for each level rather than
  either downgrading silently or recommending tick manipulation anyway.
- If a level range in the data genuinely has **no** non-tick-manip method
  recorded, the planner says so explicitly ("no calmer fallback recorded
  yet") instead of silently falling through to no plan at all — that's a
  real content-database gap worth filling, not a bug.
- Selection is **exclusive, not additive**: choosing "use tick-manipulation
  methods" doesn't add the fast option alongside the normal ones — it shows
  only tick-manipulation methods for each segment, falling back to normal
  methods just for the segments where no tick-manip method is recorded.
  Choosing normal methods never falls back the other way, since that would
  defeat the entire point of picking it. Mixing both intensities into one
  option list was the original design and turned out to be exactly the
  clutter this preference exists to avoid — fixed by having
  `SkillPathPlanner` pick one intensity band per segment rather than
  filtering a combined list.
- Even when tick manipulation is selected, the chosen step is labeled
  `[Tick manipulation]` in its title so the plan stays honest about what
  it's asking of the player rather than blending it in as if it were
  ordinary training.

This preference is threaded through every path that can hit a skill
requirement — a direct skill goal, a skill requirement buried inside a
quest's prerequisite chain, and a skill requirement gating a crafting
recipe for an item goal — so the setting means the same thing everywhere
rather than only applying to the one obvious case.

## Wilderness risk is part of the recommendation, not a footnote

`TrainingMethod.risk` is `SAFE` (default) or `PVP`, and it exists for the
same reason `intensity` does: an xp/hr figure alone silently flattens a real
trade-off. A method trained in the Wilderness costs you your gear and your
caught stock when another player kills you, and that never shows up in the
rate — so a plan that recommends one without saying where it's trained is
recommending a risk the player never agreed to.

- Any `PVP` method is labelled `[Wilderness — PvP risk]` in its option
  title, next to the rate it's being traded against, and its `notes` spell
  out the specific exposure (which Wilderness levels, what's lost on death).
- Unlike `intensity`, risk is **labelled, not filtered**. There's no setting
  that hides dangerous methods, because the safe alternatives are in the
  data for the same brackets anyway — the fix for "this plan sent me to the
  Wilderness" is having the safe option visible beside it, not a toggle that
  quietly removes a legitimate choice.
- Options stay sorted purely by xp/hr, so a fast Wilderness method can still
  sort above a safe one. That's deliberate: the sort is a rate hint and the
  label carries the rest. If a safety-weighted sort is ever wanted, it
  belongs behind an explicit preference rather than a hidden tie-break.

The Hunter 63–99 data is the worked example. The old table had a single
`Black chinchompas` entry starting at 63 (they're 73), rated at 65,000 xp/hr
(they're roughly 145,000 at 73), with no mention of the Wilderness and no
safe alternative recorded at all — so every Hunter plan past 63 pointed at
the Wilderness and understated it by more than half. It now carries
razor-backed kebbits, red chinchompas, rainbow crabs, herbiboar and both
Hunter rumour tiers alongside it, and from 72 upward the Expert rumour tier
(~160,000 xp/hr, entirely safe) sorts above black chinchompas on its own
merits.

Seven methods across six skills now carry `risk: PVP` — the Wilderness
Agility Course, Rogues' Castle chests, zombie pirates, Chaos Temple bones,
Abyss runecrafting, dark crabs and black chinchompas. Each one has at least
one safe method covering the same bracket in the data, which is the
invariant that makes labelling rather than filtering the right call: the
Wilderness is never the only thing the plan can suggest.

## Level scaling is bracketed, and identical neighbours are merged

Some methods change rate enormously across their range: 1.5-tick teaks run
93k xp/hr at level 35 and 235k at 99; lava runes depend less on level than on
which pouches you own, from 43k with none to ~101k with a colossal. One
averaged number for either is wrong at both ends, so they're recorded as
several brackets following the real curve.

That created a second problem, since breakpoints come from *every* method
regardless of intensity (deliberately — so segment boundaries don't move when
the tick-manipulation preference changes). Bracketing teaks at 41/50/61/71/80/90
split the **normal** Woodcutting plan at those levels too, where nothing about
the normal options changes, producing consecutive steps that both read
"Teak trees".

`SkillPathPlanner` now resolves segments in one pass and merges adjacent ones
that resolved to the same methods in a second, before emitting steps. A
Woodcutting 1–99 plan went from 14 steps to 9 at normal pace, while the
tick-manipulation plan keeps all seven teak brackets — each is a genuinely
different rate, so none of them merge. Fine-grained data no longer costs
readability for the players who aren't using it.

Where a rate quoted in a note is a ceiling, the note says what it assumes:
Knights of Ardougne's ~252k is 95+ Thieving in a full rogue outfit, not
something a level-55 player will see.

### …and a run of brackets you spend on one method is one step

Merging equal method *sets* is not enough on its own, because the sets change
around a method that stays put. Thieving 76–99 breaks into eight brackets:
the Knights of Ardougne rate ramps through 141k → 240k, vyres open at 82,
Rogues' Castle at 84, Pyramid Plunder at 91. Blackjacking menaphite thugs
covers the whole range at one rate — so a player who picks it reads their own
method name under eight separate headings, eight times, which is what a player
told us: *"it shows blackjacking thugs from 76-99 but it does so in brackets so
every step is actually the same"*.

Which method a bracket shows depends on what the player has chosen, so this
cannot be settled in the planner — `NextStepPanel` recomputes the runs on every
redraw. Consecutive brackets showing the same method collapse into one step
covering the whole range, with the xp summed and one estimate over the lot:

```
STEP 1 · 11.7M XP
Thieving 76 → 99
✔ Pickpocketing Knights of Ardougne
~58 HRS · 141K–240K XP/HR
Change method (8 level brackets)
```

The estimate is summed bracket by bracket rather than divided once, because the
same method is often several rows with a rising rate, and dividing 11.7M xp by
any single one of those rates is wrong for most of the range. The rate is shown
as the span it actually covers. Thieving 76–99 goes from 8 steps to 1; a full
1–99 plan goes from 14 to 6; Hunter 1–85 from 12 to 7.

Nothing is hidden: `Change method (8 level brackets)` reopens the run as the
individual brackets, each with its own options, and `← Back to one step (76 →
99)` closes it again. A bracket whose option list is already open is never
swept into a run — its choice is on screen, and merging would hide it
mid-decision. A quest route that only covers part of a merged run stays visible
underneath it, labelled with the sub-range it covers, for the same reason quest
routes aren't sorted to the top: folding the shortcut away would be deciding for
the player in the other direction.

## Multiple viable methods, not a forced single winner

Collapsing a level range to one "best" method throws away a real choice:
past Mining 45, Motherlode Mine, Blast Mine, gem rocks, and calcified rocks
are all legitimate, meaningfully different options (xp/hr, GP cost/profit,
click intensity even among normal-pace methods) — the planner has no basis
to silently pick a winner among them.

So `SkillPathPlanner` no longer resolves a segment to a single method.
Instead:

- Segments are split at every point where the *set* of eligible methods
  changes (any method's `minLevel`/`maxLevel` boundary) — not at fixed
  intervals — so each step's option list is exactly the methods valid
  across that whole sub-range, never a stale approximation.
- Every method covering a segment becomes a `PathStep.Option`, sorted by
  xp/hr as a hint, but all of them are kept — see the Mining example in
  `methods.json` (Motherlode Mine, Blast Mine, gem rocks, calcified rocks,
  plus a tick-manip alternative, all overlapping 40–99 with different
  minLevels) and the panel's bulleted rendering under each step.
- This is independent of the *number of alternatives shown within one
  intensity* — turning tick manipulation on doesn't collapse choice down to
  one option either; if two different tick-manip techniques both cover a
  segment, both are shown. What it does do is keep the two intensities from
  being mixed into the same list (see the note above) — one is shown at a
  time, by design, not as a limitation.
- `PathStep` itself changed shape to support this: every step (not just
  TRAIN_SKILL) now carries a `List<Option>`, with the single-item case
  (one option) used for ACQUIRE_ITEM/KILL_BOSS/DO_ACTIVITY/INFO steps so
  there's one consistent shape for the panel to render rather than two
  parallel step representations.

## A plan you can narrow down, not just read

Showing every viable method per segment is right as *data* and wrong as a
default *view*: a Hunter 63-99 plan is a dozen brackets deep, and at five
options each it becomes a wall of text you scroll past rather than a plan you
follow. The panel now folds it:

- **Steps start collapsed** on their top-rated option, with the count of what
  is folded away always visible (`Show 4 other methods`). Nothing is hidden —
  the alternatives are one click off, and the planner still hands the panel
  every one of them.
- **Clicking an option chooses it.** The step collapses to that method with a
  green check, and the link becomes `Change method (4 others)`.
- **A choice carries across brackets.** Choices are remembered by option
  title, not by step, so picking Hunter rumours once collapses every later
  bracket that still offers it. The bracket where it stops being available
  re-opens the choice by itself — which is the moment a player actually wants
  to be asked again, and is why this is keyed on the method rather than on
  the step it was picked in.
- `SkillPathPlanner` no longer writes `(choose one)` into a step title.
  Whether a choice is still open depends on what the player has picked, which
  only the panel knows; baked into the title, a collapsed step went on saying
  "choose one" after they had chosen.

Choices live in the panel for the session. They are deliberately not written
to config yet — worth doing, but it needs a key that survives the option
label changing, which the current title-based one does not.

## Quest routes: a round trip out to Quest Helper and back

Some skills barely need grinding at the bottom. Runecraft 1–32 is covered
outright by Client of Kourend, The Ascent of Arceuus, Enter the Abyss, Temple
of the Eye, The Eyes of Glouphrie and The Slug Menace — so recommending
talisman tiaras there is recommending a grind the player can skip.

A training method may therefore carry a `quests` list and no `xpPerHour`,
because "do these six quests" is not an hourly rate. The panel names them and
points at Quest Helper, which walks the player through each quest *and its own
requirements*; they come back here to pick the path up afterwards. That round
trip is the intended shape of the whole plugin — we map the route, they do the
content elsewhere, they return for the next leg.

**A shortcut is shown, not preferred.** Training methods are this plugin's own
contribution and stay the headline of every bracket; the quest route sits
underneath as a visible alternative. Getting this wrong is easy in both
directions, and both were tried:

- Sorting rate-less methods *last* (the original order) buried the quest route
  under the grind, and since steps collapse to their top option, the plugin
  recommended talisman tiaras while hiding the fact that quests skip the
  bracket entirely.
- Sorting them *first* fixed that and broke the opposite way: the quest route
  became the headline for its whole range, so every plan opened by telling the
  player to go and quest, with the training method folded away behind a click.
  That turns "here is a shortcut" into "leave this plugin".

The resolution is plain xp/hr order in the planner, with the panel rendering
any quest route alongside the top method while a step is still undecided.
Once the player picks something, their choice stands alone — deciding for
them in either direction is the thing to avoid, not one particular direction.

**A quest route is spelled out once per plan.** It spans a whole early range,
so it is an option in every segment inside it — four, for Runecraft 1–32.
Printing its note, its six quest names and its Quest Helper pointer under
each one rebuilt exactly the wall of text the collapsing exists to prevent.
Later segments show a single line referring back.

## The sidebar spends its space on the plan

Two sections used to sit under the planner, each a list of activity cards
whose only action was opening the wiki — the exact round trip this plugin
exists to remove.

- **"Available now" is gone.** It cost a screen of height to offer links the
  player could get from the plan itself. `update()` now takes one list instead
  of two, and the plugin no longer asks the suggestion engine for it.
- **"Almost there" stayed, rewired.** It is the one place the panel volunteers
  something you didn't ask for, so the information was worth keeping — it was
  just wired to the wrong verb. Clicking a card now plans the path to that
  activity and selects it in the Activity dropdown, so the controls agree with
  what is on screen rather than a plan appearing from nowhere. Deleting it
  would have thrown away the suggestion for the sake of the bad link.

The skill dropdown also reads `Hunter` rather than `HUNTER` now. That is a
renderer change only: the values stay uppercase because that is how RuneLite's
`Skill` enum names them and what `PlayerState` is keyed on, and title-casing
the value itself would quietly break every skill lookup in the plugin.

## Two ways in: "I know what I want" and "I don't know what to do"

The sidebar opened straight onto three dropdowns, which answers *what do I
want* and has nothing at all to say to the more common *I don't know what to
do tonight*. The player who asked for this put it plainly: *"I struggled many
times in finding something to do and this perhaps gives that idea"*.

So the panel now opens on a fork. **Plan a path** is everything that was there
before. **Surprise me** rolls one thing you can already do, with a row of mood
filters (Anything · Quests · Bosses · Diaries · Minigames · Skilling), a
`Roll again`, and `Plan it →` which drops the full route underneath the roll.

Two details that decide whether it feels broken:

- **The pool is what you can do right now.** `SuggestionEngine.getAvailableNow`
  already computed it for the old suggestion list — every requirement met,
  not already done — so a roll never offers something you can't start. Logged
  out there is no state to filter by, so it rolls from the whole catalogue and
  says so in the panel rather than sitting empty.
- **The mood filters wrap.** Six of them in a link row walked straight off the
  right edge of a 190px sidebar — a link row lays its children out
  horizontally and never wraps — so they sit three to a line.
- **The draw is weighted by priority, not flat.** Two thirds of the bundled
  activities are quests (168 of 256), so a flat draw would answer "do a quest"
  two times in three. Weighting by each activity's own priority keeps the long
  tail reachable while the notable content comes up more often. A roll also
  remembers its last six answers, so `Roll again` actually changes the answer —
  six and not more, because a narrow category filter can leave a pool only a
  few deep.

On looking players up by username: a hiscores lookup can see levels but not
quests, diaries or collection log, and inside a client we already read all of
it live. The lookup would be a worse version of what the plugin already has, so
the roll uses the live state and the username stays out of it.

## Owning one of something shouldn't make the question unaskable

An item goal subtracts what you already have, which is right — asking for 3 of
something you have 2 of is a one-item job. But it made "how do I get one of
these" unanswerable for anyone who already owns one: the plan came back
*Already done*, and the only way to see a route was to ask for two and read a
plan for the wrong quantity. In the player's words: *"it shouldn't already say I
have one there because then I always need to select minimal of 2 of said
item"*.

So counting what you own is now a stated choice rather than an assumption. The
item block carries a **Count what I already own** checkbox, on by default, and
a plan that came back complete only because of what's in the bank offers
**Plan it anyway, as if I had none** rather than ending there. The plan also
says what made it complete — *"You already have 1, counting your bank,
inventory and gear"* — since "Already done" on its own reads like the plugin
has no route rather than like the item is sitting in your bank. That matters
twice over, because the ownership read can be wrong: the bank snapshot is
whatever the player last opened.

## How the pointer at another plugin actually works

Three states, worded for what the player has:

| | |
|---|---|
| Installed and on | "Quest Helper has the walkthrough for this quest." Plain text, no link — telling someone to install a plugin that is running in the same sidebar is the kind of thing that makes a plugin feel like it isn't paying attention. |
| Installed, switched off | "Quest Helper is installed but switched off — turn it on for step-by-step help." **Clicking it enables the plugin**, off the EDT, and the panel redraws into the first state. |
| Not installed | "Install Quest Helper from the Plugin Hub for step-by-step help ↗", opening `runelite.net/plugin-hub/show/<id>`. |

The middle one used to be a dead-end sentence telling the player to go and do
something the panel could do itself. It is the only thing Next Step does *to*
another plugin, it happens on a click of a line that says so, and a plugin that
refuses to start says so rather than offering the same dead link again.

**What is not possible:** opening another plugin's side panel. `ClientToolbar`
can open a `NavigationButton`, but only the plugin that built one has a
reference to it — there is no registry to look another plugin's up in. So the
handoff ends at "it's installed and ready", and the player clicks its icon.

Detection stays dependency-free: `PluginLookup.match` takes the descriptor
names and class names of the installed plugins and returns an index, so Next
Step never compiles against Quest Helper and runs identically without it. It
is a pure function in its own class because it is the part that can be wrong
invisibly — and was. The class-name fallback used to test for a hardcoded
`com.questhelper` package, so *every* lookup answered "installed" as long as
Quest Helper was there; asking whether Zulrah Helper was installed said yes.
That was caught by chance, in conversation. `PluginLookupTest` now covers it.

## The thing you can click is in a box

Every line in this panel was the same flat column of text, and the only thing
separating a method you could pick from the sentence describing it was a 12px
indent. Four treatments were drawn at the real sidebar width before any of it
was built — hairlines as they were, a bordered card, a left rule, an inset
well — and the bordered card won: a fill one step off the background
(`#2F2D2B`), a 1px edge, 9px of padding, 6px between boxes. A chosen option is
edged in green instead of grey, so a decision shows in the shape of the panel
and not only in the tick on its title.

The box is not decoration, it is the answer to "what can I click": option
boxes hold the things that respond to a click, and the step caption, the step
title and the "change method" link stay outside them. The two options on the
opening screen get the same box for the same reason — there is nothing else
on that screen to tell them apart from the sentence under each.

One trap worth recording, because it is invisible until the text is wrong:
Swing does not wrap a label, so this panel computes a wrap width itself and
builds each line as an HTML table at that width. A box drawn around that text
has to be subtracted from the width or every line inside one runs under its
right edge. `cardInset` is that subtraction, set while a box is being filled.

## The footer sank to the bottom, the hard way

The licence credit sat directly under whatever was on screen, which on a fresh
login is almost nothing — so four lines of attribution arrived immediately
under "Almost there" and read as the answer to the question the player had just
asked. It belongs at the bottom of the panel.

The obvious way to do that hangs the client. `Scrollable.getScrollableTracksViewportHeight()`
returning true while the content is shorter than the viewport stretches the view
so glue can take the slack — and stretching it can make the vertical scrollbar
appear, which takes width away, which is what this panel wraps its text against,
which makes the text taller, which means it no longer fits, which turns tracking
off, which hides the scrollbar. Swing revalidates forever and the panel never
paints. It was caught here because the offline render stopped completing.

So the gap is measured instead: viewport height minus what the content comes to
without the spacer, floored at zero, ignored when it changes by less than 4px,
recomputed when the viewport resizes and after each render. A hairline above it
marks it off as the edge of the panel rather than one more thing to read.

Checking it needed a change to the diagnostics too. `panelshot` built the panel,
clicked through it and photographed it in a single pass on the event thread,
which means nothing the panel schedules for itself — a resize listener, an
`invokeLater` — had run yet. It now builds in one pass and captures in a
second, with the queue drained in between, and the footer shows where it will
really be.

## "Where" is half an answer: what to take comes with it

Three reports, one shape:

- Rolling "Pet snakeling (Zulrah)" and planning it said Zulrah and stopped,
  while picking Zulrah off the activity list gave four kits and seven supply
  lines.
- Asking for an Ancestral robe top said Chambers of Xeric and stopped.
- Asking for 99 Attack said "Maniacal monkeys" and stopped — the one thing a
  player standing in a bank is not asking.

All three ended at the place and left the player to go to the wiki for the
inventory, which is the single thing this plugin exists to stop. They are fixed
in three layers, all feeding the same box:

| | |
|---|---|
| An activity that is a *reason* to fight | `"preparationFrom": "pvm_zulrah"` on the collection log slot. Resolved once at load, never at render, so a reference that no longer resolves is a line in the log at startup instead of a blank space in the panel. |
| An item that drops from a fight | `"preparationFrom"` on the item source. Its `detail` already said "Chambers of Xeric" in prose, but prose is not a reference: "Smoke devils" is the Thermonuclear smoke devil and "Cyclopes in the Warriors' Guild" is not any activity's name, so matching on it would work for some rows and silently fail for others. 19 of the 35 item sources now point at a fight. |
| A training method | `gear` and `supplies` on the method itself, carried into the step's option and rendered inside its box. Recorded on the lowest bracket of a method and shared up the rest at load, exactly as its note already was. |

The heading says whose gear it is — "What to bring to Zulrah" — because a
player who asked for a pet and was handed a blowpipe and anti-venom is owed the
word Zulrah.

Writing the kits exposed the gap underneath: the Fight Caves, the Warriors'
Guild and four other minigames had no preparation at all, which is why Fire cape
and Dragon defender had nothing to borrow. They have kits now. The ones still
bare are the drops with no activity behind them at all — Lizardman shamans,
Dagannoth Kings, the Wilderness bosses — and those need an activity written
before they can have gear.

`iconcoverage` measures the training kits as well as the boss setups now: 762
of 882 lines resolve to at least one picture, 654 are replaced by pictures
outright. That is a floor — it runs against the offline fallback index, and a
client resolves the tradeable half against proper display names.

## It turned out you can open another plugin's panel

This was recorded as impossible, twice: `ClientToolbar` can open a
`NavigationButton`, but only the plugin that built one holds a reference to it,
and there is no registry to look another plugin's up in.

There is no registry, but there is a set. The client keeps the sidebar's
entries, so Next Step reads them reflectively, matches on the tooltip, and
`ClientToolbar.openPanel` does the rest. "Quest Helper has the walkthrough for
this quest. Open it." is now one click, and the fourth state of the handoff.

Reflection into the client's own fields is a thing to be careful with rather
than proud of, so: every part of it is wrapped, a RuneLite release that renames
a field costs this feature and nothing else, and the line falls back to the
sentence it used to be. The matching — exact, then case-insensitive, then
prefix, in that order, because a loose match that beats an exact one opens the
wrong panel — is a pure function in `NavButtonLookup` with its own tests,
which is the same split `PluginLookup` got after its bug.

## The data stopped at the Sailing update

A player asked why the Woodcutting path never mentions ironwood. It did not,
and the reason turned out to be bigger than one tree: the content database was
written before Sailing existed, and three Woodcutting methods are gated behind
it. Ironwood (80 Woodcutting, 72 Sailing, Sunbleak Island), rosewood (92 / 79
Sailing, Drumstick Isle) and camphor (66, 45 Sailing and Troubled Tortugans
started) were all simply absent, along with the engorged bloodwood tree. All
four are in now.

`Skill.SAILING` is in runelite-api and `NextStepPlugin` already walks
`Skill.values()`, so the player's live Sailing level is read today - but there
are **no Sailing training methods and no activity anywhere references Sailing**,
so the plugin cannot plan the 72 Sailing that ironwood needs. Requirements like
that live in a method's note for now, which is how sulliuscep's Bone Voyage and
bloodwood's The Blood Moon Rises are handled - neither of which the data
mentioned either until this pass.

Two corrections fell out of checking the rest: sulliuscep's "~102k at 99 with a
crystal axe" is the *dragon* axe figure, and bloodwood yields sap into buckets
rather than logs, so "the logs sell well" was wrong twice over.

## Attention is not intensity, and not risk

The same player then said the thing that mattered: bloodwood is the best and
also exhausting, while ironwood gives a steady rate for far less effort. The
panel had no way to say so. `Intensity` is a two-value enum - NORMAL or
TICK_MANIPULATION - and the wiki classes bloodwood as the fastest Woodcutting
that is *not* tick manipulation, so bloodwood was correctly NORMAL and sat
beside an ironwood tree you click once every four minutes as though the only
difference were the xp.

So there is a third axis, `TrainingMethod.Attention`, labelled in the card as
`Constant input` or `Nearly AFK`. It is modelled on `Risk.PVP` rather than on
`Intensity`, deliberately: a real cost, decision-changing, invisible in an
xp/hr figure, recorded as data and labelled rather than left for the player to
already know. It does **not** re-rank anything - the planner still sorts by
xp/hr and never prunes - because hiding the fastest method is not an
improvement on failing to describe it.

Three details worth keeping:

- **Unset is not ORDINARY.** A method with no judgement has not been assessed,
  and printing "ordinary" over it would be inventing a fact. The getter returns
  null and the panel prints nothing.
- **Tick-manipulation methods must leave it unset.** They already carry a label;
  two tags saying the same thing is noise. The generator asserts it and
  `ContentDatabaseTest` checks the shipped file.
- **It is populated skill by skill.** Woodcutting is judged end to end and a
  test keeps it that way, because a *partially* judged skill is the dangerous
  state: an unlabelled method next to labelled ones reads as "ordinary".

## Wintertodt named the minigame and said nothing about warm clothing

`TrainingMethod.gear`, `TrainingMethod.supplies`, `inheritPreparation` and
`PathStep.Option.withPreparation` were all implemented. Nothing called
`withPreparation`, and **not one of the 183 methods carried gear or supplies** -
the whole mechanism was dead code. The README said "planning 99 Attack gives
you the inventory for maniacal monkeys", which was never true as shipped.

Wintertodt is the clear case: it is a Firemaking method *and* a minigame whose
entry already lists four warm-clothing slots and a hammer. Copying that into
methods.json would have meant two copies of one answer, so a method can now
name an `activity` to borrow its kit from, exactly as `Activity.preparationFrom`
lets a collection log slot borrow a boss's. Guardians of the Rift was the other
match.

The borrow happens inside `load()`, which now takes the activity list, rather
than in a second call a caller has to remember - six places build this database
and forgetting the second call is invisible: the plan still renders, the gear is
simply gone. That is the bug that was already there.

## One link was answering two questions

Thieving from 55 printed five near-identical blackjacking cards in a row, with
Rogues' Castle and Pyramid Plunder somewhere underneath them. The planner was
right - the brackets genuinely differ, because Knights of Ardougne's rate climbs
through eight rows - but "Show 5 level brackets, 4 other methods" made one link
answer two unrelated questions, and the bracket half buried the other.

They are separate links now. "Show 4 other ways to train this" merges each
alternative across the whole run the same way the headline already was, so
Pyramid Plunder appears **once**, captioned `Covers 91 to 99`, with its rate
range and its note - no bracket repetition to read past. The arithmetic that
only the headline used is now a shared `mergeAcross`, which also taught it to
sum shopping lists over a run instead of taking the first bracket's.

## How many do I actually have to buy

A plan that says "9.2 hours at 1.3M xp/hr" tells you the time and not the cost,
and the division from one to the other is exactly what people were leaving the
client to do in a wiki tab. A method can now record the xp of **one action** and
what that action consumes, and the panel pictures the result with the count
under the icon: 99 Prayer from 70 is 23,423 superior dragon bones.

Two things this got right only after a test said otherwise:

- **Round up to whole actions before applying the per-action quantity.** A
  redwood shield takes two logs; 8.3 shields' worth of xp needs 9 shields and
  therefore 18 logs, not the 17 that falls out of rounding 16.6 at the end.
  Half a shield is not a shield.
- **Never abbreviate a count.** Everywhere else the panel rounds xp to "12.3M",
  because nobody needs the digits. "23k superior dragon bones" is not a number
  you can take to the Grand Exchange.

Only methods naming one concrete item carry a list. `"Dragonhide bodies"` and
`"Cutting gems (sapphire through diamond)"` name a *class*, so there is nothing
to count - and checking them turned up a separate accuracy bug: both quote the
rate of the best material in the class from the bottom of the range, so the
panel claims 313k/hr Crafting from level 63 when black hides need 84. Splitting
them per material fixes the count and the rate together. Also corrected while
checking: a yew longbow (u) needs 70 Fletching, not the 50 we had, and a redwood
shield 92, not 90.

## Still to do before the hub PR

Everything mechanical is done and committed. What is left needs a person:

1. **Run it in a real client.** Nothing in this repository has ever been loaded
   by RuneLite. Every check so far is an offline render (`paneltext`,
   `panelshot`), which catches wrong text and broken layout and cannot catch a
   plugin that fails to load, a nav button that never appears, or a varbit that
   reads differently in a live session. `./gradlew run` side-loads it. This is
   the highest-value item on the list by a distance.
2. **Take a screenshot** once it is running, for the README. An offline render
   is the wrong thing to publish: the item icons come from the client's
   `ItemManager`, so they are missing outside a client, and the picture would
   undersell the panel while looking like a photograph of it.
3. **Create the public GitHub repository and push.** The repository must be
   public for the hub to build it.
4. **Ask the OSRS Wiki Discord** (Bucket channel, contact named on
   `RuneScape:Bucket`) to confirm the attribution approach and how they would
   like it phrased. The licensing note further down this file set this as a
   pre-submission step and it has not been done. Costs nothing, removes the
   ambiguity.
5. **Open the hub PR**: fork `runelite/plugin-hub`, add a file under `plugins/`
   containing `repository=` (the HTTPS clone URL) and `commit=` (the full
   40-character hash), push to the fork, open the PR against master, then watch
   both CI checks. Each later push needs `commit=` updated.

One thing a reviewer may stop on, and it is better to have an answer ready than
to be surprised: the Quest Helper handoff reaches into the client's own fields
by reflection to find another plugin's sidebar button. The hub's README does not
forbid reflection, but it does say that if compliance is hard for them to verify
they will not merge. Every part of it is wrapped, so a RuneLite release that
renames a field costs that one feature and nothing else, and the line falls back
to the plain sentence it used to be. If a reviewer objects, dropping back to the
non-clickable sentence is a small, clean retreat.

## The sources are pure ASCII, because the hub replaces build.gradle

The plugin hub builds a submission in one of two modes. `build=gradle` uses
your own build file; `build=standard` replaces `build.gradle` and
`settings.gradle` with the hub's own, which is what buys the "expedited
review" their README offers for a plugin with no custom dependencies. We want
expedited, and we have no dependencies, so: standard.

The catch is that our `build.gradle` carries this line, and standard mode takes
it away:

```groovy
options.encoding = 'UTF-8'
```

Without it javac falls back to the platform default charset. On a machine where
that is not UTF-8, every em dash in a string literal is decoded as two
characters of garbage, and the panel renders mojibake to a real player. The
hub's replacement build file is not published anywhere in their repository, so
there is no way to look up whether it sets the encoding. Unknowable, and the
failure mode is silent and user-visible.

So the question was turned into one that has no answer to look up: a `\uXXXX`
escape is decoded by javac itself, before the parser sees it, and means the
same character under every encoding. All 139 non-ASCII characters came out of
the Java sources — 37 in string literals became their own escapes, and 102 em
dashes in comments became plain hyphens, since a comment full of escapes is a
comment nobody reads.

Two things made this safe to do mechanically rather than by hand:

- The conversion is semantics-preserving *by construction* for literals: the
  escape denotes the identical character.
- And it was checked rather than assumed. The 79 compiled `.class` files were
  hashed before and after: **byte-for-byte identical**. Then the project was
  recompiled with `options.encoding = 'US-ASCII'` — the exact scenario feared —
  and the class files were identical again. Before the conversion that build
  would have produced garbage; now encoding cannot change what the panel says.

`asciiCheck` runs as part of `./gradlew build` and fails with the file and line
if a non-ASCII character ever comes back. It was tested by planting one.

## The settings that did nothing

Auditing the plugin before publishing turned up something worse than any
missing repository file: of the six settings in the config panel, **five were
never read by any code**. Four `show...` toggles that claimed to filter the
suggestion lists, and a "Use Hiscores as fallback" switch.

The Hiscores one was the serious one. Its description read "Query the OSRS
Hiscores API for KC/CA data not yet cached locally (requires network access)",
it defaulted to **on**, and there is no network code anywhere in this project —
no `URL`, no `HttpClient`, nothing. So it advertised network access this plugin
has never made, to a player reading the settings panel and to a hub reviewer
reading the submission, and the review notes further up this file told a future
me to make a point of that fallback in the PR description. It is deleted.

The other four were wired up rather than deleted, because the panel was already
promising the behaviour and the data supports it in one line: an `Activity` has
a `Category`, so hiding one is a filter. It is applied in `SuggestionEngine`
rather than in the panel, so that an empty list stays honest — a player who
switched quests off should be told there is nothing left to do, not shown a
count including the things being hidden from them. Only the four categories
with a switch can be hidden; MINIGAME, SKILLING, COMBAT_ACHIEVEMENT and
CLUE_SCROLL have no setting, so they always show, and there is a test that says
so. Every default is unchanged, so the plugin behaves exactly as it did.

The general lesson is worth keeping: a dead config option is not a cosmetic
problem. It is the UI making a promise the code does not keep, and nothing in a
build or a test suite will ever catch it.

## What the repository needed before it could be a repository

It was not under version control at all. The pre-publish pass:

| | |
|---|---|
| `.gitignore` | `git add .` would otherwise have committed `build/` (1.4 MB), `.gradle/` (1.1 MB) and a stray `.pyc` into the first commit. |
| `.gitattributes` | Committing `gradlew` from Windows can store it with CRLF, which fails on Linux with a baffling "bad interpreter". |
| `LICENSE` | BSD 2-Clause, the licence the hub's own setup steps point you at. Kept as the bare canonical text so GitHub detects it. |
| `NOTICE.md` | The wiki data is CC BY-NC-SA 3.0 and the code is BSD. Keeping that split explicit is the point; see the licensing note further down this file for the reasoning, which has not changed. |
| `settings.gradle` | Absent, so the project was named after its directory — which is the doubled `next-step-plugin/next-step-plugin`. |
| `runelite-plugin.properties` | `author` was still `YourNameHere`. Added `version` and `build`. |
| Root `icon.png` | The hub lists a plugin with an icon of at most 48x72 at the repository root. This is the same 16x16 nav art scaled 3x by whole pixels through `tools/MakeIcon.java`, not a redraw, so the listing and the sidebar are recognisably one icon. Regenerating the 16x16 produced a byte-identical file, which is the proof the generator still matches what is committed. |
| `README.md` | Was 1,560 lines of this development log, titled "scaffold". Someone landing on the repository needs the plugin explained in a screenful; the log moved here, to `DEVLOG.md`. |
| `mockito-core` | Declared as a test dependency and used by exactly zero tests. The hub asks you to justify every dependency; an unused one is a question you do not need to be asked. |

## Gear as pictures, not just nine item names

A boss's "what to bring" list was nine item names in a row, and a loadout is a
thing players recognise by sight. Each setup now renders a strip of item icons
above its text, six to a row at sidebar width, each hovering to its item name.

Getting there means reading the prose: the gear lines in `activities.json` were
written to be read, not parsed — `Neck: rancour, torture, blood fury`,
`Head/body/legs: Masori (f), Masori, Armadyl`, `8-10 prayer potions (4)`. That
is the right way round, so `ItemTextParser` reads them rather than asking the
data to become a list of item ids. Two things make it tractable: the lines name
their slot often enough to disambiguate shorthand, and a slot prefix naming
several slots means the option is a *set* and expands into one lookup per slot
(so `Head/body/legs: Masori (f)` becomes three pictures of a real kit, not one
arbitrarily chosen helmet).

The parser is deliberately conservative, because a wrong icon beside a boss
setup is worse than no icon:

- every word of a phrase has to appear in the item's name, so "Masori body"
  cannot land on Masori chaps;
- a one-word option with **no slot named** resolves to nothing at all — bare
  "crystal" is a key, a pickaxe and a pair of boots, and guessing is worse than
  silence;
- between equally valid matches the shorter name wins (the plainer item), and
  between names of the same length the bigger dose does, since a guide that says
  "prayer potions" without a number means full ones;
- trailing prose is cut rather than searched: `4+ cooked karambwan for combo
  eating` looks up cooked karambwan.

The text always stays underneath, so anything that doesn't resolve — "skill
cape", "Best pickaxe you can wield", "40 Prayer for Protect from Magic" — costs
nothing. Icons exist only inside a running client, which makes the feature hard
to judge from outside one, so `./gradlew iconcoverage` stands RuneLite's
`ItemID` constants in for the real index and reports what every bundled line
would resolve to. That proxy currently reaches **73% of the 436 gear and supply
lines**, and it is a floor rather than a measurement: constant names lose the
brackets around variants and run dose numbers into the word, so some of the
misses it lists do resolve in a client.

`ItemTextParserTest` is where the guessing is held to account, and the cases
that must come back *empty* matter as much as the ones that must resolve.

### Why half the gear had no picture: RuneLite's search is tradeables only

`ItemManager.search` walks the Grand Exchange price map. Nothing untradeable is
in it — void, barrows gloves, an infernal cape, a slayer helmet — and the
sharpest case is a charged weapon, because the charged **scythe of vitur**
cannot be traded and the only scythe in the price list is "Scythe of vitur
(uncharged)". A best-in-slot melee setup with no scythe in it reads as broken.

Two fixes, both needed:

- **`ItemNameIndex`**, a full name-to-id index built by reflection over
  RuneLite's `ItemID` constants — no client, no network, available in tests and
  in the offline diagnostics. A lookup tries the real item index first, for its
  proper display names, and falls back to this one, which has coarser names
  (`MASORI_BODY_F`, `PRAYER_POTION4`) but all of them. The constants are read
  back as "masori body (f)" and "prayer potion(4)", and the matcher is lenient
  enough to absorb the rest.
- **An unloaded variant is a last resort, not a rejection.** `(uncharged)`,
  `(empty)` and `(u)` used to be rejected outright alongside `(broken)` and
  placeholders. They are the same object with nothing in it, and the picture is
  the same weapon, so they are now taken when nothing better matches and passed
  over when something does.

`iconcoverage` runs against that same fallback index, so what it reports is
what the panel can reach at worst; in a client the tradeable half additionally
resolves against proper display names.

## "You already meet every requirement" is not an answer

A plan that ends there stops exactly where it became useful. The next
question is always *so what do I take?*, and answering it with a wiki link
sends the player out of the sidebar to find out — which is the back-and-forth
this plugin exists to remove.

An Activity can therefore carry `setups` (named kits and their items),
`supplies`, a `strategyUrl` and a `helperPlugin`, and the panel renders a
**What to bring** block both when requirements are already met and at the end
of a full plan.

- **Budget kits are listed first, deliberately.** Someone who has only just
  met the requirements is not holding a twisted bow, and the wiki's
  recommended-equipment tables are best-in-slot by default. A panel that names
  only BiS is no use to the player who most needs it, so each boss lists an
  entry-level kit before the maxed one.
- **Items are joined into a paragraph per kit**, not one slot per line: eleven
  slots across four kits is forty-odd lines in a 225px column. The player is
  filling an inventory, not auditing a slot table.
- **The helper-plugin pointer is generic now.** `helperPointer(name, …)` looks
  up any plugin by its `PluginDescriptor` name, so Zulrah Helper gets the same
  treatment Quest Helper does — "installed and will help", "installed but
  switched off", or a Plugin Hub link — with no build dependency on any of them.
- **The wiki link stays, last.** It is the place to go for depth the panel
  shouldn't try to hold, not the answer to the question the panel was asked.

All 18 PVM activities carry this now. Two shape decisions worth recording:

- **God Wars Dungeon became four activities**, one per boss. The rooms have
  different entry requirements (70 Strength, 70 Ranged, 70 Agility, 70
  Hitpoints), different killcount and completely different gear, so a single
  "God Wars Dungeon bosses" row could not say anything specific enough to act
  on. Each now also names the item that gets you through the door — a hammer
  for Graardor, a mithril grapple for Kree'arra, two ropes for Zilyana — and
  the god item that stops the bodyguards attacking on the way in.
- **Raid kits are written as best-to-worst rows**, matching how the wiki's
  equipment tables read left to right, with a closing line saying so.
  Everything on a row is doable, so a player can scan along it to the first
  thing they own rather than being shown one unreachable kit. Chambers of
  Xeric adds the caveat that gear beyond the general table depends on which
  rooms the raid rolls.

### …and it is where the handoff was missing

A rolled suggestion always lands in this branch, because the roll only offers
things whose requirements are already met. So "Surprise me" would answer
*Lumbridge & Draynor Hard Diary*, the player would click **Plan it**, and the
whole response was one wiki link — with Quest Helper, which has that diary's
entire task list, sitting unmentioned in the same sidebar. The branch now names
it, in the same three states as everywhere else.

That it took a roll to surface this is the point: the branch that fires when
everything is already in place is the easiest one to leave half-finished,
because it is the hardest one to reach while testing.

## Quests are a signpost, not a picker

168 of the 256 activities are quests. In one alphabetical dropdown they
drowned every boss, diary, raid and minigame the picker exists to offer —
and picking a quest *as a goal* is the question Quest Helper answers better
than any path could, since it already lists what you can start and walks you
through it.

So the Activity picker filters `Category.QUEST` out — 88 entries instead of
256 — and quests get their own short section beside the other goal pickers:
a line of explanation and the usual installed-aware Quest Helper pointer.

What this does **not** change is quests as prerequisites. When a quest stands
between the player and something they did ask for, it is still named in the
plan, in order, in its own `[Quests]` block. That is the half of the job
Quest Helper doesn't do, and it stays here.

The suggestion cards under "Almost there" can still surface a quest and still
plan a route to it when clicked. That is deliberate and not an oversight: the
route to a quest — which skills, which earlier quests — is this plugin's
work, and only the walkthrough is the handoff.

## The route is ours; the content is Quest Helper's

This plugin's value is making the **path toward** a piece of content clear —
which quests, diary tiers and skill levels a goal needs, and in what order —
because that is the part that otherwise costs a player a long session of
reading wiki pages. Guiding them through the content once they arrive is not
its job. Quest Helper is a large, mature plugin that already does that
properly, and it ships helper packages for quests, achievement diaries,
miniquests and combat tasks, so a second and worse walkthrough helps nobody.

- `PathStep.Type.DO_QUEST` and `DO_DIARY` are split out from `DO_ACTIVITY`.
  The planner already emits prerequisites contiguously (the resolver walks the
  activity chain before expanding standalone requirements), so the panel
  gathers a run of one type into a single numbered block: a plain bulleted
  list of names, each clickable through to its wiki page. A Kingdom Divided's
  six prerequisite quests went from six numbered steps with a category line
  and a wiki link each to eight lines total.
- `PathStep.isTarget()` marks the one step that *is* the goal. Without it the
  destination gets swept into the "needed first" list above it — asking for
  A Tail of Two Cats would have listed three quests needed before A Tail of
  Two Cats. A target quest or diary instead renders as an ordinary step with
  the Quest Helper pointer under it, which is exactly the handoff point: the
  plan has got you to the content, and the content is Quest Helper's.
- The pointer underneath is worded from what is actually installed, via
  `PluginManager.getPlugins()` and the `PluginDescriptor` name: "Quest Helper
  has the walkthrough for each" when it's installed and on, "installed but
  switched off" when it isn't enabled, and a clickable Plugin Hub link when
  it's absent. Telling someone to install a plugin that's already running in
  the same sidebar is the kind of thing that makes a plugin feel like it
  isn't paying attention.
- **No compile-time dependency on Quest Helper**, deliberately. The lookup is
  by descriptor name (with a `com.questhelper` package check for forks that
  rename themselves), so Next Step builds and runs identically whether or not
  Quest Helper is present, and nothing breaks if their internals change.

The same reasoning should apply before adding anything that overlaps an
established plugin: list it, link it, or point at it rather than rebuild it.

## Why the notes are short

A 240px column at 12px holds about 34 characters a line. Comfortable reading
is 45-75, the sidebar cannot get wider, and **Swing's HTML renderer ignores
`line-height`** — setting it to 140% changed the rendered height by exactly
zero pixels, so extra leading is not available either. The only lever left is
putting fewer words on each line.

Two changes did that:

- **The time estimate left the prose.** It used to be glued on the end of the
  note, so every detail line ended "...but are slower than kebbits — ~3 hrs at
  160k xp/hr": three unrelated facts in one five-line paragraph, with the one
  most players were scanning for buried in the middle of it. It is now
  `PathStep.Option.meta`, rendered as its own tracked `~3 HRS · 160K XP/HR`
  line directly under the method name.
- **The notes themselves were cut.** 19 of them predated the panel getting
  this narrow. The Wilderness ones were the worst offenders because they
  described the Wilderness, which the amber tag above them now says on its
  own — so they only have to state what it costs you. The longest note went
  from 319 characters to 107, and all but one are now under 95.

Two things that bit while doing it, both worth not repeating:

- A note keyed on `(skill, name)` lands on **every bracket of a method**, so
  rewriting the Knights of Ardougne note put the same paragraph on all nine of
  its brackets. A bracketed method is one method: the note belongs on the first
  bracket only.
- The repeat test must **not** include the time estimate. It differs for every
  segment, so counting it meant the prose was never a repeat and printed in
  full under each bracket. The key is title + note + quests; the estimate and
  the risk tags render every time, because the estimate is the part that
  actually changed and a Wilderness warning is not something to suppress as a
  duplicate.

Hunter 1-85 went from 2697px to 2357px with no information removed.

## Two voices: a serif to navigate by, a sans to read

The panel read flat, and the reason was one typeface doing every job: Dialog
sans at 11, 12 and 13px with nothing between plain and bold, in pure white on
grey. Nothing on screen said "this is a heading" except a single point of size
and a bold weight.

Four treatments were mocked at the real 240px width
([canvas](https://claude.ai/artifact/Pt3WQm7BYPqva8bfYgbtYQ)) and the serif one
was chosen:

| | |
|---|---|
| Goal, step titles, method names, section headings | Georgia 16 / 14 / 15 |
| Notes, links, blurbs, the attribution | Segoe UI 12 |
| Step captions, rate lines, tags, slot labels | Segoe UI Semibold 10, tracked 0.1 |

Georgia and Segoe UI ship with Windows, which is what makes this safe: `installed()`
picks the first family the machine actually has (Georgia → Noto Serif → DejaVu
Serif → the logical serif) rather than naming a font and letting Java silently
substitute Dialog, which is how a panel ends up looking identical everywhere
regardless of what it asked for.

Three things changed with it, all from reading it at size:

- **Pure white became a warm off-white** (`#F1EDE6`). White at 13px on this grey
  is glare, and it left nowhere above the body text for the eye to go.
- **The small grey text was too weak** — the one real legibility complaint.
  Notes went `#C0C0C0` at 11px → `#C6C0B5` at **12px**, and the uppercase micro
  labels went `#C8C8C8` plain → `#ADA69C` **semibold**. Tracking alone was never
  going to fix 10px all-caps in grey; weight did. Links sit at `#B9B2A8`,
  deliberately above the note grey, because a control fainter than the prose
  around it reads as disabled.
- **The last of RuneLite's pixel font left the panel.** The game face is built
  for the game viewport; as a section heading or a field caption above a serif
  plan it was the loudest thing on screen. Those are Georgia and tracked micro
  labels now.

## The suggestion cards stopped being cards

The "almost there" list was the one thing left in the sidebar drawing chrome
around its content — a filled `DARKER_GRAY` box per entry, with a hover that
changed the whole box. Fine when it sat at the bottom of a wall of text;
impossible to miss once it moved up under the new opening screen, next to steps
that use nothing but a hairline.

Each entry now reads exactly like a step: a rule, a tracked caption, a serif
name, with the hover on the name rather than a filled panel. Same three parts
in the same order, so the eye learns one pattern instead of two.

## Reading the plan as text instead of pixels

`./gradlew paneltext --args="skill:THIEVING:99 THIEVING=76"` prints what the
panel renders, in order, as text. A PNG is the right check for spacing and the
wrong one for structure: working out from pixels whether eight level brackets
collapsed into one step means counting headings in an image. It drives the panel
too — `pick=Pickpocketing+Knights+of+Ardougne` pre-chooses a method,
`click=Show+3+level+brackets` fires a link, `own=13576x1` puts an item in the
bank — so the views a player reaches by clicking can be diffed rather than
eyeballed.

## Reading the plan at sidebar width

The plan body renders in the platform UI font rather than RuneLite's bundled
RuneScape face. That font is a pixel design meant for the game viewport and
for short labels; the plan is dense multi-line prose in a 225px column, and
at that size it smears. The detail line under each option was the worst case
— the smallest pixel font, at `0x808080` on a dark background — and it is the
most-read text in the panel. Body text is now white, detail text `0xC0C0C0`,
and both are sans-serif. The surrounding RuneLite chrome (section headers,
the goal inputs) keeps the game font, so the panel still looks native.

## Licensing note on wiki-derived data

The OSRS Wiki's text (including what the Bucket API exposes) is **CC
BY-NC-SA 3.0** (confirmed via `meta.runescape.wiki/w/Meta:Copyrights`).
Practically, for this project:

- **Attribution is required, and cheap — and is now actually in the plugin.**
  The panel carries a permanent footer crediting the Old School RuneScape
  Wiki, naming CC BY-NC-SA 3.0 and linking it, stating plainly that this
  plugin owns none of the content, and disclaiming any affiliation with
  Jagex. The same wording is in `runelite-plugin.properties`, so it shows on
  the Plugin Hub listing too. Saying it once, visibly, is the point — the
  per-entry `wikiUrl` links are navigation, not attribution, and a licence
  note that lives only in a README nobody opens protects nobody.
- **We own none of it.** Every xp rate, requirement, drop estimate and quest
  chain in `activities.json`, `methods.json` and `item-sources.json` is a
  fact read off the wiki. The structure and the planner are ours; the content
  is not, and the plugin should never imply otherwise.
- **NonCommercial** is very likely satisfied by a free, unmonetized Plugin
  Hub plugin. Revisit this only if the plugin is ever monetized directly
  (ads, paid tier) — not before.
- **ShareAlike** binds republishing/modifying wiki *pages*; extracting bare
  facts (a skill requirement, a recipe's ingredients, a drop rate) into
  structured JSON is a different kind of reuse than mirroring an article,
  and the Bucket API's own stated purpose ("so external tools don't need to
  scrape wiki pages") signals the wiki's maintainers expect and support
  this. `methods.json`'s `notes` field should stay a short paraphrase in
  our own words rather than lifted guide prose, both as the more cautious
  reading of this clause for prose specifically and as ordinary good
  practice.
- Before an actual Plugin Hub submission (not before — no need to block
  development on this), send one message in the OSRS Wiki Discord's Bucket
  channel (contact named on `RuneScape:Bucket`) confirming this approach
  and how they'd like attribution phrased. Costs nothing, removes all
  ambiguity for a public release. QuestPath's own PR shows the same
  instinct — it explicitly matched its upstream source's license rather
  than assuming reuse was fine by default.

## Done while waiting on the Discord reply

Work that doesn't depend on the licensing/Bucket answer:

- **OR-group requirements** (`Requirement.Type.ANY_OF`, with an `anyOf` list
  of child requirements) — added ahead of time because the
  `achievement_requirement` bucket's requirement trees already use exactly
  this shape (`{"type":"or","units":[...]}`), so the model is ready the
  moment real data starts flowing in rather than needing a rework then.
  `RequirementEvaluator` treats it as met if any child is met.
  `PrerequisiteResolver` passes it through as a standalone requirement (same
  as a bare skill/KC/QP gate — it isn't itself a chainable Activity), and
  `PathPlanner` renders it as an explicit multi-option choice rather than
  silently picking one branch — same "don't collapse a real choice"
  philosophy as the training-method alternatives. See `unlock_warriors_guild`
  in `activities.json` for a worked (deliberately simplified — see its
  description field) example.
- **Combined-stat requirements are now supported too**
  (`Requirement.Type.COMBINED_SKILL_LEVEL`, value = comma-separated skill
  names, threshold = the combined minimum). `unlock_warriors_guild` now
  models the real, accurate gate — 99 Attack, 99 Strength, OR 130 combined —
  instead of the simplified two-branch placeholder from before. Deliberately
  *not* decomposed into per-skill training steps the way a plain
  `SKILL_LEVEL` requirement is: raising Attack vs Strength vs some split of
  both are all valid ways to close a combined gap, and there's no basis
  here to prefer one split over another without an explicit player
  preference this scaffold doesn't collect — so it's surfaced as
  information (current combined total vs. target) rather than a forced
  plan.
- **methods.json expanded** to Woodcutting and Fishing (8 skills total now),
  including one more real tick-manipulation example (Barbarian tick fishing)
  to keep that side of the data growing too. This file was never blocked by
  the Discord question in the first place — it's hand-curated regardless.

## Fixed: missing sidebar icon

`NextStepPlugin.startUp()` loads `/com/nextstep/icon.png` for the
`NavigationButton` — that file didn't exist anywhere in the scaffold until
now, which would have thrown/returned null the moment the plugin actually
loaded in RuneLite (a bug that only shows up at runtime, not by reading the
code). Added a minimal 16x16 placeholder so the plugin actually starts;
swap it for real artwork before a real release.

## Unit tests added

`src/test/java/com/nextstep/engine/` now covers the engine layer end to end
— `RequirementEvaluatorTest`, `SuggestionEngineTest`, `SkillPathPlannerTest`,
`PrerequisiteResolverTest`, and `PathPlannerTest`. All of it is pure Java
with hand-built fixtures (`PlayerState`, `Activity`, `Requirement`,
`TrainingMethod`, `ItemSource` all now have proper constructors for this,
not just Gson-only no-arg ones) — no RuneLite client, no mocking needed.

**These have not been executed** — this sandbox has no network access to
pull the Gradle/RuneLite dependencies needed to run `./gradlew test`. They
were written and manually traced for correctness (see the reasoning in this
conversation for `SkillPathPlannerTest`'s segment-boundary math and
`PrerequisiteResolverTest`'s circular-reference case) but haven't compiled
or run anywhere. **Running them for real is the first thing to do** once
this is in an environment with network access.

One real behavior the tests surfaced while writing them, worth knowing
before it looks like a bug: a `SKILL_LEVEL` requirement with **no** bundled
training-method data **and no authored `hint`** produces zero visible steps
in the plan — it only shows up as a warning (e.g. the Herblore requirement
inside the Zamorak brew crafting test). This is intentional — the
alternative would be a step that just repeats the bare number with nothing
useful to add — but it means a step count can be smaller than expected
whenever `methods.json` has a real coverage gap. `PathPlannerTest`'s
`craftedItemRecursesThroughIngredients` test documents this exact case.

## Picking this up in Claude Code

This whole scaffold was built without the ability to actually run `gradle`
(no network access in this environment). If you're opening this in Claude
Code with normal network access, the highest-value first moves are, in order:

1. `./gradlew test` — first real compile of everything, including whether
   the constructor/field wiring across `Activity`/`Requirement`/
   `TrainingMethod`/`ItemSource` actually lines up the way it's assumed to
   here. Fix whatever breaks; report back what broke, since that's useful
   signal about where this hand-written code was wrong.
2. `./gradlew build` — full build once tests pass.
3. `./gradlew run` (per RuneLite's own dev-mode convention) to actually load
   the plugin in a real client and click through the panel by hand — no
   amount of code review substitutes for actually seeing the "Available
   now" list populate and running a "Plan path" lookup live.
4. Everything in this README's other sections (Bucket-derived data
   generation, the Discord licensing question, methods.json/item-sources.json
   coverage) is unaffected by any of this and can continue in parallel.

## First real build: it compiles, all 35 tests pass

Answering step 1 of the section above. The scaffold was written blind, so the
honest expectation was that the constructor/field wiring across
`Activity`/`Requirement`/`TrainingMethod`/`ItemSource` would not line up. It
did. Nothing broke — `./gradlew test` compiled main and test sources against
RuneLite client 1.12.39 (resolved from `latest.release`) and ran green:

| Test class | Tests |
| --- | --- |
| `PathPlannerTest` | 7 |
| `PrerequisiteResolverTest` | 6 |
| `RequirementEvaluatorTest` | 8 |
| `SkillPathPlannerTest` | 8 |
| `SuggestionEngineTest` | 6 |

35 total, 0 failures, 0 errors. `./gradlew build` also succeeds and produces
`build/libs/next-step-plugin.jar`.

Caveat on how much that proves: these are engine tests over hand-built
`PlayerState` fixtures. They exercise the resolver, planner and evaluator
logic, but nothing here touches `NextStepPlugin`/`NextStepPanel` against a
live `Client`. A green suite means the data model is self-consistent, not
that the plugin behaves correctly in a client. Step 3 below is still the
real test.

Three things had to be fixed or added along the way:

- **No Gradle wrapper existed.** The repo had `build.gradle` but no
  `gradlew`, `gradlew.bat` or `gradle/wrapper/`, so the README's own
  instructions could not run. Generated at Gradle 8.10; these are committed
  now.
- **`Skill.OVERALL` is deprecated** in current RuneLite. It is still returned
  by `Skill.values()`, so `buildPlayerState()` still has to skip it — the
  guard now compares `skill.name()` against `"OVERALL"` instead, which drops
  the deprecated reference and survives the constant eventually being
  removed. (Unrelated but worth knowing: `Skill.values()` now includes
  `SAILING`.)
- **`sourceCompatibility`/`targetCompatibility` at project level** used the
  deprecated `JavaPluginConvention` and would fail on Gradle 9. Moved into a
  `java { }` block.

The build is now warning-free apart from one environmental note: Gradle is
being run on JDK 11, and Gradle 9 will require JDK 17+ to *execute* (projects
can still target 11 via toolchains). Not urgent, but it is what will break
first on a Gradle upgrade.

### Step 3 is wired up but needs a human

`./gradlew run` did not exist either. There is now a `run` task plus
`src/test/java/com/nextstep/NextStepPluginTest.java`, which is RuneLite's
standard dev-mode launcher — a `main()` that calls
`ExternalPluginManager.loadBuiltin(NextStepPlugin.class)` and then
`RuneLite.main(args)`. It is deliberately not a unit test; Gradle's test
detection ignores it because it has no `@Test` methods.

It compiles and the task resolves, but actually running it opens a real
client and requires a game login, so the click-through — watching "Available
now" populate and running a "Plan path" lookup live — is a keyboard-and-eyes
job that has not been done yet. That remains the highest-value unknown.

## Fixed: the panel was unusable in a real client

First hands-on session in a dev client turned up three problems. All three are
fixed; the panel now renders correctly at the real sidebar width.

### The sidebar is 225px and nothing respected that

Controls were oversized and the panel scrolled horizontally. Causes, in order
of how much damage they did:

- Nothing had a maximum size. In a `BoxLayout`, a component with no cap takes
  whatever height is going spare, and reports whatever preferred width its
  content wants — a `JComboBox` full of long activity names happily asked for
  ~400px and dragged the whole panel sideways.
- Mixed `alignmentX`. `JPanel` defaults to centre (0.5), `JLabel` to left
  (0.0). Mixing them in one `BoxLayout` column makes Swing offset components
  against each other horizontally.
- The scrolled content was a plain `JPanel`, so the viewport was willing to
  scroll sideways to reach it. It is now a `Scrollable` that reports
  `getScrollableTracksViewportWidth() == true`, which forces it to the
  viewport width, and horizontal scrolling is switched off outright.
- `ActivityCard` put the name and the category side by side and pinned itself
  to 60px tall. They are stacked now and the height is whatever the content
  needs.

### "Plan path" appeared to do nothing

It was never a planning bug. The planner was producing full routes the whole
time — `./gradlew plandiag` prints them, and every activity in the database
resolves to between 2 and 11 steps. The steps were being added to the results
container and then laid out somewhere the user could not see, so the only
thing that showed was the goal title.

Text was also being clipped on the right, and the cause is worth writing down
because the obvious fix does not work: **`<html><body style='width:180px'>`
does not constrain a `JLabel`.** Swing treats that CSS width as advisory and
still reports a preferred width roughly 45px larger, so every line overflowed
the sidebar and lost its tail. A one-cell table (`<table width='180'>`) is
honoured exactly, and that is what `wrapped()` emits. The wrap width itself is
now measured from the live viewport instead of computed from panel constants,
since border, scrollbar and look-and-feel insets all take a cut.

### Mojibake

`sourceCompatibility` was set but the compiler encoding was not, so javac used
the platform default (windows-1252 on this machine) and every non-ASCII
character in the sources rendered as mojibake — the ellipsis in "Plan a path
to…" came out as `toâ€¦`, and the `→` in "Magic 1 → 55" came out as `?`. Fixed
with `options.encoding = 'UTF-8'`, which RuneLite's own example plugin sets
for exactly this reason.

### Verifying panel changes without logging in

`./gradlew panelshot --args="241 <output>.png [activityId]"` renders the panel
offline at real sidebar width, optionally clicking "Plan path" for a given
activity first, and writes a PNG. It also prints the panel's preferred width
so overflow shows up as a number rather than something you have to spot by
eye. `./gradlew plandiag` dumps what the planner returns for every activity.
Both live in `src/test/java/com/nextstep/diag/` and are not part of the
shipped jar.

### Icon

Replaced. The old one read as a woodcutting axe; it is now three ascending
steps with a forward arrow, drawn as pixel art at 16x16 rather than rendered
with antialiasing, which turns to mush at that size on a dark sidebar.

## Where the plan actually comes from

Two inputs, and it is worth being precise about which is which, because the
limits of the plugin are almost entirely the limits of the second one.

### 1. Live state read from the client

`NextStepPlugin.buildPlayerState()` rebuilds a `PlayerState` snapshot every 50
ticks (~30s) on the client thread:

| Field | Source | Status |
| --- | --- | --- |
| Skill levels | `client.getRealSkillLevel(skill)` for every skill | working |
| Completed quests | `Quest.getState(client) == FINISHED` | working |
| Diary tiers | `DiaryTracker.readInto(client, state)`, via varbits | working |
| Combat level | `client.getLocalPlayer().getCombatLevel()` | working |
| Quest points | — | **always 0**, see below |
| Boss KC | — | **not read** |
| Collection log | — | **not read** |
| Bank / inventory | — | **not read** |

The gaps are load-bearing, not cosmetic. Quest points are hardcoded to `0`,
so any `QUEST_POINTS` requirement is permanently unmet and will always show
up as a step even on a maxed account. Boss KC and collection log slots cannot
be evaluated at all. Nothing reads your bank, so an item plan always assumes
you own none of the ingredients.

### 2. Three bundled JSON files

This is the content database, and it is currently tiny:

- `activities.json` — **256** activities. Each has `requirements`, and this is
  what makes the graph: a `QUEST_COMPLETE` or `DIARY_COMPLETE` requirement
  names another activity in the same file, so the resolver can recurse. PVM
  entries additionally carry `setups`, `supplies`, `strategyUrl` and a
  `helperPlugin`.
- `methods.json` — **176** training methods across 23 skills, each with
  `skill`, `minLevel`, `maxLevel`, `xpPerHour` and optional notes,
  `intensity` and `risk`.
- `item-sources.json` — **5** items.

Everything the panel says is derived from those two inputs and nothing else.
There are no network calls, no wiki scraping at runtime, and no hiscores
lookup.

### How a path gets built

- **Activity goals.** `PrerequisiteResolver` walks the requirement graph
  backwards from the target, skipping any requirement already satisfied by
  your live state — which is why two accounts asking for the same thing get
  different plans. Quest and diary requirements recurse into their own
  activities. A requirement naming something absent from `activities.json` is
  reported as an unresolved warning rather than silently dropped.
- **Skill goals.** `SkillPathPlanner` cuts the level range at every method
  boundary, then lists every method covering each segment sorted by xp/hr,
  never pruning to a single "best". `XpTable` supplies the standard OSRS
  experience curve, so each segment states its xp cost and a time estimate at
  each method's rate.
- **Item goals.** `PathPlanner.planItem` resolves a source recursively,
  expanding `CRAFT` recipes into their ingredients until everything bottoms
  out in a buy/shop/drop leaf, with cycle detection.

### What this means in practice

- xp/hr figures are hand-entered averages from the wiki, not measurements of
  you, and they go stale as the game changes — the whole table was
  re-derived once, after Hunter turned out to be understating its headline
  method by more than half. Time estimates inherit that error. Within one
  bracket the figures are meant to describe the rate **at the level where
  you'd start the method**, not its level-99 ceiling — quoting the ceiling makes a method look better than
  it will be for the player actually reading the step, and a method whose
  rate jumps partway through (extra traps, a diary unlock) should say so in
  its notes instead.
- Drop estimates (`estimatedKc`) are rough averages. "~500 attempts" is not a
  rate you can plan around.
- An item source can carry only **one** prerequisite requirement, so Twisted
  bow lists a Ranged benchmark for raid entry and puts the rest in a hint.
  Expressing "all of these at once" needs a model change.
- With 11 activities and 5 items, most things a player would search for are
  simply not in the database yet. That is the single biggest limit on how
  useful this is, and it is content work rather than code work.

## Fixed: three things that looked broken but were mostly thin output

- **An activity you already qualify for showed a one-item plan.** Zulrah with
  the stats and quests already done rendered as "1. Start learning Zulrah",
  which reads like a failure. `isAlreadyDone` only returns true for things you
  *complete* (quests, diaries, CA tiers, collection log slots); a boss or
  minigame is never "done", so it fell through to a bare target step. `Path`
  now carries `requirementsAlreadyMet` and the panel says so explicitly.
- **Skill plans stated a rate but not a cost.** "Nightmare Zone, ~45000 xp/hr"
  does not answer how long the grind is. Steps now read
  `Attack 75 → 99 · 11.8M xp` with `~263 hrs at 45,000 xp/hr` per method.
  `XpTable` is verified against the known curve (83 / 1,154 / 101,333 /
  737,627 / 6,517,253 / 13,034,431).
- **The item field rejected anything but an exact id.** Typing "Twisted Bow"
  produced "no source data" because lookup only uppercased — it never mapped
  the space to an underscore, and Twisted bow was not in the file anyway. The
  field is now a dropdown of known items (you cannot ask for something
  unsupported), lookup normalises punctuation and spacing, and Twisted bow
  was added as a Chambers of Xeric drop. Any source can now gate on a
  requirement, not just crafting recipes.

Smaller fixes alongside: numbers were formatted in the machine's locale, so
xp read as `11,8M` next to `45,000`; single-option steps printed their own
title twice; and drop steps said "Kill Chambers of Xeric", now
"[Drop] Twisted bow from Chambers of Xeric".

## Content database expansion, bank reading, and a diary bug

### The database is no longer a toy

| File | Before | After |
| --- | --- | --- |
| `activities.json` | 11 | **186** |
| `item-sources.json` | 5 | **35** |

Provenance matters more than the count here:

- **Skill, quest-point and combat-level requirements are wiki-derived**, taken
  from the OSRS Wiki's consolidated `Quests/Skill requirements` table rather
  than typed from memory.
- **Quest-to-quest prerequisite links are hand-entered.** The wiki does not
  tabulate them anywhere fetchable, so the ~80 links in `QUEST_PREREQS` are
  the part a human should review. They cover the well-known chains (the whole
  Dragon Slayer II tree, elf series, Myreque series, Fremennik series, RFD).
- **Every `gameItemId` was read out of RuneLite's own `ItemID` class**, not
  recalled. Worth doing: two of the ids I would have written from memory were
  wrong (ancestral top is 21021, trident of the seas is 11907).
- **Drop estimates are rough averages** and are labelled as such in the UI.

Dragon Slayer II now resolves to a 74-step plan from a fresh account, which is
the behaviour the whole prerequisite resolver existed for and could never
demonstrate with 11 activities.

**Achievement diaries were deliberately left alone.** The wiki's diary page
only publishes aggregate maxima across all twelve regions, not per-region
per-tier requirements, and attaching "Fishing 96" to every elite diary would
be worse than having no diary data. Doing this properly needs 48 individual
page reads. That is the obvious next batch of content work.

### Content errors now fail the build

`ContentDatabaseTest` checks that every `QUEST_COMPLETE` names a real quest,
every `DIARY_COMPLETE` names a real diary tier, every skill name and level is
valid, every craft ingredient resolves, every item carries a usable
`gameItemId`, and ids are unique. This matters because the failure mode is
silent: a requirement naming a missing quest doesn't crash, it just quietly
turns an eight-step chain into a three-step one. Content changes far more
often than code here, so the build should be what notices. 42 tests total.

### Bank, inventory and equipment

`ItemTracker` reads all three item containers, which is what lets the planner
subtract what you already own instead of assuming an empty bank, and is the
only way to know what gear someone actually has.

The three containers are not equally available, and the difference is real
rather than a RuneLite limitation:

- **Equipment and inventory** stream continuously while logged in.
- **The bank** is only sent to the client when the player opens it. Until
  then the data is not client-side at all. So the bank is a snapshot from the
  last time it was opened, persisted to config so it survives logout instead
  of resetting to "you own nothing" every login.

Because of that, `PlayerState.isBankSnapshotAvailable()` exists and item plans
say "Bank contents unknown — open your bank once" rather than confidently
telling you to buy something already sitting in your bank.

A new `ITEM_OWNED` requirement type lets content gate on owning or wearing a
specific item, so gear requirements can be expressed rather than assumed.

### DiaryTracker never worked

It shipped with every varbit id set to `-1` and a comment saying to fill them
in, and the loop skipped every region — so diary completion was never read,
and the earlier claim in this README that diaries were "working" was wrong.
It also assumed each region packed four tiers into one varbit as a bitmask.
RuneLite exposes a separate varbit per region per tier, so there is no bitmask
and no placeholders: `Varbits.DIARY_ARDOUGNE_EASY` and friends are used
directly. Karamja is the one exception — its easy/medium/hard varbits are
progress counters where 2 means complete, not 0/1 flags.

## Diaries, collection log, combat achievements, and quest points

### Quest points were always readable

`buildPlayerState()` hardcoded quest points to `0` with a TODO saying the
`Quest` enum doesn't expose each quest's reward QP. That's true, but beside
the point: the game already keeps a running total in a var player, and
RuneLite exposes it as `VarPlayer.QUEST_POINTS` (101). One line reads it.
Until now every `QUEST_POINTS` requirement was permanently unmet, which is
why "32 Quest Points" showed up as an outstanding step even on accounts with
hundreds.

### Achievement diaries: all 48 tiers

Taken from each region's own wiki page, which does publish per-tier skill and
quest requirements — unlike the summary `Achievement Diary` page, which only
has aggregate maxima across all twelve regions and would have attached
"Fishing 96" to every elite tier.

Diary activity ids are `REGION_TIER` (`VARROCK_EASY`), matching exactly what
`DiaryTracker` writes into `PlayerState`, so completion reads across cleanly.

Two honest caveats:

- The wiki lists tier requirements **cumulatively** — a Hard entry repeats
  Medium's skills. That's kept as-is, since it is what you need in hand to
  finish that tier.
- Some entries are marked "(started)" or "Partial completion of", meaning you
  only need to have *begun* that quest. The requirement model has no way to
  express "begun but not finished", so those became full quest requirements.
  Over-requiring beats dropping the step, but it is a known inaccuracy and the
  obvious reason to add a `QUEST_STARTED` requirement type later.

### Why the wiki rather than Quest Helper

Quest Helper does have this data, and it is a reasonable place to look. It is
a separate codebase under its own licence though, so lifting its tables means
complying with that licence on top of everything else. The wiki route reuses
the CC BY-NC-SA attribution approach this README already committed to for
`methods.json` and quest requirements, which keeps the project on one
licensing story instead of two. Worth revisiting only if a region's page turns
out to be unusable.

### Restored categories

Regenerating the database earlier silently dropped the collection log and
combat achievement entries. Both are back — six CA tiers and six collection
log goals. Note that neither can currently report as *already done*: nothing
populates `completedCombatAchievementTiers` or `collectionLogUnlocks` from the
client yet, so they can be planned toward but not ticked off. That is the same
class of gap quest points had, and is the next one worth closing.

| Category | Count |
| --- | --- |
| QUEST | 168 |
| DIARY | 48 |
| PVM | 15 |
| MINIGAME | 6 |
| COMBAT_ACHIEVEMENT | 6 |
| COLLECTION_LOG | 6 |
| SKILLING | 4 |
| **Total** | **253** |

All 42 tests pass, including the integrity checks — zero dangling quest or
diary references across 253 activities.

## Combat achievements: done. Collection log: possible, but different.

These two looked like the same gap. They are not.

### Combat achievements are just varbits

The game keeps one varbit per tier (`Varbits.COMBAT_ACHIEVEMENT_TIER_EASY`
through `..._GRANDMASTER`, 12863-12868) and the client holds them for the
whole session, so there is nothing to snapshot and nothing to scrape.
`CombatAchievementTracker` reads all six on each refresh. Like the Karamja
diary varbits these are progress counters rather than flags — 0 not started,
1 in progress, 2 complete — so completion is `>= 2`.

Tier keys are `CA_<TIER>`, matching both the activity ids and the value a
`COMBAT_ACHIEVEMENT_TIER` requirement uses, so "have I done this" and "does
this need that" agree on one spelling. (The javadoc previously said the
requirement value was a bare tier name like `GRANDMASTER`; nothing used it
yet, so it now matches the activity id instead.)

### The collection log has no varbit to read

`Varbits.COLLECTION_LOG_NOTIFICATION` is a settings toggle, not progress.
There are a few dozen `VarbitID.COLLECTION_ITEM_*` entries, but they cover a
scattered handful of items that needed special tracking, not the log.

What the log actually is: server-side data sent to the client only when the
player opens the collection log interface, one page at a time. The route other
plugins take is to hook `ScriptID.COLLECTION_DRAW_LIST` (2731) via
`ScriptPostFired` and read the item widgets off `InterfaceID.COLLECTION` (621)
as each page draws, then persist what was seen.

That is workable but its availability is materially worse than the bank:

- The bank fills in from **one** action — open the bank once and the whole
  thing arrives.
- The collection log fills in **per page**. A player who never opens the
  Bosses tab leaves that part permanently unknown, and there is no way to ask
  the server for it.

So the honest design is the same shape as the bank snapshot but with a wider
unknown: record pages as they are browsed, persist them, and have the panel
distinguish "you don't have this" from "never looked". Claiming a slot is
unobtained when the player simply hasn't opened that tab would be worse than
saying nothing.

Not implemented yet. It is a real feature rather than a one-line fix, and it
needs the "never looked" state carried through the model and into the UI to
be worth having.

## Where this stands

Everything below is on disk and building. There is **no version control in
this directory** — `git init` would be the first thing worth doing before the
next round of changes, since several passes here rewrote generated data files
wholesale and there is currently no way to diff or roll back.

### Working

- Reads from the client: skill levels, completed quests, **quest points**,
  **all 48 diary tiers**, **Combat Achievement tiers**, combat level, and
  bank / inventory / worn equipment.
- 253 activities and 35 item sources, with zero dangling references.
- Planning: prerequisite chains, skill segments with xp and time estimates,
  recursive item sourcing, and item goals that subtract what you already own.
- Panel renders correctly at real sidebar width, with no horizontal scroll.
- 42 tests, including content-integrity checks that fail the build on a bad
  quest reference, skill name, ingredient or missing item id.

### Known gaps, in rough priority order

1. **Collection log is not read.** No varbit exists; the game sends it a page
   at a time as the player browses the log. Needs interface scraping via
   `ScriptID.COLLECTION_DRAW_LIST`, persistence, and a "never looked" state
   carried into the UI so an unopened tab isn't reported as "not obtained".
2. **Boss kill counts are not read**, so `BOSS_KC` requirements can never be
   satisfied — the same class of bug quest points had before it was fixed.
3. **Quest prerequisite links are hand-entered** (`tools/gen_data.py`,
   `QUEST_PREREQS`) and are the most likely source of a wrong chain.
4. **No content gates on gear.** `ITEM_OWNED` exists and works but is used by
   nothing, and `PlayerState.isEquipped` has no callers. Gear is read; it just
   doesn't change a plan yet beyond item goals.
5. **"(started)" quest requirements are over-required** on diaries — needs a
   `QUEST_STARTED` requirement type.
6. **The plugin has never been exercised against a logged-in account.** Every
   verification here was either a unit test or an offline render via
   `./gradlew panelshot`. Clicking through it in a real client with a real
   account is still the highest-value unknown.

### Useful commands

| Command | What it does |
| --- | --- |
| `./gradlew test` | 42 tests, including content integrity |
| `./gradlew run` | Launches a dev-mode RuneLite client with the plugin side-loaded |
| `./gradlew panelshot --args="241 out.png activity:<id>"` | Renders the panel offline; also accepts `skill:ATTACK:99` and `item:<ID>` |
| `./gradlew plandiag` | Dumps what the planner returns for every activity |
| `python tools/gen_data.py` | Regenerates the content database |
