# smthsVanish

[English](README.md)

Paper ağları için seviyeli vanish ve disguise. Vanish olan oyuncu sunucu değiştirince de gizli kalır.

## Gereksinimler

- Paper 26.2 ve Java 25
- Tüm sunucuların ortak kullandığı bir Redis
- Velocity (isteğe bağlı): ağa girişte otomatik vanish, sunucu listesinde gizli oyuncu sayısı, proxy modunda SkinsRestorer skinleri
- packetevents (isteğe bağlı): disguise için gerekir
- LuckPerms, PlaceholderAPI, SkinsRestorer, Simple Voice Chat (isteğe bağlı)

## Kurulum

1. `smthsVanish-paper-<sürüm>.jar` dosyasını her Paper sunucusuna koy.
2. `smthsVanish-velocity-<sürüm>.jar` dosyasını Velocity proxy'ye koy.
3. Sunucuları bir kez başlat.
4. `plugins/smthsVanish/config.yml` ve proxy'deki `plugins/smthsvanish/config.properties` dosyalarında aynı `redis.uri` değerini ayarla.
5. Sunucuları yeniden başlat.

## Komutlar

| Komut | İzin | Açıklama |
|---|---|---|
| `/vanish [oyuncu]` | `smthsvanish.use`, `smthsvanish.use.others` | Vanish'i aç veya kapat. |
| `/vanish interact` | `smthsvanish.interact` | Bir sonraki vanish'e kadar dünyayla etkileşimi aç. |
| `/vanish pickup` | `smthsvanish.pickup` | Bir sonraki vanish'e kadar eşya toplamayı aç. |
| `/vanish list` | `smthsvanish.list` | Görebildiğin vanish oyuncuları göster. |
| `/vanish reload` | `smthsvanish.admin` | Ayarları ve mesajları yeniden yükle. |
| `/disguise [ad]` | `smthsvanish.disguise`, `smthsvanish.disguise.name` | Başka bir adla görün. Ad yazmazsan ayardaki listeden rastgele bir ad seçilir. |
| `/undisguise [oyuncu]` | `smthsvanish.disguise`, `smthsvanish.disguise.others` | Disguise'ı kaldır. |

## İzinler

| İzin | Etki |
|---|---|
| `smthsvanish.level.<n>` | Vanish seviyesi. |
| `smthsvanish.see.<n>` | `n` seviyesine kadar vanish oyuncuları gör. |
| `smthsvanish.auto` | Ağa girince otomatik vanish (Velocity eklentisi gerekir). |
| `smthsvanish.silentchest` | Sandıkları animasyonsuz ve sessiz aç. |
| `smthsvanish.chat` | Vanish açıkken chat yaz. |
| `smthsvanish.fly` | Vanish açıkken uç. |
| `smthsvanish.disguise.see` | Disguise olan oyuncuların gerçek adını ve skinini gör. |

## Nasıl çalışır

- Bir oyuncu, vanish olan oyuncuyu sadece görme seviyesi o oyuncunun seviyesine eşit veya büyükse görür.
- Vanish açıkken dünyayla etkileşim ve eşya toplama kapalıdır. `/vanish interact` ve `/vanish pickup` bunları bir sonraki vanish'e kadar açar.
- Disguise, o adın sahibinin ağdaki skinini kullanır (SkinsRestorer). SkinsRestorer yoksa o adın Mojang skinini kullanır.
- Disguise sadece diğer oyuncuların gördüğünü değiştirir. Log ve moderasyon pluginleri gerçek oyuncuyu kaydeder.

## Geliştirici API'si

```java
SmthsVanishApi api = Bukkit.getServicesManager().load(SmthsVanishApi.class);
api.isVanished(uuid);
api.canSee(viewer, target);
api.visibleName(viewer, target);
```

API'yi kullanamayan pluginler `vanished` metadata'sını veya Redis'teki `smthsvanish:player:<uuid>` hash'ini okuyabilir.

## Derleme

```
./gradlew build
```
