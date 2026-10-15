# Güvenlik

Güvenlik bu projenin birinci önceliği. Aşağıda URL kısaltıcıların gerçekte karşılaştığı tehditler var; her biri için önlem ve o önlemi doğrulayacak test yazılı. Henüz hiçbiri uygulanmadı.

## 1. Zararlı link barındırma (en büyük risk)

**Neden önemli:** Kısaltıcılar phishing ve zararlı yazılım linklerini gizlemek için kullanılır. Yeterince zararlı link barındırırsak `vurli.io` alan adının kendisi Google Safe Browsing, e-posta sağlayıcıları ve Spamhaus gibi kara listelere girer. O zaman tek tek kötü linkler değil, **bütün kullanıcıların bütün linkleri** çalışmaz hale gelir.

**Önlemler:**

- Yalnızca e-posta doğrulamalı hesaplar link oluşturabilir. Her workspace 24 saatte en fazla 50 link oluşturabilir; silinen linkler de sayılır (K42). *Uygulandı.*
- Link oluşturulurken ve hedef değiştiğinde Web Risk kontrolü yapılır. Aktif linkler haftalık olarak yeniden taranır.
- Tanıtım sayfasında "Kötüye kullanım bildir" formu ve `abuse@vurli.io` adresi bulunur.
- Yönetici bir linki, bir hesabı veya bir hesabın bütün linklerini tek işlemle engelleyebilir. Her engelleme denetim kaydına yazılır.
- `vurli.io`'nun kara liste durumu düzenli kontrol edilir (Google Safe Browsing site durumu, Spamhaus DBL).

**Testler:**

- Tehlikeli işaretli URL reddedilir (Web Risk'in test URL'leriyle).
- Engelleme, commit'ten sonra başlayan bütün isteklerde etkilidir; eski kayıt cache'e geri yazılamaz (`LinkCacheTests` ile doğrulandı, M1).
- Link sahibi engeli kaldıramaz.

## 2. Başka hesabın verisine erişim (IDOR)

**Önlemler:**

- Her link sorgusu oturumdaki kullanıcının workspace'iyle filtrelenir. Workspace kimliği istekten alınmaz.
- Başka bir workspace'e ait kaynak istendiğinde `404` döner.

**Test:** İki hesap açılır. A hesabı, B'nin link ID'siyle okuma, düzenleme, silme, istatistik ve QR işlemlerini dener; hepsi `404` almalı. Yeni eklenen her endpoint bu teste dahil edilir. *Uygulandı* (`LinkApiTests`: okuma, hedef, kapatma, silme, liste ve QR; `ClickStatisticsIntegrationTests`: istatistik).

## 3. Hesap ele geçirme

**Önlemler:**

