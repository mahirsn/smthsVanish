# smthsVanish

[Türkçe](README.tr.md)

Leveled vanish and disguise for Paper networks. A vanished player stays hidden when they move between servers.

## Requirements

- Paper 26.2 and Java 25
- Redis, shared by all servers
- Velocity (optional): auto-vanish on network join, hidden player count in the server list, SkinsRestorer skins in proxy mode
- packetevents (optional): needed for disguise
- LuckPerms, PlaceholderAPI, SkinsRestorer, Simple Voice Chat (optional)

## Installation

1. Put `smthsVanish-paper-<version>.jar` on every Paper server.
2. Put `smthsVanish-velocity-<version>.jar` on the Velocity proxy.
3. Start the servers once.
4. Set the same `redis.uri` in `plugins/smthsVanish/config.yml` and in the proxy's `plugins/smthsvanish/config.properties`.
5. Restart.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/vanish [player]` | `smthsvanish.use`, `smthsvanish.use.others` | Turn vanish on or off. |
| `/vanish interact` | `smthsvanish.interact` | Allow world interaction until the next vanish. |
| `/vanish pickup` | `smthsvanish.pickup` | Allow item pickup until the next vanish. |
| `/vanish list` | `smthsvanish.list` | Show the vanished players you can see. |
| `/vanish reload` | `smthsvanish.admin` | Reload the configuration and messages. |
| `/disguise [name]` | `smthsvanish.disguise`, `smthsvanish.disguise.name` | Appear as another name. Without a name, a random one from the configuration. |
| `/undisguise [player]` | `smthsvanish.disguise`, `smthsvanish.disguise.others` | Remove the disguise. |

## Permissions

| Permission | Effect |
|---|---|
| `smthsvanish.level.<n>` | Vanish level. |
| `smthsvanish.see.<n>` | See vanished players up to level `n`. |
| `smthsvanish.auto` | Vanish automatically on network join (needs the Velocity plugin). |
| `smthsvanish.silentchest` | Open containers without animation or sound. |
| `smthsvanish.chat` | Chat while vanished. |
| `smthsvanish.fly` | Fly while vanished. |
| `smthsvanish.disguise.see` | See the real name and skin of disguised players. |

## How it works

- A player sees a vanished player only if their see level is equal to or higher than the vanished player's level.
- While vanished, world interaction and item pickup are off. `/vanish interact` and `/vanish pickup` turn them on until the next vanish.
- A disguise uses the skin the named player has on your network (SkinsRestorer). Without SkinsRestorer, it uses the Mojang skin of that name.
- A disguise changes only what other players see. Logging and moderation plugins still record the real player.

## Developer API

```java
SmthsVanishApi api = Bukkit.getServicesManager().load(SmthsVanishApi.class);
api.isVanished(uuid);
api.canSee(viewer, target);
api.visibleName(viewer, target);
```

Plugins that cannot use the API can read the `vanished` metadata or the Redis hash `smthsvanish:player:<uuid>`.

## Building

```
./gradlew build
```
