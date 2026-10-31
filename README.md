# Vancouver Month (working title)

A small turn-based game in Kotlin. You play a newcomer in Vancouver with one month to land a job offer before
the money runs out.

1. **September 1st: pick a room.** Three rooms show rent, a transit zone and whether utilities are included, next
   to a price board. Pick the room you think is cheapest in total, guess what it really costs a month, and see the
   real total and a score: 100 points for the cheapest room, and up to 100 for the guess, one point off per dollar
   missed. The rule behind it: compare rooms by their total monthly cost (rent, utilities and a transit pass), not
   by rent alone.
2. **September 2nd to 30th: one action a day.** Work a café shift, study English, practise coding, send an
   application, or rest. Every day costs living money, and every action but rest costs energy.
3. **Interviews.** An application may book a phone screen (passed on English), and passing it books a technical
   interview (passed on coding). Passing both is a job offer and ends the game. Applications close after the
   27th, so both interviews always fit in September.
4. **The end.** No offer by the 30th means October's total for the same room is due: you either can pay it or you
   cannot. What is left after the first room always covers September's living cost, so October's total is where
   the money runs short.

## Play

Needs Java 21.

```sh
./play                # new rooms and a new month
./play --seed 1011    # the same rooms and the same luck again
```

The game text is in Korean for now. This is `./play --seed 1011`, picking the room with the lowest rent and
studying English on the first day:

```
BNI Tech

밴쿠버 한 달 (가제)                판 번호 1011
잔고 $3,000 · 9월 1일

할 일: 한 달 총비용이 가장 싼 방을 고르세요.
       한 달 총비용 = 월세 + 공과금 + 교통 정기권

요금표
  교통 정기권   1구역 $105 · 2구역 $145 · 3구역 $195
  공과금        포함이면 $0, 별도면 한 달 $120~$190 (방마다 다름)

  1) 월세 $1,070   교통 1구역   공과금 포함
  2) 월세   $970   교통 3구역   공과금 별도
  3) 월세 $1,020   교통 2구역   공과금 포함

어느 방? (1-3) > 2
이 방의 한 달 총비용은 얼마일까요? ($) > 1300

  한 달 총비용  $1,345
  월세 $970 + 공과금 $180 + 교통 $195

  총비용이 가장 싼 방은 3번이었어요 ($1,165).   +0
  내 짐작과 차이 $45   +55
  방 고르기 점수 55 / 200

  첫 달을 내고 남은 잔고 $1,655

9월 한 달이 시작돼요.
  목표: 9월 30일까지 취업 제안 받기
  매일 생활비 $40이 나가고, 10월 1일에는 이 방의 총비용 $1,345를 또 내야 해요.
  면접은 두 번이에요. 1차 영어 면접은 영어 실력, 2차 기술 면접은 코딩 실력으로 붙어요.

[9월 2일] 남은 날 29일
  잔고 $1,655 · 10월 1일에 낼 총비용 $1,345
  영어 1/10 · 코딩 3/10 · 체력 5/5
오늘 할 일 하나:
  1) 카페 알바     +$110, 체력 -1
  2) 영어 공부     영어 +1, 체력 -1
  3) 코딩 연습     코딩 +1, 체력 -1
  4) 지원서 넣기   면접이 잡힐 확률 27%, 체력 -1
  5) 쉬기          체력 +2
오늘 할 일? (1-5) > 2
  영어 실력이 올랐어요. 영어 2/10
  생활비 -$40, 잔고 $1,615
```

A test plays this seed and checks that the screen still matches this sample.

### On a phone screen

The same game, drawn for a phone held upright, also works in a desktop browser. Build it, then open
`web/build/dist/js/productionExecutable/index.html` from disk or put that folder on any static host:

```sh
./gradlew :web:jsBrowserDistribution
open web/build/dist/js/productionExecutable/index.html   # add ?seed=1011 to replay a seed
```

The first build downloads Node.js and the npm packages the Kotlin/JS build uses, Three.js among them.

### Today's run

Everyone who plays today's run (오늘의 판) on the same date gets the same rooms and the same luck. The date picks
the run number, so no server is needed. At the end, the result comes as one line to paste anywhere, with no run
number, no link and nothing about the player:

```
밴쿠버 한 달 · 10월 30일 오늘의 판 · 방 고르기 155/200 · 9월 19일 취업 제안
```

The other two endings read `9월을 버팀` and `10월 총비용 모자람`.

```sh
./play --today              # today's run, from this computer's date
./play --today 2026-10-29   # that date's run (also --today=2026-10-29), any real date from 2000 to 2099
```

The terminal prints the line under `결과 한 줄 (복사해서 붙여 넣으세요)`. `--today` with a `--seed` number is
refused, and so is a date that does not exist.

On the phone screen, tap `오늘의 판` on any ending screen, or open the page with `?today`. The page reads the
browser's date and writes it into the address (`?today=2026-10-30`), so a reload or a shared link replays that
date's run, even on another day. The ending shows the line with a `결과 복사` button; where the browser does not
allow copying, the line is selected for copying by hand. `?seed` wins over `?today`.

