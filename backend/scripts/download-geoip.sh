#!/bin/sh
# Downloads the free DB-IP country database (K44) to data/geoip/dbip-country-lite.mmdb.
# DB-IP publishes a new file at the start of each month; early in a month the previous
# one is used. License: CC BY 4.0, the panel shows the required attribution.
# Run from backend/: scripts/download-geoip.sh
set -eu

target_dir="data/geoip"
target="$target_dir/dbip-country-lite.mmdb"
mkdir -p "$target_dir"
tmp="$(mktemp "$target_dir/download.XXXXXX")"
trap 'rm -f "$tmp"' EXIT

for month in "$(date -u +%Y-%m)" "$(date -u -v-1m +%Y-%m 2>/dev/null || date -u -d '1 month ago' +%Y-%m)"; do
  url="https://download.db-ip.com/free/dbip-country-lite-$month.mmdb.gz"
  if curl -fsSL --retry 2 "$url" -o "$tmp"; then
    gunzip -c "$tmp" > "$target.new"
    mv "$target.new" "$target"
    echo "Saved $target ($month)"
    exit 0
  fi
done
echo "Could not download the DB-IP country database" >&2
exit 1
