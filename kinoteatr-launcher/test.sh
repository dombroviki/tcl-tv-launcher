#!/bin/sh
# On-device smoke tests for the launcher. Needs the TV reachable over ADB.
# Resets the launcher's own settings (favourites) before and after the run.
export MSYS_NO_PATHCONV=1
ADB="${ADB:-adb}"; T="${TV:?Укажи телевизор: TV=192.168.1.50:5555 sh test.sh}"
S="$ADB -s $T shell"
"$ADB" connect "$T" >/dev/null
pass=0; fail=0
ok(){ if [ "$2" = "1" ]; then echo "PASS  $1"; pass=$((pass+1)); else echo "FAIL  $1  ($3)"; fail=$((fail+1)); fi; }
top(){ $S dumpsys activity activities | grep -m1 mResumedActivity | grep -oE "[a-z0-9_.]+/[A-Za-z0-9_.]+"; }
dump(){ $S uiautomator dump /sdcard/u.xml >/dev/null 2>&1; $S cat /sdcard/u.xml | tr '>' '\n'; }
focused(){ dump | grep 'focused="true"' | grep -oE '(text|content-desc)="[^"]+"' | head -1; }
# favourites in on-screen order: clickable tiles whose top edge is inside the tray band (y 600..800)
favs(){ dump | grep 'clickable="true"' | grep -E 'bounds="\[[0-9]+,(6[0-9][0-9]|7[0-9][0-9])\]' | grep -oE 'content-desc="[^"]+"' | sed 's/content-desc=//' | tr '\n' ' '; }
tops(){ dump | grep 'clickable="true"' | grep -v 'focused="true"' | grep -E 'bounds="\[[0-9]+,(6[0-9][0-9]|7[0-9][0-9])\]' | grep -oE 'bounds="\[[0-9]+,[0-9]+' | sed 's/.*,//' | sort -u | tr '\n' ' '; }
fresh(){ $S am force-stop app.kinoteatr.launcher; $S am start -W -n app.kinoteatr.launcher/.MainActivity >/dev/null; sleep 1.5; }
key(){ for k in "$@"; do $S input keyevent "$k"; done; sleep 0.8; }
long(){ $S input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 1; }
menu_pick(){ # $1 = item label; walks down the open menu until that item is focused, then presses OK
  for i in 1 2 3 4 5 6; do f=$(focused); echo "$f" | grep -q "$1" && { key KEYCODE_DPAD_CENTER; return 0; }; $S input keyevent KEYCODE_DPAD_DOWN; sleep .4; done; return 1; }

