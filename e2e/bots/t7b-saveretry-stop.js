// Redis stalls while Admin vanishes; the write must land once Redis is back, and a switch to B
// after that must stay hidden.
const h = require('./harness');
const { execSync } = require('child_process');
const ADMIN = '40c73079-eb42-3445-9f3c-c31a5964a44a';
const redis = cmd => execSync('docker exec smthsvanish-redis redis-cli ' + cmd).toString().trim();
(async () => {
  const obsB = await h.join('Obs'); h.console('proxy', 'send Obs b');
  const admin = await h.join('Admin'); await h.sleep(2500);
  execSync('docker stop smthsvanish-redis');
  admin.chat('/vanish'); await h.sleep(3500);
  const r = { failMsg: admin.chat_.map(c => c.text).filter(t => t.includes('yazılamadı')).length };
  execSync('docker start smthsvanish-redis'); await h.sleep(7000);
  r.redisAfter = redis('hget smthsvanish:player:' + ADMIN + ' vanished');
  obsB.seen.length = 0; obsB.chat_.length = 0;
  admin.chat('/server b'); await h.sleep(4000);
  r.obsBLeaks = obsB.seen.filter(s => s.uuid === ADMIN || s.name === 'Admin').length + obsB.chat_.filter(c => c.text.includes('Admin')).length;
  admin.chat('/vanish'); await h.sleep(1500);
  r.redisFinal = redis('exists smthsvanish:player:' + ADMIN);
  console.log(JSON.stringify(r));
  admin.chat('/server a'); await h.sleep(1000);
  [obsB, admin].forEach(b => b.quit()); await h.sleep(500); process.exit(0);
})().catch(e => { console.error(e); try { execSync('docker start smthsvanish-redis'); } catch {} process.exit(1); });
