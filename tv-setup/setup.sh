#!/bin/sh
# Настраивает Android TV (Android 11+) по ADB:
#   сохраняет исходное состояние → отключает мусор из packages.txt → анимации 0.5 и автовремя →
#   ставит лаунчер «Кинотеатр» → делает его главным экраном.
# Всё обратимо: sh restore.sh
#
# Запуск:  sh setup.sh <IP-телевизора> [город для погоды]
# Пример:  sh setup.sh 192.168.1.50 "Санкт-Петербург"
set -u
cd "$(dirname "$0")"
export MSYS_NO_PATHCONV=1

TV_IP="${1:-}"
CITY="${2:-}"
if [ -z "$TV_IP" ]; then
  echo "Укажи IP телевизора: sh setup.sh 192.168.1.50 \"Город\""
  echo "IP: Настройки → Сеть и Интернет → подключённая сеть."
  exit 1
fi
case "$TV_IP" in *:*) TV="$TV_IP" ;; *) TV="$TV_IP:5555" ;; esac

# adb: из PATH или из папки, куда его ставит winget
ADB="${ADB:-}"
if [ -z "$ADB" ]; then
  if command -v adb >/dev/null 2>&1; then
    ADB=adb
  else
    for c in "${LOCALAPPDATA:-}"/Microsoft/WinGet/Packages/Google.PlatformTools*/platform-tools/adb.exe; do
      [ -f "$c" ] && ADB="$c"
    done
  fi
fi
if [ -z "$ADB" ]; then
  echo "Не найден adb. Установи: winget install Google.PlatformTools — и запусти скрипт снова."
  exit 1
fi

tv() { "$ADB" -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
step() { printf '\n== %s\n' "$1"; }

step "Подключаюсь к $TV"
"$ADB" connect "$TV" >/dev/null 2>&1
st=""; asked=0
for i in $(seq 1 45); do
  st=$("$ADB" devices | tr -d '\r' | awk -v t="$TV" '$1 == t {print $2}')
  [ "$st" = "device" ] && break
  if [ "$st" = "unauthorized" ] && [ $asked = 0 ]; then
    echo "На экране телевизора появился запрос: поставь «Всегда разрешать с этого компьютера» и нажми ОК."
    asked=1
  fi
  [ -z "$st" ] && "$ADB" connect "$TV" >/dev/null 2>&1
  sleep 2
done
if [ "$st" != "device" ]; then
  echo "Не удалось подключиться (статус: ${st:-нет ответа})."
  echo "Проверь IP и что в «Для разработчиков» включена «Отладка по USB»."
  exit 1
fi

sdk=$(tv getprop ro.build.version.sdk)
model=$(tv getprop ro.product.model)
echo "Телевизор: $model, Android SDK $sdk"
if [ "${sdk:-0}" -lt 30 ]; then
  echo "Лаунчеру нужен Android 11 (SDK 30) или новее. Останавливаюсь, ничего не менял."
  exit 1
fi

B="backups/$(echo "$model" | tr ' /' '__')-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$B"
step "Сохраняю исходное состояние в $B"
echo "$TV" > "$B/tv.txt"
tv pm list packages -d | sed 's/^package://' > "$B/disabled-before.txt"
for k in window_animation_scale transition_animation_scale animator_duration_scale auto_time auto_time_zone; do
  echo "$k=$(tv settings get global "$k")"
done > "$B/settings-before.txt"
: > "$B/disabled-by-setup.txt"

step "Отключаю рекламу и телеметрию (обратимо)"
installed=$(tv pm list packages | sed 's/^package://')
grep -vE '^[[:space:]]*(#|$)' packages.txt | while read -r p _; do
  echo "$installed" | grep -qx "$p" || continue
  grep -qx "$p" "$B/disabled-before.txt" && continue
  if tv pm disable-user --user 0 "$p" | grep -q "disabled"; then
    echo "  отключено: $p"
    echo "$p" >> "$B/disabled-by-setup.txt"
  fi
done

step "Анимации 0.5 и автоматическое время"
for k in window_animation_scale transition_animation_scale animator_duration_scale; do
  tv settings put global "$k" 0.5
done
tv settings put global auto_time 1
tv settings put global auto_time_zone 1

step "Ставлю лаунчер «Кинотеатр»"
"$ADB" -s "$TV" install -r kinoteatr.apk </dev/null | tr -d '\r' | tail -1
tv input keyevent KEYCODE_WAKEUP
tv am start -W -n app.kinoteatr.launcher/.MainActivity >/dev/null
sleep 2
if ! tv dumpsys activity activities | grep -m1 mResumedActivity | grep -q kinoteatr; then
  echo "Лаунчер не запустился. Системный лаунчер не трогаю. Откат: sh restore.sh"
  exit 1
fi

if [ -n "$CITY" ]; then
  step "Город для погоды: $CITY"
  # Percent-encode by hand: on Windows, Cyrillic in curl's arguments gets mangled.
  q=$(printf '%s' "$CITY" | od -An -tx1 | tr -s ' \n' ' ' | sed 's/^ //; s/ $//; s/ /%/g; s/^/%/')
  geo=$(curl -s "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=ru" 2>/dev/null)
  lat=$(echo "$geo" | grep -oE '"latitude":[-0-9.]+' | head -1 | cut -d: -f2)
  lon=$(echo "$geo" | grep -oE '"longitude":[-0-9.]+' | head -1 | cut -d: -f2)
  name=$(echo "$geo" | grep -oE '"name":"[^"]+"' | head -1 | cut -d'"' -f4)
  if [ -n "$lat" ] && [ -n "$lon" ]; then
    tv am start -n app.kinoteatr.launcher/.MainActivity --es city "'${name:-$CITY}'" --es lat "$lat" --es lon "$lon" >/dev/null 2>&1
    echo "  ${name:-$CITY}: $lat, $lon"
  else
    echo "  Не нашёл такой город, оставляю Минск. Можно запустить setup.sh ещё раз с другим написанием."
  fi
fi

step "Делаю «Кинотеатр» главным экраном"
tv cmd package set-home-activity app.kinoteatr.launcher/.MainActivity >/dev/null
home() { tv cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME \
  | grep -m1 packageName | sed 's/.*packageName=//'; }
h=$(home)
if [ "$h" != "app.kinoteatr.launcher" ]; then
  echo "Системный лаунчер ($h) стоит выше по приоритету, и кнопка «Домой» откроет его."
  echo "Чтобы «Домой» открывал «Кинотеатр», системный нужно отключить. restore.sh включит его обратно."
  printf "Отключить %s? [y/N] " "$h"
  read -r answer
  case "$answer" in
    y|Y|д|Д)
      tv pm disable-user --user 0 "$h" >/dev/null
      echo "$h" >> "$B/disabled-by-setup.txt"
      tv am force-stop "$h"
      ;;
  esac
fi
tv input keyevent KEYCODE_HOME
sleep 2
if tv dumpsys activity activities | grep -m1 mResumedActivity | grep -q kinoteatr; then
  echo "Кнопка «Домой» открывает «Кинотеатр»."
else
  echo "Кнопка «Домой» пока открывает системный лаунчер. «Кинотеатр» можно запускать из списка приложений."
fi

step "Готово"
echo "Отключено пакетов: $(wc -l < "$B/disabled-by-setup.txt" | tr -d ' '). Резервная копия: $B"
echo "Управление: долгое OK на плитке — меню (открыть, переместить, избранное, удалить)."
echo "Откат всего: sh restore.sh"
echo "После настройки выключи «Отладку по USB» в «Для разработчиков»."
