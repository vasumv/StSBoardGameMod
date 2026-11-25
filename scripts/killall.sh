#!/usr/bin/env bash
# killall.sh: from the first fight, kill every enemy (alternating players), last blow via a queued card play
B=$(cd "$(dirname "$0")" && pwd); Q=$B/q.sh
$Q p1 "hit 0 9" 0; sleep 3; $Q p2 "hit 1 9" 0; sleep 3; $Q p1 "hit 2 9" 0; sleep 3; $Q p2 "hit 3 9" 0; sleep 3
$Q p1 "hit 4 3" 0; sleep 3
$Q p2 "play 1 4" 0; sleep 10
for p in p1 p2; do echo "== $p"; $Q $p state | grep -E "^monster" | cut -c1-70; $Q $p "mp map" | cut -c1-100; done
