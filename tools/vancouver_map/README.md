# The map of real Vancouver

`build_map.py` turns City of Vancouver open data into
`web/src/jsMain/kotlin/vancouvermonth/web/VancouverMapData.kt`: the peninsula's outline as a grid of squares (water,
land, park, shore), the simplified shoreline, the big park's outline, the rail on its guideway along False Creek, the
line the mountains stand behind, the city blocks and small parks, and a proposed square for each of the five places.
`VancouverMap.kt` reads it, and `Neighbourhood.kt` builds the town on it.

It also writes `web/src/jsMain/kotlin/vancouvermonth/web/WideMapData.kt`, the wide map: the town's map carried on east
through East Vancouver into south-west Burnaby, past the Burnaby town centre and its big park. `WideMap.kt` reads it.
Nothing draws it yet; the town will move onto it next.

## Rebuild

```sh
tools/vancouver_map/fetch.sh /tmp/vancouver-raw                       # a new, empty directory outside the repo
python3 -I -B tools/vancouver_map/build_map.py /tmp/vancouver-raw     # writes WideMapData.kt
python3 -I -B tools/vancouver_map/build_map.py /tmp/vancouver-raw --preview /tmp/vancouver-preview --png
python3 -I -B tools/vancouver_map/build_map.py /tmp/vancouver-raw --town   # also rewrites VancouverMapData.kt
```

Each run paints the town's map again and stops if its squares come out different from `VancouverMapData.kt` by a
single square, and stops if the wide map's top-left corner differs from it. `--town` also rewrites
`VancouverMapData.kt` from the same files; on the data fetched for 24a only its header changes (the fetch time and
the block file's SHA-1), as the town's blocks come out the same and in the same order.

Python 3 standard library only, and no network once the files are fetched. The downloaded files are data, never code:
the script only parses them as JSON, and `-I` keeps Python from loading anything from the current directory. `--png`
turns the SVG pictures into PNG files with a local Chrome, if there is one.

The script prints the grid's size and scale, the walking time between each pair of places, the squares it cleaned
up, how many flat boxes would cover the ground, the stations, and the walks between the rooms of each zone and
downtown. The datasets are updated from time to time, so a later fetch can
give a slightly different map; the SHA-1 of each file used is written at the top of `VancouverMapData.kt`.

## How the map is made

1. Longitude and latitude go onto a flat plane in metres around downtown (an equirectangular projection, well under
   1% off at this size).
2. The plane is turned so the downtown street grid runs along the game's x and z. The angle comes from the block
   outlines of Downtown and the West End: 44.7 degrees, so real north points up and to the left.
3. The area is the big park at the peninsula's tip, downtown, False Creek and a strip of its south shore, with water
   above the park and the head of False Creek on the right.
4. The scale is the finest, in half-metre steps, at which the farthest walk between two of the five places takes no
   more than 25 seconds at the game's walking speed (3.6 squares a second), measured with the same A* and
   straightening as `Grid.route`. With the five proposed spots it is 25 m a square, and the grid is 246 by 155.
5. Each square takes what lies under its centre: water, land, or the park. Ponds under 4 squares become land, specks
   of land the character could never reach become water, and every land square beside the water becomes shore.
6. Lines are simplified with Douglas-Peucker to within 0.35 of a square.
7. Each real city block becomes a rectangle of plain land squares, drawn 0.4 of a square in from its outline on every
   side. A block turned off the grid (the older streets to the east) becomes a square of the same area; one wider or
   deeper than 9 squares is cut into pieces with lanes between; where two overlap, the larger keeps the ground; where
   two still meet side to side, the smaller gives up the row or column along that side. So a street at least one
   square wide runs between any two blocks side by side; two may still meet at a corner. A block whose middle lies in
   one of the City's parks is a small park, not buildings.

The five spots are rough points in a neighbourhood, chosen by its character, never an address or a business: 내 방 in
the West End's residential streets, 카페 by the seawall on False Creek's north shore, 도서관 in the civic blocks in the
middle of downtown, 코워킹 in the old brick warehouse streets at the east end, 회사 among the office towers near the
inlet. The rail is the Expo Line from where it leaves its tunnel near the stadiums, east along False Creek. The
mountains line is the city's boundary where it runs along the middle of Burrard Inlet; the north shore is past it.

## The wide map

1. The same plane, turn (44.7 degrees) and scale (25 m a square) as the town's map, with the same left and top edges
   and the same 155 rows, so every square of the town keeps its column and row. Real north points up and to the left,
   so the grid's right is real south-east, the way the Expo Line runs from downtown to Burnaby; the grid grows only
   to the right (680 columns), because rows above the town would move every square of it. Its right edge is 1.2 km past
   the last station inside it.
2. Land is the City of Vancouver's shoreline, as in the town, and in Burnaby the squares inside the City of Burnaby's
   boundary that lie in a district lot (the survey lots stop at the river bank, while the boundary runs down the
   middle of the Fraser) or beside a legal block or a park. Past the Fraser (Richmond) is water: neither city's data
   covers it.
3. Then the town's rules, square for square: ponds under 4 squares become land, islets become water, land beside
   water is shore. Parks of 2 ha or more right of the town's corner are park squares (City of Vancouver parks and
   Burnaby's park inventory, the big Burnaby park among them); smaller ones stay plain land.
4. The rows are written as runs ("~3.2s" for "~~~..s"): 105,400 squares in under 7,000 characters.
5. The rail is the Expo Line from the tunnel's mouth by the stadiums, the City of Vancouver's line on to the second
   Burnaby station and Burnaby's pieces past it, to where it leaves the grid. The stations are the real ones within
   2.5 squares of it, the two platforms of one interchange taken as one, each put in as a point of the line. The game
   gives them made-up names.
6. The main streets are Vancouver's arterials and secondary arterials and Burnaby's arterials and major collectors,
   joined into whole streets (through a crossing into the piece that turns by less than 30 degrees) and simplified to
   within half a square. No street names or block addresses are fetched.
7. The blocks right of the town's corner (Vancouver's block outlines, Burnaby's legal blocks) are each the smallest
   turned rectangle round the real block, with a turn in the sense of `Block.turn`; blocks on parks or water, under 2
   squares or over 25 ha, are left out.
8. The zones a room can be in: 1 is the City of Vancouver's local areas Downtown and West End (by name, the only key
   that layer has), 2 the City of Burnaby community plan area round the big station, 3 the outer, pricier one: a
   strip of the plan area on the slope south of the big park, within 225 m of the city line, down toward the river;
   each cut to the grid. The two Burnaby areas are picked by their `OBJECTID` in the plan-area layer, and the build
   stops unless each is the size recorded (within 3%), zone 2's holds two stations and zone 3's reaches the city
   line.
