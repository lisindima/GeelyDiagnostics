#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
apk_path="${1:-$project_dir/GeelyDiagnostics-v0.4.0-debug.apk}"
package_name="com.geelydiagnostics.app"
activity_name="$package_name/.MainActivity"
timestamp="$(date +%Y%m%d-%H%M%S)"
result_dir="$project_dir/diagnostics-results/$timestamp"

if ! command -v adb >/dev/null 2>&1; then
    echo "Ошибка: adb не найден. Установите Android SDK Platform Tools." >&2
    exit 1
fi

if [[ ! -f "$apk_path" ]]; then
    echo "Ошибка: APK не найден: $apk_path" >&2
    exit 1
fi

device_lines="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
device_count="$(printf '%s\n' "$device_lines" | awk 'NF { count++ } END { print count+0 }')"
if [[ "$device_count" -ne 1 ]]; then
    echo "Ошибка: требуется ровно одно подключённое и авторизованное устройство; найдено: $device_count" >&2
    adb devices -l >&2
    exit 1
fi

mkdir -p "$result_dir"
serial="$(printf '%s\n' "$device_lines" | awk 'NF { print; exit }')"

echo "Устройство: $serial"
echo "Устанавливаю: $apk_path"
adb -s "$serial" install -r "$apk_path"

adb -s "$serial" shell am force-stop "$package_name"
adb -s "$serial" shell am start -W -n "$activity_name" >"$result_dir/start.txt"

echo
echo "Дождитесь завершения проверки на экране ГУ."
echo "Если нужно, нажмите «Обновить» после полной загрузки системы."
read -r -p "Нажмите Enter, чтобы сохранить screenshot и logcat... "

adb -s "$serial" exec-out screencap -p >"$result_dir/screenshot.png"
adb -s "$serial" logcat -d -v threadtime -s GeelyDiagnostics:I '*:S' >"$result_dir/logcat.txt"
{
    echo "serial=$serial"
    echo "captured_at=$(date '+%Y-%m-%dT%H:%M:%S%z')"
    echo "android_release=$(adb -s "$serial" shell getprop ro.build.version.release | tr -d '\r')"
    echo "sdk=$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
    echo "fingerprint=$(adb -s "$serial" shell getprop ro.build.fingerprint | tr -d '\r')"
    shasum -a 256 "$apk_path"
} >"$result_dir/device-and-apk.txt"

echo "Результат сохранён в: $result_dir"
