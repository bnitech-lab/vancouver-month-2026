#!/usr/bin/env python3
"""Turns City of Vancouver open data into the game's map of real Vancouver.

Reads the GeoJSON files that fetch.sh downloads, and writes
web/src/jsMain/kotlin/vancouvermonth/web/VancouverMapData.kt: a grid of squares (water, land, park, shore), the
simplified shoreline, the big park's outline, the elevated rail line, the line the mountains stand behind, and the
proposed squares for the five places. With --preview it also draws the result as SVG pictures, and with --png it turns
them into PNG files with a local Chrome.

Standard library only, and no network: give it the directory fetch.sh filled. The downloaded files are data, never
code; run this with `python3 -I`.

    python3 -I tools/vancouver_map/build_map.py RAW_DIR [--preview OUT_DIR] [--png]

The steps, in order:
  1. Project longitude and latitude onto a flat plane in metres around downtown (good to well under 1% here).
  2. Turn the plane so the downtown street grid, measured from the block outlines, runs along the game's x and z.
  3. Cut out the area: the park at the peninsula's tip, downtown, False Creek and a strip of its south shore.
  4. Shrink it to game units, so the farthest walk between two of the five places takes about 25 seconds.
  5. Paint each square by what lies under its centre, drop specks of land the character could never reach, and
     mark the land squares that touch water as shore.
"""

import argparse
import hashlib
import heapq
import json
import math
import os
import shutil
import subprocess
import sys

# The plane is centred near the middle of downtown.
LAT0 = 49.285
LON0 = -123.125
METRES_PER_DEGREE_LAT = 110574.0
METRES_PER_DEGREE_LON = 111320.0 * math.cos(math.radians(LAT0))

# The game's walking speed (Trip.WALK_SPEED) and the longest walk wanted between two of the five places.
WALK_SPEED = 3.6
LONGEST_WALK_SECONDS = 25.0

# How far past the park and downtown the map reaches, in metres: water above the park, the head of False Creek on
# the right, and a strip of the south shore below the creek.
MARGIN_LEFT = 150.0
MARGIN_TOP = 250.0
MARGIN_RIGHT = 200.0
MARGIN_BOTTOM = 500.0

# Water enclosed by land and smaller than this many squares becomes land (a pond the grid cannot show).
SMALLEST_LAKE = 4
# How far a simplified line may stray from the real one, in game units.
SIMPLIFY = 0.35

# A city block is drawn this much smaller on every side than its real outline, and where two still meet side to side
# the smaller gives up a row or column, so a street of at least one square runs between any two side by side; a
# block wider or deeper than SPLIT_OVER squares is cut into pieces with lanes.
BLOCK_INSET = 0.4
SPLIT_OVER = 9

# The proposed squares for the five places, chosen by the character of each neighbourhood. Each is a rough point in
# a neighbourhood, never an address: one square here is about 25 m across, so a square stands for a few lots.
SPOTS = [
    # 내 방: the West End's residential streets, between the park and the downtown core.
    ("HOME", -123.1365, 49.2875),
    # 카페: the seawall on the north shore of False Creek, where the towers meet the water.
    ("CAFE", -123.1255, 49.2745),
    # 도서관: the civic blocks in the middle of downtown, where the public buildings stand.
    ("LIBRARY", -123.1170, 49.2810),
    # 코워킹: the old brick warehouse streets at the east end of downtown.
    ("COWORKING", -123.1070, 49.2835),
    # 회사: the office towers of the business district, near the inlet.
    ("OFFICE", -123.1180, 49.2875),
]
PLACE_LABELS = {"HOME": "내 방", "CAFE": "카페", "LIBRARY": "도서관", "COWORKING": "코워킹", "OFFICE": "회사"}
PLACE_NOTES = {
    "HOME": "웨스트엔드 주택가, 큰 공원과 다운타운 사이",
    "CAFE": "폴스크릭 북쪽 물가, 방파제 길 가까이",
    "LIBRARY": "다운타운 한가운데, 공공건물이 모인 블록",
    "COWORKING": "동쪽 끝, 오래된 벽돌 창고 거리",
    "OFFICE": "바다 가까운 업무 지구 빌딩 숲",
}

# Where the Expo Line comes up out of its tunnel, near the station by the stadiums: the rail from here east runs
# on its guideway along False Creek. The line west of it, under downtown, is left out.
RAIL_PORTAL = (-123.1096, 49.2795)

WATER, LAND, PARK, SHORE = "~", ".", "p", "s"

DATASETS = [
    ("shoreline-2002.geojson", "Shoreline 2002", "shoreline-2002"),
    ("parks-polygon-representation.geojson", "Parks - polygon representation", "parks-polygon-representation"),
    ("rapid-transit-lines.geojson", "Rapid transit lines", "rapid-transit-lines"),
    ("city-boundary.geojson", "City boundary", "city-boundary"),
    ("local-area-boundary.geojson", "Local area boundary", "local-area-boundary"),
    ("block-outlines.geojson", "Block outlines", "block-outlines"),
]

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
KOTLIN_OUT = os.path.join(REPO, "web", "src", "jsMain", "kotlin", "vancouvermonth", "web", "VancouverMapData.kt")
WIDE_OUT = os.path.join(REPO, "web", "src", "jsMain", "kotlin", "vancouvermonth", "web", "WideMapData.kt")

# The wide map (stage 24a) -------------------------------------------------------------------------------------------
# The same plane, turn and scale as the town's map, with the grid carried on to the right (real south-east) through
# East Vancouver into south-west Burnaby. The town's map stays its top-left corner, square for square: the wide grid
# keeps its left and top edges and its rows, so every square of the town has the same column and row in both. Rows
# above it would move every square of the town down, so the wide grid grows only to the right.

# The City of Burnaby open data the wide map adds, read from the pages fetch.sh saved (NAME-1.geojson, NAME-2...).
BURNABY_DATASETS = [
    ("burnaby-boundary", "Burnaby Boundary", "d903b87782734451ae286cb0b59938ac", "OpenData3/MapServer/12"),
    ("skytrain", "SkyTrain", "b39d6277a1ec4edb8bba956664d30dc1", "OpenData5/MapServer/11"),
    ("skytrain-station", "SkyTrain Station", "cff871aeb8494cb58dd65a2d06af9454", "OpenData5/MapServer/10"),
    ("park-inventory", "Park Inventory", "a1a896a4209d4325bacacea417ffc400", "OpenData1/MapServer/6"),
    ("street", "Street", "18cb9099908640c68910b76033309d75", "OpenData5/MapServer/8"),
    ("legal-block", "Legal Block", "bb9ca652d6da4733ae32ab56e5127d1c", "OpenData4/MapServer/9"),
    ("district-lot", "District Lot", "81781be23e764ce1b7d6efec5b32e86d", "OpenData4/MapServer/6"),
    ("community-plan-areas", "Community Plan Area Boundaries", "c1efbda18f5a448583cc1deb939af351",
     "OpenData1/MapServer/10"),
]
# The City of Vancouver data the wide map reads besides the town's: the stations and the main streets.
WIDE_VANCOUVER_DATASETS = DATASETS + [
    ("rapid-transit-stations.geojson", "Rapid transit stations", "rapid-transit-stations"),
    ("public-streets.geojson", "Public streets, main streets only", "public-streets"),
]

# How far past the last station inside the grid (the one beyond the big Burnaby town centre) the grid reaches, in
# metres: room for the far edge of Burnaby, where the third zone's room is.
WIDE_MARGIN_EAST = 1200.0
# Burnaby's land: inside the City's boundary (which runs down the middle of the Fraser), a square is land when it lies
# in a district lot (the survey lots, which stop at the river bank), or when a legal block or a park lies on it or on
# a square beside it, so the streets between blocks are land and the river is not. Pockets walled in by land become
# land too.
BURNABY_REACH = 1
# A park in the wide part (outside the town's corner) is painted as park when it covers at least this many squares
# (2 ha); smaller ones stay plain land, as small parks are in the town's corner.
SMALLEST_PARK = 32
# How far a station may lie from the line it stands on, in squares, and how close two stations may be before they are
# taken as one (the two platforms of one interchange).
STATION_REACH = 2.5
STATION_MERGE = 6.0
# Lines in the wide map are simplified to within this much of the real line, in squares.
WIDE_SIMPLIFY = 0.5
# A street carries on through a crossing into the piece that turns by less than this, in degrees.
STRAIGHT_ON = 30.0
# A block bigger than this many squares (25 ha) is a park, a cemetery, a yard or a works, not a city block: left out.
LARGEST_BLOCK = 400

# The zones a room can be in, as the room chooser's transit zones: 1 is downtown and the West End (the town's corner),
# 2 is the Burnaby plan area round the big station, 3 the outer, pricier one: the Burnaby plan area on the slope south
# of the big park, down to the river, off the line. Zone 1 is two of the City of Vancouver's local areas, by name (that
# layer has no other key); zones 2 and 3 are City of Burnaby community plan areas, by their OBJECTID in that layer.
# Each is cut to the grid.
ZONE_AREAS = {
    1: [("vancouver", "Downtown"), ("vancouver", "West End")],
    2: [("burnaby", 8)],
    3: [("burnaby", 9)],
}
# Each Burnaby plan area's size as fetched, in hectares: if a republished layer numbers its areas differently, the
# build stops instead of drawing the wrong zone. It also stops unless zone 2's area holds two stations and zone 3's
# area reaches the city line.
PLAN_AREA_HECTARES = {8: 413.0, 9: 334.4}
# Zone 3 keeps only the strip of its plan area within this many squares (225 m) east of the area's west edge, the
# city line: the residential streets down the slope.
ZONE_STRIPS = {3: 9.0}
# The room in zones 2 and 3 is chosen by rule, never by hand. Of the squares in the zone with a 5 x 5 block of plain
# land round them, at least ROOM_INSET squares inside the grid's edges, 2 squares off every main street and 4 off the
# guideway, whose middle lies on a road (outside every City of Burnaby legal block and City of Vancouver block
# outline, so never on a lot), it takes the one farthest from ROOM_AWAY_FROM: in zone 2 the last station inside the
# grid, in zone 3 회사, so zone 3's room is the farther out. The build stops unless zone 3's room is farther from 회사
# than zone 2's. Zone 1's room is the town's.
ROOM_INSET = 5
ROOM_AWAY_FROM = {2: "last station", 3: "OFFICE"}
ZONE_NOTES = {
    1: "웨스트엔드 주택가 (지금 내 방)",
    2: "큰 역 구역의 북서쪽 끝 주택가, 시 경계 가까이",
    3: "큰 공원 남쪽 비탈, 강 쪽으로 내려가는 시 경계 주택가",
}


# Reading the data ----------------------------------------------------------------------------------------------------

def load_features(raw, name):
    with open(os.path.join(raw, name), encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, dict) or data.get("type") != "FeatureCollection":
        sys.exit(f"{name}: not a GeoJSON feature collection")
    return data["features"]


def lonlat_list(coords):
    """A list of [lon, lat] pairs, checked to be numbers near Vancouver."""
    out = []
    for p in coords:
        lon, lat = float(p[0]), float(p[1])
        if not (-124.0 < lon < -122.0 and 48.5 < lat < 50.0):
            sys.exit(f"a point outside Vancouver: {lon}, {lat}")
        out.append((lon, lat))
    return out


def project(lon, lat):
    return ((lon - LON0) * METRES_PER_DEGREE_LON, (lat - LAT0) * METRES_PER_DEGREE_LAT)


def sha1(path):
    with open(path, "rb") as f:
        return hashlib.sha1(f.read()).hexdigest()


# Geometry ------------------------------------------------------------------------------------------------------------

