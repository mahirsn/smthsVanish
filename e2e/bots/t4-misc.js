// Silent chest, chat block, ping count, live LuckPerms change.
const h = require('./harness');
const mc = require('minecraft-protocol');
const { Vec3 } = require('vec3');
const tab = bot => Object.values(bot.players).map(p => p.username);
const chestActions = bot => bot.seen.filter(s => s.packet === 'block_action' && s.location && s.location.x === 3 && s.location.z === -6);
(async () => {
  h.console('a', 'setblock 3 -60 -6 minecraft:chest'); await h.sleep(500);
  h.console('a', 'lp user 3f74d6aa-cc43-3f0a-878e-a8969715ec57 permission unset smthsvanish.see.3');
  const player = await h.join('Player'); const admin = await h.join('Admin'); const mod = await h.join('Mod');
  await h.sleep(2000);
  h.console('a', 'tp Player 3 -60 -3'); h.console('a', 'tp Admin 3 -60 -4'); await h.sleep(1500);
  const r = {};
  const chest = admin.blockAt(new Vec3(3, -60, -6));
  r.chestBlock = chest && chest.name;
  // Control: visible Admin opens the chest; Player must get the lid animation.
  player.seen.length = 0;
  await admin.lookAt(chest.position.offset(0.5, 0.5, 0.5), true); await h.sleep(300); await admin.activateBlock(chest, new Vec3(0, 1, 0), new Vec3(0.5, 1, 0.5)).catch(e => r.clickError = String(e)); await h.sleep(1500);
  r.controlVisibleOpen = chestActions(player).length;
  r.controlWindow = admin.seen.filter(s => s.packet === 'open_window'); admin.seen.length = 0; if (admin.currentWindow) admin.closeWindow(admin.currentWindow); await h.sleep(1500);
  admin.chat('/vanish'); await h.sleep(3000);
  h.console('a', 'tp Admin 3 -60 -4'); await h.sleep(1000);
  player.seen.length = 0;
  let opened = null; admin.once('windowOpen', w => { opened = w.title ? h.text(w.title) : true; });
  await admin.lookAt(chest.position.offset(0.5, 0.5, 0.5), true); await h.sleep(300); await admin.activateBlock(chest, new Vec3(0, 1, 0), new Vec3(0.5, 1, 0.5)).catch(e => r.clickError = String(e)); await h.sleep(1500);
  r.vanishedOpen = { playerChestActions: chestActions(player).length, adminWindows: admin.seen.filter(s => s.packet === 'open_window') };
  if (admin.currentWindow) admin.closeWindow(admin.currentWindow);
  // Chat while vanished.
  player.chat_.length = 0; admin.chat_.length = 0;
  admin.chat('gizli mesaj'); await h.sleep(1000);
  r.chat = { player: player.chat_.map(c => c.text).filter(t => t.includes('gizli')), admin: admin.chat_.map(c => c.text).filter(t => t.includes('chat')) };
  // Server list through the proxy.
  r.ping = await new Promise(res => mc.ping({ host: '127.0.0.1', port: 25600, version: '26.1' }, (e, d) => res(e ? String(e) : { online: d.players.online, sample: (d.players.sample || []).map(s => s.name) })));
  // LuckPerms: Mod gets see.3 while Admin (level 3) is vanished.
  r.modBefore = tab(mod).includes('Admin');
  h.console('a', 'lp user 3f74d6aa-cc43-3f0a-878e-a8969715ec57 permission set smthsvanish.see.3 true'); await h.sleep(2500);
  r.modAfterGrant = tab(mod).includes('Admin');
  h.console('a', 'lp user 3f74d6aa-cc43-3f0a-878e-a8969715ec57 permission unset smthsvanish.see.3'); await h.sleep(2500);
  r.modAfterRevoke = tab(mod).includes('Admin');
  r.playerTab = tab(player);
  console.log(JSON.stringify(r, null, 1));
  admin.chat('/vanish'); await h.sleep(1000);
  [player, admin, mod].forEach(b => b.quit()); await h.sleep(500); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
