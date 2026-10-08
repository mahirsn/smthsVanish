const h = require('./harness');
(async () => {
  const obs = await h.join('Player'); const admin = await h.join('Admin'); await h.sleep(1500);
  admin.chat('/disguise Kedi'); await h.sleep(3000);
  obs.chat_.length = 0;
  h.console('a', 'kill Admin'); await h.sleep(1500);
  h.console('a', 'summon minecraft:slime 1 -60 -8 {Size:3}'); await h.sleep(200);
  h.console('a', 'damage Admin 100 minecraft:mob_attack by @e[type=slime,limit=1,sort=nearest]'); await h.sleep(1500);
  console.log(JSON.stringify(obs.chat_.map(c => c.text), null, 1));
  admin.chat('/undisguise'); h.console('a', 'kill @e[type=slime]'); await h.sleep(1000);
  [obs, admin].forEach(b => b.quit()); await h.sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
