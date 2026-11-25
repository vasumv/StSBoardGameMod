#!/usr/bin/env bash
# start.sh <name> <ignored> [extra java -D args]: run a sandboxed game instance on its own hidden X display
NAME=$1; shift 2
DISP=$(( 100 + RANDOM % 800 ))
B=$(cd "$(dirname "$0")" && pwd)
cd "$B/$NAME" || exit 1
mkdir -p dbg home; rm -f dbg/cmd dbg/out
while [ -e /tmp/.X$DISP-lock ]; do DISP=$(( 100 + RANDOM % 800 )); done
echo $DISP > "$B/$NAME/display"
MESA=$(nix build --no-link --print-out-paths nixpkgs#mesa | head -1)
export LIBGL_DRIVERS_PATH=$MESA/lib/dri
unset WAYLAND_DISPLAY
nix shell nixpkgs#xorg.xorgserver -c Xvfb :$DISP -screen 0 960x540x24 +extension GLX -nolisten tcp &
XPID=$!; sleep 2
# own HOME: keeps mod config per instance and stops the game attaching to a running Steam client
# own memory-capped scope: a runaway instance gets killed alone instead of taking the terminal with it
HOME="$B/$NAME/home" DISPLAY=:$DISP systemd-run --user --scope --quiet -p MemoryHigh=3G -p MemoryMax=4G ./jre/bin/java -Xmx1536m -Dbg.nosteam=true -Dbg.debug.dir="$B/$NAME/dbg" "$@" -jar ModTheSpire.jar --skip-launcher --skip-intro --mods basemod,stslib,ojb_Bestiary,quickrestart,BoardGame > launch.log 2>&1
kill $XPID
