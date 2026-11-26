#!/usr/bin/env bash
# fight.sh "<encounter key>[, <key2>]": fresh test games (new jar), start a run, skip Neow, fight these encounters
B=$(cd "$(dirname "$0")" && pwd); Q=$B/q.sh
$B/reload.sh | tail -1
$B/flow.sh >/dev/null
[ -n "$1" ] && for p in p1 p2; do $Q $p "nextenc $1" >/dev/null; done
$Q p1 "mp vote ${X:-3} 0" >/dev/null; $Q p2 "mp vote ${X:-3} 0" >/dev/null
for i in $(seq 1 40); do
  a=$($Q p1 idle 2>/dev/null); b=$($Q p2 idle 2>/dev/null)
  echo "$a$b" | grep -q "idle=true.*idle=true" && { echo "in fight after $i s"; exit 0; }
  sleep 1
done
echo "NOT IN FIGHT"; exit 1
