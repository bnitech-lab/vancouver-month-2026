# Vancouver Month (working title)

A small turn-based game in Kotlin. You play a newcomer in Vancouver with one month to land a job offer before
the money runs out.

1. **September 1st: pick a room.** You have just arrived with $3,000, and the goal is a job offer by September 30th,
   so first you need a room for the month. Its total is paid now and again on October 1st, so a cheaper room needs
   fewer café shifts and leaves more days for the job hunt. Three rooms show rent, a transit zone and whether
   utilities are included, next to a price board. Pick the room you think is cheapest in total, guess what it really
   costs a month, and see the real total and a score: 100 points for the cheapest room, and up to 100 for the guess,
   one point off per dollar missed. One more line says how much more a month the pick costs than the cheapest room,
   or, for the cheapest, that it leaves the most money. The rule behind it: compare rooms by their total monthly
   cost (rent, utilities and a transit pass), not by rent alone.
2. **September 2nd to 30th: one action a day.** Work a café shift, study English, practise coding, send an
   application, or rest. Every day costs living money, and every action but rest costs energy.
3. **Interviews.** An application may book a phone screen (passed on English), and passing it books a technical
   interview (passed on coding). Passing both is a job offer and ends the game. Applications close after the
   27th, so both interviews always fit in September.
4. **The end.** No offer by the 30th means October's total for the same room is due: you either can pay it or you
   cannot. What is left after the first room always covers September's living cost, so October's total is where
   the money runs short.

## Play

In a browser, on a phone or a computer: **https://bnitech-lab.github.io/vancouver-month-2026/** (the phone
screen with the 3D town; add `?seed=1011` to replay a run, or `?today` for today's run).

In a terminal, which needs Java 21:

```sh
./play                # new rooms and a new month
./play --seed 1011    # the same rooms and the same luck again
```

The game text is in Korean for now. This is `./play --seed 1011`, picking the room with the lowest rent and
studying English on the first day:

```
BNI Tech

밴쿠버 한 달 (가제)                판 번호 1011

9월 1일, 딱 $3,000 들고 밴쿠버에 막 도착했어요.
목표: 9월 30일까지 취업 제안 받기
먼저 9월 한 달 지낼 방부터 구해야 해요.

할 일: 한 달 총비용이 가장 싼 방을 고르세요.
       한 달 총비용 = 월세 + 공과금 + 교통 정기권
       총비용은 오늘 내고, 10월 1일에 또 내요.
       방이 쌀수록 알바 대신 취업 준비할 날이 늘어요.

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
  가장 싼 방보다 한 달에 $180 더 내요. 10월 1일에도 그만큼 더 나가요.

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

The same game, drawn for a phone held upright, also works in a desktop browser. Every merge to `main` publishes it
to the address above through GitHub Pages (`.github/workflows/web.yml`). To try a change locally, build it, then open
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

### Continuing a month

The phone screen keeps the month in progress in the browser's `localStorage`: a short record of the run (its seed, or
today's run and its date), the room picked, the guess, and what each day was spent on. Nothing more is needed, since the
same seed and the same choices always play out the same way. The record is written when the month starts and after every
day, and emptied at any ending.

Opened again for the same run, the page first offers that month: a card shows the day and the balance it goes on from,
with 이어하기 and 새로 시작. 이어하기 rebuilds the month by playing the recorded days again through the same rules and goes
straight to that day, in the town or on the button screen; 새로 시작 drops the record and shows the rooms. An address with
no run (neither `?seed` nor `?today`) opens the kept month's run, and there 새로 시작 plays a new run instead. There is one
slot: a page opened for another run leaves the kept month alone, its start card says that starting will drop the month
kept on that day, and starting takes its place. Two tabs share the slot too, so the day last played in either is the one
kept. A record that cannot be read or replayed (another version, or a day the rules refuse) is dropped without a word,
and where the browser refuses storage there is no card and nothing is kept.

A test replays a few kept lines to months written down by hand, so a change to the rules fails it until
`MonthRecord.VERSION` goes up, which drops the older lines rather than continuing them into a different month.

### The 3D town

On the phone screen, September is played in a small 3D town that fills the screen, drawn with
[Three.js](https://threejs.org) from boxes only, with no image files. It aims for a cosy, storybook feel: soft colours,
cartoon shading, a warm sun with soft shadows, round-headed people, round trees in leaf or blossom, flowers, fences,
street lamps and a campfire. The town is real Vancouver, traced from the City's open data (see Map data below) and
shrunk so the farthest walk between two places is about half a minute: the downtown peninsula with the big park at its
tip as a forest of round trees, the West End's low blocks and downtown's towers along the real street grid, the seawall
round the shore, False Creek with its south shore past it, and the inlet with the mountains far across it. The five
places stand in the neighbourhoods that suit them: 내 방 (the room picked on September 1st) with its garden in the West
End, the 카페 by the seawall on False Creek, the 도서관 in the middle of downtown, 코워킹 in the old brick streets at the
east end, and the 회사 tower in the business district by the inlet. The SkyTrain runs on its guideway along False Creek,
out of its tunnel by the station. Each building's name floats over it, and no other place has a name. The day's figures
sit thinly over the top of the screen, and a name that would sit under them or the day's log hides there. The month's
first view looks over 내 방 toward downtown, high enough to show four of the buildings and their names.

A small map of the whole town sits in the top right corner, just under the day's figures and the day's log, so the
player always knows roughly where the character is. It lies with real north up, as a paper map of the city does, and
never turns; a small N with an arrow at its top says so. (The town itself is the real city turned 44.7 degrees, so
downtown's streets run along it; the map turns that back.) It shows the coast from the park's tip across False Creek:
the land, the water, the big park and the seawall, the city's blocks faintly, the five places (내 방 a red square, the
others dots, 회사 green on an interview day as its name is), the camera's view as a pale wedge, and the character as a
gold arrow pointing the way it faces. It is drawn again only when the character or the camera moves. A tap shows it
larger, about four fifths of the screen wide with the places' names; a tap or Escape brings it back, and a talk or the
guide closes it. It is a picture: a screen reader hears only which place the character is nearest, as in "지도: 내 방 근처".

| Do | What happens |
|---|---|
| Tap the ground | The character walks there, around buildings, people, trees and fences, with a marker where it goes |
| Tap twice quickly | It dashes there instead |
| Drag | The camera turns around the character, and tilts within limits that keep the ground in view |
| Pinch, or the mouse wheel | The camera comes nearer or goes farther |
| Tap a person, a building or its name, or walk up to a door or a person | The character goes to that place's person, and a talk opens (a second quick tap only hurries it there) |
| Tap the small map | It shows larger; a tap or Escape brings it back |

The city's buildings never get in the way. One that stands between the camera and the character, or fills the view
just in front of the camera, folds down in a quarter of a second to a low outline of its footprint in its own colour,
and rises again once clear; a tap meant for the ground behind a folded one lands there. So downtown the camera keeps the
distance and the turn the player gave it. Only the five places, the SkyTrain's deck, its station and the station's
roof, the tunnel's mouth, the café's awning and the office's canopy still pull it in front or lift it over; where one of
them stands right behind the character, it first looks along the nearest street. A building the camera would come
inside folds at once, and the near view widens with the view, so nothing out of sight folds. As the character reaches a place the camera comes round to the street side of
the door, unless the player turned it on the way.
A tap on the water walks to the near shore. A talk ignores taps for a moment after it opens, so the finger that opened
it cannot also answer it. While a day changes into the next, the town takes no taps, drags, pinches or names, and no
talk opens, until the morning.

Each place has one person, all invented: 하나 at the 카페, 레오 at the 도서관, 수진 at 코워킹, 마커스 at the 회사 desk and
the neighbour grandmother by the bench at 내 방. A talk shows their name, a small face drawn from their colours, and a
line typed one letter at a time (a tap shows all of it) that fits the day: a first meeting, low energy, money running
short, an interview booked or failed, the first or the last week, and so on. The answers are the place's action with its
effect (알바하기, 영어 공부, 코딩 연습, 지원서 넣기, 쉬기) or the reason it cannot be done today, 이야기 for small talk,
and 그만. On an interview day only 회사 offers 면접 보기, and its name is marked.

The town lives on its own. Four more people, invented too (올리버, 미나, 프리야 and 테오), stroll slow loops along the English Bay
seawall, a West End street near 내 방, the big park's edge and the square in front of the station. One stops when the
character stands right in their way. A tap on one opens a short word of small talk with only 그만, which spends no day;
they stop and face the character until it ends, and say something new each time that day. They are not obstacles, so the
character's walks go through them. In the evening they fade out on their way home and they come back at dawn. The five
at the places stay where they are, so they are always found there, but they look about, turn to face the character when
it comes near, and wave as it comes up. The SkyTrain stands at the station, pulls out east along False Creek, its cars
turning with the guideway, and comes back out of its tunnel from the west every 32 seconds, slowing into the station,
with its windows lit at night. The campfire flickers and lights the bench after dark, light glints on the water, petals
drift down from the blossom trees, and clouds drift high over the water.

Each day has its weather, sunny, cloudy or rainy, worked out from the run's seed and the day, so a run always has the
same skies and everyone who plays today's run gets the same weather. As in a Vancouver September, about three days in
ten are rainy and two cloudy, a day often keeps the sky of the day before, and September 2nd, the first day in town, is
never rainy. The weather is only for the eyes and the ears: no rule reads it, so money, energy and every chance are the
same in any weather, and a test plays the same choices in rain and in sun to the same figures. The top card shows it in
a word after the date, 맑음, 흐림 or 비, and so does the button screen. A cloudy day greys the sky and softens the light.
On a rainy day the sky is greyer and the light softer still, the windows glow a little by day, the haze comes nearer,
the sea darkens, thin rain falls slanted by a light wind with small splashes on the ground, puddles lie on the street,
and 미나 and 프리야 walk under open umbrellas (올리버 runs on, and 테오 trusts his hat). Most of the townspeople and every
passerby have a word about the rain. A new day's weather comes in the dark of the night.

An answer that spends the day ends it: a golden evening, a night with the windows, lamps and campfire lit, and the
morning, in 1.5 seconds, with the character back at 내 방's door and the day's log over the top of the screen. On a day
of rest the character spends the night sitting on the bench by the campfire, with the camera come round to the far side
of the fire; in the morning it stands at 내 방's door and the camera is the player's own again. Every answer goes to the same rules as the buttons, so the same seed and the
same choices end the same way in the town and with buttons; a test plays a whole month both ways. After an offer in the
town, the neighbour has a word on the ending.

The town draws every frame the screen offers while the character walks, the camera moves or the day changes, but its own
life moves at most 30 times a second. While only that life moves, it draws at most 30 frames a second, and 15 once
nobody has touched the town for 20 seconds or while a talk covers it; the sun's shadows are drawn again on every other
of those frames. It draws nothing while the page is hidden. It draws at most two pixels per point however sharp the
phone, and boxes of a kind together: the buildings, trees and the rest that stand still and cast shadows in one draw for
each 40-unit tile of the town, so a tile out of the camera's sight or the sun's shadow box is not drawn at all; the
ground, the sea and the glowing windows in one draw each; all five townspeople in one, all four passersby in another,
the train's cars in two, and all the rain in one, moved on the same beat as the rest of the town's life. If the phone
starts asking for less motion during the month, the town's own life stands still, the rain with it.

Where the phone asks for less motion, the month is played with buttons, under the small fixed town of the first version,
where the character simply appears at each day's building. Where the browser has no WebGL 2, which Three.js needs, there
is no town and the game plays with buttons, as before. If Three.js fails, the buttons take over and the error goes to the
console. Tests force each of these.

The town's rules are plain Kotlin with tests: `Neighbourhood` (the layout on the real map, the people, which place a
spot belongs to), `VancouverMap` (the map's squares and lines, read from the generated `VancouverMapData`), `RailLine`
(the guideway the train follows), `Grid` (the A* search for the way around things), `Trip` (a walk or a dash, and the
double tap), `FollowCamera` (turning, zooming, keeping out of the places' walls, and what a tap lands on), `Cutaway`
(which of the city's buildings fold down out of the camera's way, and how far), `Minimap` (where the grid lies on the
small map turned north up, and which place is nearest), `DayLight` (the light
through the change of day, and when the passersby are out, and how each weather greys it), `Passersby` (their loops, and
stopping for the character), `Greeter` (the five turning and waving), `TownLife` (the train's round and how loud it
rumbles, the fire's flicker and crackle, the glints, petals, clouds and rain, all worked out from the time) and
`FramePace` (which frames are drawn). The people's lines and the rule that picks them are in `text` (`Townsfolk.kt`, and
`Passersby.kt` for the passersby's), and so is each day's weather (`Weather.kt`). `WalkableTown` draws all of it, and
`Three.kt` declares only the parts of Three.js in use. The page's script, Three.js and the map included, is about 808 KB
(about 218 KB gzipped).

### Sound and the first-run guide

The page has a calm background tune of its own, made entirely in code with the Web Audio API: no audio files, no
samples, nothing downloaded. It is written by rules, not copied from any game or song: a music-box melody in F major
pentatonic over soft chords and a low note, at 70 beats a minute, in a loop of 16 bars shaped like a short song
(A A' B A''). The theme comes from a fixed seed, and each pass through the loop changes a few notes a little, so it
never repeats exactly. The player's own clock colours it: the whole tune by day (from 6:00), a softer, sparser and
darker one in the evening (from 18:00), and at night (from 22:00) only a quiet bed of soft noise that rises and falls
like the sea, under a few slow notes. A talk opens with a soft blip, a line types out with very quiet ticks (at most
one every 110 ms), and the change of day rings a gentle chime.

The town has sounds of its own, made from noise in code in the same way. On a rainy day a soft bed of rain plays under
the tune, well below its loudest notes. The SkyTrain rumbles softly as it pulls out and comes in, louder the nearer it is
to the camera and silent while it stands at the station. The campfire crackles quietly when the character is near it
after dark, in the town's own night or late on the player's clock. They follow the same switch, fade and limiter as
the tune.

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
`OfflineAudioContext` to check it is heard, never clips, is quietest at night, and keeps the rain under it; `Sound.kt`
is the switch, `FirstRunGuide.kt` the card and `Memory.kt` the storage that may be refused. The words are in `text`
(`Guide.kt`).

## How it is built

| Part | What | Notes |
|---|---|---|
| `core` | The rules: rooms, the score, the month, interviews, endings, and the run number for a date | Kotlin Multiplatform, standard library only, so the same rules run on the JVM and in a browser. All luck goes through one `Dice` interface, so tests can control it |
| `save` | A month in progress as a short record, and the month rebuilt from it by playing each day's choice again | Kotlin Multiplatform, core only, so the JVM tests it by replaying hundreds of random months beside the live ones |
| `text` | The Korean lines both screens share (amounts, dates, actions, events, endings, today's result line, the townspeople's lines) and how a typed amount is read | Kotlin Multiplatform, no layout |
| `terminal` | The text screen | Kotlin on the JVM |
| `web` | The phone screen in a browser, with the 3D town to walk in | Kotlin/JS drawing the page with the DOM, plain CSS, no framework. The town is drawn with Three.js |

```sh
./gradlew test                                  # rules, balance and screen tests, on the JVM and in headless Chrome
./gradlew :core:jvmTest --tests '*MonthTest*'   # one test class, on the JVM only
```

The browser tests run in Chrome, so `./gradlew test` needs Chrome installed. The web tests draw the real town
with WebGL on SwiftShader, Chrome's software renderer, set up in `web/karma.config.d`. `./gradlew test` does not take
`--tests`: to run some tests only, name the module's JVM test task, which is `jvmTest` in `core`, `save` and `text`
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

Dawn plays the month on a phone and decides what grows next.

## Map data

`tools/vancouver_map` traces real Vancouver (the peninsula, the big park at its tip, False Creek and its south shore)
into a grid of squares in `VancouverMapData.kt`, with its city blocks and small parks, and the town is built on it.
The page shows the licence's statement at the foot of the card on how to play. Its sources are
the City of Vancouver's open datasets Shoreline 2002, Parks - polygon representation, Rapid transit lines, City
boundary, Block outlines and Local area boundary, fetched on 2026-10-31 from
[opendata.vancouver.ca](https://opendata.vancouver.ca).

Contains information licensed under the
[Open Government Licence – Vancouver](https://opendata.vancouver.ca/pages/licence/).

The map data is licensed under that licence (version 1.0), not MIT, and keeps its attribution: on the page, the
card on how to play shows the statement with a link to the licence, and `MAP-DATA-NOTICE.txt` ships beside the
script. The code that makes and reads the data is MIT. How to fetch the data again and rebuild the map is in
[tools/vancouver_map/README.md](tools/vancouver_map/README.md).

## Licence

MIT, except the map data: see [LICENSE](LICENSE), and Map data above for the map. The page carries both notices
beside its script, `three-LICENSE.txt` and `MAP-DATA-NOTICE.txt`.

The phone screen bundles [Three.js](https://github.com/mrdoob/three.js) 0.169.0, which is also under the MIT
licence (Copyright © 2010-2024 three.js authors). The build copies its full licence text next to the script, as
`three-LICENSE.txt`.