The date is each player's own, so near midnight two time zones can be on different runs. The line names the
month and day but not the year, so the same date in two different years reads the same, although those are two
different runs.

### The 3D town

On the phone screen, September is played in a small 3D town that fills the screen, drawn with
[Three.js](https://threejs.org) from boxes only, with no image files. It aims for a cosy, storybook feel: soft colours,
cartoon shading, a warm sun with soft shadows, round-headed people, round trees in leaf or blossom, flowers, fences,
street lamps and a campfire. The five places stand on a stone street with brick pavements: 내 방 (the room picked on
September 1st) with its garden, the 카페, the 도서관, 코워킹 and the 회사 tower, with a park and a pond, a seawall path by
the water, a SkyTrain guideway with a station and a train, and mountains across the water. Each building's name floats
over it. The day's figures sit thinly over the top of the screen. The month's first view looks down the street from
its west end, high enough to show four of the buildings and their names.

| Do | What happens |
|---|---|
| Tap the ground | The character walks there, around buildings, people, trees and fences, with a marker where it goes |
| Tap twice quickly | It dashes there instead |
| Drag | The camera turns around the character, and tilts within limits that keep the ground in view |
| Pinch, or the mouse wheel | The camera comes nearer or goes farther |
| Tap a person, a building or its name, or walk up to a door or a person | The character goes to that place's person, and a talk opens (a second quick tap only hurries it there) |

A talk ignores taps for a moment after it opens, so the finger that opened it cannot also answer it. While a day
changes into the next, the town takes no taps, drags, pinches or names, and no talk opens, until the morning.

Each place has one person, all invented: 하나 at the 카페, 레오 at the 도서관, 수진 at 코워킹, 마커스 at the 회사 desk and
the neighbour grandmother by the bench at 내 방. A talk shows their name, a small face drawn from their colours, and a
line typed one letter at a time (a tap shows all of it) that fits the day: a first meeting, low energy, money running
short, an interview booked or failed, the first or the last week, and so on. The answers are the place's action with its
effect (알바하기, 영어 공부, 코딩 연습, 지원서 넣기, 쉬기) or the reason it cannot be done today, 이야기 for small talk,
and 그만. On an interview day only 회사 offers 면접 보기, and its name is marked.

The town lives on its own. Four more people, invented too (올리버, 미나, 프리야 and 테오), stroll slow loops along the
seawall, the south pavement and across the street, the park's paths and the plaza by the station. One stops when the
character stands right in their way. A tap on one opens a short word of small talk with only 그만, which spends no day;
they stop and face the character until it ends, and say something new each time that day. They are not obstacles, so
the character's walks go through them. In the
evening they fade out on their way home and they come back at dawn. The five at the places stay where they are, so they
are always found there, but they look about, turn to face the character when it comes near, and wave as it comes up.
The SkyTrain stands at the station, pulls out east and comes back in from the west every 32 seconds, slowing into the
station, with its windows lit at night. The campfire flickers and lights the bench after dark, light glints on the
water, petals drift down from the blossom trees, and clouds drift high over the water.

An answer that spends the day ends it: a golden evening, a night with the windows, lamps and campfire lit, and the
morning, in 1.5 seconds, with the character back at 내 방's door and the day's log over the top of the screen. On a day
of rest the character spends the night sitting on the bench by the campfire, with the camera come round to the far side
of the fire; in the morning it stands at 내 방's door and the camera is the player's own again. Every answer goes to the same rules as the buttons, so the same seed and the
same choices end the same way in the town and with buttons; a test plays a whole month both ways. After an offer in the
town, the neighbour has a word on the ending.

The town draws every frame the screen offers while the character walks, the camera moves or the day changes, but its
own life moves at most 30 times a second. While only that life moves, it draws at most 30 frames a second, and 15 once
nobody has touched the town for 20 seconds or while a talk covers it; the sun's shadows are drawn again on every other
of those frames. It draws nothing while the page is hidden. It draws at most two pixels per point however sharp the
phone, and every box of a kind in one draw: all five townspeople in one, all four passersby in another. If the phone
starts asking for less motion during the month, the town's own life stands still.

Where the phone asks for less motion, the month is played with buttons, under the small fixed town of the first version,
where the character simply appears at each day's building. Where the browser has no WebGL 2, which Three.js needs, there
is no town and the game plays with buttons, as before. If Three.js fails, the buttons take over and the error goes to the
console. Tests force each of these.

The town's rules are plain Kotlin with tests: `Neighbourhood` (the layout, the people, which place a spot belongs to),
`Grid` (the A* search for the way around things), `Trip` (a walk or a dash, and the double tap), `FollowCamera`
(turning, zooming, keeping out of walls, and what a tap lands on), `DayLight` (the light through the change of day, and
when the passersby are out), `Passersby` (their loops, and stopping for the character), `Greeter` (the five turning and
waving), `TownLife` (the train's round, the fire's flicker, the glints, petals and clouds, all worked out from the time)
and `FramePace` (which frames are drawn). The people's lines and the rule that picks them are in `text` (`Townsfolk.kt`,
and `Passersby.kt` for the passersby's). `WalkableTown` draws all of it, and `Three.kt` declares only the parts of
Three.js in use. The page's script, Three.js included, is about 692 KB (about 185 KB gzipped).

### Sound and the first-run guide

The page has a calm background tune of its own, made entirely in code with the Web Audio API: no audio files, no
samples, nothing downloaded. It is written by rules, not copied from any game or song: a music-box melody in F major
pentatonic over soft chords and a low note, at 70 beats a minute, in a loop of 16 bars shaped like a short song
(A A' B A''). The theme comes from a fixed seed, and each pass through the loop changes a few notes a little, so it
never repeats exactly. The player's own clock colours it: the whole tune by day (from 6:00), a softer, sparser and
darker one in the evening (from 18:00), and at night (from 22:00) only a quiet bed of soft noise that rises and falls
like the sea, under a few slow notes. A talk opens with a soft blip, a line types out with very quiet ticks (at most
one every 110 ms), and the change of day rings a gentle chime.

Sound is off until the player turns it on with the switch in the town's top card, at the top of the button screen, and
by the title on the room and ending screens. It is a plain button whose words say what a tap does, 소리 켜기 or 소리 끄기;
it has no `aria-pressed`, since ARIA's guidance for toggle buttons keeps a toggle's name steady. The browser's
audio is made only on that tap, as browsers require. The choice is kept in `localStorage`; when it was left on, the
sound comes back on the first tap anywhere. It pauses while the page is hidden, fades in and out, and plays quietly
through a limiter. Where the browser has no Web Audio there is no switch, and where storage is refused nothing is
remembered.

The first time the town opens, one card says how to play: tap the ground to walk, tap twice to dash, drag to look
around and pinch to zoom, and tap a person or a building to talk, where picking a job ends the day. It ends with the
month's goal, in the words the month's start uses. 시작 or Escape closes it, and the "?" in the top card opens it again.
The button screen has no card, since its buttons say what they do.

`Music.kt` writes the tune as notes and is tested without sound; `Synth.kt` plays it, and the tests render it into an
`OfflineAudioContext` to check it is heard, never clips, and is quietest at night; `Sound.kt` is the switch,
`FirstRunGuide.kt` the card and `Memory.kt` the storage that may be refused. The words are in `text` (`Guide.kt`).

## How it is built

| Part | What | Notes |
|---|---|---|
| `core` | The rules: rooms, the score, the month, interviews, endings, and the run number for a date | Kotlin Multiplatform, standard library only, so the same rules run on the JVM and in a browser. All luck goes through one `Dice` interface, so tests can control it |
| `text` | The Korean lines both screens share (amounts, dates, actions, events, endings, today's result line, the townspeople's lines) and how a typed amount is read | Kotlin Multiplatform, no layout |
| `terminal` | The text screen | Kotlin on the JVM |
| `web` | The phone screen in a browser, with the 3D town to walk in | Kotlin/JS drawing the page with the DOM, plain CSS, no framework. The town is drawn with Three.js |

```sh
./gradlew test                                  # rules, balance and screen tests, on the JVM and in headless Chrome
./gradlew :core:jvmTest --tests '*MonthTest*'   # one test class, on the JVM only
```

The browser tests run in Chrome, so `./gradlew test` needs Chrome installed. The web tests draw the real town
with WebGL on SwiftShader, Chrome's software renderer, set up in `web/karma.config.d`. `./gradlew test` does not take
`--tests`: to run some tests only, name the module's JVM test task, which is `jvmTest` in `core` and `text`
and `test` in `terminal` (for example `./gradlew :terminal:test --tests '*TerminalGameTest*'`).

Rooms come from a seed. Farther zones have lower rent and a dearer pass, and utilities may or may not be
included, so the room with the lowest rent has the lowest total in only a bit more than half of the deals.

The month is balanced by bots that play 2,000 runs with the real rules (`BalanceTest`). A bot that rests when
tired, works when the rent is at risk, studies and applies lands an offer in about 43% of runs; a bot that picks
at random lands one in about 24%; a bot that only rests never does. (Measured on seeds 1 to 2,000 with Kotlin
2.4.21; the test itself only checks a range.)

## Who wrote what

The code is written by Claude (an AI coding assistant, through Claude Code). Dawn chose the game and its rules,
plays every version and decides what changes. Some issues are left for Dawn to fix by hand, as Kotlin practice.

## Numbers

Every price, wage and chance here is invented. None of them is a real Vancouver rent, utility bill, transit fare
or wage.

## What comes next

Dawn plays the living town, listens to the village tune and decides what changes. Playing on a phone needs a public address, which waits for Dawn.

## Licence

MIT. See [LICENSE](LICENSE).

The phone screen bundles [Three.js](https://github.com/mrdoob/three.js) 0.169.0, which is also under the MIT
licence (Copyright © 2010-2024 three.js authors). The build copies its full licence text next to the script, as
`three-LICENSE.txt`.
