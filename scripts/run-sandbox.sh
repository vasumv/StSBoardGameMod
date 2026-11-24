#!/usr/bin/env bash
# Launch the modded game from an isolated sandbox folder (own saves/prefs), without Steam.
# Usage: scripts/run-sandbox.sh <name> [--headless] [-- extra java args]
#   <name>      sandbox instance name, e.g. p1 / p2 (two instances = two players)
#   --headless  run on a hidden virtual display with software OpenGL (needs nix)
# Sandboxes live in ../sandbox/<name> next to the repo and link to the Steam install.
set -euo pipefail
NAME=${1:?instance name}; shift || true
HEADLESS=0; [ "${1:-}" = "--headless" ] && { HEADLESS=1; shift; }
[ "${1:-}" = "--" ] && shift
REPO=$(cd "$(dirname "$0")/.." && pwd)
STEAM=${STEAM_APPS:-$HOME/.local/share/Steam/steamapps}
GAME=$STEAM/common/SlayTheSpire
WS=$STEAM/workshop/content/646570
BOX=${SANDBOX_ROOT:-$REPO/../sandbox}/$NAME
mkdir -p "$BOX/mods"
ln -sf "$GAME/desktop-1.0.jar" "$BOX/"
ln -sfn "$GAME/jre" "$BOX/jre"
ln -sf "$WS/1605060445/ModTheSpire.jar" "$BOX/"
for j in "$WS/1605833019/BaseMod.jar" "$WS/1609158507/StSLib.jar" \
         "$WS/2285965269/Bestiary.jar" "$WS/1805046408/quickRestart.jar" \
         "$REPO/target/BoardGame.jar"; do ln -sf "$j" "$BOX/mods/"; done
cd "$BOX"
CMD=(./jre/bin/java "$@" -jar ModTheSpire.jar --skip-launcher --skip-intro
     --mods basemod,stslib,ojb_Bestiary,quickrestart,BoardGame)
if [ $HEADLESS = 1 ]; then
  MESA=$(nix build --no-link --print-out-paths nixpkgs#mesa | head -1)
  exec env -u WAYLAND_DISPLAY LIBGL_DRIVERS_PATH="$MESA/lib/dri" \
    nix shell nixpkgs#xvfb-run -c xvfb-run -a -s "-screen 0 1280x720x24 +extension GLX" "${CMD[@]}"
else
  exec "${CMD[@]}"
fi
