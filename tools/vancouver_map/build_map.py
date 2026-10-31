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

# A city block is drawn this much smaller on every side than its real outline, so a street of at least one square
# always runs between two blocks; a block wider or deeper than SPLIT_OVER squares is cut into pieces with lanes.
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
    ("block-outlines-downtown.geojson", "Block outlines", "block-outlines"),
]

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
KOTLIN_OUT = os.path.join(REPO, "web", "src", "jsMain", "kotlin", "vancouvermonth", "web", "VancouverMapData.kt")


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
                       for f in load_features(raw, "block-outlines-downtown.geojson")]
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
        return [r for r in built if r in kept], [r for r in greens if r in kept]

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
        shore_parts = [p for p in shore_parts if len(p) > 1]
        park = simplify([f.game(*p) for p in self.sources.park_rings[0]], SIMPLIFY)
        rail_parts = clip_polyline([f.game(*p) for p in self.sources.rail], 0.0, 0.0, self.columns, self.rows)
        rail = simplify(rail_parts[0], SIMPLIFY) if rail_parts else []
        mountains = [f.game(*p) for p in self.sources.inlet_line]
        return shore_parts, park, rail, mountains


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
    out.append("//")
    out.append(f"// Fetched {fetched} from https://opendata.vancouver.ca:")
    for name, title, dataset in DATASETS:
        out.append(f"//   {title} ({dataset}), sha1 {hashes[name][:12]}")
    out.append("package vancouvermonth.web")
    out.append("")
    out.append("/**")
    out.append(" * Real Vancouver as a grid of squares: the park at the peninsula's tip, downtown, False Creek "
               "and a strip of its")
    out.append(" * south shore. Turned so the downtown street grid runs along x and z (real north points up "
               "and to the left),")
    out.append(" * and shrunk so the farthest walk between two of the five places takes about 25 seconds.")
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
    out.append("     * One string per row, north to south; one character per square, west to east:")
    out.append("     * ~ water, . land, p park, s shore (land beside the water, where the seawall runs).")
    out.append("     * Each row stays on one line, past the usual width, so the map reads as a picture.")
    out.append("     */")
    out.append("    val rows: List<String> = listOf(")
    for row in built.squares:
        out.append(f'        "{"".join(row)}",')
    out.append("    )")
    out.append("")
    out.append("    /** The real shoreline, simplified, where it crosses the grid: "
               "one string of points per stretch. */")
    out.append("    val shoreLines: List<String> = listOf(")
    for part in shore_parts:
        out.append("        " + wrap_points(part, 8) + ",")
    out.append("    )")
    out.append("")
    out.append("    /** The big park's outline, simplified. */")
    out.append("    const val PARK = " + wrap_points(park, 8))
    out.append("")
    out.append("    /** The rail on its guideway: from where it leaves its tunnel, east along False Creek. */")
    out.append("    const val RAIL = " + wrap_points(rail, 8))
    out.append("")
    out.append("    /**")
    out.append("     * A line out along the middle of Burrard Inlet (the city's boundary there): "
               "the north shore and its mountains")
    out.append("     * stand past it, to the north.")
    out.append("     */")
    out.append("    const val MOUNTAINS = " + wrap_points(mountains, 8))
    out.append("")
    built_blocks, greens = built.city_blocks()
    out.append("    /**")
    out.append("     * The real city blocks, simplified to rectangles of plain land with a street between any two: "
               "\"x,z,width,depth\"")
    out.append("     * in squares, west and north sides first. Buildings stand on these.")
    out.append("     */")
    out.append("    const val BLOCKS = " + wrap_rects(built_blocks, 8))
    out.append("")
    out.append("    /** The city blocks that are small parks, as [BLOCKS]: grass and a few trees. */")
    out.append("    const val GREENS = " + wrap_rects(greens, 8))
    out.append("")
    out.append("    /** The square proposed for each of the five places. Stage 20b builds them here. */")
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

def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("raw", help="the directory fetch.sh filled")
    parser.add_argument("--preview", help="also draw the map as SVG pictures into this directory")
    parser.add_argument("--png", action="store_true", help="turn the pictures into PNG files with a local Chrome")
    args = parser.parse_args()
    raw = os.path.abspath(args.raw)

    hashes = {name: sha1(os.path.join(raw, name)) for name, _, _ in DATASETS}
    fetched_file = os.path.join(raw, "fetched-at.txt")
    fetched = open(fetched_file, encoding="utf-8").read().strip() if os.path.exists(fetched_file) else "(date unknown)"

    sources = Sources(raw)
    built, walks = choose_scale(sources)
    write_kotlin(built, sources, fetched, hashes)

    counts = {k: sum(row.count(k) for row in built.squares) for k in (WATER, LAND, PARK, SHORE)}
    print(f"street grid turn: {built.frame.turn:.1f} degrees counter-clockwise")
    print(f"scale: 1 unit = {built.frame.scale:.1f} m")
    print(f"grid: {built.columns} x {built.rows} = {built.columns * built.rows} squares")
    print("squares:", {"water": counts[WATER], "land": counts[LAND], "park": counts[PARK], "shore": counts[SHORE]})
    print("flat boxes to draw the ground:",
          {name: greedy_boxes(built.squares, built.columns, built.rows, k)
           for name, k in (("land", LAND), ("park", PARK), ("shore", SHORE))})
    for line in built.log:
        print("cleanup:", line)
    print("spots:", built.spots)
    for (a, b), length in sorted(walks.items(), key=lambda kv: -kv[1]):
        print(f"walk {a} - {b}: {length:.1f} units, {walk_seconds(length):.1f} s")
    print("wrote", os.path.relpath(KOTLIN_OUT, REPO))
    if args.preview:
        write_previews(built, walks, os.path.abspath(args.preview), args.png)


if __name__ == "__main__":
    main()
