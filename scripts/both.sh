#!/usr/bin/env bash
# both.sh: (re)start two instances (p1 host on :99, p2 joiner on :98) and wait until both answer debug commands
B=$(cd "$(dirname "$0")" && pwd)
# stop every sandbox instance and its display first, so restarts never stack up
for pid in $(pgrep -f "java .*bg.debug.dir=$B/"); do kill $pid; done
sleep 2; for pid in $(pgrep -f "java .*bg.debug.dir=$B/"); do kill -9 $pid; done
for i in $(seq 1 30); do avail=$(awk '/MemAvailable/{print int($2/1024/1024)}' /proc/meminfo); [ "$avail" -ge 7 ] && break; sleep 1; done
[ "$avail" -ge 6 ] || { echo "only ${avail}G memory available; not starting two game copies"; exit 1; }
pkill -f "^Xvfb :[0-9]+ -screen 0 1280x720x24" 2>/dev/null
sleep 4; 
setsid nohup $B/start.sh p1 99 -Dbg.mp.host=7777 -Dbg.fast=true -Dbg.mp.name=Alice -Dbg.mp.char=${C1:-0} >/dev/null 2>&1 &
echo "p1 launched"; sleep 6
setsid nohup $B/start.sh p2 98 -Dbg.mp.join=127.0.0.1:7777 -Dbg.fast=true -Dbg.mp.name=Bob -Dbg.mp.char=${C2:-1} >/dev/null 2>&1 &
echo "p2 launched"
for i in $(seq 1 120); do
  a=$(timeout 2 $B/q.sh p1 "mp status" 0 2>/dev/null); b=$(timeout 2 $B/q.sh p2 "mp status" 0 2>/dev/null)
  [ -n "$a" ] && [ -n "$b" ] && { echo "both up after ${i} tries"; exit 0; }
  for n in p1 p2; do pgrep -f "java .*bg.debug.dir=$B/$n/dbg" >/dev/null || [ $i -lt 20 ] || { echo "$n DIED"; tail -3 $B/$n/launch.log; exit 1; }; done
  sleep 1
done
echo "TIMEOUT waiting for instances"; exit 1
