// Network: a vanished Admin switches A -> B -> A; Ghost (smthsvanish.auto) joins the network.
// Observers without permissions on both backends record every packet that names a player.
const h = require('./harness');
const ADMIN = '40c73079-eb42-3445-9f3c-c31a5964a44a', GHOST = '48f83307-567a-33d7-8604-3b22ee4c1b84';
const leaks = (bot, uuid, name) => ({
  info: bot.seen.filter(s => s.uuid === uuid || s.name === name || (s.players || []).includes(name)),
  chat: bot.chat_.filter(c => c.text.includes(name)).map(c => c.text.slice(0, 120)),
});
(async () => {
  const obsA = await h.join('Player');
  const admin = await h.join('Admin');
  await h.sleep(1500);
  admin.chat('/vanish'); await h.sleep(1500);
  // Observer on B joins via A then moves: /server needs permission, so ask the proxy console.
  const obsB = await h.join('Obs');
  h.console('proxy', 'send Obs b'); await h.sleep(3000);
  // Start counting only now: before this point the observers legitimately knew the visible Admin.
  obsA.seen.length = 0; obsA.chat_.length = 0; obsB.seen.length = 0; obsB.chat_.length = 0;
  admin.chat('/server b'); await h.sleep(4000);
  admin.chat('/server a'); await h.sleep(4000);
  const ghost = await h.join('Ghost'); await h.sleep(3000);
  h.console('proxy', 'send Ghost b'); await h.sleep(4000);
  const r = {
    adminServerLog: h.log('b').split('\n').filter(l => l.includes('Admin')).slice(-3),
    obsA_admin: leaks(obsA, ADMIN, 'Admin'), obsB_admin: leaks(obsB, ADMIN, 'Admin'),
    obsA_ghost: leaks(obsA, GHOST, 'Ghost'), obsB_ghost: leaks(obsB, GHOST, 'Ghost'),
    adminSeesGhost: Object.values(admin.players).map(p => p.username),
  };
  console.log(JSON.stringify(r, null, 1));
  admin.chat('/vanish'); await h.sleep(1000);
  [obsA, obsB, admin, ghost].forEach(b => b.quit()); await h.sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
