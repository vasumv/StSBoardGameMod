#!/usr/bin/env bash
# tofight.sh: lobby -> run start -> skip Neow -> both vote first room -> wait until both can act
B=$(cd "$(dirname "$0")" && pwd); Q=$B/q.sh
$B/flow.sh >/dev/null
$Q p1 "mp vote ${X:-3} 0" >/dev/null; $Q p2 "mp vote ${X:-3} 0" >/dev/null
for i in $(seq 1 40); do
  a=$($Q p1 idle 2>/dev/null); b=$($Q p2 idle 2>/dev/null)
  echo "$a$b" | grep -q "idle=true.*idle=true" && { echo "in fight after $i s"; exit 0; }
  sleep 1
done
echo "NOT IN FIGHT"; $Q p1 "mp map"; $Q p2 "mp map"; exit 1
