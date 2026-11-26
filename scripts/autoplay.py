#!/usr/bin/env python3
"""autoplay.py <floors>: both sandbox players play through the map.
Fights: each player plays affordable cards from hand (attacks at an enemy in their own row first),
then both end the turn. Other rooms are shown for a few seconds, then skipped.
Prints one line per floor (room, result, HP)."""
import re, subprocess, sys, time

B = "/home/vasu/fun/spire_board_game/sandbox"
# cards that open a choice screen (this script can't answer those)
SKIP = {"BGSurvivor", "BGAcrobatics", "BGPrepared", "BGWarcry", "BGHeadbutt", "BGArmaments", "BGDualcast"}


def q(p, cmd, wait=0.2):
    try:
        return subprocess.run([B + "/q.sh", p, cmd, str(wait)], capture_output=True, text=True, timeout=20).stdout
    except subprocess.TimeoutExpired:
        return ""


def phase(p):
    for _ in range(5):  # a slow answer must not look like "the fight is over"
        m = re.search(r"phase=([A-Z]+)", q(p, "mp map"))
        if m:
            return m.group(1)
        time.sleep(1)
    return "COMBAT"


def nxt():
    s = q("p1", "mp map")
    m = re.search(r"next: (\S+)", s)
    return m.group(1) if m else None


def state(p):
    s = q(p, "state")
    hand = re.search(r"^hand=(.*)$", s, re.M)
    cards = re.findall(r"(\S+?)\((-?\d+),(-?\d+),", hand.group(1)) if hand else []
    en = re.search(r"energy=(\d+)", s)
    hp = re.search(r"hp=(\d+)/(\d+)", s)
    mons = []
    for i, line in enumerate(re.findall(r"^monster .*$", s, re.M)):
        row = int(re.search(r" row(\d+) ", line).group(1))
        dead = "dead=false/false" not in line
        mons.append((i, row, dead))
    return cards, int(en.group(1)) if en else 0, hp.group(0) if hp else "?", mons


def turn(p):
    m = re.search(r"turn=(\d+)", q(p, "state"))
    return int(m.group(1)) if m else -1


def my_row(p):
    s = q(p, "mp status")
    me = re.search(r"me=(\d+)", s)
    if not me:
        return 0
    m = re.search(r"\[%s \S+ \S+ row(\d+)\]" % me.group(1), s)
    return int(m.group(1)) if m else 0


def play_turn(p):
    row = my_row(p)
    for _ in range(12):
        for _ in range(20):  # wait until the game settles
            if "idle=true" in q(p, "idle"):
                break
            time.sleep(0.4)
        cards, energy, _, mons = state(p)
        alive = [m for m in mons if not m[2]]
        if not alive:
            return
        target = next((m[0] for m in alive if m[1] == row), alive[0][0])
        played = False
        for i, (cid, cost, dmg) in enumerate(cards):
            cost = int(cost)
            if cid in SKIP or cost < 0 or cost > energy:
                continue
            r = q(p, "play %d %d" % (i, target))
            if "queued" in r:
                played = True
                time.sleep(1.6)
                break
        if not played:
            return


def fight():
    for rnd in range(25):
        time.sleep(2.5)  # die phase / start of turn
        if phase("p1") != "COMBAT":
            return "won"
        play_turn("p1")
        play_turn("p2")
        t0 = turn("p1")
        q("p1", "end")
        q("p2", "end")
        # End Turn toggles "ready": never send it again until the next turn has really started
        for _ in range(60):
            time.sleep(1)
            if phase("p1") != "COMBAT":
                return "won"
            if turn("p1") > t0 and turn("p2") > t0 and "idle=true" in q("p1", "idle") \
                    and "idle=true" in q("p2", "idle"):
                break
    # stuck: finish it so the run goes on
    _, _, _, mons = state("p1")
    for m in mons:
        q("p1", "hit %d 99" % m[0])
        time.sleep(0.5)
    time.sleep(4)
    return "forced"


floors = int(sys.argv[1]) if len(sys.argv) > 1 else 16
act4 = len(sys.argv) > 2 and sys.argv[2] == "act4"
last_kind = ""
for f in range(floors):
    n = nxt()
    if not n and "MonsterRoomBoss" in last_kind and act4:
        print("run over (Act 4 boss)", flush=True)
        break
    if not n:
        for p in ("p1", "p2"):
            q(p, "proceed")
        time.sleep(8)
        for p in ("p1", "p2"):
            q(p, "proceed")
        time.sleep(15)
        n = nxt()
        if not n:
            print("floor %d: no next room" % f, flush=True)
            break
    xy, kind = n.split("=")
    last_kind = kind
    x, y = xy.split(",")
    q("p1", "mp vote %s %s" % (x, y))
    q("p2", "mp vote %s %s" % (x, y))
    time.sleep(7)
    ph = phase("p1")
    res = ""
    if ph == "COMBAT":
        res = fight()
        time.sleep(3)  # show the rewards a moment
    else:
        time.sleep(5)  # show the room
    _, _, hp1, _ = state("p1")
    _, _, hp2, _ = state("p2")
    print("floor %d %s %s %s | Alice %s Bob %s" % (f + 1, kind, ph, res, hp1, hp2), flush=True)
    for p in ("p1", "p2"):
        q(p, "skiproom")
    time.sleep(2)
