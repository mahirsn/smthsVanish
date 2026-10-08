// Levels: Admin (level 3, see 3) vanishes. Player (no see) and Mod (see 2) must lose him;
// Mod vanishes (level 2): Admin keeps seeing Mod, Player does not.
const h = require('./harness');
const ADMIN = '40c73079-eb42-3445-9f3c-c31a5964a44a', MOD = '3f74d6aa-cc43-3f0a-878e-a8969715ec57';
const tab = bot => new Set(Object.values(bot.players).map(p => p.username));
const ent = (bot, name) => Object.values(bot.entities).some(e => e.type === 'player' && e.username === name);
(async () => {
  const player = await h.join('Player'); const mod = await h.join('Mod'); const admin = await h.join('Admin');
  await h.sleep(2500);
  const r = {};
  r.before = { playerTab: [...tab(player)], playerSeesAdminEntity: ent(player, 'Admin') };
  admin.chat('/vanish'); await h.sleep(2000);
  r.afterAdminVanish = {
    playerTab: [...tab(player)], modTab: [...tab(mod)], adminTab: [...tab(admin)],
    playerSeesAdminEntity: ent(player, 'Admin'), modSeesAdminEntity: ent(mod, 'Admin'),
    playerChat: player.chat_.map(c => c.text.trim()).filter(Boolean),
  };
  mod.chat('/vanish'); await h.sleep(2000);
  r.afterModVanish = { playerTab: [...tab(player)], adminTab: [...tab(admin)], adminSeesModEntity: ent(admin, 'Mod'), playerSeesModEntity: ent(player, 'Mod') };
  // tab-complete from Player for a command taking names
  player._client.write('tab_complete', { transactionId: 7, text: '/tell ' }); await h.sleep(1000);
  r.playerTabComplete = player.seen.filter(s => s.packet === 'tab_complete').map(s => s.matches);
  mod.chat('/vanish'); admin.chat('/vanish'); await h.sleep(2000);
  r.afterBothOff = { playerTab: [...tab(player)], playerSeesAdminEntity: ent(player, 'Admin'), playerChat: player.chat_.map(c => c.text.trim()).filter(Boolean) };
  console.log(JSON.stringify(r, null, 1));
  [player, mod, admin].forEach(b => b.quit()); await h.sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