- Şifreler Argon2id ile hash'lenir (Spring Security `DelegatingPasswordEncoder`, OWASP asgari parametreleri). Şifre en az 10, en fazla 128 karakter. *Uygulandı.*
- Giriş denemelerine hem IP hem hesap bazında hız sınırı uygulanır: IP başına dakikada 10 deneme, e-posta adresi başına (her yerden) 15 dakikada 10 başarısız deneme. Hesap sınırı şifreden önce denetlenir; dolunca doğru şifre de 429 alır. Kayıtlı olmayan adreslere de aynı şekilde uygulanır, hiçbir şey sızdırmaz. Bedeli: biri bilerek hatalı deneyerek bir kullanıcıyı 15 dakikaya kadar dışarıda bırakabilir (K42'de kabul edildi). *Uygulandı.*
- Hata mesajı "e-posta veya şifre hatalı" şeklindedir; hangisinin yanlış olduğu söylenmez. Kayıt ve şifre sıfırlama da bir hesabın var olup olmadığını belli etmez. *Uygulandı.*
- Doğrulama ve sıfırlama token'ları rastgele üretilir, tek kullanımlıktır, hash'li saklanır ve kısa ömürlüdür (24 saat ve 1 saat). E-postada URL fragment'ında taşınır. *Uygulandı.*
- Girişte oturum kimliği yenilenir; şifre sıfırlanınca bütün oturumlar kapatılır. *Uygulandı.*
- Yönetici hesaplarında TOTP ile iki adımlı doğrulama zorunludur (M4'ten önce).

**Testler** (`AccountManagementIntegrationTests`, `AuthApiTests`):

- Kullanılmış, süresi dolmuş, bozuk veya bilinmeyen token aynı şekilde reddedilir; veritabanında yalnızca SHA-256 saklanır.
- Kayıt yanıtı yeni ve kayıtlı adres için birebir aynıdır; yanlış şifre ile bilinmeyen adres aynı 401'i alır.
- Aynı adrese 8 paralel kayıttan tek hesap çıkar.
- Tekrar girişte eski oturum kimliği geçersizleşir; şifre sıfırlanınca açık oturum 401 alır.
- Negatif kontroller: oturum kimliği yenileme, sıfırlamada oturum silme, token'ın tek kullanımlık olması ve başlık filtresi sırayla bozulunca ilgili testler başarısız oluyor.
- Kaba kuvvet denemeleri hız sınırına takılır; başarılı girişler başarısız sayılmaz; kayıtlı olmayan adres aynı yanıtı alır (`RateLimitIntegrationTests`). *Uygulandı.*

## 4. URL hileleri ve yönlendirme bütünlüğü

**Önlemler:**

- Hedef her zaman veritabanındaki kayıttır ve istek parametresiyle değişmez. Açık yönlendirme yoktur.
- Kullanıcı bilgisi içeren URL'ler, `javascript:` ve `data:` şemaları, IP adresleri ve `vurli.io`'ya geri dönen linkler reddedilir.
- `Host` başlığına güvenilmez; yalnızca tanımlı host'lar işlenir. Kısa linkler yalnızca kısa link host'unda, API ve health yalnızca iç ağ host'unda yanıt verir; API kısa link alan adı üzerinden hiçbir yolla açılmaz. Proxy başlıkları (`X-Forwarded-*`) dikkate alınmaz. *Uygulandı* (`HostFilter`; `RedirectIntegrationTests`: yabancı host, `vurli.io.evil.com`, `//api/...`; filtre bozulunca testler başarısız oluyor).
- `Location` başlığına normalize edilmiş URL yazılır; CR/LF karakterleri içeremez.

**Test:** URL doğrulayıcı geniş bir sınır değer setiyle test edilir: Unicode ve punycode host'lar, encode edilmiş karakterler, IPv6, kullanıcı bilgisi ve aşırı uzun URL'ler.

## 5. Panelde XSS, CSRF ve BFF güven sınırı

**Önlemler:**

- React ve Thymeleaf varsayılan olarak escape eder. `dangerouslySetInnerHTML` ve `th:utext` kullanılmaz. Kullanıcının girdiği hedef URL'ler panelde yalnızca metin olarak gösterilir; tıklanabilir linkler `rel="noopener noreferrer"` taşır.
- CSP ile script'ler yalnızca kendi origin'imizden yüklenir; Next.js'in gerektirdiği inline script'ler her istekte yeni üretilen nonce ile izin alır (`src/proxy.ts`). Production'da satır içi stil ve `eval` yasak; `frame-ancestors 'none'`, `form-action 'self'`, `object-src 'none'`. Production sayfası tarayıcıda CSP ihlali olmadan çalıştı. *Uygulandı.*
- Ek başlıklar: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy`; `X-Powered-By` kapalı. *Uygulandı.*
- E-posta linkindeki token sayfa açılınca okunur ve adres çubuğundan silinir; tarayıcı geçmişinde kalmaz. Doğrulama sayfası token'ı yalnızca bir kez gönderir (React Strict Mode'da da). *Uygulandı.*
- Kullanıcı panelinde CSRF koruması iki katmanlı: Next.js'te `SameSite=Lax` çerez ve `Origin` kontrolü; Spring API'de her değişiklik isteğinde zorunlu `X-Vurli-Client` başlığı (OWASP'ın "custom request header" savunması; API CORS izni vermez). Yönetici panelinde Spring Security CSRF koruması açık olacak (M3).
- Oturum çerezi host-only, `Secure`, `HttpOnly` ve `SameSite=Lax`'tır. Kısa link alan adı (`vurli.io`) oturum çerezini hiç görmez.
- **Spring API internete açık değildir.** Yalnızca iç ağdan, Next.js'ten gelen istekleri kabul eder. Bu ek bir savunma katmanıdır, tek başına güvenlik değildir: Next.js'in uç noktaları da dışa açık olduğu için her yetki kararı Spring'de verilir.
- Panel formlarından gelen link kimliği API yoluna girmeden önce UUID olarak doğrulanır; böylece `../auth/logout` gibi bir değer başka bir uç noktaya ulaşamaz (`lib/links.test.ts`). *Uygulandı.*
- Next.js tarayıcıdan gelen çerezi yalnızca Spring'e iletir; başka bir adrese istek atarken çerez taşımaz. Spring'in adresi yapılandırmadan gelir, istekten alınmaz (SSRF'ye karşı).

**Testler:**

- Hedef URL'ye veya özel ada yerleştirilen bir script panelde çalışmaz.
- Başka bir origin'den gelen değişiklik isteği reddedilir.
- Spring API'nin portu Compose ağı dışından erişilemez (canlı ortam kontrol listesi, M4).
- Başka bir hesabın oturum çereziyle yapılan istekler IDOR testleriyle aynı şekilde `404` alır.

## 6. Kaynak tüketimi

- Kayıt, giriş, şifre sıfırlama ve link oluşturma işlemlerine hız sınırı uygulanır (Bucket4j, bellekte; değerler K42'de). E-posta gönderen üç istek (kayıt, şifre sıfırlama, doğrulama e-postası) IP başına tek bir sınırı paylaşır. Yönlendirmede IP başına dakikada 300. IPv6 adresleri /64 ağına göre sayılır, çünkü tek bir ev ya da sunucu genellikle bütün bir /64 alır. Sınırı aşan istek `429` ve `Retry-After` alır. *Uygulandı.*
- İstemci adresi `X-Forwarded-For`'un son değeridir; önceki değerler ziyaretçi tarafından uydurulabilir. Kısa linklerde bu değeri Caddy yazar (ziyaretçinin gönderdiğini değiştirir), API'de Next.js ziyaretçinin adresini iletir. Spring yalnızca bu ikisinin arkasında çalıştığı için bu başlığa güvenir; adres olmayan değerler yok sayılır. Adresler saklanmaz.
- Tıklama kuyruğu sınırlıdır; dolarsa olay atılır ama yönlendirme etkilenmez.
- Listeleme sayfalıdır ve sayfa boyutunun üst sınırı vardır. İstatistik sorguları tarih aralığıyla sınırlıdır.
- Ciddi bir DDoS saldırısında Cloudflare proxy'si devreye alınabilir. MVP'de kurulmayacak.

## 7. Kişisel veri

- IP adresi veritabanına yazılmaz; yalnızca ülke kodu saklanır. Referrer'dan yalnızca host saklanır, path ve query string'de veri ya da token olabilir. *Uygulandı* (`ClickStatisticsIntegrationTests` `click_events` tablosunun sütunlarını da denetler). Caddy erişim logları 7 gün tutulur.
- Hedef URL'ler loglanmaz, çünkü query string'lerinde token bulunabilir.
- Gizlilik politikası ve hesap silme özelliği kapalı betadan önce hazır olur.

## 8. Sırlar ve tedarik zinciri

- Sırlar repoda tutulmaz. Sunucuda `600` izinli `.env` dosyasında, CI'da GitHub Actions secrets'ta bulunur.
- Bağımlılık güncellemeleri Dependabot ile yapılır. Üçüncü taraf GitHub Actions commit SHA'sıyla sabitlenir.
- Fork'tan gelen PR'lar secret'lara erişemez.
- Docker image'ı root olmayan bir kullanıcıyla ve minimal bir base image üzerinde çalışır.

## Kapsam dışı (şimdilik)

Bağımsız pentest, uyum sertifikaları (ASVS, SOC 2) ve WAF şimdilik kapsam dışı. Kapalı betadan önce yukarıdaki testlerin hepsinin geçmesi gerekiyor.
