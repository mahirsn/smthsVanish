# smthsVanish

Seviyeli vanish ve disguise. Paper 26.2 ve Velocity içindir.

## Kurulum

1. `smthsVanish-paper` jar'ını her backend'e, `smthsVanish-velocity` jar'ını proxy'ye koy.
2. Tüm sunucularda `redis.uri` değerini aynı Redis'e ayarla.
3. Disguise için backend'lere packetevents kur.

## Komutlar

| Komut | İzin |
|---|---|
| `/vanish [oyuncu]` | `smthsvanish.use`, `smthsvanish.use.others` |
| `/vanish interact` | `smthsvanish.interact` |
| `/vanish pickup` | `smthsvanish.pickup` |
| `/vanish list` | `smthsvanish.list` |
| `/vanish reload` | `smthsvanish.admin` |
| `/disguise [ad] [skin]` | `smthsvanish.disguise`, `.name`, `.skin` |
| `/undisguise [oyuncu]` | `smthsvanish.disguise`, `.others` |

## İzinler

| İzin | Etki |
|---|---|
| `smthsvanish.level.N` | Vanish seviyesi. |
| `smthsvanish.see.N` | N ve altındaki seviyeleri görür. |
| `smthsvanish.auto` | Ağa girince otomatik vanish. |
| `smthsvanish.silentchest` | Sandıkları sessiz açar. |
| `smthsvanish.chat` | Vanish açıkken chat yazar. |
| `smthsvanish.fly` | Vanish açılınca uçar. |
| `smthsvanish.disguise.see` | Disguise olanların gerçek adını görür. |

Vanish açıkken dünyayla etkileşim ve eşya toplama kapalıdır. `/vanish interact` ve `/vanish pickup` bunları açar. Bir sonraki vanish'te ikisi de yine kapanır.
