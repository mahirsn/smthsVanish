#!/bin/bash
# Test network control: start|cmd|stop|log
T=$(dirname "$(readlink -f "$0")")
case "$1" in
  start)
    for s in ${2:-proxy a b}; do
      d=$T/run/$s; rm -f $d/in; mkfifo $d/in
      jar=paper.jar; args="--nogui"; [ $s = proxy ] && { jar=velocity.jar; args=""; }
      (cd $d && nohup bash -c "while true; do cat in; done | java -Xmx1G -jar $jar $args" > $d/console.log 2>&1 &)
    done ;;
  cmd) echo "$3" > $T/run/$2/in ;;
  stop)
    echo end > $T/run/proxy/in; echo stop > $T/run/a/in; echo stop > $T/run/b/in
    sleep 8; pkill -f "while true; do cat in" ; pkill -f "java -Xmx1G -jar (paper|velocity).jar" ;;
  log) tail -n ${3:-30} $T/run/$2/console.log ;;
esac
