# Mimari

Bu belge planlanan yapıyı anlatır; henüz kod yok. Kararların gerekçeleri [decisions.md](decisions.md) içinde.

## Genel bakış

Tek bir Spring Boot uygulaması ve tek bir PostgreSQL veritabanı. Önlerinde TLS'i yöneten Caddy var. Hepsi tek VPS üzerinde Docker Compose ile çalışır.

```
                 HTTPS (Caddy, otomatik Let's Encrypt)
İnternet ──────────────────────────────────────────┐
                                                   ▼
┌──────────────────────────── VPS ─────────────────────────────┐
│  Caddy ──► Spring Boot uygulaması ──► PostgreSQL               │
│              ├─ vurli.io/{kod}   → yönlendirme                 │
│              ├─ vurli.io         → tanıtım, kötüye kullanım     │
│              └─ app.vurli.io     → panel                       │
└───────────────────────────────────────────────────────────────┘
Dış servisler: e-posta gönderimi, Google Web Risk, yedek deposu
```

## Modüller

Kök paket `io.vurli`. Her modül kendi tablolarının sahibidir. Başka bir modülün tablosuna veya repository'sine doğrudan erişilmez; modülün dışa açık servis arayüzü kullanılır. Bu kural Spring Modulith'in doğrulama testiyle CI'da kontrol edilir.

| Modül | Sorumluluk |
|---|---|
| `account` | Kayıt, giriş, e-posta doğrulama, şifre sıfırlama, workspace |
| `link` | Link oluşturma ve düzenleme, kod üretimi, URL doğrulama, QR |
| `redirect` | Kısa kodu çözüp yönlendirmek. Panelden bağımsız ve olabildiğince hızlı |
| `analytics` | Tıklama kaydı, bot ayıklama, günlük özetler, istatistik sorguları |
| `abuse` | URL güvenlik kontrolü, bildirimler, yönetici engellemesi, denetim kaydı |

`redirect` yalnızca iki şey kullanır: `link` modülünün okuma arayüzü ve `analytics` modülünün tıklama kayıt arayüzü. Panel controller'ları ve Thymeleaf şablonları ilgili modülün `web` alt paketinde durur.

## Veri modeli

```
users             id, email (unique, küçük harf), password_hash, email_verified_at,
                  role (USER | ADMIN), created_at
workspaces        id, name, owner_id → users, created_at
links             id, workspace_id → workspaces, code (unique), target_url,
                  status (ACTIVE | DISABLED | BLOCKED), expires_at, deleted_at,
                  safety_checked_at, version, created_by → users, created_at, updated_at
email_tokens      id, user_id → users, purpose (VERIFY | RESET), token_hash,
                  expires_at, used_at
click_events      id, link_id, occurred_at, referrer_host, country, device_type
link_daily_stats  link_id, day, clicks, by_referrer (jsonb), by_country (jsonb),
                  by_device (jsonb)                         PK (link_id, day)
abuse_reports     id, link_id, reason, reporter_email (opsiyonel), status, created_at
audit_log         id, actor_id, action, target_type, target_id, details (jsonb), created_at
```

Spring Session JDBC kendi oturum tablolarını ayrıca oluşturur.

Modeldeki önemli tercihler:

- **Durum ve silme ayrı.** `DISABLED` sahibinin kapattığı, `BLOCKED` yöneticinin engellediği linktir. Silme bir durum değil, `deleted_at` alanıdır. Satır fiziksel olarak silinmez, böylece kod sonsuza kadar rezerve kalır ([ürün kuralı 2](product.md#ürün-kuralları)).
- **Süre sonu ayrı bir durum değil.** `expires_at` her yönlendirmede kontrol edilir. Zamanlanmış bir işin çalışmasına bağlı değildir.
- **`version` alanı iyimser kilitleme içindir.** İki sekmeden aynı anda yapılan düzenlemede biri diğerini sessizce ezmez; ikinci kayıt çakışma hatası alır.
- **Workspace izolasyonu her sorguda uygulanır.** Workspace oturumdaki kullanıcıdan alınır, istekten asla alınmaz. Başka workspace'e ait bir kaynak istenirse `403` değil `404` döner; kaynağın varlığı bile sızdırılmaz.
- **Token'lar hash'li saklanır.** Veritabanı sızsa bile doğrulama ve şifre sıfırlama linkleri kullanılamaz.

## Yönlendirme akışı

```
GET /{kod}                                  (RedirectController → LinkResolver)
 1. Kod formatı geçersizse veya rezerve bir adsa      → 404 (cache'e ve veritabanına gidilmez)
 2. Cache'te ara; yoksa veritabanından oku ve cache'e koy ("yok" sonucu da cache'lenir)
 3. Link yoksa                                          → 404
 4. Silinmiş, kapalı, engellenmiş veya süresi dolmuşsa  → 410 "This link is no longer available"
 5. Veritabanına ulaşılamazsa                           → 503; tahminle yönlendirme yapılmaz
 6. 302, Location: kayıtlı hedef
 7. Tıklamayı bellekteki kuyruğa bırak (M3; yanıt bunu beklemez)
```

- Bütün yanıtlar `Cache-Control: no-store` taşır. Yönlendirme yanıtları çerez veya oturum oluşturmaz.
- `HEAD` aynı yanıtı gövdesiz verir ama tıklama sayılmaz.
- Ziyaretçinin query string'i hedefe eklenmez.
- Süre sonu, cache'teki kayıt üzerinde her istekte o anki saate göre hesaplanır.
- Sunucu hedef siteyi hiçbir zaman ziyaret etmez. Web Risk kontrolü URL'yi Google'a sorar, siteyi açmaz; bu yüzden SSRF yüzeyi yoktur.
- Yalnızca tanımlı host'lar (`vurli.io`, `app.vurli.io`) işlenecek; başka bir `Host` başlığı `404` alacak. Henüz uygulanmadı; panel ayrı host'a taşınırken (M2) eklenecek.
- Rezerve adlar `AliasPolicy` içinde tanımlı (`app`, `api`, `admin`, `login`, `actuator`, `abuse` ve benzerleri).
- Kimlik doğrulaması gerekmeyen yollar yalnızca tek segmentli `GET`/`HEAD` istekleri ve `/actuator/health`. Geri kalan her şey girişe yönlendirilir.

### Cache ve engellemenin etkisi

Cache Caffeine'dir: boyutu sınırlıdır ve girdiler 60 saniye yaşar. Bir link değiştiğinde (oluşturma, hedef, durum veya silme), transaction commit edildikten sonra o kodun cache girdisi silinir.

İlk tasarımda bir yarış endişesi vardı: cache'i dolduran bir okuma, eski kaydı engellemeden önce okuyup engellemeden sonra cache'e yazabilir mi? Caffeine aynı anahtarı atomik olarak yükler ve o anahtar için devam eden yükleme bitene kadar `invalidate` çağrısını bekletir. Bu yüzden engellemeden önce okunan eski kayıt, engellemeden sonra cache'te kalamaz. `LinkCacheTests` bu senaryoyu bariyerlerle zorluyor. Cache atomik olmayan bir yüklemeyle (oku, yükle, koy) değiştirildiğinde test başarısız oluyor. Dolayısıyla taahhüt şu: **uygulama üzerinden yapılan engelleme, commit anından itibaren yeni isteklerde etkilidir.** 60 saniyelik TTL yalnızca uygulama dışından (elle SQL ile) yapılan değişikliklerin en geç ne zaman görüneceğini sınırlar.

Commit'ten önce kararı verilmiş, yanıtı yolda olan bir yönlendirme geri alınamaz. "Anında" ifadesi commit'ten sonra başlayan istekler içindir.

İkinci bir uygulama instance'ı eklenirse cache silme işleminin instance'lar arasında yayılması gerekir. PostgreSQL `LISTEN/NOTIFY` bunun için yeterli. Karar o zaman verilir.

## Link yönetimi

- Sahibin her işlemi workspace kapsamlıdır: link `id + workspaceId` ile yüklenir. Başka bir workspace'in veya silinmiş bir link "bulunamadı" görünür.
- Değişiklikler son görülen `version` ile gelir. Uyuşmazlık, başkasının değişikliğini ezmek yerine çakışma hatası verir. Kontrol iki katmanlıdır: önce yüklenen satır karşılaştırılır, sonra JPA `@Version` arada commit edilmiş değişikliği yakalar.
- Engellenmiş link sahibi için donmuştur; yalnızca silinebilir. Yönetici engellemesi sürüm istemez ve sahibin işlemine karşı her zaman kazanır.
- Silme satırı silmez, `deleted_at` alanını doldurur; kod rezerve kalır.
- Her başarılı değişiklikten sonra, commit'in ardından o kodun cache girdisi silinir.

## Link oluşturma

1. **URL doğrulama** (`TargetUrlValidator`).
   - Yalnızca `http` ve `https` şeması kabul edilir.
   - Kullanıcı bilgisi içeren URL'ler reddedilir (`https://google.com@kotu.site` tuzağı).
   - Boşluk, kontrol karakterleri ve ters bölü reddedilir. Tarayıcılar `\` karakterini `/` gibi yorumlar; `https://kotu.site\@google.com` tuzağı bu yüzden çalışır.
   - Uzunluk, kodlamadan önce ve sonra en fazla 2048 karakterdir.
   - Host bir IP adresi olamaz (onaltılık ve IPv6 biçimleri dahil). Son etiket harflerden oluşmalı, dolayısıyla `localhost` gibi tek parçalı adlar da reddedilir.
   - Yalnızca standart portlar kabul edilir (http için 80, https için 443).
   - `vurli.io`'ya ve alt alan adlarına giden linkler reddedilir. Böylece döngü ve zincirleme gizleme önlenir.
   - Kabul edilen URL normalize edilerek saklanır: şema ve host küçük harfe çevrilir, Türkçe karakterli alan adları punycode'a (`xn--…`) dönüştürülür, ASCII olmayan path karakterleri kodlanır. Var olan `%` kodlamaları değiştirilmez, çünkü değiştirilirse URL'nin anlamı bozulabilir.
2. **Güvenlik kontrolü.** URL Web Risk'e sorulur. Tehlikeli işaretlenirse link reddedilir. Servis yanıt vermezse link oluşturulur ama `safety_checked_at` boş kalır; arka plan işi birkaç dakika içinde yeniden kontrol eder ve tehlikeli çıkarsa engeller. Bu tercih kullanıcı deneyimini korur. Riski, doğrulanmış hesap şartı ve kota sınırlar. Aktif linkler haftalık olarak yeniden taranır, çünkü bir hedef sonradan zararlıya dönüşebilir.
3. **Kota.** Hesap başına günlük link sınırı uygulanır ([A04](decisions.md#açık-sorular)).
4. **Kod.**
   - Özel ad verildiyse küçük harfe çevrilir (`Kafe-Menu` → `kafe-menu`). Böylece yalnızca harf büyüklüğüyle ayrışan, birbirine karıştırılabilecek iki link oluşamaz. Ad 3–50 karakter olmalı; yalnızca küçük harf, rakam ve tire içermeli; tireyle başlayıp bitmemeli; rezerve listede (`AliasPolicy`) olmamalı.
   - Özel ad yoksa 7 karakterlik rastgele Base62 kod üretilir. Rastgele kodlar büyük-küçük harf duyarlıdır. Rezerve bir ada denk gelen kod atlanır.
   - Ardından `INSERT` yapılır. Unique ihlali olursa özel adda "bu ad alınmış" hatası döner; rastgele kodda en fazla 3 kez yeniden denenir.
   - Kodun tekliğini veritabanı constraint'i (`uk_links_code`) garanti eder. "Önce kontrol et, sonra yaz" yaklaşımı eşzamanlı isteklerde çift kayda yol açar. Constraint hatası Postgres'te transaction'ı geçersiz kıldığı için her deneme kendi transaction'ında yapılır.

QR kod ZXing ile sunucuda üretilir ve hedefi değil kısa URL'yi kodlar. Böylece hedef değişse de basılı QR geçerli kalır.

## Tıklama kaydı

- Yönlendirme, tıklamayı sınırlı boyutlu bir bellek kuyruğuna (örneğin 10.000 olay) bırakır. Arka plan işi her saniye toplu `INSERT` yapar.
- Kuyruk doluysa olay atılır ve bir metrik artar. Yönlendirme asla beklemez.
- Bilinen bot User-Agent'ları ve link önizleme botları (WhatsApp, Slack, X, Facebook gibi) sayılmaz.
- Ülke MaxMind GeoLite2 veritabanından bulunur ve IP hemen atılır. Referrer'dan yalnızca host saklanır.
- Gece işi önceki günü `link_daily_stats` tablosuna özetler ve 90 günden eski `click_events` kayıtlarını siler.

## Panel

`app.vurli.io` üzerinde Thymeleaf ve htmx ile çalışır.

- Spring Security form girişi kullanılır ve CSRF koruması açıktır. htmx istekleri CSRF token'ını başlıkta gönderir.
- Oturum çerezi `Secure`, `HttpOnly`, `SameSite=Lax` ve host-only'dir (domain belirtilmez).

## Dağıtım

- **Servisler:** Docker Compose ile `caddy`, `app` ve `postgres`.
- **Deploy:** GitHub Actions image'ı build eder ve GHCR'a gönderir; sunucu yeni image'ı çeker ve yeniden başlatır. MVP'de birkaç saniyelik kesinti kabul edilir.
- **Yedekleme:** her gün `pg_dump` alınır ve sunucu dışındaki bir depoya (S3 uyumlu veya Storage Box) gönderilir. Yedekler 30 gün saklanır. Geri yükleme ayda bir boş bir ortamda denenir; denenmemiş yedek, yedek sayılmaz.
- **İzleme:** dışarıdan uptime kontrolü yapılır. Spring Actuator health endpoint'i yalnızca içeriden erişilebilir. Uygulama logları JSON biçiminde tutulur.

## Ölçek

Bu bölüm mülakat için. Buradaki sayılar M3'teki yük testiyle ölçülecek; şimdilik tahmin.

- Yük okuma ağırlıklı: link oluşturma seyrek, yönlendirme sık. Tek PostgreSQL ve bellek cache'i ilk aşama için fazlasıyla yeterli.
- Tek bir link viral olursa sorun çıkmaz; cache sayesinde veritabanına yük binmez.
- İlk darboğaz büyük ihtimalle tıklama yazımı olur. Toplu yazım bunu baştan hafifletiyor. Sonraki adım `click_events` tablosunu aylık bölümlemek.
- Ölçek gerekirse izlenecek sıra:
  1. sunucuyu büyütmek,
  2. ikinci instance eklemek ve cache silme işlemini yaymak,
  3. okuma replikası eklemek,
  4. yönlendirmeyi ayrı bir servise ayırmak.

  Her adım ancak ölçülen bir darboğaz varsa atılır.
