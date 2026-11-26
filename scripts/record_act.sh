#!/usr/bin/env bash
# record_act.sh <floors> <out.mp4>: fresh 2-player run (Ironclad + Silent), record while both autoplay
B=$(cd "$(dirname "$0")" && pwd)
C1=0 C2=1 $B/reload.sh | tail -1
$B/flow.sh >/dev/null
for p in p1 p2; do $B/q.sh $p "maxhp 25" >/dev/null; $B/q.sh $p skiproom >/dev/null; done; sleep 2
$B/rec.sh start
python3 $B/autoplay.py ${1:-16}
$B/rec.sh stop "$2"
