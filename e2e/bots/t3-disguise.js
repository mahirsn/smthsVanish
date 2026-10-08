// Disguise: Admin becomes "Kedi". Observers must learn only Kedi (tab, entity, chat, switch).
const h = require('./harness');
const ADMIN = '40c73079-eb42-3445-9f3c-c31a5964a44a';
const tab = bot => Object.values(bot.players).map(p => p.username);
const mentions = (bot, name) => ({
  info: bot.seen.filter(s => s.name === name || (s.players || []).includes(name) || (s.display || '').includes(name) || (s.prefix || '').includes(name)),
  chat: bot.chat_.filter(c => c.text.includes(name)).map(c => c.text.slice(0, 140)),
});
(async () => {
  const obsA = await h.join('Player');
  const admin = await h.join('Admin');
  const obsB = await h.join('Obs'); h.console('proxy', 'send Obs b');
  await h.sleep(2500);
  obsA.seen.length = 0; obsA.chat_.length = 0;
  admin.chat('/disguise Kedi'); await h.sleep(4000);
  const r = { adminSelfTab: tab(admin), obsATab: tab(obsA) };
  r.obsAEntity = Object.values(obsA.entities).filter(e => e.type === 'player').map(e => e.username);
  admin.chat('merhaba ben Admin'); await h.sleep(1000);
  h.console('a', 'say Admin burada mi?'); await h.sleep(1000);
  obsB.seen.length = 0; obsB.chat_.length = 0;
  admin.chat('/server b'); await h.sleep(4000);
  r.obsBTab = tab(obsB);
  admin.chat('B de selam'); await h.sleep(1000);
  r.obsA_realName = mentions(obsA, 'Admin'); r.obsB_realName = mentions(obsB, 'Admin');
  r.obsA_fake = mentions(obsA, 'Kedi').chat; r.obsB_fake = mentions(obsB, 'Kedi');
  admin.chat('/undisguise'); await h.sleep(3000);
  r.obsBTabAfterUndisguise = tab(obsB);
  console.log(JSON.stringify(r, null, 1));
  admin.chat('/server a'); await h.sleep(1500);
  [obsA, obsB, admin].forEach(b => b.quit()); await h.sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
