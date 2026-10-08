# smthsVanish

Seviyeli vanish ve disguise. Paper 26.2 backend'leri ve Velocity proxy için. Durum Redis'te tutulur, bu yüzden oyuncu backend değiştirince gizli kalır.

## Modüller

| Modül | Görev |
|---|---|
| `smthsVanish-common` | Redis durumu (`VanishState`, `VanishStore`) ve seviye kuralı (`Levels`). |
| `smthsVanish-paper` | Gizleme, disguise, komutlar, entegrasyonlar. Her backend'e kurulur. |
| `smthsVanish-velocity` | Ağa girişte otomatik vanish ve ping sayısı. Proxy'ye kurulur. |

## Kurulum

1. Derle: `./gradlew build`.
2. `smthsVanish-paper/build/libs/smthsVanish-paper-<sürüm>.jar` dosyasını her backend'in `plugins/` klasörüne koy.
3. `smthsVanish-velocity/build/libs/smthsVanish-velocity-<sürüm>.jar` dosyasını Velocity'nin `plugins/` klasörüne koy.
4. Sunucuları bir kez başlat. Ayar dosyaları oluşur.
5. Tüm sunucularda ve proxy'de `redis.uri` değerini AYNI Redis'e ayarla.
6. Backend'deki `levels.max` ile proxy'deki `levels.max` değerini aynı yap.

Disguise için backend'de packetevents gerekir. LuckPerms, PlaceholderAPI ve voicechat isteğe bağlıdır.

## Seviyeler

- Vanish olan oyuncunun seviyesi, sahip olduğu en yüksek `smthsvanish.level.N` iznidir. İzin yoksa seviye 1'dir.
- İzleyicinin görme seviyesi, sahip olduğu en yüksek `smthsvanish.see.N` iznidir.
- İzleyici, vanish olan oyuncuyu sadece görme seviyesi o oyuncunun seviyesine eşit veya büyükse görür.
- Örnek: admin `level.3`, moderatör `see.2`. Moderatör admini görmez. Admin `see.3` ile moderatörü (`level.2`) görür.
- LuckPerms'te izin değişince görünürlük hemen yenilenir. Oyuncunun yeniden girmesi gerekmez.

## Komutlar

| Komut | İzin | Açıklama |
|---|---|---|
| `/vanish` (`/v`, `/gizlen`) | `smthsvanish.use` | Kendi vanish durumunu değiştir. |
| `/vanish <oyuncu>` | `smthsvanish.use.others` | Başkasının durumunu değiştir. Göremediğin oyuncu "bulunamadı" görünür. |
| `/vanish list` | `smthsvanish.list` | Bu sunucuda görebildiğin vanish oyuncular. |
| `/vanish reload` | `smthsvanish.admin` | `config.yml` ve `messages.yml` yeniden yüklenir. |
| `/disguise` (`/dis`, `/kilik`) | `smthsvanish.disguise` | Listeden rastgele bir ad. |
| `/disguise <ad>` | `smthsvanish.disguise.name` | Adı sen seç. Skin, o adın Mojang skin'idir. |
| `/disguise <ad> <skin>` | `smthsvanish.disguise.skin` | Skin'i başka bir hesaptan al. |
| `/undisguise [oyuncu]` | `smthsvanish.disguise`, `smthsvanish.disguise.others` | Disguise'ı kaldır. |

## Diğer izinler

| İzin | Etki |
|---|---|
| `smthsvanish.auto` | Ağa her girişte proxy oyuncuyu ilk backend'den önce vanish yapar. |
| `smthsvanish.chat` | Vanish açıkken chat yazabilir. |
| `smthsvanish.pickup` | Vanish açıkken eşya ve XP toplar. |
| `smthsvanish.interact` | Vanish açıkken basınç plakası ve tripwire tetikler. |
| `smthsvanish.silentchest` | Sandık, varil ve shulker sessiz açılır (salt okunur kopya). |
| `smthsvanish.fly` | Vanish açılınca uçma açılır. |
| `smthsvanish.disguise.see` | Disguise olan oyuncuların gerçek adını ve skin'ini görür. |

