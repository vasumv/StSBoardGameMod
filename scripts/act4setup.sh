#!/usr/bin/env bash
# act4setup.sh N: fresh N-player run at Ascension 11, straight to Act 4, big HP, standing on the first room's map
B=$(cd "$(dirname "$0")" && pwd); Q=$B/q.sh; N=${1:-3}
cp /home/vasu/fun/spire_board_game/StSBoardGameMod/target/BoardGame.jar $B/BoardGame.jar
$B/party.sh $N | tail -1
$Q p1 "rule finalact on" >/dev/null; $Q p1 "rule asc 11" >/dev/null
$B/flowN.sh $N >/dev/null; sleep 3
for k in $(seq 1 $N); do $Q p$k skiproom >/dev/null; done; sleep 3
for k in $(seq 1 $N); do $Q p$k act4 >/dev/null; done; sleep 15
for k in $(seq 1 $N); do $Q p$k "maxhp 600" >/dev/null; done
$Q p1 "mp map" | cut -c1-90
