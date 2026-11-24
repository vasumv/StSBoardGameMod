#!/usr/bin/env bash
# flow.sh: from a fresh lobby, start a 2-player run, get both through Neow, and vote into the first fight
B=$(cd "$(dirname "$0")" && pwd); Q=$B/q.sh
$Q p1 "mp start ${SEED:-4242}"; sleep 15
$Q p1 skiproom; $Q p2 skiproom; sleep 2

$Q p1 "mp map"