def point_in_ring(x, y, ring):
    inside = False
    j = len(ring) - 1
    for i in range(len(ring)):
        xi, yi = ring[i]
        xj, yj = ring[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def street_grid_angle(blocks, areas):
    """The angle of the downtown street grid, in degrees from east, between 0 and 90, from the block outlines."""
    rings = [[project(*p) for p in ring] for ring in areas]
    histogram = [0.0] * 90
    edges = []
    for block in blocks:
        ring = [project(*p) for p in block]
        cx = sum(p[0] for p in ring) / len(ring)
        cy = sum(p[1] for p in ring) / len(ring)
        if not any(point_in_ring(cx, cy, area) for area in rings):
            continue
        for a, b in zip(ring, ring[1:]):
            length = math.hypot(b[0] - a[0], b[1] - a[1])
            if length < 5.0:
                continue
            angle = math.degrees(math.atan2(b[1] - a[1], b[0] - a[0])) % 90.0
            histogram[int(angle) % 90] += length
            edges.append((angle, length))
    peak = max(range(90), key=lambda i: histogram[i - 1] + histogram[i] + histogram[(i + 1) % 90]) + 0.5
    # The length-weighted mean of the edges within three degrees of the peak.
    total = weight = 0.0
    for angle, length in edges:
        off = (angle - peak + 45.0) % 90.0 - 45.0
        if abs(off) <= 3.0:
            total += off * length
            weight += length
    return round(peak + total / weight, 1)


class Frame:
    """The plane turned by [turn] degrees counter-clockwise, then cut to the area and shrunk to game units."""

    def __init__(self, turn, left, top, metres_per_unit):
        self.turn = turn
        self.cos = math.cos(math.radians(turn))
        self.sin = math.sin(math.radians(turn))
        self.left = left
        self.top = top
        self.scale = metres_per_unit

    @staticmethod
    def turned(turn, x, y):
        c, s = math.cos(math.radians(turn)), math.sin(math.radians(turn))
        return (x * c - y * s, x * s + y * c)

    def game(self, lon, lat):
        """Game units: x east along the grid's columns, z south down its rows."""
        x, y = project(lon, lat)
        tx, ty = x * self.cos - y * self.sin, x * self.sin + y * self.cos
        return ((tx - self.left) / self.scale, (self.top - ty) / self.scale)


def scanline_fill(rings, columns, rows):
    """The squares whose centre lies inside [rings] (even-odd, so a ring inside another is a hole)."""
    edges = []
    for ring in rings:
        for i in range(len(ring)):
            a, b = ring[i - 1], ring[i]
            if a[1] == b[1]:
                continue
            if max(a[1], b[1]) < -1 or min(a[1], b[1]) > rows + 1:
                continue
            edges.append((a, b))
    filled = [[False] * columns for _ in range(rows)]
    for z in range(rows):
        zc = z + 0.5
        xs = []
        for (ax, az), (bx, bz) in edges:
            if (az > zc) != (bz > zc):
                xs.append(ax + (zc - az) * (bx - ax) / (bz - az))
        xs.sort()
        for k in range(0, len(xs) - 1, 2):
            first = max(0, math.ceil(xs[k] - 0.5))
            last = min(columns - 1, math.ceil(xs[k + 1] - 0.5) - 1)
            for x in range(first, last + 1):
                filled[z][x] = True
    return filled


def simplify(points, tolerance):
    """Douglas-Peucker: keeps the points needed to stay within [tolerance] of the line."""
    if len(points) < 3:
        return list(points)
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        first, last = stack.pop()
        ax, az = points[first]
        bx, bz = points[last]
        dx, dz = bx - ax, bz - az
        length = math.hypot(dx, dz)
        worst, index = -1.0, -1
        for i in range(first + 1, last):
            px, pz = points[i]
            if length == 0:
                d = math.hypot(px - ax, pz - az)
            else:
                d = abs(dx * (az - pz) - (ax - px) * dz) / length
            if d > worst:
                worst, index = d, i
        if worst > tolerance:
            keep[index] = True
            stack.append((first, index))
            stack.append((index, last))
    return [p for p, k in zip(points, keep) if k]


def clip_polyline(points, x0, z0, x1, z1):
    """The parts of a line inside a rectangle (Liang-Barsky on each segment), as a list of lines."""
    parts, current = [], []
    for a, b in zip(points, points[1:]):
        dx, dz = b[0] - a[0], b[1] - a[1]
        t0, t1 = 0.0, 1.0
        ok = True
        for p, q in ((-dx, a[0] - x0), (dx, x1 - a[0]), (-dz, a[1] - z0), (dz, z1 - a[1])):
            if p == 0:
                if q < 0:
                    ok = False
                    break
            else:
                t = q / p
                if p < 0:
                    t0 = max(t0, t)
                else:
                    t1 = min(t1, t)
        if not ok or t0 > t1:
            if len(current) > 1:
                parts.append(current)
            current = []
            continue
        start = (a[0] + t0 * dx, a[1] + t0 * dz)
        end = (a[0] + t1 * dx, a[1] + t1 * dz)
        if not current:
            current = [start]
        elif current[-1] != start:
            if len(current) > 1:
                parts.append(current)
            current = [start]
        current.append(end)
        if t1 < 1.0:
            parts.append(current)
            current = []
    if len(current) > 1:
        parts.append(current)
    return parts


# The grid ------------------------------------------------------------------------------------------------------------

def components(squares, columns, rows, wanted):
    """Groups of squares of the kinds in [wanted] joined side to side (a walk never cuts a corner)."""
    seen = [[False] * columns for _ in range(rows)]
    groups = []
    for z in range(rows):
        for x in range(columns):
            if seen[z][x] or squares[z][x] not in wanted:
                continue
            group = []
            seen[z][x] = True
            stack = [(x, z)]
            while stack:
                cx, cz = stack.pop()
                group.append((cx, cz))
                for nx, nz in ((cx + 1, cz), (cx - 1, cz), (cx, cz + 1), (cx, cz - 1)):
                    if 0 <= nx < columns and 0 <= nz < rows and not seen[nz][nx] and squares[nz][nx] in wanted:
                        seen[nz][nx] = True
                        stack.append((nx, nz))
            groups.append(group)
    return groups


class Walker:
    """The game's Grid.route, step for step: A* over squares, then the turns a straight walk can skip dropped."""

    CLEARANCE = 0.3
    SAMPLE_STEP = 0.1

    def __init__(self, squares, columns, rows):
        self.squares = squares
        self.columns = columns
        self.rows = rows

    def free(self, x, z):
        return 0 <= x < self.columns and 0 <= z < self.rows and self.squares[z][x] != WATER

    def search(self, start, goal):
        slant = math.sqrt(2.0)

        def guess(c):
            across, down = abs(c[0] - goal[0]), abs(c[1] - goal[1])
            return max(across, down) + (slant - 1) * min(across, down)

        cost = {start: 0.0}
        came = {}
        done = set()
        heap = [(guess(start), 0, start)]
        tie = 0
        while heap:
            _, _, current = heapq.heappop(heap)
            if current in done:
                continue
            done.add(current)
            if current == goal:
                cells = [goal]
                while cells[-1] in came:
                    cells.append(came[cells[-1]])
                return cells[::-1]
            cx, cz = current
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    if dx == 0 and dz == 0:
                        continue
                    nx, nz = cx + dx, cz + dz
                    if not self.free(nx, nz) or (nx, nz) in done:
                        continue
                    if dx and dz and not (self.free(cx + dx, cz) and self.free(cx, cz + dz)):
                        continue
                    new = cost[current] + (slant if dx and dz else 1.0)
                    if new < cost.get((nx, nz), float("inf")):
                        cost[(nx, nz)] = new
                        came[(nx, nz)] = current
                        tie += 1
                        heapq.heappush(heap, (new + guess((nx, nz)), tie, (nx, nz)))
        return None

    def clear(self, a, b):
        steps = max(1, math.ceil(math.hypot(b[0] - a[0], b[1] - a[1]) / self.SAMPLE_STEP))
        for step in range(steps + 1):
            x = a[0] + (b[0] - a[0]) * step / steps
            z = a[1] + (b[1] - a[1]) * step / steps
            for dx in (-self.CLEARANCE, self.CLEARANCE):
                for dz in (-self.CLEARANCE, self.CLEARANCE):
                    if not self.free(math.floor(x + dx), math.floor(z + dz)):
                        return False
        return True

    def route_length(self, a, b):
        cells = self.search(a, b)
        if cells is None:
            return None
        spots = [(c[0] + 0.5, c[1] + 0.5) for c in cells]
        kept, last = [spots[0]], 0
        while last < len(spots) - 1:
            farthest = len(spots) - 1
            while farthest > last + 1 and not self.clear(spots[last], spots[farthest]):
                farthest -= 1
            kept.append(spots[farthest])
            last = farthest
        return sum(math.hypot(q[0] - p[0], q[1] - p[1]) for p, q in zip(kept, kept[1:]))


def greedy_boxes(squares, columns, rows, kind):
    """How many flat boxes cover every square of [kind], each box as wide and then as deep as it can grow."""
    used = [[False] * columns for _ in range(rows)]
    count = 0
    for z in range(rows):
        for x in range(columns):
            if used[z][x] or squares[z][x] != kind:
                continue
            width = 1
            while x + width < columns and squares[z][x + width] == kind and not used[z][x + width]:
                width += 1
            depth = 1
            while z + depth < rows and all(
                squares[z + depth][x + i] == kind and not used[z + depth][x + i] for i in range(width)
            ):
                depth += 1
            for dz in range(depth):
                for dx in range(width):
                    used[z + dz][x + dx] = True
            count += 1
    return count


# Building the map ----------------------------------------------------------------------------------------------------

class Sources:
    def __init__(self, raw):
        self.raw = raw
        shore = load_features(raw, "shoreline-2002.geojson")
        lines = sorted((lonlat_list(f["geometry"]["coordinates"]) for f in shore), key=len, reverse=True)
        # The long line runs from the city's east edge on Burrard Inlet round to the Fraser River, and the short one
        # carries on to the east edge there. Joined end to end, and closed along the city's east edge, they ring
        # all of the city's land.
        main, rest = lines[0], lines[1]
        if main[-1] != rest[0]:
            sys.exit("shoreline: the two lines do not meet")
        self.shoreline = main + rest[1:]

        parks = load_features(raw, "parks-polygon-representation.geojson")
        park = [f for f in parks if f["properties"].get("park_name") == "Stanley Park"]
        if len(park) != 1 or park[0]["geometry"]["type"] != "Polygon":
            sys.exit("parks: expected one polygon for the big park")
        self.park_rings = [lonlat_list(r) for r in park[0]["geometry"]["coordinates"]]
        # The other parks' outlines: a city block inside one is a green, not buildings.
        self.other_parks = []
        for f in parks:
            if f["properties"].get("park_name") == "Stanley Park":
                continue
            geometry = f["geometry"]
            polygons = [geometry["coordinates"]] if geometry["type"] == "Polygon" else geometry["coordinates"]
            for polygon in polygons:
                self.other_parks.append(lonlat_list(polygon[0]))

        rail = [f for f in load_features(raw, "rapid-transit-lines.geojson")
                if f["properties"].get("line") == "Expo Line"]
        if len(rail) != 1:
            sys.exit("rapid transit: expected one Expo Line")
        line = lonlat_list(rail[0]["geometry"]["coordinates"])
        portal = project(*RAIL_PORTAL)
        cut = min(range(len(line)), key=lambda i: math.dist(project(*line[i]), portal))
        self.rail = line[cut:]

        city = load_features(raw, "city-boundary.geojson")[0]["geometry"]["coordinates"]
        # The part of the city's boundary that runs out along the middle of Burrard Inlet: the north shore and its
        # mountains are past it.
        inlet = [lonlat_list(part) for part in city]
        line = max((part for part in inlet if min(p[1] for p in part) > 49.29),
                   key=lambda part: max(p[0] for p in part))
        # It comes up through English Bay first; keep it from the narrows off the park's northern tip, eastwards.
        tip = project(*max(self.park_rings[0], key=lambda p: p[1]))
        narrows = min(range(len(line)), key=lambda i: math.dist(project(*line[i]), tip))
        self.inlet_line = line[narrows:]

        areas = {f["properties"]["name"]: lonlat_list(f["geometry"]["coordinates"][0])
                 for f in load_features(raw, "local-area-boundary.geojson")}
        self.areas = areas
        self.blocks = [lonlat_list(f["geometry"]["coordinates"][0])
                       for f in load_features(raw, "block-outlines.geojson")]
        self.turn = 90.0 - street_grid_angle(self.blocks, [areas["Downtown"], areas["West End"]])

    def extent(self):
        """The area in turned metres: left, top, right, bottom."""
        park = [Frame.turned(self.turn, *project(*p)) for p in self.park_rings[0]]
        downtown = [Frame.turned(self.turn, *project(*p)) for p in self.areas["Downtown"]]
        left = min(p[0] for p in park) - MARGIN_LEFT
        top = max(p[1] for p in park) + MARGIN_TOP
        right = max(p[0] for p in downtown) + MARGIN_RIGHT
        bottom = min(p[1] for p in downtown) - MARGIN_BOTTOM
        return left, top, right, bottom


class Map:
    def __init__(self, sources, metres_per_unit):
        self.sources = sources
        left, top, right, bottom = sources.extent()
        self.frame = Frame(sources.turn, left, top, metres_per_unit)
        self.columns = math.ceil((right - left) / metres_per_unit)
        self.rows = math.ceil((top - bottom) / metres_per_unit)
        self.log = []
        self.paint()

    def paint(self):
        f, columns, rows = self.frame, self.columns, self.rows
        land_ring = [f.game(*p) for p in self.sources.shoreline]
        land = scanline_fill([land_ring], columns, rows)
        park = scanline_fill([[f.game(*p) for p in ring] for ring in self.sources.park_rings], columns, rows)
        squares = [[LAND if land[z][x] else WATER for x in range(columns)] for z in range(rows)]

        # Ponds too small to show become land; larger water inside the land stays.
        for group in components(squares, columns, rows, {WATER}):
            edge = any(x in (0, columns - 1) or z in (0, rows - 1) for x, z in group)
            if not edge and len(group) < SMALLEST_LAKE:
                for x, z in group:
                    squares[z][x] = LAND
                self.log.append(f"filled a pond of {len(group)} squares at {group[0]}")
        # Land the character could never walk to (a rock, an islet) becomes water.
        groups = sorted(components(squares, columns, rows, {LAND}), key=len, reverse=True)
        for group in groups[1:]:
            for x, z in group:
                squares[z][x] = WATER
            self.log.append(f"dropped an islet of {len(group)} squares at {min(group)}")

        for z in range(rows):
            for x in range(columns):
                if squares[z][x] != LAND:
                    continue
                near_water = any(
                    0 <= x + dx < columns and 0 <= z + dz < rows and squares[z + dz][x + dx] == WATER
                    for dx in (-1, 0, 1) for dz in (-1, 0, 1)
                )
                if near_water:
                    squares[z][x] = SHORE
                elif park[z][x]:
                    squares[z][x] = PARK
        # Specks of land caught between the park's outline and the shore join the park.
        for group in components(squares, columns, rows, {LAND}):
            if len(group) >= SMALLEST_LAKE * 3:
                continue
            touches_park = any(
                0 <= nx < columns and 0 <= nz < rows and squares[nz][nx] == PARK
                for x, z in group for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))
            )
            if touches_park:
                for x, z in group:
                    squares[z][x] = PARK
        self.squares = squares
        self.spots = {}
        for name, lon, lat in SPOTS:
            gx, gz = f.game(lon, lat)
            x, z = math.floor(gx), math.floor(gz)
            self.spots[name] = (x, z)
            room = [squares[z + dz][x + dx] for dx in range(-2, 3) for dz in range(-2, 3)
                    if 0 <= x + dx < columns and 0 <= z + dz < rows]
            if len(room) != 25 or any(kind != LAND for kind in room):
                sys.exit(f"{name}: no room for a building at {(x, z)}")

    def city_blocks(self):
        """The real city blocks as rectangles of plain land squares: (x, z, width, depth) each, and whether the block
        lies in a park (a green) or holds buildings. A block is drawn smaller than its outline so streets run between
        blocks, a block turned off the grid's axes is drawn as a square of the same area, and a very big one is cut."""
        f = self.frame
        parks = [[project(*p) for p in ring] for ring in self.sources.other_parks]
        built, greens = [], []
        for ring in self.sources.blocks:
            pts = [f.game(*p) for p in ring]
            xs = [p[0] for p in pts]
            zs = [p[1] for p in pts]
            x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
            box = (x1 - x0) * (z1 - z0)
            area = abs(sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(pts, pts[1:] + pts[:1]))) / 2
            if box <= 0 or area < 1.0:
                continue
            fill = area / box
            if fill < 0.75:
                # Turned off the axes (the older streets to the east): a square of the same area round its middle.
                shrink = math.sqrt(fill)
                cx, cz = (x0 + x1) / 2, (z0 + z1) / 2
                x0, x1 = cx - (cx - x0) * shrink, cx + (x1 - cx) * shrink
                z0, z1 = cz - (cz - z0) * shrink, cz + (z1 - cz) * shrink
            first_x = math.ceil(x0 + BLOCK_INSET - 0.5)
            last_x = math.floor(x1 - BLOCK_INSET - 0.5)
            first_z = math.ceil(z0 + BLOCK_INSET - 0.5)
            last_z = math.floor(z1 - BLOCK_INSET - 0.5)
            rect = self.trim_to_land(first_x, first_z, last_x, last_z)
            if rect is None:
                continue
            middle = project(*[sum(c) / len(ring) for c in zip(*ring)])
            green = any(point_in_ring(middle[0], middle[1], park) for park in parks)
            for piece in split_rect(*rect):
                (greens if green else built).append(piece)
        # Where two outlines overlap once squared off, the larger block keeps the ground.
        kept = []
        for rect in sorted(built + greens, key=lambda r: (-r[2] * r[3], r)):
            if not any(overlaps(rect, other) for other in kept):
                kept.append(rect)
        # Where two blocks still meet side to side, the smaller gives up the row or column along that side, so a street
        # of at least one square runs between any two.
        kept = keep_streets(kept)
        return [kept[r] for r in built if r in kept], [kept[r] for r in greens if r in kept]

    def trim_to_land(self, x0, z0, x1, z1):
        """The rectangle of squares x0..x1, z0..z1 cut down from its edges until every square in it is plain land,
        as (x, z, width, depth), or None when too little is left."""
        def bad(x, z):
            return not (0 <= x < self.columns and 0 <= z < self.rows) or self.squares[z][x] != LAND
        while x0 <= x1 and z0 <= z1:
            edges = {
                "west": sum(bad(x0, z) for z in range(z0, z1 + 1)),
                "east": sum(bad(x1, z) for z in range(z0, z1 + 1)),
                "north": sum(bad(x, z0) for x in range(x0, x1 + 1)),
                "south": sum(bad(x, z1) for x in range(x0, x1 + 1)),
            }
            worst = max(edges, key=edges.get)
            if edges[worst] == 0:
                break
            if worst == "west":
                x0 += 1
            elif worst == "east":
                x1 -= 1
            elif worst == "north":
                z0 += 1
            else:
                z1 -= 1
        width, depth = x1 - x0 + 1, z1 - z0 + 1
        if width < 1 or depth < 1 or width * depth < 2:
            return None
        return x0, z0, width, depth

    def walks(self):
        walker = Walker(self.squares, self.columns, self.rows)
        names = [s[0] for s in SPOTS]
        out = {}
        for i, a in enumerate(names):
            for b in names[i + 1:]:
                out[(a, b)] = walker.route_length(self.spots[a], self.spots[b])
        return out

    def lines(self):
        """The simplified lines in game units: shoreline parts, park outline, rail, mountains."""
        f = self.frame
        box = (-1.0, -1.0, self.columns + 1.0, self.rows + 1.0)
        shore = [f.game(*p) for p in self.sources.shoreline]
        shore_parts = [simplify(part, SIMPLIFY) for part in clip_polyline(shore, *box)]
        # Keep the stretches that are lines on the map: two distinct points or more, not all on the clipping frame.
        def on_frame(point):
            return point[0] in (box[0], box[2]) or point[1] in (box[1], box[3])
        shore_parts = [p for p in shore_parts if len(set(p)) > 1 and not all(on_frame(q) for q in p)]
        park = simplify([f.game(*p) for p in self.sources.park_rings[0]], SIMPLIFY)
        rail_parts = clip_polyline([f.game(*p) for p in self.sources.rail], 0.0, 0.0, self.columns, self.rows)
        rail = simplify(rail_parts[0], SIMPLIFY) if rail_parts else []
        mountains = [f.game(*p) for p in self.sources.inlet_line]
        return shore_parts, park, rail, mountains


