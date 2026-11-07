#!/bin/sh
# Откатывает то, что сделал setup.sh, по его резервной копии.
# Запуск:  sh restore.sh                     — последняя копия из backups/
#          sh restore.sh backups/<папка>     — конкретная копия
set -u
cd "$(dirname "$0")"
export MSYS_NO_PATHCONV=1

B="${1:-$(ls -d backups/*/ 2>/dev/null | sort | tail -1)}"
B="${B%/}"
if [ -z "$B" ] || [ ! -f "$B/tv.txt" ]; then
  echo "Не нашёл резервную копию в backups/. Укажи папку: sh restore.sh backups/<папка>"
  exit 1
fi
TV=$(cat "$B/tv.txt")

ADB="${ADB:-}"
if [ -z "$ADB" ]; then
  if command -v adb >/dev/null 2>&1; then ADB=adb
  else for c in "${LOCALAPPDATA:-}"/Microsoft/WinGet/Packages/Google.PlatformTools*/platform-tools/adb.exe; do [ -f "$c" ] && ADB="$c"; done
  fi
fi
[ -z "$ADB" ] && { echo "Не найден adb. Установи: winget install Google.PlatformTools"; exit 1; }
tv() { "$ADB" -s "$TV" shell "$@" </dev/null | tr -d '\r'; }

echo "Копия: $B, телевизор: $TV"
"$ADB" connect "$TV" >/dev/null 2>&1
sleep 1
if ! "$ADB" devices | tr -d '\r' | grep -q "^$TV[[:space:]]*device"; then
  echo "Телевизор не отвечает. Если сменился IP, впиши новый в $B/tv.txt."
  exit 1
fi

echo "== Включаю обратно отключённые пакеты"
while read -r p; do
  [ -n "$p" ] && tv pm enable --user 0 "$p" >/dev/null && echo "  включено: $p"
done < "$B/disabled-by-setup.txt"

echo "== Возвращаю настройки"
while IFS='=' read -r k v; do
  ns=global
  case "$k" in secure:*) ns=secure; k=${k#secure:} ;; esac
  if [ "$v" = "null" ] || [ -z "$v" ]; then tv settings delete "$ns" "$k" >/dev/null
  else tv settings put "$ns" "$k" "$v"; fi
  echo "  $k = $v"
done < "$B/settings-before.txt"

printf "Удалить лаунчер «Кинотеатр»? [y/N] "
read -r answer
case "$answer" in
  y|Y|д|Д)
    # The launcher first gives up its screen-off right, otherwise Android refuses to uninstall it.
    tv am start -n app.kinoteatr.launcher/.MainActivity --ez dropAdmin true >/dev/null 2>&1
    sleep 1
    "$ADB" -s "$TV" uninstall app.kinoteatr.launcher </dev/null | tr -d '\r' ;;
  *) if grep -q 'launcher' "$B/disabled-by-setup.txt"; then
       echo "Оставляю. Главным экраном снова будет системный лаунчер."
     else
       echo "Оставляю."
     fi ;;
esac
tv input keyevent KEYCODE_HOME
echo "Готово."
