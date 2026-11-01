# Vancouver Month (working title)

A small turn-based game in Kotlin. You play a newcomer in Vancouver with one month to land a job offer before
the money runs out.

1. **September 1st: pick a room.** You have just arrived with $3,000, and the goal is a job offer by September 30th,
   so first you need a room for the month. Its total is paid now and again on October 1st, so a cheaper room needs
   fewer café shifts and leaves more days for the job hunt. Three rooms stand as numbered pins on the whole map, with
   every 회사 and 카페 named on it; a pin shows its room's rent, transit zone and whether utilities are included, next to a
   price board (in the terminal, the three are a list). Pick the room you think is cheapest in total, guess what it really
   costs a month, and see the real total. The guess moves money as the month starts: $100 less the dollars it missed
   by, never below -$300 (exact +$100, $100 off $0, $250 off -$150, $400 off or more -$300). One more line says how
   much more a month the pick costs than the cheapest room, or, for the cheapest, that it leaves the most money; the
   cheapest pick earns nothing extra beyond paying less. The rule behind it: compare rooms by their total monthly
   cost (rent, utilities and a transit pass), not by rent alone.
2. **September 2nd to 30th: a day of stamina.** Each day starts at 8 in the morning with 5 stamina. Work a café shift
   (3 stamina, $70), study English or practise coding (2 each; four studies raise a skill a level), or send an
   application (1, once a day). Each action moves the day's clock on 3 hours for each stamina it costs, so a day holds
   several actions; with no stamina left it is night (11 in the evening), and sleep at 내 방 ends the day and starts
   the next morning. Sleeping early, with stamina left, is allowed and costs nothing more. Every day costs living money,
   paid at night.
3. **Interviews.** An application may book a phone screen (passed on English), and passing it books a technical
   interview (passed on coding), both at the 회사 the application went to. On an interview day the interview comes first
   (2 stamina), and the rest of the day is free. Passing both is a job offer and ends the game. Applications close after
   the 27th, so both interviews always
   fit in September.
4. **The end.** No offer by the 30th means October's total for the same room is due: you either can pay it or you
   cannot. What is left after the first room, even after the largest penalty for the guess, always covers
   September's living cost, so October's total is where the money runs short.

## Play

In a browser, on a phone or a computer: **https://bnitech-lab.github.io/vancouver-month-2026/** (the phone
screen with the 3D town; add `?seed=1011` to replay a run, or `?today` for today's run).

What changed in each version is in [PATCH_NOTES.md](PATCH_NOTES.md), in Korean, the same notes the game shows under
업데이트 소식.

In a terminal, which needs Java 21:

```sh
./play                # new rooms and a new month
./play --seed 1011    # the same rooms and the same luck again
```

The game text is in Korean for now. This is `./play --seed 1011`, picking the room with the lowest rent and
studying English first thing on the first day, which takes the morning and leaves the afternoon:

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

총비용 짐작: 방을 고른 뒤 그 방의 한 달 총비용을 짐작해 적어요.
             맞힐수록 최대 $100을 더 받고, $100보다 더 틀리면 그만큼 잃어요(최대 $300).

어느 방? (1-3) > 2
이 방의 한 달 총비용은 얼마일까요? ($) > 1300

  한 달 총비용  $1,345
  월세 $970 + 공과금 $180 + 교통 $195

  총비용이 가장 싼 방은 3번이었어요 ($1,165).
  내 짐작과 차이 $45
  가장 싼 방보다 한 달에 $180 더 내요. 10월 1일에도 그만큼 더 나가요.

  짐작 보상 +$55, 잔고 $1,710
  $100에서 틀린 만큼 빼고 받아요.

9월 한 달이 시작돼요.
  목표: 9월 30일까지 취업 제안 받기
  매일 생활비 $40이 나가고, 10월 1일에는 이 방의 총비용 $1,345를 또 내야 해요.
  하루는 아침 8시에 체력 5로 시작해요. 일을 하면 체력만큼 시간이 흘러요.
  체력을 다 쓰면 밤이에요. 내 방에서 자면 다음 날 아침이에요.
  면접은 두 번이에요. 1차 영어 면접은 영어 실력, 2차 기술 면접은 코딩 실력으로 붙어요.

[9월 2일 아침 8시] 남은 날 29일
  잔고 $1,710 · 10월 1일에 낼 총비용 $1,345
  영어 1/10 · 코딩 3/10 · 체력 5/5
지금 할 일:
  1) 카페 알바     +$70, 체력 -3
  2) 영어 공부     레벨업까지 4번, 체력 -2
  3) 코딩 연습     레벨업까지 4번, 체력 -2
  4) 지원서 넣기   면접이 잡힐 확률 27%, 체력 -1
  5) 자기          다음 날 아침, 체력 5
지금 할 일? (1-5) > 2
  영어 공부를 했어요. 레벨업까지 3번