9. Each zone has a room. Zone 1's is the town's 내 방. In zones 2 and 3 a written rule picks it, never a hand: of the
   squares in the zone with a 5 x 5 block of plain land round them, at least 5 squares inside the grid's edges, 2
   off every main street and 4 off the guideway, whose middle lies on a road (outside every Burnaby legal block and
   Vancouver block outline, so never on a lot), the one farthest from the last station inside the grid in zone 2,
   and the one farthest from 회사 in zone 3. The build stops unless zone 3's room is farther from 회사 than zone 2's.
   The rooms are stored as grid squares, not as points, and the build checks again that neither lies on a lot.
10. A guard: the build stops if water lies along the city line inland, where the two cities' outlines meet.

## How the data is written

The two files ship inside the page's script, so the long parts are written small. Both maps keep each row as runs
("~3.2s" for "~~~..s"; the town's 38,130 squares take 3,470 characters). The town's shoreline and big park, the wide
map's main streets and its blocks are packed: each number in tenths of a square (a block's turn in whole degrees),
written as the change from the same number of the point or block before, as a base-64 VLQ, the way source maps write
numbers. `unpackNumbers` and `unpackPoints` in `VancouverMap.kt` read them back to exactly the numbers the plain form
gave (the 1,703 blocks take 13,991 characters instead of 37,780). The short lines (the rails, the stations, the
mountains line, the zones) and the town's blocks stay as plain "x,z" numbers.

## Data and licence