def keep_streets(rects):
    """[rects] with every two that meet side to side pulled apart by a square: the smaller one loses its row or column
    on that side (the larger, when the smaller is only one square across). Returns each rectangle mapped to what is
    left of it, and leaves out one that has nothing left."""
    now = {r: r for r in rects}
    changed = True
    while changed:
        changed = False
        live = sorted((r for r in now if now[r] is not None), key=lambda r: (-now[r][2] * now[r][3], r))
        for i, big in enumerate(live):
            for small in live[i + 1:]:
                a, b = now[big], now[small]
                if a is None or b is None:
                    continue
                side = meeting_side(a, b)
                if side is None:
                    continue
                trimmed = trim(b, side)
                if trimmed is None:
                    opposite = {"west": "east", "east": "west", "north": "south", "south": "north"}[side]
                    trimmed_big = trim(a, opposite)
                    if trimmed_big is not None:
                        now[big] = trimmed_big
                    else:
                        now[small] = None
                else:
                    now[small] = trimmed
                changed = True
    return {r: v for r, v in now.items() if v is not None}


def meeting_side(a, b):
    """The side of [b] that meets [a] edge to edge with a stretch in common, or None: "west" when a lies west of b."""
    ax0, az0, ax1, az1 = a[0], a[1], a[0] + a[2], a[1] + a[3]
    bx0, bz0, bx1, bz1 = b[0], b[1], b[0] + b[2], b[1] + b[3]
    across = min(az1, bz1) - max(az0, bz0)
    down = min(ax1, bx1) - max(ax0, bx0)
    if across > 0 and ax1 == bx0:
        return "west"
    if across > 0 and bx1 == ax0:
        return "east"
    if down > 0 and az1 == bz0:
        return "north"
    if down > 0 and bz1 == az0:
        return "south"
    return None


def trim(rect, side):
    """[rect] less its row or column on [side], or None when that would leave nothing."""
    x, z, w, d = rect
    if side in ("west", "east"):
        if w < 2:
            return None
        return (x + 1, z, w - 1, d) if side == "west" else (x, z, w - 1, d)
    if d < 2:
        return None
    return (x, z + 1, w, d - 1) if side == "north" else (x, z, w, d - 1)


def overlaps(a, b):
    """True when rectangles a and b, each (x, z, width, depth) in squares, share a square."""
    return a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]


def split_rect(x, z, width, depth):
    """A block cut into pieces no wider or deeper than SPLIT_OVER squares, with a lane of one square between them."""
    def cuts(start, size):
        if size <= SPLIT_OVER:
            return [(start, size)]
        count = math.ceil((size + 1) / (SPLIT_OVER + 1))
        piece = (size - (count - 1)) // count
        extra = (size - (count - 1)) - piece * count
        out, at = [], start
        for index in range(count):
            length = piece + (1 if index < extra else 0)
            out.append((at, length))
            at += length + 1
        return out
    return [(px, pz, pw, pd) for px, pw in cuts(x, width) for pz, pd in cuts(z, depth)]


def choose_scale(sources):
    """The metres one game unit stands for, in steps of half a metre: the finest scale at which the farthest walk
    between two of the five places still takes no more than LONGEST_WALK_SECONDS."""
    longest = LONGEST_WALK_SECONDS * WALK_SPEED
    scale = 25.0
    for _ in range(3):
        farthest = max(Map(sources, scale).walks().values())
        scale = math.ceil(scale * farthest / longest * 2) / 2
    while True:
        built = Map(sources, scale)
        walks = built.walks()
        if max(walks.values()) <= longest:
            return built, walks
        scale += 0.5


# The wide map --------------------------------------------------------------------------------------------------------

def load_pages(raw, stem):
    """The features of every page fetch.sh saved for [stem]: stem-1.geojson, stem-2.geojson and so on."""
    features, page = [], 1
    while os.path.exists(os.path.join(raw, f"{stem}-{page}.geojson")):
        features += load_features(raw, f"{stem}-{page}.geojson")
        page += 1
    if page == 1:
        sys.exit(f"{stem}: no pages fetched")
    return features


def polygons_of(geometry):
    """Each polygon of a Polygon or MultiPolygon as its list of rings (the outline first, then any holes)."""
    if geometry["type"] == "Polygon":
        return [[lonlat_list(r) for r in geometry["coordinates"]]]
    if geometry["type"] == "MultiPolygon":
        return [[lonlat_list(r) for r in polygon] for polygon in geometry["coordinates"]]
    sys.exit(f"expected a polygon, got {geometry['type']}")


def lines_of(geometry):
    if geometry["type"] == "LineString":
        return [lonlat_list(geometry["coordinates"])]
    if geometry["type"] == "MultiLineString":
        return [lonlat_list(part) for part in geometry["coordinates"]]
    sys.exit(f"expected a line, got {geometry['type']}")


def pages_sha1(raw, stem):
    """One SHA-1 per page, joined with '+'."""
    hashes, page = [], 1
    while os.path.exists(os.path.join(raw, f"{stem}-{page}.geojson")):
        hashes.append(sha1(os.path.join(raw, f"{stem}-{page}.geojson")))
        page += 1
    return "+".join(hashes)


class WideSources:
    """What the wide map reads besides the town's sources: the rest of the Expo Line and its stations, the main
    streets, East Vancouver's blocks and parks (all City of Vancouver), and Burnaby's boundary, blocks, parks, streets,
    line, stations and community plan areas (City of Burnaby)."""

    def __init__(self, raw, town):
        self.town = town
        expo = [f for f in load_features(raw, "rapid-transit-lines.geojson") if f["properties"].get("line") == "Expo Line"]
        line = lonlat_list(expo[0]["geometry"]["coordinates"])
        portal = project(*RAIL_PORTAL)
        line = line[min(range(len(line)), key=lambda i: math.dist(project(*line[i]), portal)):]
        # The City of Vancouver's line runs on to the second station in Burnaby; Burnaby's pieces, one between each
        # two stations, carry it on. Join the ones that start where the line ends, until none does.
        pieces = [lines_of(f["geometry"])[0] for f in load_pages(raw, "skytrain")
                  if f["properties"].get("NAME") == "EXPO LINE"]
        while True:
            end = project(*line[-1])
            nxt = [p for p in pieces if math.dist(project(*p[0]), end) < 40.0]
            if not nxt:
                break
            pieces.remove(nxt[0])
            line = line + nxt[0][1:]
        self.rail = line

        self.stations = []
        for f in load_features(raw, "rapid-transit-stations.geojson"):
            lon, lat = f["geometry"]["coordinates"][:2]
            self.stations.append((f["properties"]["station"], (float(lon), float(lat))))
        for f in load_pages(raw, "skytrain-station"):
            lon, lat = f["geometry"]["coordinates"][:2]
            self.stations.append((f["properties"]["NAME"].title(), (float(lon), float(lat))))

        self.streets = [part for f in load_features(raw, "public-streets.geojson") for part in lines_of(f["geometry"])]
        self.streets += [part for f in load_pages(raw, "street") for part in lines_of(f["geometry"])]

        boundary = load_pages(raw, "burnaby-boundary")
        self.burnaby = [polygon for f in boundary for polygon in polygons_of(f["geometry"])]
        self.burnaby_blocks = [polygon for f in load_pages(raw, "legal-block") for polygon in polygons_of(f["geometry"])]
        self.district_lots = [polygon for f in load_pages(raw, "district-lot") for polygon in polygons_of(f["geometry"])]

        # Every park, grouped by name within each city so a park in several pieces counts as one.
        self.parks = {}
        for f in load_features(raw, "parks-polygon-representation.geojson"):
            name = f["properties"].get("park_name")
            if name == "Stanley Park":
                continue
            self.parks.setdefault(("vancouver", name), []).extend(polygons_of(f["geometry"]))
        for f in load_pages(raw, "park-inventory"):
            self.parks.setdefault(("burnaby", f["properties"].get("NAME")), []).extend(polygons_of(f["geometry"]))

        # The community plan areas, by their OBJECTID.
        self.plan_areas = {}
        for f in load_pages(raw, "community-plan-areas"):
            self.plan_areas.setdefault(f["properties"]["OBJECTID"], []).extend(polygons_of(f["geometry"]))


def paint_polygon(mask, rings, columns, rows):
    """Marks in [mask] the squares whose centre lies inside [rings] (even-odd, so a ring inside another is a hole),
    and returns how many squares that is. Only the rows the rings cross are looked at."""
    edges = []
    low, high = rows, -1
    for ring in rings:
        for i in range(len(ring)):
            a, b = ring[i - 1], ring[i]
            if a[1] == b[1]:
                continue
            edges.append((a, b))
            low = min(low, a[1], b[1])
            high = max(high, a[1], b[1])
    count = 0
    for z in range(max(0, math.floor(low)), min(rows, math.ceil(high) + 1)):
        zc = z + 0.5
        xs = sorted(ax + (zc - az) * (bx - ax) / (bz - az) for (ax, az), (bx, bz) in edges if (az > zc) != (bz > zc))
        for k in range(0, len(xs) - 1, 2):
            for x in range(max(0, math.ceil(xs[k] - 0.5)), min(columns - 1, math.ceil(xs[k + 1] - 0.5) - 1) + 1):
                mask[z][x] = True
                count += 1
    return count


def clip_polygon(points, x0, z0, x1, z1):
    """The part of a polygon inside a rectangle (Sutherland-Hodgman), or an empty list."""
    def cut(points, inside, cross):
        out = []
        for i, b in enumerate(points):
            a = points[i - 1]
            if inside(b):
                if not inside(a):
                    out.append(cross(a, b))
                out.append(b)
            elif inside(a):
                out.append(cross(a, b))
        return out

    def at_x(x):
        return lambda a, b: (x, a[1] + (b[1] - a[1]) * (x - a[0]) / (b[0] - a[0]))

    def at_z(z):
        return lambda a, b: (a[0] + (b[0] - a[0]) * (z - a[1]) / (b[1] - a[1]), z)

    for inside, cross in ((lambda p: p[0] >= x0, at_x(x0)), (lambda p: p[0] <= x1, at_x(x1)),
                          (lambda p: p[1] >= z0, at_z(z0)), (lambda p: p[1] <= z1, at_z(z1))):
        if not points:
            return []
        points = cut(points, inside, cross)
    return points


def turned_box(points):
    """The smallest rectangle round [points], turned to fit: (centre x, centre z, width, depth, turn in degrees), the
    width along the turn and the turn between -45 and 45 degrees."""
    hull = convex_hull(points)
    best = None
    for a, b in zip(hull, hull[1:] + hull[:1]):
        angle = math.atan2(b[1] - a[1], b[0] - a[0])
        c, s = math.cos(-angle), math.sin(-angle)
        us = [p[0] * c - p[1] * s for p in hull]
        vs = [p[0] * s + p[1] * c for p in hull]
        area = (max(us) - min(us)) * (max(vs) - min(vs))
        if best is None or area < best[0]:
            best = (area, angle, (min(us) + max(us)) / 2, (min(vs) + max(vs)) / 2, max(us) - min(us), max(vs) - min(vs))
    _, angle, u, v, width, depth = best
    # Back from the turned frame to the map's.
    cx = u * math.cos(angle) - v * math.sin(angle)
    cz = u * math.sin(angle) + v * math.cos(angle)
    degrees = math.degrees(angle)
    while degrees > 45.0:
        degrees -= 90.0
        width, depth = depth, width
    while degrees <= -45.0:
        degrees += 90.0
        width, depth = depth, width
    return cx, cz, width, depth, degrees


def convex_hull(points):
    pts = sorted(set(points))
    if len(pts) < 3:
        return pts

    def half(seq):
        out = []
        for p in seq:
            while len(out) >= 2 and ((out[-1][0] - out[-2][0]) * (p[1] - out[-2][1])
                                     - (out[-1][1] - out[-2][1]) * (p[0] - out[-2][0])) <= 0:
                out.pop()
            out.append(p)
        return out

    lower, upper = half(pts), half(reversed(pts))
    return lower[:-1] + upper[:-1]


def chain_lines(segments):
    """Pieces of street joined end to end into whole streets: at each end the piece carries on into the piece that
    goes on most nearly straight, if one turns by less than STRAIGHT_ON degrees."""
    def key(p):
        return (round(p[0] * 20), round(p[1] * 20))

    def heading(a, b):
        return math.atan2(b[1] - a[1], b[0] - a[0])

    ends = {}
    for i, seg in enumerate(segments):
        for p in (seg[0], seg[-1]):
            ends.setdefault(key(p), []).append(i)
    used = [False] * len(segments)
    chains = []
    for start in sorted(range(len(segments)), key=lambda i: -len(segments[i])):
        if used[start]:
            continue
        used[start] = True
        chain = list(segments[start])
        for forward in (True, False):
            while True:
                tip, before = (chain[-1], chain[-2]) if forward else (chain[0], chain[1])
                going = heading(before, tip)
                best = None
                for j in ends[key(tip)]:
                    if used[j]:
                        continue
                    seg = segments[j] if key(segments[j][0]) == key(tip) else segments[j][::-1]
                    turn = abs((heading(seg[0], seg[1]) - going + math.pi) % (2 * math.pi) - math.pi)
                    if turn < math.radians(STRAIGHT_ON) and (best is None or turn < best[0]):
                        best = (turn, j, seg)
                if best is None:
                    break
                _, j, seg = best
                used[j] = True
                chain = chain + seg[1:] if forward else seg[::-1][:-1] + chain
        chains.append(chain)
    return chains


