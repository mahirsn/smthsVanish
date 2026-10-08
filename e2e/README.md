# Uçtan uca testler

İki Paper 26.2 backend (`a`, `b`), bir Velocity proxy ve Redis ile çalışır. Botlar mineflayer ile 26.1 protokolünde bağlanır. ViaVersion ve ViaBackwards bunu 26.2'ye çevirir.

## Kurulum

1. `e2e/jars/` içine şunları koy: Paper 26.2, Velocity, packetevents, ViaVersion, ViaBackwards, LuckPerms (Bukkit ve Velocity).
2. `e2e/run/a`, `e2e/run/b` ve `e2e/run/proxy` klasörlerini oluştur. Velocity modern forwarding, `online-mode=false` ve `login-ratelimit = 0` kullanır. Backend portları 25601 ve 25602, proxy portu 25600.
3. Redis'i başlat: `docker run -d --name smthsvanish-redis -p 127.0.0.1:6390:6379 redis:7-alpine`.
4. Plugin ayarlarında `redis.uri` değerini `redis://127.0.0.1:6390/0` yap.
5. `./ctl.sh start` ile sunucuları başlat. `./perms.sh` ile test izinlerini ver.
6. `cd bots && npm install`.

## Testler

| Dosya | Kontrol |
|---|---|
| `t1-levels.js` | Seviye kuralı, tab listesi, entity, sahte giriş ve çıkış mesajları, tab-complete. |
| `t2-switch.js` | Vanish olan oyuncu A → B → A geçer. Otomatik vanish (`smthsvanish.auto`) ile ağa giriş. İki backend'deki izleyiciye hiçbir paket gitmemeli. |
| `t3-disguise.js` | Tab, entity, chat, `/say`, giriş ve çıkış satırları, backend geçişi. |
| `t4-misc.js` | Sessiz sandık, chat engeli, proxy ping sayısı, LuckPerms ile canlı izin değişimi. |
| `t5-death.js` | Disguise olan oyuncunun ölüm mesajı. |
| `t6-redisdown.js` | Redis kapalıyken yetkili oyuncu gizli girer, normal oyuncu görünür girer. |
| `t7*-saveretry.js` | Redis yazması başarısız olunca yeniden denenir ve geçişte sızıntı olmaz. |

Her test JSON yazar. Bir testi `node t2-switch.js` ile çalıştır.

Not: mineflayer bazen blok tıklamasını sunucuya ulaştırmaz. `t4` bu yüzden ara sıra kontrol adımında başarısız olur. Bu durumda testi yeniden çalıştır.
