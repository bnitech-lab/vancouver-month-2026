# CLAUDE.md

Rules for an AI agent that changes this repository. The README says what the game is and how it plays; this file
says how to change it without breaking it.

## What this is

- A small turn-based game in Kotlin Multiplatform: one month in a made-up Vancouver, one job offer to land.
- The rules live in `core` and run the same on the JVM (terminal) and in the browser (phone page).
- A merge to `main` publishes to GitHub Pages (while the repository variable `PAGES_ENABLED` is `true`, as it is
  now), so `main` is what players see. Keep it releasable.

## Commands

Run from the repository root. The first web build downloads Node.js and npm packages. Java 21 and Chrome are needed.

```sh
./gradlew test --console=plain                  # every test, JVM and headless Chrome (what CI runs)
./gradlew :core:jvmTest                         # one module on the JVM; also :save:jvmTest and :text:jvmTest
./gradlew :terminal:test                        # the terminal screen's tests
./gradlew :web:jsBrowserTest                    # the web tests (drawn for real on SwiftShader, headless Chrome)
./gradlew :core:jvmTest --tests '*MonthTest*'   # one class; the root `test` task does not take --tests
./gradlew :web:jsBrowserDistribution            # build the page into web/build/dist/js/productionExecutable/
./play                                          # the terminal game (--seed 1011, --today, --today 2026-10-29)
```

CI (`.github/workflows/web.yml`) runs `./gradlew test :web:jsBrowserDistribution`, then fails if
`vancouver-month.js` is over 1048576 bytes. Match that before opening a PR.

## Modules and who may depend on whom

| Module | Owns | Depends on |
|---|---|---|
| `core` | The rules: rooms, guess money, the month, interviews, endings; `Dice` carries a month's luck; rooms are dealt from the seed | none (stdlib) |
| `save` | The month in progress as a short record (`MonthRecord`), replayed through core to rebuild it | `core` |
| `text` | Korean wording both screens share, patch notes (`PatchNotes.kt`), typed amounts. No layout | `core` |
| `terminal` | The text screen (JVM only) | `core`, `text` |
| `web` | The phone page: DOM, CSS, 3D town (Three.js), sound | `text`, `save` (and so `core`) |

Never point an arrow the other way, and never put layout or browser APIs in `core`, `save` or `text`.
`tools/vancouver_map` is the script that makes the map data; its README says how to rebuild it.

## Rules for every change

1. **People, businesses and numbers are invented.** Real city, district and water names (Vancouver, East Vancouver,
   Burnaby, False Creek) and the SkyTrain may appear, as they already do. Every person, business, address, brand,
   station name and number is made up. This repository is public. Why: the game must be safe to publish.
   The map's shapes come from open data under its licence.
2. **Rules stay in `core` and stay deterministic.** The same seed and choices give the same result. Luck in a
   month goes through `Dice`; anything else random (the room deal) takes a seeded `Random(seed)`. No wall clock and
   no unseeded `Random` in `core`. Why: replays, today's run and saves depend on it.
3. **A rule change re-runs the balance bot** (`BalanceTest`, in `core`) and the README states the new rates.
   Why: the month's tightness is the design.
4. **Other layers must not re-derive a rule.** If `text`, `web` or `terminal` needs to know why something is
   refused or what a forecast is, ask `core`. Why: two copies drift.
5. **A change players can see adds a patch note and a version.** Edit `PatchNotes.kt` in `text/src/commonMain/`:
   add a `PatchNote` at the top of `PatchNotes.ALL` and set `CURRENT` to the same version. Then regenerate the file with
   `UPDATE_PATCH_NOTES=1 ./gradlew :text:jvmTest --tests '*PatchNotesFileTest*' --rerun` and commit `PATCH_NOTES.md`
   with it (a test fails if they differ). Give the note the day it merges in Vancouver (`2026-11-01` form) and at
   least one line under `added`, `improved`, `fixed` or `known`; `PatchNotesTest` checks the order, the dates and
   that `CURRENT` is the newest. Lines are short, friendly Korean for players: no PR numbers, file names or
   tools; at most 72 columns a line and 32 for a headline, where a Hangul syllable counts 2 (`PatchNotesTest`).
6. **The save record.** When the shape of `MonthRecord` or what a record means changes, bump `MonthRecord.VERSION`
   and drop old lines without a crash: a record that cannot be read or replayed is dropped silently. Do not key
   saved data to enum order. The replay test must still pass against the hand-written months.
