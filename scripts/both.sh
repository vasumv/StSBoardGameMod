#!/usr/bin/env bash
# both.sh: (re)start two instances (p1 host on :99, p2 joiner on :98) and wait until both answer debug commands
B=$(cd "$(dirname "$0")" && pwd)
for n in p1 p2; do for pid in $(pgrep -f "java .*bg.debug.dir=$B/$n/dbg"); do kill $pid; done; done
sleep 3
setsid nohup $B/start.sh p1 99 -Dbg.mp.host=7777 -Dbg.mp.name=Alice -Dbg.mp.char=${C1:-0} >/dev/null 2>&1 &
sleep 10
setsid nohup $B/start.sh p2 98 -Dbg.mp.join=127.0.0.1:7777 -Dbg.mp.name=Bob -Dbg.mp.char=${C2:-1} >/dev/null 2>&1 &
for i in $(seq 1 110); do
  a=$(timeout 2 $B/q.sh p1 "mp status" 0 2>/dev/null); b=$(timeout 2 $B/q.sh p2 "mp status" 0 2>/dev/null)
  [ -n "$a" ] && [ -n "$b" ] && { echo "both up after ${i} tries"; exit 0; }
  sleep 1
done
echo "TIMEOUT waiting for instances"; tail -2 $B/p1/launch.log $B/p2/launch.log | cut -c1-200; exit 1
