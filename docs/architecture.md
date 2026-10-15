# Mimari

Bu belge sistemin yapısını anlatır. M1'de anlatılanlar (link, yönlendirme, cache) uygulandı; panel, hesaplar ve sonrası henüz plan. Güncel durum [progress.md](progress.md), kararların gerekçeleri [decisions.md](decisions.md) içinde.

## Genel bakış

Bir Spring Boot uygulaması, kullanıcı paneli için bir Next.js uygulaması ve bir PostgreSQL veritabanı. Önlerinde TLS'i yöneten Caddy var. Hepsi tek VPS üzerinde Docker Compose ile çalışır. Kod monorepo'da: `backend/` (Spring) ve `frontend/` (Next.js).

```
                    HTTPS (Caddy, otomatik Let's Encrypt)
İnternet ─────────────────────────────────────────────────┐
                                                          ▼
┌─────────────────────────────── VPS ────────────────────────────────┐
│  Caddy ─┬─ vurli.io/{kod}, vurli.io ──► Spring Boot ──► PostgreSQL   │
│         │   (yönlendirme, tanıtım)        ▲                          │
│         └─ app.vurli.io ──► Next.js ──────┘ iç ağ: /api/v1          │
│                              (BFF)          (internete açık değil)   │
└────────────────────────────────────────────────────────────────────┘
Dış servisler: e-posta (SMTP), Google Web Risk, yedek deposu
```

Yönlendirme Node.js'ten geçmez; kısa linkler doğrudan Spring'e gider. Next.js veritabanına hiç bağlanmaz.

## Modüller

Kök paket `io.vurli`. Her modül kendi tablolarının sahibidir. Başka bir modülün tablosuna veya repository'sine doğrudan erişilmez; modülün dışa açık servis arayüzü kullanılır. Bu kural Spring Modulith'in doğrulama testiyle CI'da kontrol edilir.

| Modül | Sorumluluk |
|---|---|
| `account` | Kayıt, giriş, e-posta doğrulama, şifre sıfırlama, workspace |
| `link` | Link oluşturma ve düzenleme, kod üretimi, URL doğrulama, QR |
| `redirect` | Kısa kodu çözüp yönlendirmek. Panelden bağımsız ve olabildiğince hızlı |
| `analytics` | Tıklama kaydı, bot ayıklama, günlük özetler, istatistik sorguları |
| `abuse` | URL güvenlik kontrolü, bildirimler, yönetici engellemesi, denetim kaydı |

`redirect` yalnızca iki şey kullanır: `link` modülünün okuma arayüzü ve `analytics` modülünün tıklama kayıt arayüzü. Next.js'in çağırdığı REST controller'ları ve yönetici panelinin Thymeleaf şablonları ilgili modülün `web` alt paketinde durur.

## Veri modeli

```
users             id, email (unique, küçük harf), password_hash ({argon2}…), email_verified_at,
                  locale (tr | en), role (USER | ADMIN), created_at, updated_at
workspaces        id, name, owner_id → users, created_at
links             id, workspace_id → workspaces, code (unique), target_url,
                  status (ACTIVE | DISABLED | BLOCKED), expires_at, deleted_at,
                  safety_checked_at, version, created_by → users, created_at, updated_at
email_tokens      id, user_id → users, purpose (VERIFY_EMAIL | RESET_PASSWORD),
                  token_hash (SHA-256, unique), expires_at, used_at, created_at
click_events      id, link_id, occurred_at, referrer_host, country, device_type
link_daily_stats  link_id, day, clicks, by_referrer (jsonb), by_country (jsonb),
                  by_device (jsonb)                         PK (link_id, day)
abuse_reports     id, link_id, reason, reporter_email (opsiyonel), status, created_at
audit_log         id, actor_id, action, target_type, target_id, details (jsonb), created_at
```

Spring Session JDBC'nin tabloları (`spring_session`, `spring_session_attributes`) V4 migration'ında; şemayı Spring değil Flyway yönetir. `workspaces.owner_id`, hesaplardan önce oluşturulmuş satırlar için boş olabilir; her yeni workspace'in sahibi var. `links.created_by` henüz yok.

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
- Spring yalnızca tanımlı host'larda yanıt verir (`HostFilter`, bütün filtrelerden önce). Tek segmentli yollar (`/`, `/{kod}`) yalnızca kısa link host'unda (`vurli.hosts.public`, canlıda `vurli.io`), geri kalan her şey (API, health) yalnızca iç ağ host'unda (`vurli.hosts.internal`) işlenir. Başka bir `Host` gövdesiz `404` alır; oturum yüklenmez. Bilinmeyen yollar iç ağa sayıldığı için `vurli.io//api/...` gibi hileler API'ye ulaşamaz. Canlı ortamın iç host adı M4'te eklenir.
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

