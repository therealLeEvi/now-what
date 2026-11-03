# Notices and attribution

The plugin is two separable things, and they do not carry the same licence.

## The code

Everything under `src/main/java`, `src/test/java` and `tools/` is covered by
the BSD 2-Clause licence in [LICENSE](LICENSE).

## The bundled content database

`src/main/resources/com/nextstep/activities.json`, `methods.json` and
`item-sources.json` describe quest and activity requirements, xp rates, drop
sources and recommended setups. That information is taken from the
[Old School RuneScape Wiki](https://oldschool.runescape.wiki/), which
publishes its content under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/).

What that means in practice for anyone reusing this repository:

- **We own none of it.** Every requirement, xp rate, drop estimate and quest
  chain in those files is a fact read off the wiki. The structure, the planner
  and the panel are ours; the content is not, and nothing here should be taken
  to imply otherwise. The BSD licence covers the code, and it does not and
  cannot relicense the wiki's content.
- **Attribution** is required, and it is given where people actually see it:
  permanently in the plugin's own sidebar, in the plugin hub listing, and here.
  Keep the credit if you reuse the data. Each record also carries the `wikiUrl`
  of the page it came from, so any single claim the panel makes can be traced
  to its source - that is navigation, though, not the attribution itself.
- **NonCommercial** is satisfied by a free, unmonetised plugin distributed at no
  charge through the RuneLite plugin hub. Anyone reusing this data commercially
  would need to revisit that; this project does not.
- **ShareAlike** is the clause that takes the most care. Extracting bare facts -
  a skill requirement, a drop rate - into structured JSON is a different kind of
  reuse from mirroring an article, and the wiki's own Bucket API exists so that
  external tools do not have to scrape pages. Where prose was unavoidable, as in
  the `notes` field of `methods.json`, it is a short paraphrase in our own words
  rather than lifted guide text. That is both the more cautious reading of the
  clause and simply better practice.

## Jagex

Old School RuneScape is a trademark of Jagex Ltd. This plugin is a third-party
tool. It is not made by, endorsed by, or affiliated with Jagex in any way, and
it claims no ownership of the game, its content, or its item images, which are
rendered by the RuneLite client from the player's own game assets.
