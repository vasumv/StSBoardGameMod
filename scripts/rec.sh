#!/usr/bin/env bash
# rec.sh start | stop <out.mp4>: record both sandbox games (p1 left, p2 right) side by side.
B=$(cd "$(dirname "$0")" && pwd); R=$B/rec; mkdir -p $R
FF="nix shell nixpkgs#ffmpeg-full -c ffmpeg"
case $1 in
 start)
  rm -f $R/p1.mkv $R/p2.mkv
  for n in p1 p2; do
    d=$(cat $B/$n/display)
    setsid nohup $FF -loglevel error -f x11grab -framerate 12 -video_size 960x540 -i :$d \
      -c:v libx264 -preset ultrafast -crf 26 $R/$n.mkv >$R/$n.log 2>&1 &
  done
  sleep 3; echo "recording";;
 stop)
  pkill -INT -f "x11grab.*rec/" ; sleep 4
  $FF -loglevel error -y -i $R/p1.mkv -i $R/p2.mkv -filter_complex "[0:v][1:v]hstack=inputs=2[v]" -map "[v]" \
    -c:v libx264 -preset veryfast -crf 30 -pix_fmt yuv420p -movflags +faststart "$2" && ls -la "$2";;
esac
