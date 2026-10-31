# The map of real Vancouver

`build_map.py` turns City of Vancouver open data into
`web/src/jsMain/kotlin/vancouvermonth/web/VancouverMapData.kt`: the peninsula's outline as a grid of squares (water,
land, park, shore), the simplified shoreline, the big park's outline, the rail on its guideway along False Creek, the
line the mountains stand behind, the city blocks and small parks, and a proposed square for each of the five places.
`VancouverMap.kt` reads it, and `Neighbourhood.kt` builds the town on it.

## Rebuild

```sh
tools/vancouver_map/fetch.sh /tmp/vancouver-raw                       # a new, empty directory outside the repo
python3 -I tools/vancouver_map/build_map.py /tmp/vancouver-raw        # writes VancouverMapData.kt
python3 -I tools/vancouver_map/build_map.py /tmp/vancouver-raw --preview /tmp/vancouver-preview --png
```

Python 3 standard library only, and no network once the files are fetched. The downloaded files are data, never code:
the script only parses them as JSON, and `-I` keeps Python from loading anything from the current directory. `--png`
turns the SVG pictures into PNG files with a local Chrome, if there is one.

The script prints the grid's size and scale, the walking time between each pair of places, the squares it cleaned
up, and how many flat boxes would cover the ground. The datasets are updated from time to time, so a later fetch can
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
| [Local area boundary](https://opendata.vancouver.ca/explore/dataset/local-area-boundary/) | Which blocks are downtown, and the area's size |

Fetched on 2026-10-31. The map data in the game is derived from these datasets and licensed under the Open
Government Licence – Vancouver 1.0, not MIT, with the same attribution and a link to the licence on the page;
`web/src/jsMain/resources/MAP-DATA-NOTICE.txt` ships beside the script. The code that makes and reads it is MIT like
the rest of the repository.