def along_line(line, point):
    """Where [point] comes nearest [line]: (how far along the line, in units, the nearest point, the distance)."""
    best, walked = None, 0.0
    for a, b in zip(line, line[1:]):
        dx, dz = b[0] - a[0], b[1] - a[1]
        length = math.hypot(dx, dz)
        t = 0.0 if length == 0 else max(0.0, min(1.0, ((point[0] - a[0]) * dx + (point[1] - a[1]) * dz) / length ** 2))
        near = (a[0] + t * dx, a[1] + t * dz)
        d = math.dist(near, point)
        if best is None or d < best[2]:
            best = (walked + t * length, near, d)
        walked += length
    return best


class WideMap:
    """The town's map carried on to the right through East Vancouver into south-west Burnaby: the same frame, the town's
    squares unchanged in its top-left corner, and the same painting rules everywhere."""

    def __init__(self, wide, town):
        self.wide = wide
        self.town = town
        self.frame = town.frame
        f = self.frame
        last = max((p for _, p in wide.stations if self.frame.game(*p)[1] >= 0),
                   key=lambda p: f.game(*p)[0])
        self.columns = math.ceil(f.game(*last)[0] + WIDE_MARGIN_EAST / f.scale)
        self.rows = town.rows
        self.log = []
        self.paint()
        self.compare()

    def mask(self):
        return [[False] * self.columns for _ in range(self.rows)]

    def in_town(self, x, z):
        return x < self.town.columns and z < self.town.rows

    def paint(self):
        f, columns, rows = self.frame, self.columns, self.rows
        town = self.town.sources
        vancouver = scanline_fill([[f.game(*p) for p in town.shoreline]], columns, rows)
        burnaby = self.mask()
        for polygon in self.wide.burnaby:
            paint_polygon(burnaby, [[f.game(*p) for p in ring] for ring in polygon], columns, rows)
        near = self.mask()
        for polygon in self.wide.burnaby_blocks:
            paint_polygon(near, [[f.game(*p) for p in ring] for ring in polygon], columns, rows)
        for (city, _), polygons in self.wide.parks.items():
            if city == "burnaby":
                for polygon in polygons:
                    paint_polygon(near, [[f.game(*p) for p in ring] for ring in polygon], columns, rows)
        lots = self.mask()
        for polygon in self.wide.district_lots:
            paint_polygon(lots, [[f.game(*p) for p in ring] for ring in polygon], columns, rows)
        land = [[vancouver[z][x] or (burnaby[z][x] and lots[z][x]) or (burnaby[z][x] and any(
            near[nz][nx] for nz in range(max(0, z - BURNABY_REACH), min(rows, z + BURNABY_REACH + 1))
            for nx in range(max(0, x - BURNABY_REACH), min(columns, x + BURNABY_REACH + 1))))
            for x in range(columns)] for z in range(rows)]
        # Pockets inside Burnaby's boundary walled in by land (a wide street, a yard between blocks) are land.
        filled = 0
        for group in components([["." if c else "~" for c in row] for row in land], columns, rows, {"~"}):
            edge = any(x in (0, columns - 1) or z in (0, rows - 1) for x, z in group)
            if not edge and all(burnaby[z][x] for x, z in group):
                for x, z in group:
                    land[z][x] = True
                filled += len(group)
        self.log.append(f"Burnaby: {filled} squares of pockets between blocks made land")
        squares = [[LAND if land[z][x] else WATER for x in range(columns)] for z in range(rows)]

        # From here on, the town's rules, square for square.
        for group in components(squares, columns, rows, {WATER}):
            edge = any(x in (0, columns - 1) or z in (0, rows - 1) for x, z in group)
            if not edge and len(group) < SMALLEST_LAKE:
                for x, z in group:
                    squares[z][x] = LAND
                self.log.append(f"filled a pond of {len(group)} squares at {group[0]}")
        groups = sorted(components(squares, columns, rows, {LAND}), key=len, reverse=True)
        for group in groups[1:]:
            for x, z in group:
                squares[z][x] = WATER
            self.log.append(f"dropped an islet of {len(group)} squares at {min(group)}")

        big_park = scanline_fill([[f.game(*p) for p in ring] for ring in town.park_rings], columns, rows)
        parks = self.mask()
        self.park_count = 0
        for key, polygons in sorted(self.wide.parks.items(), key=lambda kv: (kv[0][0], kv[0][1] or "")):
            one = self.mask()
            size = sum(paint_polygon(one, [[f.game(*p) for p in ring] for ring in polygon], columns, rows)
                       for polygon in polygons)
            outside = sum(one[z][x] for z in range(rows) for x in range(self.town.columns, columns))
            if size >= SMALLEST_PARK and outside:
                self.park_count += 1
                for z in range(rows):
                    for x in range(self.town.columns, columns):
                        if one[z][x]:
                            parks[z][x] = True
        for z in range(rows):
            for x in range(columns):
                if squares[z][x] != LAND:
                    continue
                near_water = any(
                    0 <= x + dx < columns and 0 <= z + dz < rows and squares[z + dz][x + dx] == WATER
                    for dx in (-1, 0, 1) for dz in (-1, 0, 1)
                )
                if near_water:
                    squares[z][x] = SHORE
                elif big_park[z][x] or parks[z][x]:
                    squares[z][x] = PARK
        for group in components(squares, columns, rows, {LAND}):
            if len(group) >= SMALLEST_LAKE * 3:
                continue
            touches_park = any(
                0 <= nx < columns and 0 <= nz < rows and squares[nz][nx] == PARK
                for x, z in group for nx, nz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))
            )
            if touches_park:
                for x, z in group:
                    squares[z][x] = PARK
        self.squares = squares
        self.burnaby_mask = burnaby

    def compare(self):
        """The town's corner of the wide grid against the town's own map: the squares that differ, as a list."""
        self.differences = [(x, z, self.town.squares[z][x], self.squares[z][x])
                            for z in range(self.town.rows) for x in range(self.town.columns)
                            if self.town.squares[z][x] != self.squares[z][x]]

    def box(self):
        return (0.0, 0.0, float(self.columns), float(self.rows))

    def rail(self):
        """The Expo Line from the tunnel's mouth by the stadiums to where it leaves the grid, simplified."""
        parts = join_parts(clip_polyline([self.frame.game(*p) for p in self.wide.rail], *self.box()))
        return simplify(parts[0], SIMPLIFY)

    def stations(self):
        """The stations on the rail inside the grid, in order from the stadiums east: (name, point on the line, how far
        along it). The names are for the report only; the game gives its stations made-up names."""
        rail = self.rail()
        found = []
        for name, lonlat in self.wide.stations:
            gx, gz = self.frame.game(*lonlat)
            if not (0 <= gx < self.columns and 0 <= gz < self.rows):
                continue
            along, near, distance = along_line(rail, (gx, gz))
            if distance <= STATION_REACH:
                found.append((along, name, near))
        found.sort()
        merged = []
        for along, name, near in found:
            if merged and along - merged[-1][2] < STATION_MERGE:
                continue
            merged.append((name, near, along))
        return merged

    def streets(self):
        """The main streets (Vancouver's arterials and secondary arterials, Burnaby's arterials and major collectors),
        joined into whole streets, simplified and cut to the grid."""
        segments = [[self.frame.game(*p) for p in line] for line in self.wide.streets]
        out = []
        for chain in chain_lines(segments):
            for part in join_parts(clip_polyline(chain, *self.box())):
                part = simplify(part, WIDE_SIMPLIFY)
                if math.dist(part[0], part[-1]) >= 2.0 or len(part) > 2:
                    out.append(part)
        return out

    def blocks(self):
        """The city blocks of the wide part (East Vancouver's from the City of Vancouver, Burnaby's legal blocks), each
        as the smallest turned rectangle round it: (centre x, centre z, width, depth, turn). Only blocks whose middle
        lies right of the town's corner, on plain land or shore, and that cover at least two squares."""
        f = self.frame
        rings = [[f.game(*p) for p in ring] for ring in self.town.sources.blocks]
        rings += [[f.game(*p) for p in polygon[0]] for polygon in self.wide.burnaby_blocks]
        out = []
        for ring in rings:
            area = abs(sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(ring, ring[1:] + ring[:1]))) / 2
            if area < 2.0 or area > LARGEST_BLOCK:
                continue
            cx, cz, width, depth, turn = turned_box(ring)
            x, z = math.floor(cx), math.floor(cz)
            if not (self.town.columns <= x < self.columns and 0 <= z < self.rows):
                continue
            if self.squares[z][x] not in (LAND, SHORE):
                continue
            out.append((cx, cz, width, depth, turn))
        out.sort(key=lambda b: (round(b[1], 1), round(b[0], 1)))
        return out

    def city_line(self):
        """The line between the two cities in grid units: the straight edge that closes Vancouver's land ring, along
        Boundary Road from the Fraser to the inlet."""
        shore = self.town.sources.shoreline
        return [self.frame.game(*shore[-1]), self.frame.game(*shore[0])]

    def check_city_line(self):
        """Stops if water lies along the city line inland. The two cities' outlines leave a seam there at most a
        square wide, which the pond rule fills; a wider gap would leave a channel of water between the cities."""
        line = self.city_line()
        length = math.dist(*line)
        wet = [(x, z) for z in range(self.rows) for x in range(self.columns) if self.squares[z][x] == WATER
               and along_line(line, (x + 0.5, z + 0.5))[2] <= 1.5
               and 3.0 < along_line(line, (x + 0.5, z + 0.5))[0] < length - 3.0]
        if wet:
            sys.exit(f"water along the city line at {wet[:5]}")

    def check_zone_areas(self):
        """Stops unless the Burnaby plan areas picked by OBJECTID are the ones meant: their sizes as recorded, zone 2's
        holding two stations, and zone 3's reaching the city line."""
        f = self.frame
        for zone, areas in ZONE_AREAS.items():
            for city, key in areas:
                if city != "burnaby":
                    continue
                polygons = self.wide.plan_areas.get(key)
                if not polygons:
                    sys.exit(f"zone {zone}: no plan area {key} in the data")
                hectares = sum(abs(polygon_area(polygon[0])) for polygon in polygons)
                if abs(hectares - PLAN_AREA_HECTARES[key]) > 0.03 * PLAN_AREA_HECTARES[key]:
                    sys.exit(f"zone {zone}: plan area {key} is {hectares:.1f} ha, not {PLAN_AREA_HECTARES[key]} ha")
                rings = [[f.game(*p) for p in polygon[0]] for polygon in polygons]
                if zone == 2:
                    held = [s for s in self.stations() if any(point_in_ring(s[1][0], s[1][1], r) for r in rings)]
                    if len(held) != 2:
                        sys.exit(f"zone 2: plan area {key} holds {len(held)} stations, not 2")
                if zone == 3:
                    west = min(p for r in rings for p in r)
                    if along_line(self.city_line(), west)[2] > 2.0:
                        sys.exit(f"zone 3: plan area {key} does not reach the city line")

    def zones(self):
        """Each zone's areas as polygons in grid units, cut to the grid and simplified."""
        f = self.frame
        out = {}
        for zone, areas in ZONE_AREAS.items():
            shapes = []
            for city, key in areas:
                if city == "vancouver":
                    polygons = [[self.town.sources.areas[key]]]
                else:
                    polygons = self.wide.plan_areas[key]
                for polygon in polygons:
                    points = clip_polygon([f.game(*p) for p in polygon[0]], *self.box())
                    if zone in ZONE_STRIPS and points:
                        points = self.west_strip(points, ZONE_STRIPS[zone])
                    if len(points) >= 3:
                        shapes.append(simplify(points + points[:1], WIDE_SIMPLIFY)[:-1])
            out[zone] = shapes
        return out

    def west_strip(self, points, width):
        """The part of a polygon within [width] squares east of its westernmost point, east as the real compass has it
        on the turned map."""
        def east(p):
            return p[0] * self.frame.cos - p[1] * self.frame.sin
        limit = min(east(p) for p in points) + width
        out = []
        for i, b in enumerate(points):
            a = points[i - 1]
            if east(b) <= limit:
                if east(a) > limit:
                    t = (limit - east(a)) / (east(b) - east(a))
                    out.append((a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])))
                out.append(b)
            elif east(a) <= limit:
                t = (limit - east(a)) / (east(b) - east(a))
                out.append((a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])))
        return out

    def homes(self):
        """The room in each zone: zone 1's is the town's 내 방; in zones 2 and 3 it is chosen by the rule written at
        ROOM_INSET. Worked out once."""
        if getattr(self, "_homes", None):
            return self._homes
        f = self.frame
        lots = LotIndex([[f.game(*p) for p in polygon[0]] for polygon in self.wide.burnaby_blocks]
                        + [[f.game(*p) for p in ring] for ring in self.town.sources.blocks])
        away = {"last station": self.stations()[-1][1]}
        away.update({name: (x + 0.5, z + 0.5) for name, (x, z) in self.town.spots.items()})
        rail = self.rail()
        streets = self.streets()
        zones = self.zones()
        out = {1: self.town.spots["HOME"]}
        for zone in (2, 3):
            shapes = zones[zone]
            xs = [p[0] for shape in shapes for p in shape]
            zs = [p[1] for shape in shapes for p in shape]
            best = None
            for z in range(max(ROOM_INSET, math.floor(min(zs))), min(self.rows - ROOM_INSET, math.ceil(max(zs)))):
                for x in range(max(ROOM_INSET, math.floor(min(xs))), min(self.columns - ROOM_INSET, math.ceil(max(xs)))):
                    middle = (x + 0.5, z + 0.5)
                    far = math.dist(middle, away[ROOM_AWAY_FROM[zone]])
                    if best is not None and far <= best[0]:
                        continue
                    if not any(point_in_ring(middle[0], middle[1], shape) for shape in shapes):
                        continue
                    if any(self.squares[z + dz][x + dx] != LAND for dx in range(-2, 3) for dz in range(-2, 3)):
                        continue
                    if lots.holds(middle) or along_line(rail, middle)[2] < 4.0:
                        continue
                    if any(along_line(street, middle)[2] < 2.0 for street in streets):
                        continue
                    best = (far, (x, z))
            if best is None:
                sys.exit(f"zone {zone}: no square meets the rule for a room")
            out[zone] = best[1]
        office = away["OFFICE"]
        if math.dist((out[3][0] + 0.5, out[3][1] + 0.5), office) <= math.dist((out[2][0] + 0.5, out[2][1] + 0.5), office):
            sys.exit("zone 3's room is not farther from 회사 than zone 2's")
        # The rule's road test, said again as a check: no room's middle on a lot.
        for zone, (x, z) in out.items():
            if zone != 1 and lots.holds((x + 0.5, z + 0.5)):
                sys.exit(f"zone {zone}: the room at {(x, z)} is on a lot")
        self._homes = out
        return out

    def walks(self):
        """Walks on bare ground from the five places and between the rooms and the stations, in units."""
        walker = Walker(self.squares, self.columns, self.rows)
        homes = self.homes()
        stations = self.stations()
        ends = {f"내 방 {zone}구역": cell for zone, cell in homes.items()}
        ends.update({PLACE_LABELS[name]: self.town.spots[name] for name in ("OFFICE", "COWORKING", "LIBRARY", "CAFE")})
        for index, (_, near, _) in enumerate(stations, start=1):
            ends[f"역 {index}"] = (math.floor(near[0]), math.floor(near[1]))
        # The big station of zone 2: the one inside it nearest its room.
        zone_two = [i for i, (_, near, _) in enumerate(stations, start=1)
                    if any(point_in_ring(near[0], near[1], shape) for shape in self.zones()[2])]
        big = min(zone_two, key=lambda i: math.dist(stations[i - 1][1], homes[2]))
        pairs = [("내 방 2구역", "회사"), ("내 방 3구역", "회사"), ("내 방 2구역", "코워킹"), ("내 방 1구역", "내 방 2구역"),
                 ("내 방 2구역", "내 방 3구역"), ("회사", f"역 {big}"), ("역 1", f"역 {big}"), ("내 방 2구역", f"역 {big}"),
                 ("내 방 3구역", f"역 {big}")]
        out = {}
        for a, b in pairs:
            out[(a, b)] = walker.route_length(free_near(self.squares, ends[a]), free_near(self.squares, ends[b]))
        return out


