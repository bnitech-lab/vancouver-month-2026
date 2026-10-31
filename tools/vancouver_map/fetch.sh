#!/bin/sh
# Downloads the City of Vancouver open data the map is built from, into a new empty directory outside the repository.
# Usage: tools/vancouver_map/fetch.sh DIRECTORY
# The files are data, never code: build_map.py only reads them, and nothing in them is run.
set -eu
out="${1:?usage: fetch.sh DIRECTORY (new or empty)}"
mkdir -p "$out"
if [ -n "$(find "$out" -mindepth 1 -print -quit)" ]; then
    echo "$out is not empty; give a new directory" >&2
    exit 1
fi
base=https://opendata.vancouver.ca/api/explore/v2.1/catalog/datasets
for dataset in shoreline-2002 parks-polygon-representation rapid-transit-lines local-area-boundary city-boundary; do
    curl -sS --fail --max-time 120 "$base/$dataset/exports/geojson" -o "$out/$dataset.geojson"
done
# Only the blocks around downtown, for the angle of its street grid and the comparison picture.
curl -sS --fail --max-time 120 -G "$base/block-outlines/exports/geojson" \
    --data-urlencode "where=in_bbox(geo_point_2d, 49.255, -123.165, 49.320, -123.085)" \
    -o "$out/block-outlines-downtown.geojson"
date -u +%Y-%m-%dT%H:%M:%SZ > "$out/fetched-at.txt"
echo "Fetched into $out"
