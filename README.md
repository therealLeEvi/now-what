# Next Step

A RuneLite sidebar plugin that answers the two questions a bank screen tends to
produce: **"what should I do tonight?"** and **"how do I actually get that?"**

It reads your live skills, quests, achievement diaries, combat level and
collection log progress, and plans backwards from a goal to the things you have
to do first - including what to wear and what to bring when you get there.

## What it does

**Something to do.** The panel opens on what you can start right now, ranked,
plus an "Almost there" list of what one more requirement would unlock. If you
would rather not choose, it will roll one for you, optionally within a category.

**Plan a path to anything.** Pick a goal three ways:

- **an activity** - a boss, a raid, a quest, a diary tier, a minigame
- **a skill level** - "99 Attack", broken into the methods that get you there
- **an item** - the drop, the raid, or the Grand Exchange. Type to search it:
  "drag cl" finds Dragon claws, "seers ring" finds Seers' ring with or without
  the apostrophe

The plan walks the requirement graph backwards. Asking for Zulrah finds Regicide,
and Regicide's own unmet quest requirements under that, and the skill levels
under those, until everything left is something you can start today. Circular
and dangling requirements are reported rather than silently dropped.

**What to bring.** A plan does not stop at naming the place. Each step ends in
the gear and supplies for it, rendered as item icons rather than a list of
names - and a goal that is only a *reason* to go somewhere inherits that
somewhere's kit. Planning "Pet snakeling" gives you Zulrah's setup; planning an
Ancestral robe top gives you the Chambers of Xeric's; planning 99 Firemaking
gives you Wintertodt's warm clothing.

**And how much of it to buy.** Where a method's xp comes in countable actions,
the card does the division: 99 Prayer from 43 at a gilded altar is not just
"9 hours", it is **23,423 superior dragon bones**. That is the arithmetic people
leave the client to do in a wiki tab.

**It defers to plugins that do a job better.** Where Quest Helper is the right
tool for a quest step, the panel says so, and opens its panel for you.

## Settings

| Setting | Default | Effect |
|---|---|---|
| Show quest suggestions | on | Include quests in the suggestion lists |
| Show diary suggestions | on | Include achievement diary tiers |
| Show PvM suggestions | on | Include bosses and PvM |
| Show collection log suggestions | on | Include collection log slots |
| Prefer tick-manipulation methods | off | Plan with tick-manipulation training methods where they exist, falling back to normal ones. Exclusive, not additive, and overridable per lookup in the panel. |

Methods also carry what an xp/hr figure cannot say: `Wilderness - PvP risk`,
`Tick manipulation`, and - where it has been assessed - `Constant input` or
`Nearly AFK`. Bloodwood trees are the fastest Woodcutting that is not tick
manipulation *and* a two-tick cycle across three trees; ironwood is one tree
every four minutes at half the rate. The panel says which is which instead of
ranking them by xp and leaving you to find out.

## Installing

Once it is on the RuneLite plugin hub: **Configuration** (the wrench) →
**Plugin Hub** → search for *Next Step* → **Install**.

To build and run it from source you need JDK 11 or later:

```
./gradlew run      # launches a RuneLite client with the plugin side-loaded
./gradlew build    # compiles and runs the tests
```

## The data

The plugin ships its own content database rather than calling anything at
runtime: **256 activities**, **183 training methods across 23 skills** and
**110 item sources**. It makes no network requests of its own.

That data is taken from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/),
which publishes under CC BY-NC-SA 3.0. Every record carries the `wikiUrl` of the
page it came from, so any claim the panel makes can be traced to its source.
See [NOTICE.md](NOTICE.md) for the attribution and licensing in full - the code
and the data do not carry the same licence.

Old School RuneScape is a trademark of Jagex Ltd. This is a third-party tool,
not made by or affiliated with Jagex.

## Development

The JSON under `src/main/resources/com/nextstep/` is generated, not hand-edited.
`tools/` holds the generators and explains how to regenerate it; `ContentDatabaseTest`
is what catches a dangling quest reference or a bad skill name afterwards.

Four diagnostic harnesses render the panel without a game client, which is how
most of it was checked:

```
./gradlew paneltext --args="skill:THIEVING:99 THIEVING=76"   # what the panel renders, as text
./gradlew panelshot --args="240 out.png skill:ATTACK:99"     # ... and as a PNG
./gradlew plandiag                                           # a plan, without the Swing layer
./gradlew iconcoverage                                       # which gear lines resolve to item icons
```

Java sources are deliberately pure ASCII, with `\uXXXX` escapes for the few
typographic characters the panel renders. The plugin hub's `standard` build
replaces `build.gradle`, and with it the UTF-8 compiler setting, so an em dash
checked in as a raw byte could reach a player's sidebar as mojibake. The
`asciiCheck` task, which `./gradlew build` runs, is what enforces that.

`DEVLOG.md` is the long-form record of how this was built and which approaches
failed - written for whoever maintains it next, including a future me.

## Licence

[BSD 2-Clause](LICENSE) for the code. The bundled wiki data is CC BY-NC-SA 3.0;
see [NOTICE.md](NOTICE.md).