class LotIndex:
    """City blocks (lots between roads) in buckets of 10 x 10 squares, to ask quickly whether a point lies on one."""

    def __init__(self, rings):
        self.rings = rings
        self.buckets = {}
        for index, ring in enumerate(rings):
            xs = [p[0] for p in ring]
            zs = [p[1] for p in ring]
            for bx in range(math.floor(min(xs)) // 10, math.floor(max(xs)) // 10 + 1):
                for bz in range(math.floor(min(zs)) // 10, math.floor(max(zs)) // 10 + 1):
                    self.buckets.setdefault((bx, bz), []).append(index)

    def holds(self, point):
        bucket = (math.floor(point[0]) // 10, math.floor(point[1]) // 10)
        return any(point_in_ring(point[0], point[1], self.rings[i]) for i in self.buckets.get(bucket, []))


def join_parts(parts):
    """Pieces of a cut line joined again where one ends where the next starts: the cut can end a piece a rounding
    error away from where the next piece starts."""
    out = []
    for part in parts:
        if out and math.dist(out[-1][-1], part[0]) < 1e-6:
            out[-1] = out[-1] + part[1:]
        else:
            out.append(list(part))
    return out


def free_near(squares, cell):
    """[cell], or the nearest square beside it that is not water."""
    x, z = cell
    for ring in range(0, 5):
        for dz in range(-ring, ring + 1):
            for dx in range(-ring, ring + 1):
                nx, nz = x + dx, z + dz
                if 0 <= nz < len(squares) and 0 <= nx < len(squares[0]) and squares[nz][nx] != WATER:
                    return (nx, nz)
    return cell


# Writing the Kotlin file ---------------------------------------------------------------------------------------------

def fmt(v):
    text = f"{v:.1f}"
    return "0.0" if text == "-0.0" else text


def points_text(points):
    return " ".join(f"{fmt(x)},{fmt(z)}" for x, z in points)


def wrap_points(points, indent, width=112):
    """A long points string split into Kotlin string pieces joined with +."""
    text = points_text(points)
    room = width - indent - 4
    pieces = []
    while len(text) > room:
        cut = text.rfind(" ", 0, room)
        pieces.append(text[:cut + 1])
        text = text[cut + 1:]
    pieces.append(text)
    pad = " " * indent
    return (" +\n" + pad).join(f'"{p}"' for p in pieces)


VLQ_DIGITS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"


def vlq(n):
    """A whole number as a base-64 VLQ, as source maps write them: the sign in the lowest bit, then five bits to a
    character, lowest first, the sixth bit set while more follow."""
    value = (-n << 1) | 1 if n < 0 else n << 1
    out = []
    while True:
        digit = value & 31
        value >>= 5
        out.append(VLQ_DIGITS[digit | (32 if value else 0)])
        if not value:
            return "".join(out)


def packed(items):
    """Items of whole numbers, all of one length, packed as the game's unpackNumbers reads them: each number as the
    change from the same number of the item before (the first item's from 0), as a base-64 VLQ."""
    out, before = [], None
    for item in items:
        before = before or [0] * len(item)
        out.extend(vlq(v - b) for v, b in zip(item, before))
        before = item
    return "".join(out)


def tenths(v):
    """[v] in tenths of a square, exactly as [fmt] writes it, so the game reads back the same number."""
    return int(fmt(v).replace(".", ""))


def packed_points(points):
    """A line's points packed, x and z in tenths of a square, as the game's unpackPoints reads them."""
    return packed([(tenths(x), tenths(z)) for x, z in points])


def wrap_packed(text, indent, width=112):
    """A long packed string split into Kotlin string pieces joined with +."""
    room = width - indent - 4
    pieces = [text[i:i + room] for i in range(0, len(text), room)] or [""]
    pad = " " * indent
    return (" +\n" + pad).join(f'"{p}"' for p in pieces)


def wrap_rects(rects, indent, width=112):
    """Rectangles as "x,z,w,d" groups in Kotlin string pieces joined with +."""
    text = " ".join(f"{x},{z},{w},{d}" for x, z, w, d in rects)
    room = width - indent - 4
    pieces = []
    while len(text) > room:
        cut = text.rfind(" ", 0, room)
        pieces.append(text[:cut + 1])
        text = text[cut + 1:]
    pieces.append(text)
    pad = " " * indent
    return (" +\n" + pad).join(f'"{p}"' for p in pieces)


def write_kotlin(built, sources, fetched, hashes):
    shore_parts, park, rail, mountains = built.lines()
    f = built.frame
    out = []
    out.append("// Generated by tools/vancouver_map/build_map.py from City of Vancouver open data. "
               "Do not edit by hand:")
    out.append("// change the script and run it again (tools/vancouver_map/README.md says how).")
    out.append("//")
    out.append("// Contains information licensed under the Open Government Licence – Vancouver.")
    out.append("// https://opendata.vancouver.ca/pages/licence/")
    out.append("// This data is licensed under the Open Government Licence – Vancouver 1.0, not under the MIT licence")
    out.append("// of the code: see Map data in the README, and MAP-DATA-NOTICE.txt beside the page.")
    out.append("//")
    out.append(f"// Fetched {fetched} from https://opendata.vancouver.ca:")
    for name, title, dataset in DATASETS:
        out.append(f"//   {title} ({dataset}), sha1 {hashes[name]}")
    out.append("package vancouvermonth.web")
    out.append("")
    out.append("/**")
    out.append(" * Real Vancouver as a grid of squares: the park at the peninsula's tip, downtown, False Creek "
               "and a strip of its")
    out.append(" * south shore. Turned so the downtown street grid runs along x and z (real north points up "
               "and to the left),")
    out.append(" * and shrunk so the farthest walk between two of the five places takes about 25 seconds on "
               "bare ground (along")
    out.append(" * the streets between the blocks, a little more).")
    out.append(" * Points are \"x,z\" pairs in grid units, with x east along the columns and z south down the rows.")
    out.append(" */")
    out.append("internal object VancouverMapData {")
    out.append(f"    const val COLUMNS = {built.columns}")
    out.append(f"    const val ROWS = {built.rows}")
    out.append("")
    out.append("    /** How many metres of the real city one grid square stands for. */")
    out.append(f"    const val METRES_PER_UNIT = {fmt(f.scale)}")
    out.append("")
    out.append("    /** How far the map is turned from real north, counter-clockwise, in degrees. */")
    out.append(f"    const val TURN_DEGREES = {fmt(f.turn)}")
    out.append("")
    out.append("    /**")
    out.append("     * One string per row, north to south, west to east, as runs: a square's symbol, then how many of it come")
    out.append("     * in a row, the count left out when it is one (\"~3.2s\" is \"~~~..s\"): ~ water, . land, p park, s shore")
    out.append("     * (land beside the water, where the seawall runs).")
    out.append("     */")
    out.append("    val rows: List<String> = listOf(")
    for row in built.squares:
        out.append(f'        "{runs_text("".join(row))}",')
    out.append("    )")
    out.append("")
    out.append("    /**")
    out.append("     * The real shoreline, simplified, where it crosses the grid: one string of points per stretch, packed as")
    out.append("     * [unpackPoints] reads them.")
    out.append("     */")
    out.append("    val shoreLines: List<String> = listOf(")
    for part in shore_parts:
        out.append("        " + wrap_packed(packed_points(part), 8) + ",")
    out.append("    )")
    out.append("")
    out.append("    /** The big park's outline, simplified, packed as [unpackPoints] reads it. */")
    out.append("    val PARK =")
    out.append("        " + wrap_packed(packed_points(park), 8))
    out.append("")
    out.append("    /** The rail on its guideway: from where it leaves its tunnel, east along False Creek. */")
    out.append("    val RAIL = " + wrap_points(rail, 8))
    out.append("")
    out.append("    /**")
    out.append("     * A line out along the middle of Burrard Inlet (the city's boundary there): "
               "the north shore and its mountains")
    out.append("     * stand past it, to the north.")
    out.append("     */")
    out.append("    val MOUNTAINS = " + wrap_points(mountains, 8))
    out.append("")
    built_blocks, greens = built.city_blocks()
    out.append("    /**")
    out.append("     * The real city blocks, simplified to rectangles of plain land with a street between any two: "
               "\"x,z,width,depth\"")
    out.append("     * in squares, west and north sides first. Buildings stand on these.")
    out.append("     */")
    out.append("    val BLOCKS = " + wrap_rects(built_blocks, 8))
    out.append("")
    out.append("    /** The city blocks that are small parks, as [BLOCKS]: grass and a few trees. */")
    out.append("    val GREENS = " + wrap_rects(greens, 8))
    out.append("")
    out.append("    /** The square proposed for each of the five places; each stands within a few squares of it. */")
    out.append("    val spots: Map<Place, Cell> = mapOf(")
    for name, _, _ in SPOTS:
        x, z = built.spots[name]
        out.append(f"        Place.{name} to Cell({x}, {z}),")
    out.append("    )")
    out.append("}")
    text = "\n".join(out) + "\n"
    if chr(0x2014) in text:
        sys.exit("an em dash slipped into the Kotlin file")
    with open(KOTLIN_OUT, "w", encoding="utf-8") as fh:
        fh.write(text)


def runs_text(row):
    """A row as runs: each square's symbol followed by how many of it come in a row, the count left out when it is
    one. "~~~..s" is "~3.2s"."""
    out, x = [], 0
    while x < len(row):
        end = x
        while end < len(row) and row[end] == row[x]:
            end += 1
        out.append(row[x] + (str(end - x) if end - x > 1 else ""))
        x = end
    return "".join(out)


def unpack_runs(text):
    """Runs spelt out square by square, as the game's unpackRuns does: "~3.2s" is "~~~..s"."""
    out, i = [], 0
    while i < len(text):
        symbol = text[i]
        i += 1
        digits = ""
        while i < len(text) and text[i].isdigit():
            digits += text[i]
            i += 1
        out.append(symbol * (int(digits) if digits else 1))
    return "".join(out)


def wrap_list(items, indent, width=112):
    """Short items joined by spaces into Kotlin strings of about [width] characters, one per line of a listOf."""
    lines, current = [], ""
    room = width - indent - 4
    for item in items:
        if current and len(current) + 1 + len(item) > room:
            lines.append(current)
            current = item
        else:
            current = f"{current} {item}" if current else item
    if current:
        lines.append(current)
    return lines


def with_stops(line, stops):
    """[line] with each of [stops] (points on it) as a point of its own, and where each stop now is: a stop within a
    fifth of a square of a point of the line takes that point, so no leg is left a hair long; any other goes in where
    it lies along the line. Returns the line and, for each stop in order, its point and how far along the line it is."""
    out = list(line)
    placed = []
    for stop in stops:
        closest = min(range(len(out)), key=lambda i: math.dist(out[i], stop))
        if math.dist(out[closest], stop) <= 0.2:
            placed.append(out[closest])
            continue
        index = min(range(len(out) - 1), key=lambda i: along_line(out[i:i + 2], stop)[2])
        out.insert(index + 1, stop)
        placed.append(stop)
    lengths = [0.0]
    for a, b in zip(out, out[1:]):
        lengths.append(lengths[-1] + math.dist(a, b))
    return out, [(p, lengths[out.index(p)]) for p in placed]


def write_wide_kotlin(wide, fetched, van_hashes, bby_hashes):
    f = wide.frame
    stations = wide.stations()
    # Each station is a point of the line itself, so it stands exactly on the rail. The points are rounded as they
    # are written, so the line and the stations agree to the digit.
    rounded = [(round(x, 1), round(z, 1)) for x, z in wide.rail()]
    rail, stops = with_stops(rounded, [(round(p[0], 1), round(p[1], 1)) for _, p, _ in stations])
    streets = wide.streets()
    blocks = wide.blocks()
    zones = wide.zones()
    homes = wide.homes()
    out = []
    out.append("// Generated by tools/vancouver_map/build_map.py from City of Vancouver and City of Burnaby open data. Do not")
    out.append("// edit by hand: change the script and run it again (tools/vancouver_map/README.md says how).")
    out.append("//")
    out.append("// Contains information licensed under the Open Government Licence – Vancouver.")
    out.append("// https://opendata.vancouver.ca/pages/licence/")
    out.append("// Contains information licensed under the Open Government Licence – British Columbia.")
    out.append("// (the default statement of the Open Government Licence – Burnaby, which the City of Burnaby's data is under;")
    out.append("// credit: City of Burnaby) https://data.burnaby.ca/pages/open-government-licence")
    out.append("// This data is licensed under those two licences, not under the MIT licence of the code: see Map data in the")
    out.append("// README, and MAP-DATA-NOTICE.txt beside the page.")
    out.append("//")
    out.append(f"// Fetched {fetched} from https://opendata.vancouver.ca:")
    for name, title, dataset in WIDE_VANCOUVER_DATASETS:
        out.append(f"//   {title} ({dataset}), sha1 {van_hashes[name]}")
    out.append("// and from https://data.burnaby.ca (https://gis.burnaby.ca/arcgis/rest/services/OpenData):")
    for stem, title, item, layer in BURNABY_DATASETS:
        pages = bby_hashes[stem].split("+")
        if len(pages) == 1:
            out.append(f"//   {title} ({layer}), sha1 {pages[0]}")
        else:
            out.append(f"//   {title} ({layer}), in {len(pages)} pages, sha1 {' and '.join(pages[:1])}")
            for page in pages[1:]:
                out.append(f"//     and {page}")
    out.append("package vancouvermonth.web")
    out.append("")
    out.append("/**")
    out.append(" * The wide map: real Vancouver from the big park at the peninsula's tip east through East Vancouver into")
    out.append(" * south-west Burnaby, to past the Burnaby town centre and its big park. The same turn and scale as")
    out.append(" * [VancouverMapData]: the town's map is this map's top-left corner, square for square, so every square of the")
    out.append(" * town has the same column and row in both. Real north points up and to the left, so the map's right is real")
    out.append(" * south-east, the way the Expo Line runs. Points are \"x,z\" pairs in grid units, as in [VancouverMapData].")
    out.append(" */")
    out.append("internal object WideMapData {")
    out.append(f"    const val COLUMNS = {wide.columns}")
    out.append(f"    const val ROWS = {wide.rows}")
    out.append("")
    out.append("    /** How many metres of the real city one grid square stands for, as in the town's map. */")
    out.append(f"    const val METRES_PER_UNIT = {fmt(f.scale)}")
    out.append("")
    out.append("    /** How far the map is turned from real north, counter-clockwise, in degrees, as in the town's map. */")
    out.append(f"    const val TURN_DEGREES = {fmt(f.turn)}")
    out.append("")
    out.append("    /** The town's map is columns 0 until this of every row: those squares are the town's, one for one. */")
    out.append(f"    const val TOWN_COLUMNS = {wide.town.columns}")
    out.append("")
    out.append("    /**")
    out.append("     * One string per row, north to south, as runs: a square's symbol, as in [VancouverMapData.rows], then how")
    out.append("     * many of it come in a row, the count left out when it is one (\"~3.2s\" is \"~~~..s\").")
    out.append("     * Parks of 2 ha or more right of the town's corner are park squares; smaller ones are plain land.")
    out.append("     */")
    out.append("    val rows: List<String> = listOf(")
    for row in wide.squares:
        out.append(f'        "{runs_text(row)}",')
    out.append("    )")
    out.append("")
    out.append("    /**")
    out.append("     * The Expo Line on its guideway: from the tunnel's mouth by the stadiums (the start of the town's rail), east")
    out.append("     * through East Vancouver into Burnaby, to where it leaves the grid past the last station. Each of")
    out.append("     * [STATIONS] is one of its points.")
    out.append("     */")
    out.append("    val RAIL =")
    out.append("        " + wrap_points(rail, 8))
    out.append("")
    out.append("    /**")
    out.append("     * The real stations on [RAIL], in order from the tunnel's mouth: \"x,z,along\", the point on the line and how")
    out.append("     * far along it that is, in squares. The game gives them made-up names.")
    out.append("     */")
    items = [f"{fmt(p[0])},{fmt(p[1])},{fmt(along)}" for p, along in stops]
    out.append("    val STATIONS =")
    out.append("        " + " +\n        ".join(f'"{line} "' for line in wrap_list(items, 8))[:-2] + '"')
    out.append("")
    out.append("    /**")
    out.append("     * The main streets (arterials), simplified to within half a square: one string of points per street,")
    out.append("     * packed as [unpackPoints] reads them.")
    out.append("     */")
    out.append("    val streets: List<String> = listOf(")
    for street in streets:
        out.append("        " + wrap_packed(packed_points(street), 8) + ",")
    out.append("    )")
    out.append("")
    out.append("    /**")
    out.append("     * The city blocks right of the town's corner (the town's own are [VancouverMapData.BLOCKS]), each the")
    out.append("     * smallest turned rectangle round the real block, as five numbers packed as [unpackNumbers] reads them: its")
    out.append("     * middle's x and z, its size along and across the turn, all in tenths of a square, and the turn in degrees")
    out.append("     * in the same sense as [Block.turn]. Blocks on parks or water, and the few over 25 ha (yards, works, a")
    out.append("     * cemetery), are left out. Rows of them, north to south.")
    out.append("     */")
    items = [(tenths(cx), tenths(cz), tenths(w), tenths(d), round(-turn)) for cx, cz, w, d, turn in blocks]
    out.append("    val blocks =")
    out.append("        " + wrap_packed(packed(items), 8))
    out.append("")
    out.append("    /**")
    out.append("     * Where each transit zone a room can be in lies: 1 downtown and the West End (the town's corner), 2 the")
    out.append("     * Burnaby plan area round the big station, 3 the outer one: a strip of the Burnaby plan area on the slope")
    out.append("     * south of the big park, along the city line down toward the river. Each zone is one or more areas, each")
    out.append("     * a string of points round its edge, cut to the grid.")
    out.append("     */")
    out.append("    val zones: Map<Int, List<String>> = mapOf(")
    for zone, shapes in zones.items():
        out.append(f"        {zone} to listOf(")
        for shape in shapes:
            out.append("            " + wrap_points(shape, 12) + ",")
        out.append("        ),")
    out.append("    )")
    out.append("")
    out.append("    /**")
    out.append("     * The square of 내 방 in each zone: zone 1's is the town's own; in zones 2 and 3, the square the build's")
    out.append("     * written rule picks, on a road between blocks (never on a lot), off the main streets.")
    out.append("     */")
    out.append("    val homes: Map<Int, Cell> = mapOf(")
    for zone, (x, z) in homes.items():
        out.append(f"        {zone} to Cell({x}, {z}),")
    out.append("    )")
    out.append("}")
    text = "\n".join(out) + "\n"
    if chr(0x2014) in text:
        sys.exit("an em dash slipped into the Kotlin file")
    with open(WIDE_OUT, "w", encoding="utf-8") as fh:
        fh.write(text)
    return text


# Pictures ------------------------------------------------------------------------------------------------------------

COLOURS = {WATER: "#6FB3D9", LAND: "#F3ECD8", PARK: "#5E9A5A", SHORE: "#C9B48A"}
SHORE_EDGE = "#BFB39A"


def svg_squares(built, px, colours):
    """The grid as rectangles, one per run of same squares in a row."""
    parts = []
    for z, row in enumerate(built.squares):
        x = 0
        while x < built.columns:
            kind = row[x]
            end = x
            while end < built.columns and row[end] == kind:
                end += 1
            colour = colours.get(kind)
            if colour:
                parts.append(f'<rect x="{x * px:.1f}" y="{z * px:.1f}" width="{(end - x) * px + 0.3:.1f}" '
                             f'height="{px + 0.3:.1f}" fill="{colour}"/>')
            x = end
    return "".join(parts)


def svg_path(points, px, close=False):
    if not points:
        return ""
    d = "M" + " L".join(f"{x * px:.1f},{z * px:.1f}" for x, z in points)
    return d + (" Z" if close else "")


def walk_seconds(length):
    return length / WALK_SPEED


def write_previews(built, walks, out_dir, png):
    os.makedirs(out_dir, exist_ok=True)
    shore_parts, park, rail, mountains = built.lines()
    px = 5.0
    width = built.columns * px
    pad = 40.0
    band = 150.0
    map_top = pad + 70.0 + band
    names = [s[0] for s in SPOTS]

    def header(title, subtitle, total_height):
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width + 2 * pad:.0f}" height="{total_height:.0f}" '
                f'viewBox="0 0 {width + 2 * pad:.0f} {total_height:.0f}" '
                f'font-family="Apple SD Gothic Neo, Noto Sans KR, sans-serif">'
                f'<rect width="100%" height="100%" fill="#FBF7EE"/>'
                f'<text x="{pad}" y="{pad + 22}" font-size="30" font-weight="700" fill="#2B2B2B">{title}</text>'
                f'<text x="{pad}" y="{pad + 56}" font-size="20" fill="#555">{subtitle}</text>')

    def mountains_band():
        # The mountains, drawn as a band past the line in the inlet, kept to the strip above the grid.
        pts = list(mountains)
        far = -10 * band / px
        ring = [(-band / px, pts[0][1])] + pts + [(pts[-1][0] + band / px, pts[-1][1]),
                                                  (pts[-1][0] + band / px, far), (-band / px, far)]
        return (f'<g clip-path="url(#above)"><path d="{svg_path(ring, px, close=True)}" fill="#4D7A5E"/>'
                f'<path d="{svg_path(pts, px)}" fill="none" stroke="#F7F9FB" stroke-width="3"/></g>')

    clip = (f'<defs><clipPath id="above"><rect x="0" y="{-band:.0f}" width="{width:.0f}" '
            f'height="{band:.0f}"/></clipPath>'
            f'<clipPath id="all"><rect x="0" y="{-band:.0f}" width="{width:.0f}" '
            f'height="{band + built.rows * px:.0f}"/></clipPath>'
            f'<clipPath id="grid"><rect x="0" y="0" width="{width:.0f}" '
            f'height="{built.rows * px:.0f}"/></clipPath></defs>')

    def spot_marks(size):
        marks = []
        for i, name in enumerate(names, start=1):
            x, z = built.spots[name]
            cx, cz = (x + 0.5) * px, (z + 0.5) * px
            marks.append(f'<circle cx="{cx:.1f}" cy="{cz:.1f}" r="{size}" fill="#C0584A" stroke="#FFF" '
                         f'stroke-width="3"/>'
                         f'<text x="{cx:.1f}" y="{cz + size * 0.38:.1f}" font-size="{size * 1.15:.0f}" '
                         f'font-weight="700" fill="#FFF" text-anchor="middle">{i}</text>'
                         f'<text x="{cx + size + 6:.1f}" y="{cz - size * 0.2:.1f}" font-size="24" font-weight="700" '
                         f'fill="#2B2B2B" stroke="#FBF7EE" stroke-width="5" paint-order="stroke">'
                         f'{PLACE_LABELS[name]}</text>')
        return "".join(marks)

    def rail_line():
        d = svg_path(rail, px)
        return (f'<path d="{d}" fill="none" stroke="#FFFFFF" stroke-width="9" stroke-linecap="round"/>'
                f'<path d="{d}" fill="none" stroke="#3C6CB4" stroke-width="5" stroke-linecap="round" '
                f'stroke-dasharray="10 6"/>')

    def scale_bar(y):
        units = 20
        metres = units * built.frame.scale
        return (f'<g transform="translate({pad},{y})"><rect width="{units * px}" height="10" fill="#2B2B2B"/>'
                f'<text x="{units * px + 14}" y="11" font-size="20" fill="#2B2B2B">'
                f'{units}칸 = {metres:.0f} m = 걸어서 {units / WALK_SPEED:.1f}초</text></g>')

    # 1. The map as the game would walk it.
    legend_top = map_top + built.rows * px + 30
    rows_text = []
    pairs = sorted(walks.items(), key=lambda kv: -kv[1])
    for (a, b), length in pairs:
        rows_text.append(f'{PLACE_LABELS[a]} ↔ {PLACE_LABELS[b]}: {length:.0f}칸, {walk_seconds(length):.1f}초')
    total = legend_top + 60 + 34 * 3 + 34 + 30 * len(names) + 16 + 30 * ((len(rows_text) + 1) // 2) + 60
    svg = [header("실제 밴쿠버 윤곽 미리보기 (20a)",
                  f"격자 {built.columns} x {built.rows}칸, 1칸 = {built.frame.scale:.1f} m, "
                  f"지도를 {built.frame.turn:.1f}도 돌려 다운타운 길이 가로세로로", total)]
    svg.append(f'<g transform="translate({pad},{map_top})">{clip}')
    svg.append(f'<rect x="0" y="{-band}" width="{width}" height="{band}" fill="{COLOURS[WATER]}"/>')
    svg.append(mountains_band())
    svg.append(svg_squares(built, px, COLOURS))
    built_blocks, greens = built.city_blocks()
    for (x, z, w, d), colour in [(r, "#B7AE9C") for r in built_blocks] + [(r, "#8DBF6A") for r in greens]:
        svg.append(f'<rect x="{x * px:.1f}" y="{z * px:.1f}" width="{w * px:.1f}" height="{d * px:.1f}" '
                   f'fill="{colour}"/>')
    for part in shore_parts:
        svg.append(f'<path d="{svg_path(part, px)}" fill="none" stroke="{SHORE_EDGE}" stroke-width="1.2" '
                   f'clip-path="url(#grid)"/>')
    svg.append(rail_line())
    svg.append(spot_marks(15))
    svg.append(f'<rect x="0" y="0" width="{width}" height="{built.rows * px}" fill="none" stroke="#2B2B2B" '
               f'stroke-width="1.5"/>')
    svg.append("</g>")
    y = legend_top
    keys = [(COLOURS[WATER], "물"), (COLOURS[LAND], "길"), ("#B7AE9C", "블록"), ("#8DBF6A", "작은 공원"),
            (COLOURS[PARK], "큰 공원"),
            (COLOURS[SHORE], "물가(방파제 길)"),
            ("#4D7A5E", "건너편 산 (배경)")]
    x = pad
    for colour, label in keys:
        svg.append(f'<rect x="{x}" y="{y}" width="26" height="26" fill="{colour}" stroke="#999"/>'
                   f'<text x="{x + 34}" y="{y + 21}" font-size="21" fill="#2B2B2B">{label}</text>')
        x += 34 + 22 * len(label) + 30
    y += 46
    svg.append(f'<path d="M{pad},{y + 10} h60" stroke="#FFFFFF" stroke-width="9"/>'
               f'<path d="M{pad},{y + 10} h60" stroke="#3C6CB4" stroke-width="5" stroke-dasharray="10 6"/>'
               f'<text x="{pad + 74}" y="{y + 18}" font-size="21" fill="#2B2B2B">'
               f'스카이트레인 고가 선로 (폴스크릭 따라)</text>')
    y += 40
    svg.append(scale_bar(y))
    y += 50
    svg.append(f'<text x="{pad}" y="{y}" font-size="22" font-weight="700" fill="#2B2B2B">'
               f'제안한 다섯 자리</text>')
    y += 34
    for i, name in enumerate(names, start=1):
        svg.append(f'<text x="{pad}" y="{y}" font-size="20" fill="#2B2B2B">{i}. {PLACE_LABELS[name]}: '
                   f'{PLACE_NOTES[name]} (칸 {built.spots[name][0]}, {built.spots[name][1]})</text>')
        y += 30
    y += 16
    svg.append(f'<text x="{pad}" y="{y}" font-size="22" font-weight="700" fill="#2B2B2B">'
               f'걷는 시간 (초속 {WALK_SPEED}칸, 지금 길찾기 그대로)</text>')
    y += 34
    half = (len(rows_text) + 1) // 2
    for i, line in enumerate(rows_text):
        column, row = divmod(i, half)
        svg.append(f'<text x="{pad + column * (width / 2)}" y="{y + row * 30}" font-size="20" '
                   f'fill="#2B2B2B">{line}</text>')
    svg.append("</svg>")
    first = os.path.join(out_dir, "01-map-preview.svg")
    with open(first, "w", encoding="utf-8") as fh:
        fh.write("".join(svg))

    # 2. The same outline over a faint trace of the real city.
    f = built.frame
    total2 = map_top + built.rows * px + 140
    svg = [header("실제 윤곽 비교",
                  "옅은 회색: 실제 블록과 해안선(시 오픈데이터), 빨간 선: 게임 격자의 물과 땅 경계", total2)]
    svg.append(f'<g transform="translate({pad},{map_top})">{clip}<g clip-path="url(#all)">')
    svg.append(f'<rect x="0" y="{-band}" width="{width}" height="{band + built.rows * px}" fill="#EAF3F8"/>')
    faint = {WATER: None, LAND: "#F6F1E4", PARK: "#DDEBD8", SHORE: "#F6F1E4"}
    svg.append(svg_squares(built, px, faint))
    for block in sources_blocks(built):
        svg.append(f'<path d="{svg_path(block, px, close=True)}" fill="#E4DED0" stroke="#C9C1AE" stroke-width="0.6"/>')
    for name, ring in built.sources.areas.items():
        pts = [f.game(*p) for p in ring]
        svg.append(f'<path d="{svg_path(pts, px, close=True)}" fill="none" stroke="#B9B3A6" stroke-width="1" '
                   f'stroke-dasharray="4 4"/>')
    park_raw = [f.game(*p) for p in built.sources.park_rings[0]]
    svg.append(f'<path d="{svg_path(park_raw, px, close=True)}" fill="none" stroke="#7FA07A" stroke-width="1.5"/>')
    shore_raw = [f.game(*p) for p in built.sources.shoreline]
    for part in clip_polyline(shore_raw, -20, -20 - band / px, built.columns + 20, built.rows + 20):
        svg.append(f'<path d="{svg_path(part, px)}" fill="none" stroke="#6E6E6E" stroke-width="1.4"/>')
    svg.append(f'<path d="{svg_path(rail, px)}" fill="none" stroke="#3C6CB4" stroke-width="3" stroke-dasharray="8 5"/>')
    svg.append(f'<path d="{svg_path(mountains, px)}" fill="none" stroke="#4D7A5E" stroke-width="2" '
               f'stroke-dasharray="2 5"/>')
    svg.append(game_outline(built, px))
    for name, ring in built.sources.areas.items():
        pts = [f.game(*p) for p in ring]
        cx = sum(p[0] for p in pts) / len(pts)
        cz = sum(p[1] for p in pts) / len(pts)
        if 0 < cx < built.columns and 0 < cz < built.rows:
            svg.append(f'<text x="{cx * px:.0f}" y="{cz * px + 40:.0f}" font-size="18" fill="#8A8478" '
                       f'text-anchor="middle" '
                       f'font-style="italic">{name}</text>')
    svg.append(spot_marks(11))
    svg.append("</g>")
    svg.append(f'<rect x="0" y="0" width="{width}" height="{built.rows * px}" fill="none" stroke="#2B2B2B" '
               f'stroke-width="1.5"/></g>')
    footer = [
        "Contains information licensed under the Open Government Licence – Vancouver.",
        "Shoreline 2002, Block outlines, Parks - polygon representation, Local area boundary, Rapid transit lines, "
        "City boundary.",
        "동네 이름(회색 기울임)은 비교용일 뿐, 게임에는 띄우지 않는다. 회색 점선: 동네 경계, 녹색 점선: 산이 시작되는 선.",
    ]
    for i, line in enumerate(footer):
        svg.append(f'<text x="{pad}" y="{map_top + built.rows * px + 36 + i * 28}" font-size="18" fill="#555">'
                   f'{line}</text>')
    svg.append("</svg>")
    second = os.path.join(out_dir, "02-outline-compare.svg")
    with open(second, "w", encoding="utf-8") as fh:
        fh.write("".join(svg))

    if png:
        for path, height in ((first, total), (second, total2)):
            to_png(path, int(width + 2 * pad), int(height))


def sources_blocks(built):
    f = built.frame
    return [[f.game(*p) for p in block] for block in built.sources.blocks]


def game_outline(built, px):
    """The edges between water and walkable squares, as one path."""
    segments = []
    sq = built.squares
    for z in range(built.rows):
        for x in range(built.columns):
            here = sq[z][x] != WATER
            if x + 1 < built.columns and (sq[z][x + 1] != WATER) != here:
                segments.append(f"M{(x + 1) * px:.0f},{z * px:.0f} v{px:.0f}")
            if z + 1 < built.rows and (sq[z + 1][x] != WATER) != here:
                segments.append(f"M{x * px:.0f},{(z + 1) * px:.0f} h{px:.0f}")
    return f'<path d="{" ".join(segments)}" fill="none" stroke="#D2463C" stroke-width="2"/>'


def to_png(svg_path_name, width, height):
    chrome = shutil.which("google-chrome") or shutil.which("chromium")
    mac = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    if not chrome and os.path.exists(mac):
        chrome = mac
    if not chrome:
        print("no Chrome found: the SVG files are written, the PNG files are not")
        return
    out = svg_path_name[:-4] + ".png"
    subprocess.run([chrome, "--headless=new", "--disable-gpu", "--hide-scrollbars", f"--window-size={width},{height}",
                    f"--screenshot={out}", "file://" + svg_path_name], check=True, capture_output=True)
    print("wrote", out)


# Main ----------------------------------------------------------------------------------------------------------------

def town_rows_committed():
    """The rows of the town's map as VancouverMapData.kt has them now."""
    rows = []
    with open(KOTLIN_OUT, encoding="utf-8") as fh:
        inside = False
        for line in fh:
            if line.startswith("    val rows: List<String> = listOf("):
                inside = True
            elif inside and line.startswith("    )"):
                break
            elif inside:
                rows.append(unpack_runs(line.strip().rstrip(",").strip('"')))
    return rows


ZONE_COLOURS = {1: "#7B5EA7", 2: "#E07B39", 3: "#C0392B"}
STATION_BLUE = "#3C6CB4"


def write_wide_previews(wide, walks, out_dir, png):
    """Two pictures of the wide map: the grid as the game would walk it, and its edge over a faint trace of the real
    cities. Each has the whole grid on top and the Burnaby end, larger, below."""
    os.makedirs(out_dir, exist_ok=True)
    f = wide.frame
    rail = wide.rail()
    stations = wide.stations()
    streets = wide.streets()
    blocks = wide.blocks()
    zones = wide.zones()
    homes = wide.homes()
    town_blocks, town_greens = wide.town.city_blocks()
    pad = 40.0
    big_px = 2.0
    zoom_x0, zoom_px = 470, 6.0
    width = wide.columns * big_px
    zoom_width = (wide.columns - zoom_x0) * zoom_px

    def header(title, lines, total_width, total_height):
        text = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{total_width:.0f}" height="{total_height:.0f}" '
                f'viewBox="0 0 {total_width:.0f} {total_height:.0f}" '
                f'font-family="Apple SD Gothic Neo, Noto Sans KR, sans-serif">'
                f'<rect width="100%" height="100%" fill="#FBF7EE"/>'
                f'<text x="{pad}" y="{pad + 26}" font-size="34" font-weight="700" fill="#2B2B2B">{title}</text>')
        for i, line in enumerate(lines):
            text += f'<text x="{pad}" y="{pad + 64 + i * 30}" font-size="22" fill="#555">{line}</text>'
        return text

    def path(points, px, x0, close=False):
        if not points:
            return ""
        d = "M" + " L".join(f"{(x - x0) * px:.1f},{z * px:.1f}" for x, z in points)
        return d + (" Z" if close else "")

    def squares_layer(px, x0, colours):
        parts = []
        for z, row in enumerate(wide.squares):
            x = x0
            while x < wide.columns:
                kind = row[x]
                end = x
                while end < wide.columns and row[end] == kind:
                    end += 1
                colour = colours.get(kind)
                if colour:
                    parts.append(f'<rect x="{(x - x0) * px:.1f}" y="{z * px:.1f}" width="{(end - x) * px + 0.3:.1f}" '
                                 f'height="{px + 0.3:.1f}" fill="{colour}"/>')
                x = end
        return "".join(parts)

    def blocks_layer(px, x0, colour):
        parts = []
        for x, z, w, d in town_blocks:
            if x + w > x0:
                parts.append(f'<rect x="{(x - x0) * px:.1f}" y="{z * px:.1f}" width="{w * px:.1f}" '
                             f'height="{d * px:.1f}" fill="{colour}"/>')
        for cx, cz, w, d, turn in blocks:
            if cx + w < x0:
                continue
            parts.append(f'<rect x="{-w / 2 * px:.1f}" y="{-d / 2 * px:.1f}" width="{w * px:.1f}" height="{d * px:.1f}" '
                         f'fill="{colour}" transform="translate({(cx - x0) * px:.1f},{cz * px:.1f}) '
                         f'rotate({turn:.1f})"/>')
        return "".join(parts)

    def streets_layer(px, x0, stroke, wide_stroke):
        return "".join(f'<path d="{path(s, px, x0)}" fill="none" stroke="{stroke}" stroke-width="{wide_stroke}" '
                       f'stroke-linecap="round" stroke-linejoin="round"/>' for s in streets)

    def zones_layer(px, x0, labels):
        parts = []
        for zone, shapes in zones.items():
            colour = ZONE_COLOURS[zone]
            for shape in shapes:
                parts.append(f'<path d="{path(shape, px, x0, close=True)}" fill="{colour}" fill-opacity="0.16" '
                             f'stroke="{colour}" stroke-width="{max(2.0, px * 0.6):.1f}" stroke-dasharray="8 5"/>')
            # The name just inside the zone's westernmost corner, when that corner is in the picture.
            wx, wz = min(p for shape in shapes for p in shape)
            if labels and wx >= x0:
                parts.append(f'<text x="{(wx - x0) * px + 16:.0f}" y="{min(max(wz * px + 9, 30), wide.rows * px - 10):.0f}" '
                             f'font-size="26" font-weight="700" fill="{colour}" stroke="#FBF7EE" '
                             f'stroke-width="5" paint-order="stroke">{zone}구역</text>')
        return "".join(parts)

    def rail_layer(px, x0, size):
        d = path(rail, px, x0)
        parts = [f'<path d="{d}" fill="none" stroke="#FFFFFF" stroke-width="{size + 4}" stroke-linecap="round"/>',
                 f'<path d="{d}" fill="none" stroke="{STATION_BLUE}" stroke-width="{size}" stroke-linecap="round" '
                 f'stroke-dasharray="{size * 2} {size * 1.2}"/>']
        r = size * 2.2
        for i, (_, (x, z), _) in enumerate(stations, start=1):
            if x < x0:
                continue
            parts.append(f'<circle cx="{(x - x0) * px:.1f}" cy="{z * px:.1f}" r="{r:.1f}" fill="{STATION_BLUE}" '
                         f'stroke="#FFF" stroke-width="2.5"/>'
                         f'<text x="{(x - x0) * px:.1f}" y="{z * px + r * 0.42:.1f}" font-size="{r * 1.2:.0f}" '
                         f'font-weight="700" fill="#FFF" text-anchor="middle">{i}</text>')
        return "".join(parts)

    def places_layer(px, x0, size, labels):
        parts = []
        for i, name in enumerate([s[0] for s in SPOTS], start=1):
            x, z = wide.town.spots[name]
            if x < x0:
                continue
            cx, cz = (x + 0.5 - x0) * px, (z + 0.5) * px
            parts.append(f'<circle cx="{cx:.1f}" cy="{cz:.1f}" r="{size}" fill="#C0584A" stroke="#FFF" '
                         f'stroke-width="2.5"/><text x="{cx:.1f}" y="{cz + size * 0.4:.1f}" font-size="{size * 1.2:.0f}" '
                         f'font-weight="700" fill="#FFF" text-anchor="middle">{i}</text>')
            if labels:
                parts.append(f'<text x="{cx + size + 4:.1f}" y="{cz - size * 0.4:.1f}" font-size="20" '
                             f'font-weight="700" fill="#2B2B2B" stroke="#FBF7EE" stroke-width="4" '
                             f'paint-order="stroke">{PLACE_LABELS[name]}</text>')
        return "".join(parts)

    def homes_layer(px, x0, size):
        parts = []
        for zone, (x, z) in homes.items():
            if x < x0:
                continue
            cx, cz = (x + 0.5 - x0) * px, (z + 0.5) * px
            colour = ZONE_COLOURS[zone]
            parts.append(f'<path d="M{cx:.1f},{cz - size * 1.2:.1f} L{cx + size:.1f},{cz - size * 0.2:.1f} '
                         f'L{cx + size:.1f},{cz + size:.1f} L{cx - size:.1f},{cz + size:.1f} '
                         f'L{cx - size:.1f},{cz - size * 0.2:.1f} Z" fill="{colour}" stroke="#FFF" stroke-width="2.5"/>'
                         f'<text x="{cx:.1f}" y="{cz + size * 0.75:.1f}" font-size="{size * 1.1:.0f}" '
                         f'font-weight="700" fill="#FFF" text-anchor="middle">{zone}</text>')
            if px >= 3:
                # The name on the right of the mark, or on its left near the picture's right edge.
                right = cx < (wide.columns - x0) * px - 160
                parts.append(f'<text x="{cx + size + 5 if right else cx - size - 5:.1f}" y="{cz + size * 0.4:.1f}" '
                             f'font-size="22" font-weight="700" text-anchor="{"start" if right else "end"}" '
                             f'fill="{colour}" stroke="#FBF7EE" stroke-width="5" paint-order="stroke">내 방 {zone}구역'
                             f'</text>')
        return "".join(parts)

    def frame_layer(px, x0, rows):
        parts = [f'<rect x="0" y="0" width="{(wide.columns - x0) * px:.1f}" height="{rows * px:.1f}" fill="none" '
                 f'stroke="#2B2B2B" stroke-width="1.5"/>']
        if x0 < wide.town.columns:
            parts.append(f'<rect x="0" y="0" width="{(wide.town.columns - x0) * px:.1f}" height="{rows * px:.1f}" '
                         f'fill="none" stroke="#2B2B2B" stroke-width="2.5" stroke-dasharray="10 6"/>')
        return "".join(parts)

    def scale_bar(x, y, px, units):
        metres = units * f.scale
        return (f'<g transform="translate({x},{y})"><rect width="{units * px}" height="10" fill="#2B2B2B"/>'
                f'<text x="{units * px + 14}" y="12" font-size="22" fill="#2B2B2B">'
                f'{units}칸 = {metres / 1000:.1f} km = 걸어서 {units / WALK_SPEED:.0f}초</text></g>')

    terrain = {WATER: "#6FB3D9", LAND: "#F3ECD8", PARK: "#5E9A5A", SHORE: "#C9B48A"}

    # 1. The wide map as the game would walk it.
    lines1 = [f"격자 {wide.columns} x {wide.rows}칸, 1칸 = {f.scale:.0f} m. 왼쪽 점선 칸(246 x 155)은 지금 마을 지도와 한 칸도 다르지 않다.",
              "오른쪽이 실제 남동쪽: 엑스포 선을 따라 이스트 밴쿠버를 지나 버나비 큰 공원과 큰 역 둘레까지."]
    top1 = pad + 64 + 30 * len(lines1) + 20
    big_h = wide.rows * big_px
    zoom_top = top1 + big_h + 70
    zoom_h = wide.rows * zoom_px
    legend_top = zoom_top + zoom_h + 40
    total_w = max(width, zoom_width) + 2 * pad
    walk_lines = [f"{a} ↔ {b}: {length:.0f}칸, 걸어서 {walk_seconds(length):.0f}초 (달리면 {length / 8:.0f}초)"
                  for (a, b), length in walks.items()]
    total_h = legend_top + 46 * 3 + 40 + 30 * 3 + 50 + 30 * len(walk_lines) + 60
    svg = [header("버나비까지 넓힌 지도 미리보기 (24a)", lines1, total_w, total_h)]
    for x0, px, top, labels in ((0, big_px, top1, False), (zoom_x0, zoom_px, zoom_top, True)):
        svg.append(f'<g transform="translate({pad},{top})">')
        svg.append(f'<clipPath id="c{x0}"><rect x="0" y="0" width="{(wide.columns - x0) * px:.1f}" '
                   f'height="{wide.rows * px:.1f}"/></clipPath><g clip-path="url(#c{x0})">')
        svg.append(squares_layer(px, x0, terrain))
        svg.append(blocks_layer(px, x0, "#DCD3BF"))
        svg.append(streets_layer(px, x0, "#FFFFFF", max(1.5, px * 0.55)))
        svg.append(zones_layer(px, x0, True))
        svg.append(rail_layer(px, x0, 3.0 if px < 3 else 5.0))
        svg.append(places_layer(px, x0, 9 if px < 3 else 12, labels))
        svg.append(homes_layer(px, x0, 9 if px < 3 else 13))
        svg.append("</g>")
        svg.append(frame_layer(px, x0, wide.rows))
        svg.append("</g>")
        if x0 == 0:
            zx = pad + zoom_x0 * big_px
            svg.append(f'<rect x="{zx:.1f}" y="{top:.1f}" width="{(wide.columns - zoom_x0) * big_px:.1f}" '
                       f'height="{big_h:.1f}" fill="none" stroke="#C0392B" stroke-width="3"/>'
                       f'<text x="{zx:.1f}" y="{top + big_h + 32:.1f}" font-size="22" fill="#C0392B">'
                       f'아래 확대: 버나비 쪽 (빨간 틀)</text>')
    y = legend_top
    keys = [(terrain[WATER], "물"), (terrain[LAND], "땅·길"), ("#DCD3BF", "블록"), (terrain[PARK], "공원"),
            (terrain[SHORE], "물가")]
    x = pad
    for colour, label in keys:
        svg.append(f'<rect x="{x}" y="{y}" width="28" height="28" fill="{colour}" stroke="#999"/>'
                   f'<text x="{x + 36}" y="{y + 23}" font-size="22" fill="#2B2B2B">{label}</text>')
        x += 36 + 24 * len(label) + 34
    svg.append(f'<path d="M{x},{y + 14} h56" stroke="#999" stroke-width="7"/><path d="M{x},{y + 14} h56" '
               f'stroke="#FFF" stroke-width="5"/><text x="{x + 66}" y="{y + 23}" font-size="22" fill="#2B2B2B">'
               f'큰길</text>')
    y += 46
    svg.append(f'<path d="M{pad},{y + 12} h60" stroke="#FFFFFF" stroke-width="9"/>'
               f'<path d="M{pad},{y + 12} h60" stroke="{STATION_BLUE}" stroke-width="5" stroke-dasharray="10 6"/>'
               f'<circle cx="{pad + 84}" cy="{y + 12}" r="12" fill="{STATION_BLUE}"/>'
               f'<text x="{pad + 106}" y="{y + 20}" font-size="22" fill="#2B2B2B">스카이트레인 엑스포 선과 실제 역 자리 '
               f'{len(stations)}곳 (번호는 터널 입구부터 동쪽으로, 이름은 24b에서 지어낸 이름)</text>')
    y += 46
    zone_text = "  ".join(f"{zone}구역: {ZONE_NOTES[zone]}" for zone in homes)
    svg.append(f'<circle cx="{pad + 12}" cy="{y + 12}" r="12" fill="#C0584A"/>'
               f'<text x="{pad + 34}" y="{y + 20}" font-size="22" fill="#2B2B2B">다섯 곳 (1 내 방, 2 카페, 3 도서관, '
               f'4 코워킹, 5 회사): 그대로 다운타운. 점선 테두리 = 구역, 집 모양 = 구역별 내 방 제안</text>')
    y += 46
    for zone in homes:
        svg.append(f'<text x="{pad}" y="{y + 20}" font-size="22" fill="{ZONE_COLOURS[zone]}">'
                   f'{zone}구역 내 방 (칸 {homes[zone][0]}, {homes[zone][1]}): {ZONE_NOTES[zone]}</text>')
        y += 30
    y += 10
    svg.append(scale_bar(pad, y, big_px * 4, 40))
    y += 50
    svg.append(f'<text x="{pad}" y="{y}" font-size="24" font-weight="700" fill="#2B2B2B">'
               f'걷는 시간 (맨땅, 초속 {WALK_SPEED}칸, 지금 길찾기 그대로)</text>')
    y += 34
    for line in walk_lines:
        svg.append(f'<text x="{pad}" y="{y}" font-size="22" fill="#2B2B2B">{line}</text>')
        y += 30
    svg.append("</svg>")
    first = os.path.join(out_dir, "01-wide-preview.svg")
    with open(first, "w", encoding="utf-8") as fh:
        fh.write("".join(svg))

    # 2. The game's edge between water and land over a faint trace of the real cities.
    lines2 = ["옅은 회색: 실제 블록(밴쿠버 블록 윤곽, 버나비 법정 블록)과 밴쿠버 해안선, 검은 점선: 두 도시 경계, 회색 점선: 동네 경계.",
              "빨간 선: 게임 격자의 물과 땅 경계. 파란 점: 실제 역 자리. 회색 기울임 글씨는 비교용, 게임에는 띄우지 않는다."]
    top2 = pad + 64 + 30 * len(lines2) + 20
    zoom_top2 = top2 + big_h + 70
    foot_top = zoom_top2 + zoom_h + 50
    total_h2 = foot_top + 30 * 4 + 40
    svg = [header("실제 윤곽과 비교 (24a)", lines2, total_w, total_h2)]
    van_blocks = [[f.game(*p) for p in ring] for ring in wide.town.sources.blocks]
    bby_blocks = [[f.game(*p) for p in polygon[0]] for polygon in wide.wide.burnaby_blocks]
    areas = [(name, [f.game(*p) for p in ring]) for name, ring in wide.town.sources.areas.items()]
    # Burnaby's plan areas by their number in the data (OBJECTID): the build reads no names for them.
    areas += [(f"plan area {key}", [f.game(*p) for p in polygon[0]]) for key, polygons in wide.wide.plan_areas.items()
              for polygon in polygons]
    burnaby_edge = [[f.game(*p) for p in polygon[0]] for polygon in wide.wide.burnaby]
    shore_raw = [f.game(*p) for p in wide.town.sources.shoreline]
    real_rail = [f.game(*p) for p in wide.wide.rail]
    matched = {name for name, _, _ in stations}
    park_rings = [[f.game(*p) for p in polygon[0]] for (city, _), polygons in wide.wide.parks.items()
                  for polygon in polygons if city == "burnaby" or abs(polygon_area(polygon[0])) > 1.0]
    for x0, px, top in ((0, big_px, top2), (zoom_x0, zoom_px, zoom_top2)):
        svg.append(f'<g transform="translate({pad},{top})">')
        svg.append(f'<clipPath id="d{x0}"><rect x="0" y="0" width="{(wide.columns - x0) * px:.1f}" '
                   f'height="{wide.rows * px:.1f}"/></clipPath><g clip-path="url(#d{x0})">')
        svg.append(f'<rect x="0" y="0" width="{(wide.columns - x0) * px:.1f}" height="{wide.rows * px:.1f}" '
                   f'fill="#EAF3F8"/>')
        svg.append(squares_layer(px, x0, {LAND: "#F8F4EA", SHORE: "#F8F4EA", PARK: "#E3EEDD"}))
        for ring in van_blocks + bby_blocks:
            if max(p[0] for p in ring) < x0:
                continue
            svg.append(f'<path d="{path(ring, px, x0, close=True)}" fill="none" stroke="#CFC6B2" '
                       f'stroke-width="{0.5 if px < 3 else 0.8}"/>')
        for ring in park_rings:
            if max(p[0] for p in ring) < x0:
                continue
            svg.append(f'<path d="{path(ring, px, x0, close=True)}" fill="none" stroke="#7FA07A" '
                       f'stroke-width="{1 if px < 3 else 1.6}"/>')
        for name, ring in areas:
            svg.append(f'<path d="{path(ring, px, x0, close=True)}" fill="none" stroke="#B9B3A6" stroke-width="1" '
                       f'stroke-dasharray="4 4"/>')
        for ring in burnaby_edge:
            svg.append(f'<path d="{path(ring, px, x0, close=True)}" fill="none" stroke="#333" stroke-width="1.6" '
                       f'stroke-dasharray="7 4"/>')
        svg.append(f'<path d="{path(shore_raw, px, x0)}" fill="none" stroke="#6E6E6E" stroke-width="1.4"/>')
        svg.append(f'<path d="{path(real_rail, px, x0)}" fill="none" stroke="{STATION_BLUE}" stroke-width="2.5" '
                   f'stroke-dasharray="8 5"/>')
        svg.append(game_outline_wide(wide, px, x0))
        for name, (lon, lat) in wide.wide.stations:
            gx, gz = f.game(lon, lat)
            if name in matched and x0 <= gx < wide.columns and 0 <= gz < wide.rows:
                svg.append(f'<circle cx="{(gx - x0) * px:.1f}" cy="{gz * px:.1f}" r="{3 if px < 3 else 6}" '
                           f'fill="{STATION_BLUE}"/>')
                if px >= 3:
                    svg.append(f'<text x="{(gx - x0) * px + 9:.1f}" y="{gz * px + (22 if gz < 5 else -8):.1f}" font-size="17" '
                               f'fill="{STATION_BLUE}" font-style="italic" stroke="#FFF" stroke-width="3" '
                               f'paint-order="stroke">{name}</text>')
        for name, ring in areas:
            cx = sum(p[0] for p in ring) / len(ring)
            cz = sum(p[1] for p in ring) / len(ring)
            if x0 <= cx < wide.columns and 4 < cz < wide.rows - 4 and (px >= 3 or cx < zoom_x0):
                svg.append(f'<text x="{(cx - x0) * px:.0f}" y="{cz * px:.0f}" font-size="{16 if px < 3 else 20}" '
                           f'fill="#8A8478" text-anchor="middle" font-style="italic">{name}</text>')
        svg.append(places_layer(px, x0, 8 if px < 3 else 11, False))
        svg.append("</g>")
        svg.append(frame_layer(px, x0, wide.rows))
        svg.append("</g>")
    footer = [
        "Contains information licensed under the Open Government Licence – Vancouver. (City of Vancouver Open Data)",
        "Contains information licensed under the Open Government Licence – British Columbia. "
        "(City of Burnaby Open Data, Open Government Licence – Burnaby)",
        "밴쿠버: Shoreline 2002, Block outlines, Parks, Local area boundary, Rapid transit lines and stations, Public streets.",
        "버나비: Burnaby Boundary, District Lot, Legal Block, Park Inventory, SkyTrain, SkyTrain Station, Street, "
        "Community Plan Area Boundaries.",
    ]
    for i, line in enumerate(footer):
        svg.append(f'<text x="{pad}" y="{foot_top + i * 30}" font-size="19" fill="#555">{line}</text>')
    svg.append("</svg>")
    second = os.path.join(out_dir, "02-wide-outline-compare.svg")
    with open(second, "w", encoding="utf-8") as fh:
        fh.write("".join(svg))
    if png:
        for name, height in ((first, total_h), (second, total_h2)):
            to_png(name, int(total_w), int(height))


def polygon_area(ring):
    """The area of a ring of longitude and latitude, in hectares."""
    pts = [project(*p) for p in ring]
    return sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(pts, pts[1:] + pts[:1])) / 2 / 10000