## Ne gizlenir

Her madde `config.yml` içinden kapatılabilir.

- Dünyadaki oyuncu, tab listesi, isim etiketi. Paper'ın plugin bazlı `hidePlayer` API'si kullanılır. Başka bir plugin `showPlayer` çağırsa bile oyuncu gizli kalır.
- Giriş, çıkış, ölüm ve advancement mesajları.
- Tab-complete önerileri.
- Sunucu listesindeki oyuncu sayısı ve örnek liste (backend ve proxy).
- Chat, eşya ve XP toplama, mob hedeflemesi, basınç plakası, sculk sensör, Frost Walker izi.
- Uyku sayımı, çarpışma, mob doğması, hasar ve açlık.
- Sandık, varil ve shulker animasyonu ve sesi.
- Simple Voice Chat yakın ses (grup sesi değil).
- `"vanished"` metadata'sı ayarlanır. TAB, smthsSMP ve benzeri pluginler bunu okur.

## Disguise nasıl çalışır

Disguise sadece diğer oyunculara giden paketleri değiştirir. Sunucu oyuncunun gerçek profilini tutar. Bu yüzden CoreProtect, LiteBans, HuskSync ve AxInventoryRestore gerçek oyuncuyu kaydeder.

İzleyiciye giden şu paketlerde gerçek ad, disguise adıyla değişir:

- tab girişi (ad ve skin),
- chat, sistem mesajları, başlık ve action bar,
- tab'daki görünen ad,
- takım paketleri (isim etiketi prefix ve suffix),
- skor tablosu satırları,
- entity metinleri (isim etiketi display'leri, hologramlar).

Ad, metnin içindeki tıklama (`/tell <ad>`), hover ve insertion alanlarında da değişir. LuckPerms varsa prefix ve suffix, `disguise.mask-rank-group` grubunun değerleriyle gösterilir. Bu değişiklik geçicidir ve veritabanına yazılmaz.

## Bilinen sınırlar

- **UUID değişmez.** Değiştirilmiş bir client, UUID'den gerçek adı bulabilir. Normal client bunu göstermez.
- **İmzalı chat gövdesi değişmez.** Disguise olan oyuncu kendi gerçek adını yazarsa, imzalı gövdede o ad kalır. Normal client imzasız (değiştirilmiş) metni gösterir.
- **Başka pluginlerin kendi özellikleri.** smthsFriends (çevrimiçi bildirimi), smthsSMP ağ giriş mesajları, smthsChat, smthsQueue ve smthsPlayerCountBridge vanish durumunu okumaz. Bu pluginler `"vanished"` metadata'sını, `SmthsVanishApi` servisini veya Redis'teki `smthsvanish:player:<uuid>` hash'ini okumalı.
- **Blok kırma sesi ve parçacığı** vanilla olarak diğer oyunculara gider.
- **Disguise adı çakışması** sadece aynı backend'de kontrol edilir.
- **Folia** desteklenmez.

## Diğer pluginler için

```java
SmthsVanishApi api = Bukkit.getServicesManager().load(SmthsVanishApi.class);
api.isVanished(uuid);
api.canSee(viewer, target);
api.visibleName(viewer, target);
```

Backend dışında Redis hash'i okunur: `HGETALL smthsvanish:player:<uuid>` → `vanished` (`1`/`0`), `level`, `disguise`. Değişiklik olunca `smthsvanish:update` kanalına oyuncunun UUID'si yayınlanır.

## Testler

- Birim testleri: `./gradlew test`.
- Uçtan uca testler: `e2e/`. İki Paper backend, Velocity ve Redis ile mineflayer botları çalışır. Bkz. `e2e/README.md`.
