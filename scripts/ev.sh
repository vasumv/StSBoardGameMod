#!/usr/bin/env bash
# ev.sh <EventClass> [fresh]: (fresh = restart the games and start a run) then go to the next map node as an event room and swap in this event in both games
B=$(cd "$(dirname "$0")" && pwd); Q=$B/q.sh
[ -n "$2" ] && { $B/reload.sh | tail -1; $B/flow.sh >/dev/null; }
for p in p1 p2; do $Q $p skiproom >/dev/null; done; sleep 2
[ -n "$PRE1" ] && $Q p1 "$PRE1"; [ -n "$PRE2" ] && $Q p2 "$PRE2"
nxt=$($Q p1 "mp map" | sed 's/.*next://' | awk '{print $1}'); xy=${nxt%%=*}; x=${xy%,*}; y=${xy#*,}
for p in p1 p2; do $Q $p "setroom $x $y event" >/dev/null; done
$Q p1 "mp vote $x $y" >/dev/null; $Q p2 "mp vote $x $y" >/dev/null; sleep 6
for p in p1 p2; do $Q $p "event $1"; done