- Liste en yeniden eskiye, sayfalıdır (en fazla 100) ve sayım sorgusu yapmaz; bir satır fazla okuyarak sonraki sayfa olup olmadığını (`hasNext`) söyler.
- Yanıtlar kısa linkin tam adresini (`shortUrl`) taşır; başı `vurli.short-base-url` ayarından gelir.

QR kod ZXing ile sunucuda üretilir ve hedefi değil kısa URL'yi kodlar. Böylece hedef değişse de basılı QR geçerli kalır. Hata düzeltme seviyesi M (yaklaşık %15 hasara dayanır), standardın istediği 4 modüllük sessiz alan var. PNG yaklaşık 1000 piksel; SVG yalnızca sayılardan oluşan tek bir path, yani kullanıcı girdisi taşımaz. Uç nokta: `GET /api/v1/links/{id}/qr?format=png|svg` (workspace kapsamlı, indirme olarak).

## Tıklama kaydı

- Yönlendirme, tıklamayı sınırlı boyutlu bir bellek kuyruğuna (örneğin 10.000 olay) bırakır. Arka plan işi her saniye toplu `INSERT` yapar.
- Kuyruk doluysa olay atılır ve bir metrik artar. Yönlendirme asla beklemez.
- Bilinen bot User-Agent'ları ve link önizleme botları (WhatsApp, Slack, X, Facebook gibi) sayılmaz.
- Ülke MaxMind GeoLite2 veritabanından bulunur ve IP hemen atılır. Referrer'dan yalnızca host saklanır.
- Gece işi önceki günü `link_daily_stats` tablosuna özetler ve 90 günden eski `click_events` kayıtlarını siler.

## Kullanıcı paneli (Next.js BFF)

`app.vurli.io` üzerinde Next.js (App Router), Tailwind CSS ve shadcn/ui ile çalışır. Türkçe ve İngilizce; dil `Accept-Language`'den seçilir, metinler `src/lib/i18n/dictionaries` altında (K40). Hesap ekranları (M2 dilim 3) ve link ekranları (dilim 4) uygulandı: ana sayfada oluşturma formu ve sayfalı liste; `/links/{id}` sayfasında kopyalama, QR önizleme ve indirme, hedef ve bitiş tarihi değiştirme, kapatma/açma ve onaylı silme.

- **Yapı:** `src/lib/api.ts` Spring'e konuşan tek yer (`server-only`). `src/app/actions/auth.ts` form işlemlerini yapan server action'lar. `src/lib/session.ts` her sayfada kullanıcıyı Spring'in `/me` uç noktasına sorar. `src/proxy.ts` CSP nonce'u üretir ve çerez yoksa `/login`'e yönlendirir.
- **Render:** bütün sayfalar dinamik; `cacheComponents` kapalı (K39).
- **Link işlemleri:** `src/app/actions/links.ts`. Formlar linkin kimliğini ve son görülen sürümü gizli alanda taşır; ikisi de tarayıcıdan geldiği için API yoluna girmeden önce biçimleri kontrol edilir (UUID ve tamsayı, `lib/links.ts`), yetki ve sürüm kararı Spring'de. Değişiklikten veya sürüm çakışmasından sonra sayfa güncel veriyle yeniden çizilir. Bitiş tarihi tarayıcının saat diliminde seçilir ve UTC an olarak gönderilir. QR `src/app/links/[id]/qr/route.ts` üzerinden Spring'den aktarılır.

- **Kim neye sahip:** kimlik, yetki ve verinin tek sahibi Spring'dir. Next.js yalnızca ekranı üretir ve Spring'in `/api/v1` uç noktalarını sunucu tarafında çağırır; iş kuralı içermez, veritabanına bağlanmaz.
- **Oturum:** giriş isteğini Next.js Spring'e iletir. Spring oturumu açar (Spring Session JDBC) ve oturum kimliğini döndürür. Next.js `Set-Cookie`'den yalnızca kimliğin değerini alır ve çerezi kendi bayraklarıyla yeniden yazar; Spring'den gelen bayraklara güvenmez. Sonraki isteklerde tarayıcının çerezlerinden yalnızca `VURLI_SESSION`'ı Spring'e iletir. Tarayıcıda JavaScript'in okuyabildiği bir token yoktur. Spring'in adresi `VURLI_API_URL` ortam değişkeninden gelir (yalnızca sunucuda) ve istekte yalnızca `/api/v1/` yollarına izin verilir.
- **Yönlendirmeler:** girişten sonra her zaman `/`'e gidilir; istekteki "geri dön" adresi kabul edilmez (açık yönlendirme açığı).
- **Çerez:** `Secure`, `HttpOnly`, `SameSite=Lax`, host-only (`app.vurli.io`). Kısa link alan adı (`vurli.io`) bu çerezi hiç görmez.
- **CSRF:** iki katman. Next.js'te `SameSite` çerez ve değişiklik isteklerinde `Origin` kontrolü (server action'larda yerleşik). Spring API'de ise token tabanlı CSRF yerine her değişiklik isteğinde `X-Vurli-Client` başlığı zorunludur (`ClientHeaderFilter`). Başka bir sitenin formu veya linki özel başlık ekleyemez; script'i de CORS ön kontrolü olmadan ekleyemez ve API hiçbir origin'e CORS izni vermez. Spring API ayrıca yalnızca iç ağdan erişilebilir olacak (M4).
- **Yetki:** Next.js'in uç noktaları da dışa açık bir API'dir. Bu yüzden her yetki kararı Spring'de verilir; Next.js'te gizlenen bir buton güvenlik önlemi sayılmaz.

