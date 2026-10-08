// Redis is down: a staff member must join hidden, a normal player must join normally.
const h = require('./harness');
const { execSync } = require('child_process');
(async () => {
  const obs = await h.join('Player'); await h.sleep(1000);
  execSync('docker stop smthsvanish-redis');
  const t0 = Date.now();
  const admin = await h.join('Admin');
  const joinMs = Date.now() - t0;
  const mod = await h.join('Obs');
  await h.sleep(2000);
  const r = {
    joinMs,
    obsSeesAdmin: Object.values(obs.players).some(p => p.username === 'Admin') || obs.seen.some(s => s.name === 'Admin'),
    obsSeesObs: Object.values(obs.players).some(p => p.username === 'Obs'),
    adminMsg: admin.chat_.map(c => c.text).filter(t => t.includes('okunamad')),
  };
  execSync('docker start smthsvanish-redis'); await h.sleep(4000);
  admin.chat('/vanish'); await h.sleep(2000); // turn it off: Redis must accept the write again
  r.afterRecovery = { obsSeesAdmin: Object.values(obs.players).some(p => p.username === 'Admin'), redis: execSync('docker exec smthsvanish-redis redis-cli keys "*"').toString().trim() };
  console.log(JSON.stringify(r, null, 1));
  [obs, admin, mod].forEach(b => b.quit()); await h.sleep(500); process.exit(0);
})().catch(e => { console.error(e); try { execSync('docker start smthsvanish-redis'); } catch {} process.exit(1); });
