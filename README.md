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
   interview (passed on coding). Passing both is a job offer and ends the game.
4. **The end.** No offer by the 30th means October's total for the same room is due: you either can pay it or you
   cannot. Running out of money earlier ends the game at once.

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

## How it is built

| Part | What | Notes |
|---|---|---|
| `core` | The rules: rooms, the score, the month, interviews, endings | Kotlin standard library only, so the same rules can later run in a browser or on a server. All luck goes through one `Dice` interface, so tests can control it |
| `terminal` | The text screen | Kotlin on the JVM |

```sh
./gradlew test        # rules, balance and screen tests
```

Rooms come from a seed. Farther zones have lower rent and a dearer pass, and utilities may or may not be
included, so the room with the lowest rent has the lowest total in only a bit more than half of the deals.

The month is balanced by bots that play 2,000 runs with the real rules (`BalanceTest`). A bot that rests when
tired, works when the rent is at risk, studies and applies lands an offer in about 43% of runs; a bot that picks
at random lands one in about 20%; a bot that only rests never does.

## Who wrote what

The code is written by Claude (an AI coding assistant, through Claude Code). Dawn chose the game and its rules,
plays every version and decides what changes. Some issues are left for Dawn to fix by hand, as Kotlin practice.

## Numbers

Every price, wage and chance here is invented. None of them is a real Vancouver rent, utility bill, transit fare
or wage.

## What comes next

A phone screen in the browser, then the town drawn as a small 3D map.

## Licence

MIT. See [LICENSE](LICENSE).