Every input is from the [City of Vancouver Open Data Portal](https://opendata.vancouver.ca), under the
[Open Government Licence – Vancouver](https://opendata.vancouver.ca/pages/licence/) (version 1.0), which allows
copying, changing and publishing the data, also commercially, with this attribution:

> Contains information licensed under the Open Government Licence – Vancouver.

| Dataset | Used for |
|---|---|
| [Shoreline 2002](https://opendata.vancouver.ca/explore/dataset/shoreline-2002/) | Land and water |
| [Parks - polygon representation](https://opendata.vancouver.ca/explore/dataset/parks-polygon-representation/) | The big park's outline, and which blocks are small parks |
| [Rapid transit lines](https://opendata.vancouver.ca/explore/dataset/rapid-transit-lines/) | The rail along False Creek |
| [City boundary](https://opendata.vancouver.ca/explore/dataset/city-boundary/) | The line in Burrard Inlet the mountains stand behind |
| [Block outlines](https://opendata.vancouver.ca/explore/dataset/block-outlines/) | The street grid's angle, the city blocks, and the comparison picture |
| [Local area boundary](https://opendata.vancouver.ca/explore/dataset/local-area-boundary/) | Which blocks are downtown, and the area's size; zone 1 |
| [Rapid transit stations](https://opendata.vancouver.ca/explore/dataset/rapid-transit-stations/) | The wide map's stations in Vancouver |
| [Public streets](https://opendata.vancouver.ca/explore/dataset/public-streets/) | The wide map's main streets in Vancouver (arterial and secondary arterial, their use only) |

Fetched on 2026-10-31. The map data in the game is derived from these datasets and licensed under the Open
Government Licence – Vancouver 1.0, not MIT, with the same attribution and a link to the licence on the page;
`web/src/jsMain/resources/MAP-DATA-NOTICE.txt` ships beside the script. The code that makes and reads it is MIT like
the rest of the repository.

The wide map also uses [City of Burnaby Open Data](https://data.burnaby.ca), read from the City's ArcGIS server
(`https://gis.burnaby.ca/arcgis/rest/services/OpenData`), credit City of Burnaby, under the
[Open Government Licence – Burnaby](https://data.burnaby.ca/pages/open-government-licence). That licence is version
2.0 of the Open Government Licence – British Columbia with the City of Burnaby in the Province's place. It allows
copying, changing and publishing the data, also commercially, with the attribution the provider names, a link to the
licence where possible, and no suggestion that the City endorses the game. Its own statement, used word for word:

> Contains information licensed under the Open Government Licence – British Columbia.

| Dataset (server layer) | Used for |
|---|---|
| [Burnaby Boundary](https://data.burnaby.ca/datasets/d903b87782734451ae286cb0b59938ac) (OpenData3/MapServer/12) | Where Burnaby's land can be |
| [District Lot](https://data.burnaby.ca/datasets/81781be23e764ce1b7d6efec5b32e86d) (OpenData4/MapServer/6) | Burnaby's land up to the Fraser's bank |
| [Legal Block](https://data.burnaby.ca/datasets/bb9ca652d6da4733ae32ab56e5127d1c) (OpenData4/MapServer/9) | Burnaby's land and city blocks |
| [Park Inventory](https://data.burnaby.ca/datasets/a1a896a4209d4325bacacea417ffc400) (OpenData1/MapServer/6) | Burnaby's parks, the big park among them (name only, to join a park's pieces) |
| [SkyTrain](https://data.burnaby.ca/datasets/b39d6277a1ec4edb8bba956664d30dc1) (OpenData5/MapServer/11) | The Expo Line past the second Burnaby station |
| [SkyTrain Station](https://data.burnaby.ca/datasets/cff871aeb8494cb58dd65a2d06af9454) (OpenData5/MapServer/10) | The stations in Burnaby |
| [Street](https://data.burnaby.ca/datasets/18cb9099908640c68910b76033309d75) (OpenData5/MapServer/8) | Burnaby's main streets (arterials and major collectors, their class only) |
| [Community Plan Area Boundaries](https://data.burnaby.ca/datasets/c1efbda18f5a448583cc1deb939af351) (OpenData1/MapServer/10) | Zones 2 and 3, by `OBJECTID` only |

Fetched on 2026-11-01 at 01:15 UTC (2026-10-31 in Vancouver), the whole south-west of Burnaby from Boundary Road to
about 2 km past the big station and from Kingsway to the Fraser for the large layers. The part of the wide map derived
from these datasets is licensed under the Open Government Licence – Burnaby, not MIT, with the statement, the credit
and a link to the licence; the SHA-1 of each file is at the top of `WideMapData.kt`. No OpenStreetMap data is used.
