#!/bin/bash
# Build, copy the Paper jar to both backends and restart them.
set -e
T=$(dirname "$(readlink -f "$0")")
cd $T/.. && ./gradlew --no-daemon -q build
for s in a b; do cp smthsVanish-paper/build/libs/smthsVanish-paper-0.1.0.jar $T/run/$s/plugins/; done
for s in a b; do echo stop > $T/run/$s/in; done
for i in $(seq 1 40); do
  alive=0; for p in $(pgrep -x java); do case "$(readlink /proc/$p/cwd)" in $T/run/a|$T/run/b) alive=1;; esac; done
  [ $alive = 0 ] && break; sleep 1
done
for p in $(pgrep -f "while true; do cat in"); do case "$(readlink /proc/$p/cwd)" in $T/run/a|$T/run/b) kill $p;; esac; done
for s in a b; do : > $T/run/$s/console.log; done
$T/ctl.sh start "a b" </dev/null >/dev/null 2>&1
until grep -q "Done (" $T/run/a/console.log && grep -q "Done (" $T/run/b/console.log; do sleep 2; done
echo redeployed
