#!/usr/bin/env bash
# flowN.sh N: from a fresh lobby, start an N-player run and get everyone past Neow onto the map
B=$(cd "$(dirname "$0")" && pwd); Q=$B/q.sh; N=${1:-2}
$Q p1 "mp start ${SEED:-4242}"; sleep 18
for k in $(seq 1 $N); do $Q p$k skiproom >/dev/null; done; sleep 2
$Q p1 "mp map"