[9월 2일 오후 2시] 남은 날 29일
  잔고 $1,710 · 10월 1일에 낼 총비용 $1,345
  영어 1/10 · 코딩 3/10 · 체력 3/5
지금 할 일:
  1) 카페 알바     +$70, 체력 -3
  2) 영어 공부     레벨업까지 3번, 체력 -2
  3) 코딩 연습     레벨업까지 4번, 체력 -2
  4) 지원서 넣기   면접이 잡힐 확률 27%, 체력 -1
  5) 자기          다음 날 아침, 체력 5
지금 할 일? (1-5) >
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
밴쿠버 한 달 · 10월 30일 오늘의 판 · 짐작 보상 +$55 · 9월 19일 취업 제안
```

A guess that lost money reads `짐작 패널티 -$150`. The other two endings read `9월을 버팀` and `10월 총비용 모자람`.

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
today's run and its date), the room picked, the guess, every action, interview and night's sleep in order, each action
with the place of its kind it was taken at (a digit after its letter, none for downtown's), and in its place between
them each spend in the town (what, and how much). The last application's place is where its interview is held. A day is
everything up to its sleep, so the days need no
mark of their own. Nothing more is needed, since the same seed and the same choices always play out the same way. The
record is written when the month starts, after every action, interview, sleep and spend, and emptied at any ending.
Continued, a month keeps what was bought, the day's time and stamina, and what was spent since the last action still
heads the next one's log; the character starts at 내 방's door, at the time the day had reached.

Opened again for the same run, the intro first offers that month: at its top, the day (with the time, once the day is
past its morning) and the balance it goes on from, and at its foot 이어하기 and 새로 시작 in place of 시작하기. 이어하기
rebuilds the month by playing the recorded choices again
through the same rules (the guess's money included) and goes straight to that day, in the town or on the button screen;
새로 시작 drops the record and shows the rooms. An address with no run (neither `?seed` nor `?today`) opens the kept
month's run, and there 새로 시작 plays a new run instead, straight to its rooms. There is one
slot: a page opened for another run leaves the kept month alone, its start card says that starting will drop the month
kept on that day, and starting takes its place. Two tabs share the slot too, so the day last played in either is the one
kept. A record that cannot be read or replayed (another version, or a day the rules refuse) is dropped without a word,
and where the browser refuses storage there is no card and nothing is kept.

A test replays a few kept lines to months written down by hand, so a change to the rules fails it until
`MonthRecord.VERSION` goes up, which drops the older lines rather than continuing them into a different month. It is 4
since a day began to hold several actions until the night's sleep (v0.17): a month kept before that, one letter a day,
would play out as another month. The places' digits (v0.20) needed no new version: a line without them took every
action downtown, where every place then stood, and goes on so, its interview at downtown's 회사.

### The 3D town

On the phone screen, September is played in a small 3D town that fills the screen, drawn with
[Three.js](https://threejs.org) from boxes only, with no image files. It aims for a cosy, storybook feel: soft colours,
cartoon shading, a warm sun with soft shadows, round-headed people, round trees in leaf or blossom, flowers, fences,
street lamps and a campfire. The town is real Vancouver and south-west Burnaby, traced from the two cities' open data
(see Map data below), one square for 25 m, so the farthest walk between two of the downtown places is about half a
minute: the downtown peninsula with the big park at its tip as a forest of round trees, the West End's low blocks and
downtown's towers along the real street grid, the seawall round the shore, False Creek with its south shore past it, and
the inlet with the mountains far across it; then, east of downtown, East Vancouver and Burnaby: detached houses on each
real block, each with a pitched roof, a front door to the street and a lawn, a tree in some yards, smaller and lower
with wider yards between them the farther from downtown; mid-rise round the SkyTrain stations, towers round the Burnaby
town centre, and their parks as forest, about two minutes on foot from downtown. There are three cafés, three
libraries, two coworking spaces and three offices, each named for what is round it, each kind with its own look (a
café's green and cream awning, a library's steps, a coworking space's green sign, an office tower's canopy and its bands
of glass round each storey), and the same
action at any place of a kind. Downtown has one of each in the neighbourhoods that suit them: 물가 카페 by the seawall on
False Creek, 시내 도서관 in the middle of downtown, 벽돌길 코워킹 in the old brick streets at the east end and 항구 회사, a
tower in the business district by the inlet. East Vancouver has 갈림길 회사 by the junction's station, 동쪽 마을 도서관
and 작은 공원 카페 by theirs; Burnaby has 큰길 코워킹 by the high street's stop near zone 2's 내 방, 큰 공원 도서관 by the big
park's stop, 중심가 회사 across the guideway from the town centre's station and 비탈 카페 by the slope's stop beside zone 3's 내
방, so a room in
any zone has each kind within a short ride. 내 방 (the room picked on September 1st) stands with its garden where the
room is: in the West End for a room in zone 1, in Burnaby for zones 2 and 3, each morning
starting at its door. The SkyTrain runs on its guideway out of its tunnel by the station, along False Creek and out
through East Vancouver to Burnaby. Each place's name floats over it, and so does the bike shop's (see below); no other
building has a name. Only the ground and buildings near the camera are drawn; the far city is left to the haze. The
houses stand one by one only within 75 squares of the camera and in its view, and round the character; farther off, each
row of houses is one low box (out where the yards are wide, each house is), and a house coming near grows out of it, its
yard opening and its roof and door growing on over the last 12 squares. Where the yards between houses are wide, mostly
in Burnaby, the character walks through them. The day's figures sit thinly over the top of the screen, and a name that
would sit under them or the day's log hides there. The month's first view looks over 내 방, toward downtown from the West
End, high enough to show four of the buildings and their names.

A small map sits in the top right corner, just under the day's figures and the day's log, so the player always knows
roughly where the character is. It lies with real north up, as a paper map of the city does, and never turns; a small N
with an arrow at its top says so. (The town itself is the real city turned 44.7 degrees, so downtown's streets run along
it; the map turns that back.) It shows the ground round the character, 100 squares (2.5 km) across, the character in its
middle, and follows it: the land, the water, the parks and the seawall, the city's blocks faintly, the places (내 방
a red square, the others dots, the 회사 of an interview day green as its name is), the camera's view as a pale wedge,
and the character as a gold arrow pointing the way it faces. 내 방 and the 회사 nearest the character (on an interview
day, that day's 회사 instead), when off the small map, show as small arrows on its edge pointing the way to them. It is
drawn again only when the
character or the camera moves. A tap shows the whole map larger, about four fifths of the screen wide with the places
named by their kind (카페, 도서관, 코워킹, 회사, and 내 방) and an interview day's 회사 by its own name; a tap or Escape brings the
small one back, and
a talk or the guide closes it. It is a picture: a screen reader hears only which place or station the character is
nearest, as in "지도: 내 방 근처", or, more than 40 squares from any of them, which way the nearest is, as in
"지도: 큰 공원역 쪽".

| Do | What happens |
|---|---|
| Tap the ground | The character walks there, around buildings, people, trees and fences, with a marker where it goes |
| Tap twice quickly | It dashes there instead |
| Drag | The camera turns around the character, and tilts within limits that keep the ground in view |
| Pinch, or the mouse wheel | The camera comes nearer or goes farther |
| Tap a person, a building or its name, or walk up to a door or a person | The character goes to that place's person and a talk opens: at once when it is already at the place, on arrival from afar, with "비탈 카페로 가는 중" on the top card on the way (a second quick tap only hurries it there) |
| Tap the bike shop, its name or its keeper | The character goes to 벤, and his talk opens, as at a place |
| Tap a shared bike or e-scooter | The character walks up to it, and a card offers where to ride it, each place with its fare; it rides to the one chosen |
| Tap 자전거 타기 or 퀵보드 타기 on the top card, once bought | The character gets on; 내리기 gets off |
| Tap a SkyTrain station, a bus stop or a water bus dock | The character walks there, and a card offers where to go from it |
| Tap 택시 부르기 on the top card | A card offers every place a taxi goes, each with its fare, before anything is paid |
| Tap the small map | It shows the whole map larger; a tap or Escape brings the small one back |

The city's buildings never get in the way. One that stands between the camera and the character, or fills the view
just in front of the camera, folds down in a quarter of a second to a low outline of its footprint in its own colour,
and rises again once clear; a tap meant for the ground behind a folded one lands there. So downtown the camera keeps the
distance and the turn the player gave it. Only the places, the SkyTrain's deck, its station and the station's
roof, the tunnel's mouth, the café's awning and the office's canopy still pull it in front or lift it over; where one of
them stands right behind the character, it first looks along the nearest street. A building the camera would come
inside folds at once, and the near view widens with the view, so nothing out of sight folds. As the character reaches a place the camera comes round to the street side of
the door, unless the player turned it on the way.
A tap on the water walks to the near shore. A talk ignores taps for a moment after it opens, so the finger that opened
it cannot also answer it. A job is done at its place, not from afar: at a place means within reach of its door or its
person (0.9 of a square, the test a night's sleep has always had). From afar a tap on a place's name only starts the
walk, which the taxi button, a stop, a dock, a shared one or another tap can still cut short or change; the month's
first walk over 60 squares says so once ("멀어요. 택시나 스카이트레인을 타면 빨라요."). A talk that is somehow open away from its
place holds back its answer and 이야기, saying "도착하면 할 수 있어요." until the character has come. While a day changes
into the next, the town takes no taps, drags, pinches or names, and no talk opens, until the morning.

Each place has its own person, all invented: downtown 하나 at the 카페, 레오 at the 도서관, 수진 at 코워킹 and 마커스 at
the 회사 desk; out past it 아이샤, 켄지 at the other offices' desks, 로사 and 준호 at the libraries, 소피 and 마테오 at the
cafés and 니나 at Burnaby's coworking space; and the neighbour grandmother by the bench at 내 방. People of a kind dress
alike (an apron, glasses and a book, a badge, a collar and tie). A talk shows their name, a small face drawn from their
colours, and a
line typed one letter at a time (a tap shows all of it) that fits the day: a first meeting, low energy, money running
short, an interview booked or failed, the night, the first or the last week, and so on. The answers are the place's
action with its effect and its stamina (알바하기, 영어 공부, 코딩 연습, 지원서 넣기, and at 내 방 자기, which is there
any time) or the reason it cannot be done now (too little stamina, an application already in today, and so on),
이야기 for small talk, and 그만. On an interview day only the 회사 applied at offers 면접 보기, first thing, once the
character is there, its name is marked and the top card names it ("오늘은 면접 날이에요. 중심가 회사로 가세요."); at the
other offices the desk sends the player on there.

Money can be spent in the town without spending stamina or time. On the West End's wide street toward downtown, on the way from
내 방 to the business district, stands a bike shop (자전거 가게), kept by 벤, invented like everyone else, with a bike on
show beside it. It is not one of the places, as no day is spent there, but it matters as they do: its name floats
over it, it never folds and the camera keeps out of it. His talk offers 자전거 사기 ($350), 퀵보드 사기 ($550), 이야기 and
그만. Buying takes the price off the balance and nothing else: the date stays, the keeper says how to get on it, and the
button then shows it owned, or why it cannot be bought: 잔고가 모자라요., or 이걸 사면 9월 생활비가 모자라요. when what would
be left could not pay the living cost of every day left in September, so the balance never goes below zero in September
(October's total is another matter). What is bought is the player's for the month. Then one row on the top card
offers 자전거 타기 or 퀵보드 타기, and while riding says what is ridden (a shared one with its fare), with 내리기. A
bike goes about 9 units a second and an e-scooter 11, against 3.6 on foot (a double tap is a little faster still), along
the same streets, never over the water, the rider pedalling on the saddle or standing on the deck, and the camera keeps
up. Shared bikes stand two to a dock near the West End's 내 방, 도서관, 카페 and the station, and in Burnaby near 내 방 in
zones 2 and 3 and the two stations of the town centre, and shared e-scooters on four street corners, all in an invented purple. A tap on one walks up to it, and a card like the taxi's
lists every place a taxi goes with the fare there, nearest first, by the way along the streets from where it stands: a
bike $1 and
$0.40 for every ten squares (250 m), at most $15, an e-scooter $1 and $0.60, at most $20, to the dollar as the taxi's
meter is (across downtown a few dollars, out to Burnaby the most). A place whose fare would leave too little for
September's living cost is greyed out and says why; when even the cheapest one is, the top card says so instead of the
card. The fare shown is the fare paid, through core, as the ride sets off and only then; it is kept in the record (s and
q with the amount). The character rides there along the very way priced and gets off; on the way the town takes no taps, and 내리기 gets off
where it is. Across downtown it goes at its everyday speed; a long ride goes faster, up to 16 squares a second on a bike
and 20 on an e-scooter (a taxi's top is 24), so the West End to the town centre's station is about half a minute; like
every ride, however long, it takes no stamina and leaves the day's clock where it was. At a
place's door, 내 방's or the bike shop's, the character turns to the person there and the talk opens, as at the end of
a walk there. The shared one goes back to its dock or corner when the character gets off, a talk opens or the day ends.
Any talk gets the character off first, and the day's end too, so each morning starts on foot; a fare is only ever paid
for a ride that starts. The money spent before an action heads that action's log, in two lines at most ("그 밖에 5건 -$15" for the
rest), and on a small phone the log gives up its last lines before a talk would leave the screen. Every price is invented, and no shop, brand or bike-share or scooter company is real. On the
button screen there is no shop and no riding.

The SkyTrain, buses, a water bus and taxis take the character across town, still without spending stamina. The SkyTrain
has nine stations, named for what is round them: 다운타운역 under downtown, 터널 입구역 (the station by the tunnel's
mouth, as before), then the Expo Line's real stations from the open data out to the Burnaby town centre under made-up
names (크릭 끝역, 갈림길역, 동쪽 마을역, 작은 공원역, 경계역, 큰 공원역, 중심가역), each up on the line with an entrance
on the ground under its platform, but the first, whose entrance stands on a downtown street. The line goes on past the
town centre into the haze, where the game stops nowhere. A tap on a station's building
walks the character to its door, and a card lists the other stations; the train comes in, the camera follows it along
the guideway, and the character steps out at the station chosen. A long ride goes faster, so downtown to Burnaby takes
about 18 seconds on the move. Under downtown the town gives way to the station's platform and the tunnel, drawn on
their own in eight draws (`Underground.kt`): getting on at 다운타운역, the character waits at the platform's edge under
the station's sign, the train comes in out of the tunnel, opens its doors, takes the character and pulls out, with the
camera behind it; in the tunnel the lamps on its walls go by and the mouth comes up ahead, in the sky's colour by day
or by night; the camera rides out in the tunnel for most of a second (until it comes to the mouth on a long ride) while
the train runs on out into the open, and then cuts to the town as the train comes out of the mouth at 터널 입구역. The other way, the camera follows the train in from the
mouth, and at the platform the doors open and the character steps out and walks off before the street door. The camera
is never still for more than a moment down there, the rumble is heard where it rides, the small map's arrow goes with
the train under the streets, and no name floats over the platform. With less motion the lamps are left out and the
camera does not drift. A bus runs a
loop on its own along the West End's and downtown's streets, with five stops (공원 입구, 쇼핑 거리, 다운타운, 다운타운
남쪽, 웨스트엔드), and another out and back along Burnaby's main streets, from 경계역 앞 by the station at the city line
past 버나비 큰길 (by 내 방 in zone 2) and 큰 공원 옆 to 비탈 마을 (by 내 방 in zone 3); each stop is a shelter and a
sign, and a tap on one offers the other stops of its line, a bus taking the character there the shorter way round. Both are free with the month's pass, which the room's
total already pays. A water bus crosses False Creek between three docks (카페 앞 by the café, 남쪽 기슭 on the far shore,
크릭 끝 near its head) for $4 a ride, the character standing on its open deck. 택시 부르기 on the top card, whenever the
character is on foot and no talk is open, lists every place, the bike shop and the nine stations, nearest first, with
each fare: $4 and $1.50 for every ten squares
along the streets up to 160 squares and $0.70 for every ten past that, to the dollar (downtown to Burnaby is about $45;
the meter stops at $60); a taxi appears where the
character stands and drives there fast, faster still on a long ride (up to 24 squares a second, so Burnaby to downtown
takes about 20 seconds), and its 내리기 lets the character out on the way. A fare is paid through core
when the ride sets off and only then, heads the next action's log ("수상버스를 탔어요. -$4", "택시를 탔어요. -$12"), is kept
in the record (w and t: letters of their own, which needed no new version; a month kept before version 4, v0.17, does
not go on), and is refused with the
reason, as a purchase is, when what would be left could not pay September's living cost. While riding, the town takes no
taps and no talk opens, so no day can end mid-ride; the camera follows the vehicle, the buildings in its way fold and
the small map's arrow moves with it. The small map marks the stations, stops and docks, and the bike shop. Every
station, stop, dock, colour and price is invented; no real station, transit company or taxi company is named. On the
button screen there is no transport.

The town lives on its own. Four more people, invented too (올리버, 미나, 프리야 and 테오), stroll slow loops along the English Bay
seawall, a West End street near 내 방, the big park's edge and the square in front of the station. One stops when the
character stands right in their way. A tap on one opens a short word of small talk with only 그만, which spends no day;
they stop and face the character until it ends, and say something new each time that day. They are not obstacles, so the
character's walks go through them. In the evening they fade out on their way home and they come back at dawn. The people
at the places stay where they are, so they are always found there, but they look about, turn to face the character when
it comes near, and wave as it comes up. The SkyTrain stands at the station, pulls out east along the line, its cars
turning with the guideway, and comes back from the west every 32 seconds, slowing into the station, with its windows lit
at night: at the station nearest 내 방 at first, then at whichever station is nearest where the player looks, moving
there while the train is away down the line. The campfire flickers and lights the bench after dark, light glints on the water, petals
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

An answer that takes an action leaves the character where it is, and the time it took passes over the town in two
seconds: the sky, the sun, the lamps and the windows move little by little from the time before to the time after, from
a soft morning (8:00) through a bright day (14:00) and a warm late afternoon (17:00) to a golden evening (20:00) and the
night (23:00), with the lamps and windows coming on toward evening and the passersby going home; a rainy day greys every
one of those hours. What happened goes over the top of the screen, and the top card shows the time and the stamina left.
With nothing but sleep left (no stamina, or too little for anything still open), a line there says to go home to sleep;
the character walks, rides or takes a taxi home as by day. Sleep starts only with the character at 내 방: a tap on its
name from afar walks home first, and were its talk open before then, 자기 would wait with "내 방에 도착하면 잘 수 있어요."
until it arrives. Sleep ends the day: from the
time it is, on to a
night with the windows, lamps and campfire lit, then the morning, in 1.5 seconds; the character spends the night sitting
on the bench by the campfire, with the camera come round to the far side of the fire, and in the morning it stands at
내 방's door and the camera is the player's own again. Nothing ever takes the character home on its own: a new month
starts at 내 방's door in the morning, and so does each day after a night's sleep there. Every answer goes to the same
rules as the buttons, so the same seed and the same choices end the same way in the town and with buttons; a test plays
a whole month both ways. After an offer in the town, the neighbour has a word on the ending.

The town draws every frame the screen offers while the character walks, the camera moves or the day changes, but its
own life moves at most 30 times a second, and so does the light while an action's time passes. While only that life
moves, it draws at most 30 frames a second, and 15 once
nobody has touched the town for 20 seconds or while a talk covers it; the sun's shadows are drawn again on every other
of those frames. It draws nothing while the page is hidden. It draws at most two pixels per point however sharp the
phone, and boxes of a kind together: the buildings, trees and the rest that stand still and cast shadows in one draw for
each 40-unit tile of the town, so a tile out of the camera's sight or the sun's shadow box is not drawn at all; the
ground, the sea and the glowing windows in one draw each (each place out past downtown its windows and lamps in one of
its
own, drawn only near it); downtown's townspeople in one, East Vancouver's and Burnaby's in one each, drawn only near
them, all four passersby in another,
the train's cars in two, and all the rain in one, moved on the same beat as the rest of the town's life. If the phone
starts asking for less motion during the month, the town's own life stands still, the rain with it.

Where the phone asks for less motion, the month is played with buttons, under the small fixed town of the first version,
where the character simply appears at each day's building. Where the browser has no WebGL 2, which Three.js needs, there
is no town and the game plays with buttons, as before. If Three.js fails, the buttons take over and the error goes to the
console. Tests force each of these.

The town's rules are plain Kotlin with tests: `Neighbourhood` (the layout on the wide map, the people, which place a
spot belongs to), `HomeSite` (the town for each zone's room: where 내 방 stands, and the ground to walk on with it), `Suburbs` (each
residential block split into lots, a house on each, by how far it is from downtown), `HouseDetail` (which houses stand
one by one near the camera, and their roofs, doors, lawns and trees),
`VancouverMap` and `WideMap` (the maps' squares and lines, read from the generated `VancouverMapData` and
`WideMapData`), `RailLine`
(the guideway the train follows), `Grid` (the A* search for the way around things), `Trip` (a walk or a dash, and the
double tap), `Rides` (the bikes and e-scooters, their speeds, and where the shared ones stand), `Transit` (the stations,
the two bus lines and their stops, the water bus's docks and way over the water, the places a taxi goes, and each ride's glide
along its way), `UndergroundRide` (a ride under downtown as it is shown: the train coming in, the doors, the character
on the platform and the camera), `FollowCamera`
(turning, zooming, keeping out of the places' walls, and what a tap lands on), `Cutaway`
(which of the city's buildings fold down out of the camera's way, and how far), `Minimap` (where the grid lies on the
small map turned north up, and which place is nearest), `DayLight` (the light
through the day's hours and through a night's sleep, when the passersby are out, and how each weather greys it), `Passersby` (their loops, and
stopping for the character), `Greeter` (the townspeople turning and waving), `TownLife` (the train's round and how loud
it
rumbles, the fire's flicker and crackle, the glints, petals, clouds and rain, all worked out from the time) and
`FramePace` (which frames are drawn). The people's lines and the rule that picks them are in `text` (`Townsfolk.kt`, and
`Passersby.kt` for the passersby's), and so are each day's weather (`Weather.kt`) and the bike shop's and riding's
words (`Shop.kt`), and the SkyTrain's, the bus's, the water bus's and the taxi's (`Transit.kt`). `WalkableTown` draws
all of it, and
`Three.kt` declares only the parts of Three.js in use. The page's script, Three.js and the maps included, is about 944 KB
(about 284 KB gzipped), under the 1 MiB aim: the maps' rows, long lines and the wide map's blocks are packed small (the map tool's README
says how), and Kotlin writes the page as ES2015, with JavaScript classes. `TownBudgetTest` measures what a frame costs downtown (by day and at night),
in East Vancouver, in Burnaby (by day and at night), on a walk, on the train, under downtown and with the camera turning
(so the near houses are written again each frame), the sun's shadows counted too, and keeps it within a phone's budget.

### Sound and the guide

The page has a calm background tune of its own, made entirely in code with the Web Audio API: no audio files, no
samples, nothing downloaded. It is written by rules, not copied from any game or song: a music-box melody in F major
pentatonic over soft chords and a low note, at 70 beats a minute, in a loop of 16 bars shaped like a short song
(A A' B A''). The theme comes from a fixed seed, and each pass through the loop changes a few notes a little, so it
never repeats exactly. The clock colours it, the month's own while a month is played and the phone's on the screens
around it: the whole tune by day (from 6:00), a softer, sparser and darker one in the evening (from 18:00), and at
night (from 22:00) only a quiet bed of soft noise that rises and falls like the sea, under a few slow notes. A talk
opens with a soft blip, a line types out with very quiet ticks (at most
one every 110 ms), and the change of day rings a gentle chime.

The town has sounds of its own, made from noise in code in the same way. On a rainy day a soft bed of rain plays under
the tune, well below its loudest notes. The SkyTrain rumbles softly as it pulls out and comes in, louder the nearer it is
to the camera and silent while it stands at the station. The campfire crackles quietly when the character is near it
after dark, in the town's own evening and night. They follow the same switch, fade and limiter as
the tune.

Sound is off until the player turns it on with the switch in the town's top card, at the top of the button screen, and
by the title on the intro, the room screen and the ending. It is a plain button whose words say what a tap does, 소리 켜기 or 소리 끄기;
it has no `aria-pressed`, since ARIA's guidance for toggle buttons keeps a toggle's name steady. The browser's
audio is made only on that tap, as browsers require. The choice is kept in `localStorage`; when it was left on, the
sound comes back on the first tap anywhere. It pauses while the page is hidden, fades in and out, and plays quietly
through a limiter. Where the browser has no Web Audio there is no switch, and where storage is refused nothing is
remembered.

How to play is said on the intro (see below) and again on a card the "?" in the town's top card opens: tap the ground
to walk and twice to dash, drag to look around and pinch to zoom, tap a person or a building to talk (an action takes
stamina and time, and is done only on arrival), sleep at 내 방 once the stamina is spent, tap a station, stop or dock
to ride, buy or rent a bike or call a taxi, find yourself on the small map, and that the cafés, libraries, coworking
spaces and offices stand in several neighbourhoods, the same work at any of a kind. The
card ends with the month's goal, in the words the month's start uses; 닫기 or Escape closes it. It never opens by
itself, since the intro has just said the same. The button screen has no card, since its buttons say what they do.

### The intro

The page opens on an intro: the title with the version, 업데이트 소식 and the sound switch; the arrival story (September
1st, $3,000, the goal, a room first); how to play, in the same eight lines as the "?" card; how the guess pays, in one
line ("맞힐수록 최대 $100을 더 받고, $100보다 더 틀리면 그만큼 잃어요(최대 $300).") with three worked examples; and the
map's credit.
시작하기 sits in a bar held at the foot of the screen, so it is in sight on a 375 x 812 or 320 x 568 phone and on one
turned on its side, however long the rest; it leads to the rooms. With a month kept for the run, the intro opens with it
and offers 이어하기 and 새로 시작 instead (see Continuing a month). 다시 하기 on the ending goes straight to a new run's
rooms: it opens `?seed=...&start`, and the page writes the address back without `&start`, so a reload or a shared link
opens on the intro.

`Music.kt` writes the tune as notes and is tested without sound; `Synth.kt` plays it, and the tests render it into an
`OfflineAudioContext` to check it is heard, never clips, is quietest at night, and keeps the rain under it; `Sound.kt`
is the switch, `FirstRunGuide.kt` the card and `Memory.kt` the storage that may be refused. The words are in `text`
(`Guide.kt`).

### Choosing the room on the map

The rooms are chosen on the whole wide map, north up, as the small map shows it larger: its ground, the stations, stops
and docks, the places as dots with every 회사 and 카페 named by its kind, and a pin for each room at the door 내 방 has in
that room's zone (the West End for zone 1, Burnaby for zones 2 and 3), so the player sees how far each room is from the
offices (where the applications and interviews are) and the cafés (where the shifts are). A pin carries
the room's number; a tap on it picks the room and shows its card under the map (rent, zone, utilities), the guess and
the real total following as before, and the picked pin stays filled in. The pins are buttons, 44 points across, reached
with Tab in the rooms' order, each heard by a screen reader as its room in full ("1번 방, 월세 $1,070, 교통 1구역,
웨스트엔드, 공과금 포함", the area standing in for the map); the offices' and cafés' names are placed where they cover no
pin and no other name.
Pins closer than a finger on a narrow map are pushed apart, with a line back to their doors. The map is a 2D canvas, so it is the same on the button screen; where a browser
cannot draw one, the rooms are the list of buttons they were before. Nothing of the town is made until the month starts.
`RoomMap` draws it, with the small map's painting (`Minimap`). The map's ground, and the city's layout it is painted
from, are made while the intro is read, when the browser is idle, so the rooms open at once after 시작하기; tapped
before that is done, the rooms screen still shows first and the picture follows just after. A tap made before the rooms
were on the screen (a second tap on 시작하기, queued while the page was busy) picks no pin. The map is measured only once
the whole screen is built, so a pick never pulls a scrolled page back up, and it is painted again when the page
changes size.

### Patch notes (업데이트 소식)

Players read what changed the way a game's patch notes read: each version with its date (in Vancouver), a one-line
headline, and short lines under 새로운 기능, 개선, 수정 and 알려진 문제, newest first. The first screen shows the version
small after the title and an 업데이트 소식 button next to the sound switch; the button screen has the same button at its
foot, and in the town it is a link at the foot of the "?" card, next to the map's credit, left out while a talk is open
or the day changes. The notes open as a card: the newest version open, the older ones folded, the list scrolling inside
it, closed with 닫기 or Escape. A small dot sits on the button while the newest version has not been opened yet, kept in
`localStorage`, so a first visit has it too; where storage is refused, it goes once the notes are opened on that page.

The notes are data in `text` (`PatchNotes.kt`), and [PATCH_NOTES.md](PATCH_NOTES.md) is written from the same data;
a test (`PatchNotesFileTest`) fails when the two differ. **Every change players can see adds one entry:**

1. Add a `PatchNote` at the top of `PatchNotes.ALL` with the next version, the day it merges in Vancouver as
   `2026-11-01`, a headline, and at least one line under `added`, `improved`, `fixed` or `known`. Write to players in
   short, friendly Korean: no pull request numbers, file names or tools, and no em dash.
2. Bump `CURRENT` to that version. Players who opened the notes on an older version see the dot again.
3. Write the file again: `UPDATE_PATCH_NOTES=1 ./gradlew :text:jvmTest --tests '*PatchNotesFileTest*' --rerun`.

`PatchNotesTest` checks the order (newest first, each version newer than the one below it), the dates, that `CURRENT`
is the newest, and the lines' widths and words.

## How it is built

| Part | What | Notes |
|---|---|---|
| `core` | The rules: rooms, the guess's money, the month, interviews, endings, money spent in the town, and the run number for a date | Kotlin Multiplatform, standard library only, so the same rules run on the JVM and in a browser. All luck goes through one `Dice` interface, so tests can control it |
| `save` | A month in progress as a short record, and the month rebuilt from it by playing each action, interview, sleep and spend again | Kotlin Multiplatform, core only, so the JVM tests it by replaying hundreds of random months beside the live ones |
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

The month is balanced by bots that play 2,000 runs with the real rules (`BalanceTest`). Each picks the cheapest room
and guesses its total in a fixed way, and is asked again and again through each day; what it asks for that cannot be
taken is sleep instead. A bot that reads the price board for its guess (rent, pass, and the middle of the utilities'
range when they are separate: +$94 on average), then works a shift a day when the rent is at risk, applies once a day
and studies the weaker skill, sleeping when none of that fits, lands an offer in about 46% of runs (47% when a day held
one action, 43% before the guess moved money); the same bot with a guess $500 off lands one in about 38%. The same bot
never working fails to pay October in about 47% of runs, so September's money stays tight. A bot that takes the rent
for the total and picks at random lands one in about 12%, and a bot that only sleeps never does. (Measured on seeds 1
to 2,000 with Kotlin 2.4.21; the test checks the thoughtful rate within 3 points of one action a day's, and ranges for
the rest.)

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
The page shows the licence's statement at the foot of the intro and of the card on how to play. Its sources are
the City of Vancouver's open datasets Shoreline 2002, Parks - polygon representation, Rapid transit lines, City
boundary, Block outlines and Local area boundary, fetched on 2026-10-31 from
[opendata.vancouver.ca](https://opendata.vancouver.ca).

Contains information licensed under the
[Open Government Licence – Vancouver](https://opendata.vancouver.ca/pages/licence/).

The map data is licensed under that licence (version 1.0), not MIT, and keeps its attribution: on the page, the
intro and the card on how to play show the statement with a link to the licence, and `MAP-DATA-NOTICE.txt` ships beside the
script. The code that makes and reads the data is MIT. How to fetch the data again and rebuild the map is in
[tools/vancouver_map/README.md](tools/vancouver_map/README.md).

The same tool also makes the wide map, `WideMapData.kt`: the town's map carried on east through East Vancouver into
south-west Burnaby, with the Expo Line and its stations, the main streets, the city blocks and the zones a room can
be in. The town's map is its top-left corner, square for square. The town does not use it yet. Besides more of the
City of Vancouver's open data (Rapid transit stations, Public streets), it uses the City of Burnaby's open datasets
Burnaby Boundary, District Lot, Legal Block, Park Inventory, SkyTrain, SkyTrain Station, Street and Community Plan
Area Boundaries, fetched on 2026-10-31 (Vancouver time) from [data.burnaby.ca](https://data.burnaby.ca), credit
City of Burnaby, under the
[Open Government Licence – Burnaby](https://data.burnaby.ca/pages/open-government-licence) (based on version 2.0 of
the Open Government Licence – British Columbia). That licence's own attribution statement reads:

Contains information licensed under the Open Government Licence – British Columbia.

That part of the map data is licensed under the Open Government Licence – Burnaby, not MIT, with that statement,
the credit and a link to the licence. No OpenStreetMap data is used.

## Licence

MIT, except the map data: see [LICENSE](LICENSE), [NOTICE](NOTICE), and Map data above for the map. The page
carries both notices beside its script, `three-LICENSE.txt` and `MAP-DATA-NOTICE.txt`.

The phone screen bundles [Three.js](https://github.com/mrdoob/three.js) 0.169.0, which is also under the MIT
licence (Copyright © 2010-2024 three.js authors). The build copies its full licence text next to the script, as
`three-LICENSE.txt`.
