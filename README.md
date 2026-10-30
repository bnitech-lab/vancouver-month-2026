# Vancouver Month (working title)

A small turn-based game in Kotlin about getting by in Vancouver. Its one rule: compare rooms by their total
monthly cost (rent, utilities and a transit pass), not by rent alone.

This first version is only the first minute of a run. You see three rooms and a price board, pick the room you
think is cheapest in total, guess what it really costs a month, and the game shows the real total and a score:
100 points for the cheapest room, and up to 100 for the guess, one point off per dollar missed.

## Play

Needs Java 21.

```sh
./play                # new rooms
./play --seed 1011    # the same rooms again
```

The game text is in Korean for now. This is `./play --seed 1011`, picking the room with the lowest rent:

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
  이번 판 점수 55 / 200

  첫 달을 내고 남은 잔고 $1,655

한 판 더? (y/n) > n
```

A test plays this seed and checks that the screen still matches this sample.

## How it is built

| Part | What | Notes |
|---|---|---|
| `core` | The rules: rooms, zones, the total, the guess, the score | Kotlin standard library only, so the same rules can later run in a browser or on a server |
| `terminal` | The text screen | Kotlin on the JVM |

```sh
./gradlew test        # rules and screen tests
```

Rooms come from a seed. Farther zones have lower rent and a dearer pass, and utilities may or may not be
included, so the room with the lowest rent has the lowest total in only a bit more than half of the deals.

## Who wrote what

The code is written by Claude (an AI coding assistant, through Claude Code). Dawn chose the game and its rules,
plays every version and decides what changes. Some issues are left for Dawn to fix by hand, as Kotlin practice.

## Numbers

Every price here is invented. None of them is a real Vancouver rent, utility bill or transit fare.

## What comes next

Dawn plays this first minute five times. Only if a sixth round is wanted does the game grow: one action a day
for a month, then a phone screen.

## Licence

MIT. See [LICENSE](LICENSE).
