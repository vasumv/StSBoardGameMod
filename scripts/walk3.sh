#!/usr/bin/env bash
# walk.sh <floors>: both players skip each room, vote the same next room, and clear fights with direct hits.
# Prints per floor: room type, whether both games built the same fight, and any desync.
B=$(cd "$(dirname "$0")" && pwd); Q=$B/q.sh
nodes() { $Q p1 "mp map" | sed 's/.*next://' ; }
acts=0
for f in $(seq 1 ${1:-5}); do
  $Q p1 skiproom >/dev/null; $Q p2 skiproom >/dev/null; sleep 2
  nxt=$(nodes | awk '{print $1}')
  if [ -z "$nxt" ]; then
    acts=$((acts+1)); [ $acts -ge 3 ] && { echo "act 3 boss beaten"; exit 0; }
    # after a boss: Proceed to the boss chest, take nothing, Proceed to the next act
    for p in p1 p2; do $Q $p proceed >/dev/null; done; sleep 6
    for p in p1 p2; do $Q $p proceed >/dev/null; done; sleep 15
    nxt=$(nodes | awk '{print $1}')
    [ -z "$nxt" ] && { echo "floor $f: no next room ($($Q p1 'mp map' | cut -c1-80))"; break; }
    echo "--- next act: $($Q p1 state | head -1 | cut -c1-40)"
  fi
  xy=${nxt%%=*}; kind=${nxt#*=}; x=${xy%,*}; y=${xy#*,}
  $Q p1 "mp vote $x $y" >/dev/null; $Q p2 "mp vote $x $y" >/dev/null; sleep 6
  ph1=$($Q p1 "mp map" | grep -o "phase=[A-Z]*"); ph2=$($Q p2 "mp map" | grep -o "phase=[A-Z]*")
  line="floor $f -> $kind ($xy) p1:$ph1 p2:$ph2"
  if [ "$ph1" = "phase=COMBAT" ]; then
    for i in $(seq 1 20); do $Q p1 idle | grep -q "idle=true" && $Q p2 idle | grep -q "idle=true" && break; sleep 1; done
    m1=$($Q p1 state | grep "^monster" | cut -d' ' -f2-4); m2=$($Q p2 state | grep "^monster" | cut -d' ' -f2-4)
    [ "$m1" = "$m2" ] && line="$line same-fight[$(echo $m1 | tr '\n' ' ')]" || line="$line FIGHT-DIFFERS[$m1 | $m2]"
    n=$(echo "$m1" | wc -l)
    for r in 1 2 3 4 5 6 7 8; do
      n=$($Q p1 state | grep -c "^monster")
      for i in $(seq 0 $((n-1))); do $Q p1 "hit $i 99" >/dev/null; sleep 0.6; done
      sleep 3; $Q p1 "mp map" | grep -q "phase=COMBAT" || break
      # enemies like Slime Boss split on their own turn: end the turn so they can
      $Q p1 "mp keep" >/dev/null; $Q p2 "mp keep" >/dev/null; $Q p1 "mp ready" >/dev/null; $Q p2 "mp ready" >/dev/null; sleep 8
    done
    sleep 3; e1=$($Q p1 "mp map" | grep -o "phase=[A-Z]*"); e2=$($Q p2 "mp map" | grep -o "phase=[A-Z]*")
    line="$line after: p1:$e1 p2:$e2"
  fi
  echo "$line"
done
