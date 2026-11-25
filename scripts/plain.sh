#!/usr/bin/env bash
# plain.sh: start p1 and p2 with no automatic multiplayer connection; wait until both answer
B=$(cd "$(dirname "$0")" && pwd)
for pid in $(pgrep -f "java .*bg.debug.dir=$B/"); do kill $pid; done; sleep 3
setsid nohup $B/start.sh p1 0 -Dbg.fast=true >/dev/null 2>&1 &
sleep 6
setsid nohup $B/start.sh p2 0 -Dbg.fast=true >/dev/null 2>&1 &
for i in $(seq 1 120); do
  a=$(timeout 2 $B/q.sh p1 "mp status" 0 2>/dev/null); b=$(timeout 2 $B/q.sh p2 "mp status" 0 2>/dev/null)
  [ -n "$a" ] && [ -n "$b" ] && { echo "both up after $i"; exit 0; }
  for n in p1 p2; do pgrep -f "java .*bg.debug.dir=$B/$n/dbg" >/dev/null || [ $i -lt 20 ] || { echo "$n DIED"; grep -m3 "Exception" $B/$n/launch.log; exit 1; }; done
  sleep 1
done; echo TIMEOUT; exit 1