def game_outline_wide(wide, px, x0):
    """The edges between water and walkable squares of the wide grid, from column [x0], as one path."""
    segments = []
    sq = wide.squares
    for z in range(wide.rows):
        for x in range(x0, wide.columns):
            here = sq[z][x] != WATER
            if x + 1 < wide.columns and (sq[z][x + 1] != WATER) != here:
                segments.append(f"M{(x + 1 - x0) * px:.0f},{z * px:.0f} v{px:.0f}")
            if z + 1 < wide.rows and (sq[z + 1][x] != WATER) != here:
                segments.append(f"M{(x - x0) * px:.0f},{(z + 1) * px:.0f} h{px:.0f}")
    return f'<path d="{" ".join(segments)}" fill="none" stroke="#D2463C" stroke-width="2"/>'


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("raw", help="the directory fetch.sh filled")
    parser.add_argument("--preview", help="also draw the maps as SVG pictures into this directory")
    parser.add_argument("--png", action="store_true", help="turn the pictures into PNG files with a local Chrome")
    parser.add_argument("--town", action="store_true",
                        help="also rewrite the town's map, VancouverMapData.kt, from the same files (on the data "
                             "fetched for 24a only its header changes; every run checks its rows anyway)")
    args = parser.parse_args()
    raw = os.path.abspath(args.raw)

    hashes = {name: sha1(os.path.join(raw, name)) for name, _, _ in WIDE_VANCOUVER_DATASETS}
    burnaby_hashes = {stem: pages_sha1(raw, stem) for stem, _, _, _ in BURNABY_DATASETS}
    fetched_file = os.path.join(raw, "fetched-at.txt")
    fetched = open(fetched_file, encoding="utf-8").read().strip() if os.path.exists(fetched_file) else "(date unknown)"

    sources = Sources(raw)
    built, walks = choose_scale(sources)
    if args.town:
        write_kotlin(built, sources, fetched, hashes)
        print("wrote", os.path.relpath(KOTLIN_OUT, REPO))
    # The town's map as painted now must be the one the town is built on, square for square.
    committed = town_rows_committed()
    painted = ["".join(row) for row in built.squares]
    if committed != painted:
        sys.exit("the town's map came out different from VancouverMapData.kt: the data or the script changed")

    counts = {k: sum(row.count(k) for row in built.squares) for k in (WATER, LAND, PARK, SHORE)}
    print(f"street grid turn: {built.frame.turn:.1f} degrees counter-clockwise")
    print(f"scale: 1 unit = {built.frame.scale:.1f} m")
    print(f"town grid: {built.columns} x {built.rows} = {built.columns * built.rows} squares, "
          f"the same as VancouverMapData.kt")
    print("town squares:", {"water": counts[WATER], "land": counts[LAND], "park": counts[PARK], "shore": counts[SHORE]})
    for (a, b), length in sorted(walks.items(), key=lambda kv: -kv[1]):
        print(f"town walk {a} - {b}: {length:.1f} units, {walk_seconds(length):.1f} s")

    wide = WideMap(WideSources(raw, sources), built)
    if wide.differences:
        sys.exit(f"the wide map's corner differs from the town's map in {len(wide.differences)} squares: "
                 f"{wide.differences[:10]}")
    wide.check_city_line()
    wide.check_zone_areas()
    text = write_wide_kotlin(wide, fetched, hashes, burnaby_hashes)
    counts = {k: sum(row.count(k) for row in wide.squares) for k in (WATER, LAND, PARK, SHORE)}
    print(f"wide grid: {wide.columns} x {wide.rows} = {wide.columns * wide.rows} squares; "
          f"the town's {built.columns} x {built.rows} corner is the same square for square")
    print("wide squares:", {"water": counts[WATER], "land": counts[LAND], "park": counts[PARK], "shore": counts[SHORE]})
    print("wide flat boxes to draw the ground:",
          {name: greedy_boxes(wide.squares, wide.columns, wide.rows, k)
           for name, k in (("land", LAND), ("park", PARK), ("shore", SHORE))})
    print(f"parks of {SMALLEST_PARK} squares or more painted right of the town: {wide.park_count}")
    for line in wide.log:
        print("wide cleanup:", line)
    rail = wide.rail()
    print(f"rail: {len(rail)} points, {sum(math.dist(a, b) for a, b in zip(rail, rail[1:])):.0f} units long")
    for i, (name, point, along) in enumerate(wide.stations(), start=1):
        print(f"station {i}: {name} at {point[0]:.1f},{point[1]:.1f}, {along:.1f} units along")
    streets = wide.streets()
    print(f"main streets: {len(streets)} lines, {sum(len(s) for s in streets)} points")
    print(f"blocks right of the town: {len(wide.blocks())}")
    print("zones:", {zone: len(shapes) for zone, shapes in wide.zones().items()})
    print("homes:", wide.homes())
    wide_walks = wide.walks()
    for (a, b), length in wide_walks.items():
        print(f"wide walk {a} - {b}: {length:.1f} units, {walk_seconds(length):.1f} s")
    rows_size = sum(len(runs_text(row)) for row in wide.squares)
    print(f"wrote {os.path.relpath(WIDE_OUT, REPO)}: {len(text.encode('utf-8'))} bytes "
          f"(rows as runs {rows_size} characters, {wide.columns * wide.rows} squares)")
    if args.preview:
        out = os.path.abspath(args.preview)
        if args.town:
            write_previews(built, walks, out, args.png)
        write_wide_previews(wide, wide_walks, out, args.png)


if __name__ == "__main__":
    main()
