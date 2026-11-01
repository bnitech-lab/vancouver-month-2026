#!/bin/sh
# Downloads the City of Vancouver and City of Burnaby open data the maps are built from, into a new empty directory
# outside the repository.
# Usage: tools/vancouver_map/fetch.sh DIRECTORY
# The files are data, never code: build_map.py only reads them, and nothing in them is run.
set -eu
out="${1:?usage: fetch.sh DIRECTORY (new or empty)}"
mkdir -p "$out"
if [ -n "$(find "$out" -mindepth 1 -print -quit)" ]; then
    echo "$out is not empty; give a new directory" >&2
    exit 1
fi

# City of Vancouver Open Data Portal (Open Government Licence - Vancouver).
base=https://opendata.vancouver.ca/api/explore/v2.1/catalog/datasets
for dataset in shoreline-2002 parks-polygon-representation rapid-transit-lines rapid-transit-stations \
    local-area-boundary city-boundary; do
    curl -sS --fail --max-time 120 "$base/$dataset/exports/geojson" -o "$out/$dataset.geojson"
done
# The city blocks over the whole wide grid (downtown, East Vancouver to Boundary Road): the angle of the street grid,
# the buildings, and the comparison picture.
curl -sS --fail --max-time 300 -G "$base/block-outlines/exports/geojson" \
    --data-urlencode "where=in_bbox(geo_point_2d, 49.185, -123.185, 49.325, -123.020)" \
    -o "$out/block-outlines.geojson"
# The main streets only (arterial and secondary arterial), and only their use: no block addresses.
curl -sS --fail --max-time 300 -G "$base/public-streets/exports/geojson" \
    --data-urlencode "select=streetuse" \
    --data-urlencode "where=streetuse in (\"Arterial\", \"Secondary Arterial\") and in_bbox(geo_point_2d, 49.185, -123.185, 49.325, -123.020)" \
    -o "$out/public-streets.geojson"

# City of Burnaby Open Data (Open Government Licence - Burnaby), from the City's ArcGIS server.
burnaby=https://gis.burnaby.ca/arcgis/rest/services/OpenData
# South-west Burnaby: Boundary Road to about 2 km past the big Burnaby station, Kingsway down to the Fraser.
envelope='{"xmin":-123.03,"ymin":49.18,"xmax":-122.95,"ymax":49.245,"spatialReference":{"wkid":4326}}'
# fetch_layer NAME LAYER FIELDS WHERE [envelope]: every feature, page by page (the server gives at most 2000 at a
# time), into NAME-1.geojson, NAME-2.geojson and so on.
fetch_layer() {
    page=1
    offset=0
    while :; do
        file="$out/$1-$page.geojson"
        if [ "${5:-}" = envelope ]; then
            curl -sS --fail --max-time 300 -G "$burnaby/$2/query" --data-urlencode "where=$4" \
                --data-urlencode "outFields=$3" --data-urlencode "outSR=4326" --data-urlencode "f=geojson" \
                --data-urlencode "geometry=$envelope" --data-urlencode "geometryType=esriGeometryEnvelope" \
                --data-urlencode "spatialRel=esriSpatialRelIntersects" --data-urlencode "orderByFields=OBJECTID" \
                --data-urlencode "resultOffset=$offset" --data-urlencode "resultRecordCount=1000" -o "$file"
        else
            curl -sS --fail --max-time 300 -G "$burnaby/$2/query" --data-urlencode "where=$4" \
                --data-urlencode "outFields=$3" --data-urlencode "outSR=4326" --data-urlencode "f=geojson" \
                --data-urlencode "orderByFields=OBJECTID" \
                --data-urlencode "resultOffset=$offset" --data-urlencode "resultRecordCount=1000" -o "$file"
        fi
        if grep -q '"error"' "$file"; then
            echo "$1: the server answered with an error" >&2
            exit 1
        fi
        grep -q '"exceededTransferLimit" *: *true' "$file" || break
        page=$((page + 1))
        offset=$((offset + 1000))
    done
}
fetch_layer burnaby-boundary OpenData3/MapServer/12 OBJECTID "1=1"
fetch_layer skytrain OpenData5/MapServer/11 NAME "1=1"
fetch_layer skytrain-station OpenData5/MapServer/10 NAME "1=1"
fetch_layer community-plan-areas OpenData1/MapServer/10 NAME "1=1"
fetch_layer park-inventory OpenData1/MapServer/6 "NAME,CLASSIFICATION" "1=1" envelope
# Burnaby's main streets: its arterials and major collectors, about what Vancouver calls arterial and secondary arterial.
fetch_layer street OpenData5/MapServer/8 STREETCLASS_desc \
    "STREETCLASS_desc IN ('Arterial Primary', 'Arterial Secondary', 'Major Collector Primary', 'Major Collector Secondary')" \
    envelope
fetch_layer legal-block OpenData4/MapServer/9 OBJECTID "1=1" envelope
fetch_layer district-lot OpenData4/MapServer/6 OBJECTID "1=1" envelope

date -u +%Y-%m-%dT%H:%M:%SZ > "$out/fetched-at.txt"
echo "Fetched into $out"
