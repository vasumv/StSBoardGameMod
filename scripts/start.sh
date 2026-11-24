#!/usr/bin/env bash
# start.sh <name> <display-number> [extra java -D args]: run a sandboxed game instance on a hidden X display
NAME=$1; DISP=$2; shift 2
B=$(cd "$(dirname "$0")" && pwd)
cd "$B/$NAME" || exit 1
mkdir -p dbg home; rm -f dbg/cmd dbg/out
for i in $(seq 1 20); do [ -e /tmp/.X$DISP-lock ] || break; sleep 0.5; done
MESA=$(nix build --no-link --print-out-paths nixpkgs#mesa | head -1)
export LIBGL_DRIVERS_PATH=$MESA/lib/dri
unset WAYLAND_DISPLAY
nix shell nixpkgs#xorg.xorgserver -c Xvfb :$DISP -screen 0 1280x720x24 +extension GLX -nolisten tcp &
XPID=$!; sleep 2
# own HOME: keeps mod config per instance and stops the game attaching to a running Steam client
HOME="$B/$NAME/home" DISPLAY=:$DISP ./jre/bin/java -Dbg.nosteam=true -Dbg.debug.dir="$B/$NAME/dbg" "$@" -jar ModTheSpire.jar --skip-launcher --skip-intro --mods basemod,stslib,ojb_Bestiary,quickrestart,BoardGame > launch.log 2>&1
kill $XPID