inmenu(){ echo "$1" | grep -qE 'Открыть|Переместить|Убрать из избранного|Добавить в избранное|О приложении|Удалить'; }
walk_menu(){ # $1 = where; opens menu on focused tile, goes 8x down and 8x up, checks focus never leaves
  long; okk=1; last=""
  for i in 1 2 3 4 5 6 7 8; do $S input keyevent KEYCODE_DPAD_DOWN; sleep .3; f=$(focused); inmenu "$f" || { okk=0; last="down#$i: $f"; break; }; done
  ok "$1: 8x down stays in menu (ends on: $(echo $f | sed 's/text=//'))" "$( [ $okk = 1 ] && echo 1)" "$last"
  for i in 1 2 3 4 5 6 7 8; do $S input keyevent KEYCODE_DPAD_UP; sleep .2; done; f=$(focused)
  ok "$1: 8x up returns to 'Открыть'" "$(echo "$f" | grep -q 'Открыть' && echo 1)" "$f"
  $S input keyevent KEYCODE_DPAD_LEFT KEYCODE_DPAD_RIGHT; sleep .4; f=$(focused)
  ok "$1: left/right stay in menu" "$(inmenu "$f" && echo 1)" "$f"
  key KEYCODE_BACK
}
fbox(){ dump | grep 'focused="true"' | grep -oE 'content-desc="[^"]+"|bounds="[^"]+"' | tr '
' ' '; }
where(){ dump | grep "content-desc=\"$1\"" | grep -oE 'bounds="[^"]+"' | head -1; }
center(){ echo "$1" | grep -oE '[0-9]+' | tr '
' ' ' | awk '{print int(($1+$3)/2)","int(($2+$4)/2)}'; } # focus scaling changes the box, not the centre
stays_put(){ # $1 = where; remaining args = keys to get there. Screen must not move and focus must come back.
  name="$1"; shift
  fresh; for k in "$@"; do $S input keyevent "$k"; done; sleep 1.2
  f0=$(fbox); d=$(echo "$f0" | grep -oE 'content-desc="[^"]+"' | sed 's/content-desc="//;s/"$//'); b0=$(echo "$f0" | grep -oE 'bounds="[^"]+"')
  long; b=$(where "$d")
  ok "$name ($d): screen stays while menu is open" "$( [ "$(center "$b")" = "$(center "$b0")" ] && echo 1)" "centre $(center "$b0") -> $(center "$b")"
  key KEYCODE_BACK; sleep .6; f=$(fbox)
  ok "$name ($d): Back returns focus to same tile, same place" "$( [ "$f" = "$f0" ] && echo 1)" "$f0 -> $f"
  long; menu_pick "О приложении"; sleep 2.5; a=$(top)
  key KEYCODE_BACK; sleep 1.5; f=$(fbox)
  ok "$name ($d): 'О приложении' and back keeps place" "$(echo "$a" | grep -q settings && [ "$f" = "$f0" ] && echo 1)" "opened=$a; $f0 -> $f"
}
$S input keyevent KEYCODE_WAKEUP; $S logcat -c; $S pm clear app.kinoteatr.launcher >/dev/null

fresh; f=$(focused); ok "cold start focuses first favourite" "$(echo "$f" | grep -qi Lampa && echo 1)" "$f"
key KEYCODE_DPAD_RIGHT; f=$(focused); ok "right moves to SmartTube" "$(echo "$f" | grep -q SmartTube && echo 1)" "$f"
key KEYCODE_BACK; ok "back on home is ignored" "$(top | grep -q kinoteatr && echo 1)" "$(top)"
key KEYCODE_DPAD_CENTER; sleep 2; a=$(top); ok "OK opens focused app" "$(echo "$a" | grep -q smarttube && echo 1)" "$a"
$S am force-stop org.smarttube.stable

fresh; key KEYCODE_DPAD_DOWN; f=$(focused); ok "down goes to inputs row" "$(echo "$f" | grep -qE 'HDMI|Эфир' && echo 1)" "$f"
key KEYCODE_DPAD_DOWN; f=$(focused); ok "down again goes into all-apps grid" "$(echo "$f" | grep -q content-desc && echo 1)" "$f"
key KEYCODE_DPAD_UP KEYCODE_DPAD_UP; f=$(focused); ok "up twice returns to tray" "$(echo "$f" | grep -qiE 'Lampa|SmartTube|TorrServe|Vimu|Spotify|Steam|AniLibria' && echo 1)" "$f"

fresh; key KEYCODE_DPAD_DOWN; for i in 1 2 3 4 5 6 7 8; do $S input keyevent KEYCODE_DPAD_RIGHT; done; sleep .8
f=$(focused); ok "settings is last in inputs row" "$(echo "$f" | grep -q 'Настройки' && echo 1)" "$f"
key KEYCODE_DPAD_CENTER; sleep 1.5; a=$(top); ok "settings opens" "$(echo "$a" | grep -q tv.settings && echo 1)" "$a"
$S input keyevent KEYCODE_BACK

fresh; key KEYCODE_DPAD_DOWN; for i in 1 2 3 4 5 6 7 8; do $S input keyevent KEYCODE_DPAD_LEFT; done; sleep .8
f=$(focused); ok "HDMI 1 is first input" "$(echo "$f" | grep -q 'HDMI 1' && echo 1)" "$f"
key KEYCODE_DPAD_LEFT; f=$(focused); ok "left on first input stays put" "$(echo "$f" | grep -q 'HDMI 1' && echo 1)" "$f"
key KEYCODE_DPAD_CENTER; sleep 3; a=$(top); ok "HDMI 1 opens TV player" "$(echo "$a" | grep -qE 'com.tcl.tv' && echo 1)" "$a"

# --- context menu
fresh; long; f=$(focused); ok "long press opens menu on 'Открыть'" "$(echo "$f" | grep -q 'Открыть' && echo 1)" "$f"
ok "long press did not launch the app" "$(top | grep -q kinoteatr && echo 1)" "$(top)"
key KEYCODE_DPAD_UP; f=$(focused); ok "up on first menu item stays in menu" "$(echo "$f" | grep -q 'Открыть' && echo 1)" "$f"
key KEYCODE_BACK; f=$(focused); ok "back closes menu and returns to the tile" "$(echo "$f" | grep -qi Lampa && echo 1)" "$f"

# --- the hero must not redraw when focus returns to the same tile (user-reported flicker)
fresh; $S logcat -c; long; key KEYCODE_BACK; sleep .8
n=$($S logcat -d -s Kinoteatr:V | grep -c "hero swap")
ok "menu open + Back on Lampa: title not redrawn" "$( [ "$n" = "0" ] && echo 1)" "$n swaps"
fresh; key KEYCODE_DPAD_RIGHT; $S logcat -c; long; key KEYCODE_BACK; sleep .8
n=$($S logcat -d -s Kinoteatr:V | grep -c "hero swap")
ok "menu open + Back on SmartTube: title not redrawn" "$( [ "$n" = "0" ] && echo 1)" "$n swaps"
fresh; $S logcat -c; key KEYCODE_DPAD_RIGHT; sleep .5
n=$($S logcat -d -s Kinoteatr:V | grep -c "hero swap: SmartTube")
ok "moving to another app still swaps the title" "$( [ "$n" = "1" ] && echo 1)" "$n swaps"

# --- Home from the bottom of the grid (user-reported: title disappeared)
for depth in 2 4 6; do
  fresh; i=0; while [ $i -lt $depth ]; do $S input keyevent KEYCODE_DPAD_DOWN; i=$((i+1)); done; sleep 1
  $S input keyevent KEYCODE_HOME; sleep 1.5
  f=$(focused); title=$(dump | grep -oE 'text="LAMPA"' | head -1)
  ok "Home after $depth x down: focus on first favourite and its title shown" "$(echo "$f" | grep -qi lampa && [ -n "$title" ] && echo 1)" "focus=$f title=$title"
done

# --- screen position must survive the menu (user-reported jump to top)
stays_put "favourite #1"
stays_put "favourite #5" KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT
stays_put "grid row 1" KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT
stays_put "grid row 2" KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN
stays_put "grid row 3" KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN

# --- menu focus must never escape (user-reported bug)
fresh; walk_menu "favourite Lampa"
fresh; key KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT; walk_menu "favourite #5"
fresh; key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN; walk_menu "grid tile 1"
fresh; key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT; walk_menu "grid tile 6"
fresh; key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN; walk_menu "grid row 2"
fresh; long; $S input keyevent KEYCODE_HOME; sleep 1; f=$(focused)
ok "Home while menu is open closes it" "$(inmenu "$f" || echo 1)" "$f"

fresh; long; menu_pick "Переместить"; key KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_CENTER; sleep .6
ok "move mode does not launch apps" "$(top | grep -q kinoteatr && echo 1)" "$(top)"
t=$(tops); ok "all favourites aligned after move (one top edge)" "$( [ $(echo $t | wc -w) = 1 ] && echo 1)" "tops: $t"
$S am force-stop app.kinoteatr.launcher; fresh
o=$(favs); ok "new order persists (Lampa third)" "$(echo "$o" | awk -F'" "' '{print $3}' | grep -qi lampa && echo 1)" "$o"

$S pm clear app.kinoteatr.launcher >/dev/null
fresh; key KEYCODE_DPAD_RIGHT; long; menu_pick "Убрать из избранного"; sleep .5
f=$(focused); ok "menu removes it: focus follows the app down into the grid" "$(echo "$f" | grep -q SmartTube && echo 1)" "$f"
found=$(echo "$f" | grep -c SmartTube)
long; menu_pick "Добавить в избранное"; sleep .5
o=$(favs); ok "menu adds SmartTube back from the grid" "$( [ $found = 1 ] && echo "$o" | grep -q SmartTube && echo 1)" "found=$found; $o"

fresh; key KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN; long
f=$(dump | grep -c 'В избранном нет места'); ok "full favourites: menu explains instead of offering add" "$( [ "$f" -ge 1 ] && echo 1)" "$f"
key KEYCODE_BACK

$S pm clear app.kinoteatr.launcher >/dev/null
fresh
for i in $(seq 1 15); do $S input keyevent KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_UP KEYCODE_DPAD_UP KEYCODE_DPAD_LEFT; done; sleep 2  # warm-up: banners load lazily
before=$($S dumpsys meminfo app.kinoteatr.launcher | grep -m1 'TOTAL PSS' | awk '{print $3}')
for i in $(seq 1 15); do $S input keyevent KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_RIGHT KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_DPAD_LEFT KEYCODE_DPAD_LEFT KEYCODE_DPAD_UP KEYCODE_DPAD_UP KEYCODE_DPAD_LEFT; done
sleep 2; after=$($S dumpsys meminfo app.kinoteatr.launcher | grep -m1 'TOTAL PSS' | awk '{print $3}')
ok "memory stable after 150 key presses ($before -> $after KB)" "$( [ $((after - before)) -lt 8000 ] && echo 1)" "grew $((after-before)) KB"
t=$(tops); ok "favourites aligned after navigation" "$( [ $(echo $t | wc -w) = 1 ] && echo 1)" "tops: $t"

$S dumpsys gfxinfo app.kinoteatr.launcher reset >/dev/null
for i in $(seq 1 10); do $S input keyevent KEYCODE_DPAD_RIGHT KEYCODE_DPAD_LEFT; done; sleep 1
$S dumpsys gfxinfo app.kinoteatr.launcher | grep -E "Total frames|Janky frames|90th percentile" | sed 's/^/      /'

pid=$($S pidof app.kinoteatr.launcher); sleep 22
cpu=$($S top -b -n 2 -d 3 -p $pid | grep -E "^ *$pid" | tail -1 | awk '{print $9}')
echo "      idle CPU on home screen after 25 s: ${cpu}%"

crash=$($S logcat -d -b crash | grep -c kinoteatr)
ok "no crashes in log" "$( [ "$crash" = "0" ] && echo 1)" "$crash crash lines"
$S pm clear app.kinoteatr.launcher >/dev/null; $S am start -n app.kinoteatr.launcher/.MainActivity >/dev/null  # leave default favourites
echo "passed $pass, failed $fail"
