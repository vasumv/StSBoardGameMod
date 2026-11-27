#!/usr/bin/env bash
# party.sh N: (re)start N sandbox games (p1 hosts, p2..pN join) and wait until all are connected.
# Characters: C1..C4 (default Ironclad, Silent, Defect, Watcher). Checks free memory before each copy.
B=$(cd "$(dirname "$0")" && pwd)
N=${1:-2}
NAMES=(x Alice Bob Cara Dan)
DEF=(x 0 1 2 3)
for pid in $(pgrep -f "java .*bg.debug.dir=$B/"); do kill $pid; done
sleep 2; for pid in $(pgrep -f "java .*bg.debug.dir=$B/"); do kill -9 $pid; done
for n in p1 p2 p3 p4; do [ -f $B/$n/display ] && pkill -f "^Xvfb :$(cat $B/$n/display) " 2>/dev/null; done
sleep 4
for k in $(seq 1 $N); do
  for i in $(seq 1 40); do avail=$(awk '/MemAvailable/{print int($2/1024/1024)}' /proc/meminfo); [ "$avail" -ge 3 ] && break; sleep 2; done
  [ "$avail" -ge 3 ] || { echo "only ${avail}G free before starting p$k; stopping"; exit 1; }
  cv=C$k; ch=${!cv:-${DEF[$k]}}
  if [ $k -eq 1 ]; then role="-Dbg.mp.host=7777"; else role="-Dbg.mp.join=127.0.0.1:7777"; fi
  setsid nohup $B/start.sh p$k 0 $role -Dbg.fast=true -Dbg.mp.name=${NAMES[$k]} -Dbg.mp.char=$ch >/dev/null 2>&1 &
  echo "p$k launched (${NAMES[$k]}, character $ch)"
  # wait for this copy to answer before starting the next (keeps the memory peak lower)
  for i in $(seq 1 120); do timeout 2 $B/q.sh p$k "mp status" 0 2>/dev/null | grep -q . && break; sleep 1; done
done
for k in $(seq 2 $N); do
  for j in $(seq 1 20); do
    $B/q.sh p$k "mp status" 0 | grep -q "active=true" && break
    $B/q.sh p$k "mp join 127.0.0.1 7777" >/dev/null; sleep 2
  done
  $B/q.sh p$k "mp status" 0 | grep -q "active=true" || { echo "p$k could not join"; exit 1; }
done
# if memory runs out, a test game should be the one the kernel stops, not the user's apps
for pid in $(pgrep -f "java .*bg.debug.dir=$B/"); do echo 1000 > /proc/$pid/oom_score_adj 2>/dev/null; done
echo "all $N up"; free -g | sed -n 2p
