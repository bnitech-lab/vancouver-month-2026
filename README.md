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

The first build downloads Node.js and the npm packages the Kotlin/JS build uses.

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

## How it is built

| Part | What | Notes |
|---|---|---|
| `core` | The rules: rooms, the score, the month, interviews, endings, and the run number for a date | Kotlin Multiplatform, standard library only, so the same rules run on the JVM and in a browser. All luck goes through one `Dice` interface, so tests can control it |
| `text` | The Korean lines both screens share (amounts, dates, actions, events, endings, today's result line) and how a typed amount is read | Kotlin Multiplatform, no layout |
| `terminal` | The text screen | Kotlin on the JVM |
| `web` | The phone screen in a browser | Kotlin/JS drawing the page with the DOM, plain CSS, no framework |

```sh
./gradlew test                                  # rules, balance and screen tests, on the JVM and in headless Chrome
./gradlew :core:jvmTest --tests '*MonthTest*'   # one test class, on the JVM only
```

The browser tests run in Chrome, so `./gradlew test` needs Chrome installed. `./gradlew test` does not take
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

The town drawn as a small 3D map.

## Licence

MIT. See [LICENSE](LICENSE).
