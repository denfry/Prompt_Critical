#!/usr/bin/env bash
# Start the film client in a world and return once the player is in it.
# Usage: art/film/launch.sh <world> [timeout_s]   (from the repo root; log in $TEMP/pc-film.log)
# Stops any previous dev client or Gradle daemon first, so only one game runs at a time.
set -u
world=${1:?world}
timeout=${2:-600}
log="${TEMP:-/tmp}/pc-film.log"

powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'fml.modFolders|GradleWrapperMain|gradle-wrapper' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
sleep 2
: > "$log"
nohup ./gradlew :pc-mod:runFilm -PquickPlay="$world" > "$log" 2>&1 &

marker='\[film\] player ready|BUILD FAILED|Crash report|Exception in thread "main"'
for ((t = 0; t < timeout; t += 3)); do
    if grep -qE "$marker" "$log"; then
        grep -E "$marker" "$log" | head -1 | sed 's/.*\]: //'
        grep -qE '\[film\] player ready' "$log"
        exit $?
    fi
    sleep 3
done
echo "timeout after ${timeout}s; last log line:"; tail -1 "$log"
exit 1