7. **Page size and frame budget.** The page's script stays under 1 MiB (CI checks it; the README states the current
   size, update it when it moves). The draw budgets are in `web/src/jsTest/.../TownBudgetTest.kt`; keep them green
   and re-run them when something is drawn each frame.
8. **Tests do not depend on the machine.** Fixed screen size, no wall clock, no timing waits, no real-time quantity
   (such as how far a character has walked) deciding a result. Drive the clock from the test.
9. **Issues labelled `for-dawn` belong to the owner.** Never fix them; give hints in a comment only.
10. **Never commit or push to `main`.** Branch `vancouver-month-2026/claude/<topic>`, open a PR, wait for CI to be
    green. A development agent never merges; the session that coordinates the work merges after its checks.
11. **Commits:** `[claude] <type>: <description>`, small, one concern each, ending with the same
    `Co-Authored-By` trailer lines as the recent commits in `git log`. Use no em dash anywhere (code, docs,
    patch notes, commit messages).
12. **Language.** Text players read is Korean in the game's voice (read the newest entries in `PATCH_NOTES.md`
    first: polite "-요" endings, plain words, no translated-sounding phrases). Code, comments and docs are English.

## Findings that came back twice or more in review

Each of these was raised on more than one PR. Check your change against them before asking for review.

- **A patch note or README line says more than the game does, or is wrong for its version.** Read each line
  against the behaviour for the actual zone, day and state, and do not repeat an older entry.
  (PR 20, 24, 26, 28, 29, 30)
- **Korean that reads clipped or grammatically off** in player text: noun fragments, a wrong particle ("$20이에요",
  not "$20예요"), word-for-word English. Read it aloud. (PR 20, 26, 27, 29, 33)
- **Two PRs claim the same version.** Before merging, rebase on `main` and re-number the note, `CURRENT`, the order
  of `PatchNotes.ALL` and `PATCH_NOTES.md`; re-check tests that name a version or a record's shape. (PR 20, 21, 29, 30)
- **A test that cannot fail on the claim it makes:** compares a value with itself, checks only what a list holds, or
  was loosened to pass. Pin golden values; show the test fails when the code is broken. (PR 5, 14, 18, 26, 29, 33)
- **Tests that depend on real time or hardware:** an animation or walk read in wall-clock time, a timing
  assertion, state updated only on drawn frames. Make them deterministic. (PR 12, 17, 29, 31)
- **Per-frame allocation and per-frame cost in the town:** new arrays or objects each frame, a material drawn twice,
  the near draw rewritten twice. Reuse buffers; add the case to `TownBudgetTest`. (PR 12, 14, 23, 28, 29, 30)
- **Work in the frame loop outside its `try`:** an error in a map, sound or weather step stops the whole town. Guard
  each new per-frame step on its own and `console.warn` the error. (PR 8, 14, 18)
- **Small phones unchecked:** a new card, list or log that was not laid out at 320 x 568 (and sideways) can push the
  actions off screen or cover the map. Test the layout at that size. (PR 8, 14, 21, 23)
- **A double tap or tap during the night can spend a day or open the wrong thing.** New buttons over the canvas
  need the same guards as the canvas (dash, 400 ms talk guard, input blocked while the day changes). (PR 10, 12, 33)
- **Rule logic copied outside `core`** (reasons for a refusal, a forecast, date validity). (PR 6, 7, 10)
- **Stale numbers in the README** (page size, balance rates) after a change moved them. (PR 13, 20, 27, 32)
- **Licence and attribution for data and libraries** must keep the statement and the link, on the page and in the
  notice files. (PR 8, 17, 25)
- **Ties and edges in the rules:** two rooms tied for cheapest, amounts at the boundary, bad input to core. Test the
  edges, and make core reject an impossible value. (PR 1, 4, 5)

## Formatting conventions

- No formatter or linter runs in CI. Follow the code around you: Kotlin style, 4-space indent, KDoc on non-obvious
  things, comments that say why.
- Keep new lines to 120 columns (tables excepted). Older code and README lines that run longer are left alone
  unless you are editing them.
- Patch note limits are enforced by `PatchNotesTest` (72 and 32 columns, Hangul counted as 2).
- No em dash (U+2014) in any file or commit message; an en dash in a date range is fine.
- This is a public repository: no local paths, mail addresses or personal details in files, tests, fixtures or commit
  messages.
