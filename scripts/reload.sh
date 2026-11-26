#!/usr/bin/env bash
# reload.sh: stop the test games, install the freshly built jar, start them again (C1/C2 = characters)
B=$(cd "$(dirname "$0")" && pwd)
for pid in $(pgrep -f "java .*bg.debug.dir=$B/"); do kill $pid; done; sleep 3
for pid in $(pgrep -f "java .*bg.debug.dir=$B/"); do kill -9 $pid; done
pgrep -f "ModTheSpire.jar" | while read p; do grep -q "bg.debug.dir" /proc/$p/cmdline 2>/dev/null || { echo "a real game is running: not replacing the jar"; exit 1; }; done || exit 1
cp /home/vasu/fun/spire_board_game/StSBoardGameMod/target/BoardGame.jar $B/BoardGame.jar  # the test games' own copy (builds don't touch it)
$B/both.sh
