#!/bin/bash
# Same LuckPerms nodes on both backends and the proxy, set by offline UUID.
C=$(dirname "$(readlink -f "$0")")/ctl.sh
ADMIN=40c73079-eb42-3445-9f3c-c31a5964a44a
MOD=3f74d6aa-cc43-3f0a-878e-a8969715ec57
GHOST=48f83307-567a-33d7-8604-3b22ee4c1b84
for s in ${1:-a b proxy}; do
  lp=lp; [ $s = proxy ] && lp=lpv
  for n in smthsvanish.use smthsvanish.use.others smthsvanish.list smthsvanish.level.3 smthsvanish.see.3 smthsvanish.silentchest smthsvanish.disguise smthsvanish.disguise.name smthsvanish.disguise.skin; do
    $C cmd $s "$lp user $ADMIN permission set $n true"; sleep 0.2
  done
  for n in smthsvanish.use smthsvanish.level.2 smthsvanish.see.2; do
    $C cmd $s "$lp user $MOD permission set $n true"; sleep 0.2
  done
  for n in smthsvanish.use smthsvanish.auto smthsvanish.level.5 smthsvanish.see.5; do
    $C cmd $s "$lp user $GHOST permission set $n true"; sleep 0.2
  done
  $C cmd $s "$lp user $ADMIN permission set velocity.command.server true"; sleep 0.2
done
