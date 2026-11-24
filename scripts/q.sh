#!/usr/bin/env bash
# q.sh <name> "<cmd>" [wait-seconds]: send a debug command to a sandbox instance and print the reply
D=$(cd "$(dirname "$0")" && pwd)/$1/dbg; rm -f $D/out; printf "%s\n" "$2" > $D/cmd
for i in $(seq 1 30); do [ -f $D/out ] && break; sleep 0.1; done
sleep ${3:-0.2}; grep -v "^>" $D/out 2>/dev/null