## Hesaplar

`account` modülü; uç noktalar `/api/v1/auth` altında. Uygulandı (M2 dilim 2).

- **Kayıt:** e-posta küçük harfe çevrilir, şifre en az 10, en fazla 128 karakter (NIST: kompozisyon kuralı yok). Şifre Argon2id ile hash'lenir (OWASP asgarisi: 19 MiB, 2 tur). Hesap, kişisel workspace ve doğrulama token'ı tek transaction'da oluşur. Aynı adrese eşzamanlı kayıtlarda tekliği `uk_users_email` sağlar.
- **Hesap sızdırmama:** kayıt ve şifre sıfırlama isteği her durumda 202 döner. Kayıtlı adrese ikinci kayıt denemesinde sahibine "zaten hesabın var" e-postası (doğrulanmamışsa yeni doğrulama e-postası) gider. Şifre her durumda hash'lenir, böylece yanıt süresi de fark etmez. Yanlış şifre ile bilinmeyen adres aynı 401'i alır.
- **Token'lar:** 32 rastgele bayt (Base64url); veritabanında yalnızca SHA-256'sı. Tüketme tek bir `UPDATE … RETURNING` ile yapıldığı için aynı token iki kez kullanılamaz. Doğrulama 24 saat, sıfırlama 1 saat geçerli. E-postadaki link token'ı URL fragment'ında (`#token=`) taşır; fragment sunucuya gönderilmediği için erişim loglarına ve `Referer` başlığına düşmez.
- **E-posta:** düz metin, kullanıcının diline göre (`messages*.properties`). Gönderim hatası yanıtı değiştirmez (hangi dalın çalıştığını belli etmemek için); log'a yalnızca kullanıcı kimliği yazılır. Yeniden gönderme ve sıfırlama dakikada bir ile sınırlı.
- **Oturum:** Spring Session JDBC, 24 saat hareketsizlikte sona erer. Girişte oturum kimliği değişir (session fixation). Şifre sıfırlanınca kullanıcının bütün oturumları silinir. Çerez `SessionCookieConfiguration`'da kodda tanımlı.
- **Doğrulanmamış hesap** giriş yapabilir ama link oluşturamaz (`email_not_verified`, 403). Doğrulama durumu oturumdan değil veritabanından okunur, bu yüzden yeniden giriş gerekmez.

## Hız sınırları

`abuse` modülündeki `RateLimiter`, sınır ve anahtar (adres, e-posta, kullanıcı) başına bir Bucket4j kovası tutar. Kovalar bellekte, Caffeine içinde: boşta kalan kova bir periyot sonra silinir, sınır başına en fazla 200.000 anahtar tutulur. Değerler `vurli.rate-limits.*` ayarlarında (K42); dev profili yerelde bütün istekler aynı adresten geldiği için adres başına sınırları genişletir.

- Giriş, kayıt, şifre sıfırlama ve doğrulama e-postası `AuthController`'da; link oluşturma `LinkApiController`'da; yönlendirme `RedirectController`'da sınırlanır.
- Günlük link kotası bellekte değil, veritabanında sayılır: son 24 saatte oluşturulan linkler, silinenler dahil. Yeniden başlatma kotayı sıfırlamaz. Sınırdaki iki eşzamanlı istek kotayı bir aşabilir; bunun için kilit kullanmaya değmez.
- İstemci adresi `ClientAddresses` ile bulunur (bkz. [güvenlik](security.md#6-kaynak-tüketimi)). Next.js her API çağrısına ziyaretçinin adresini `X-Forwarded-For` olarak ekler.

## Yönetici paneli

Spring içinde Thymeleaf ile, cPanel benzeri bir iç araç: link ve hesap arama, engelleme, kötüye kullanım bildirimleri, denetim kaydı. Yalnızca `ADMIN` rolü; iki adımlı doğrulama zorunlu. `admin.vurli.io`'da çalışacak (K43); ayrıntılar M3 dilim 3'te.

## Dağıtım

- **Servisler:** Docker Compose ile `caddy`, `backend` (Spring), `frontend` (Next.js) ve `postgres`. Yerel geliştirmede ayrıca e-postaları yakalayan Mailpit.
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
