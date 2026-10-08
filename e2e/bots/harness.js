// Small E2E harness: bots join through the proxy, every packet that could name a player is
// recorded per bot, and console commands go to the servers through their fifo.
const mineflayer = require('mineflayer');
const fs = require('fs');
const path = require('path');
const RUN = path.join(__dirname, '..', 'run');
const VERSION = process.env.MCV || '26.1';

const sleep = ms => new Promise(r => setTimeout(r, ms));
const console_ = (server, cmd) => fs.writeFileSync(path.join(RUN, server, 'in'), cmd + '\n');
const log = (server) => fs.readFileSync(path.join(RUN, server, 'console.log'), 'utf8');

function text(json) {
  // Every string leaf of a chat component, in any of its encodings (JSON, NBT, objects).
  const out = [];
  const walk = v => {
    if (v == null) return;
    if (typeof v === 'string') { try { const j = JSON.parse(v); if (typeof j === 'object') return walk(j); } catch {} out.push(v); return; }
    if (Array.isArray(v)) return v.forEach(walk);
    if (typeof v === 'object') for (const [k, x] of Object.entries(v)) if (k !== 'type' && k !== 'color') walk(x);
  };
  walk(json);
  return out.join(' ');
}

function join(name, opts = {}) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25600, username: name, version: VERSION, auth: 'offline', ...opts });
    bot.seen = [];          // every packet that mentions another player, as {t, name, ...}
    bot.chat_ = [];
    const t0 = Date.now();
    bot._client.on('packet', (data, meta) => {
      const n = meta.name;
      const at = Date.now() - t0;
      if (n === 'player_info') {
        for (const e of data.data || []) {
          bot.seen.push({ at, packet: n, uuid: e.uuid, name: e.player?.name, display: e.displayName ? text(e.displayName) : undefined, listed: e.listed });
        }
      } else if (n === 'named_entity_spawn' || (n === 'spawn_entity' && data.objectUUID)) {
        bot.seen.push({ at, packet: n, uuid: data.playerUUID || data.objectUUID, id: data.entityId });
      } else if (n === 'system_chat' || n === 'profileless_chat' || n === 'player_chat') {
        const s = text(data);
        bot.chat_.push({ at, packet: n, text: s });
      } else if (n === 'teams') {
        bot.seen.push({ at, packet: n, team: data.team, players: data.players, prefix: data.prefix ? text(data.prefix) : undefined });
      } else if (n === 'block_action') {
        bot.seen.push({ at, packet: n, location: data.location, byte1: data.byte1, byte2: data.byte2 });
      } else if (n === 'open_window' || n === 'open_screen') {
        bot.seen.push({ at, packet: 'open_window', title: text(data.windowTitle ?? data.title), type: data.inventoryType ?? data.windowType });
      } else if (n === 'tab_complete') {
        bot.seen.push({ at, packet: n, matches: (data.matches || []).map(m => m.match ?? m) });
      }
    });
    bot.once('spawn', () => resolve(bot));
    bot.once('kicked', r => reject(new Error(name + ' kicked: ' + JSON.stringify(r))));
    bot.once('error', reject);
  });
}

module.exports = { join, sleep, console: console_, log, text };
