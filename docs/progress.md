# İlerleme

Son güncelleme: 2026-10-15

## Şu an

M0, M1, M2 ve M3 tamamlandı; repo `burhancabiroglu/vurli-io`. M3'ün "bitti" şartları karşılandı: yönetici engellemesinin uçtan uca testi (`e2e/admin-block.spec.ts`) ve kaydedilen yük testi sonuçları ([mimari, Ölçek](architecture.md#ölçek)).

## Sıradaki adım

1. Dilim 5 commit'inin CI işlerini kontrol et.
2. M4, yayın ve kapalı beta ([yol haritası](roadmap.md)). Açık kararlar: A01 (e-posta servisi), A02 (VPS), A05 (şirket, gizlilik politikası, kullanım koşulları). Önce dağıtım dosyaları (Dockerfile'lar, canlı Compose, Caddy) ve sırlar (Web Risk anahtarı, TOTP anahtarı, veritabanı) planlanmalı.

## Kayıt

### 2026-10-15 — M3 dilim 5: ilk yük testi

- Karar: K52 (Gatling, VPS benzeri konteyner, her ziyaret kendi adresiyle).
- Eklenenler:
  - Gatling Gradle eklentisi 3.16.0 ve `src/gatling/java/io/vurli/loadtest/RedirectSimulation.java`. Senaryolar: `browse` (10.000 link, %80'i ilk 200'e), `viral` (tek link) ve `limit` (tek adres).
  - `backend/loadtest/compose.yaml`: production jar'ı ve ayarları; uygulama 2 vCPU / 2 GB, PostgreSQL 1 vCPU / 1 GB.
  - `backend/loadtest/seed.sql` ve `backend/loadtest/run.sh`. Betik her çalıştırmadan sonra yazılan tıklama sayısını da gösteriyor.
  - `gradle/gradle-daemon-jvm.properties`: Gradle'ın kendisi de Java 25'te çalışıyor.
- Sonuçlar [mimari belgesinde](architecture.md#ölçek):
  - 1.000–8.000 istek/sn kademelerinde hiç hata yok; p99 2–16 ms.
  - 8.000/sn'de uygulama 2 çekirdeğin üçte ikisini kullandı.
  - Hiçbir kademede tıklama düşmedi.
  - Tek adresten gelen yükte isteklerin %55'i 429 aldı, beklenen oranda.
- Yol üstünde bulunan sorunlar:
  - **Gatling eski Java'yla çalışıyordu:** eklenti simülasyonları Gradle daemon'ının JVM'inde çalıştırıyor ve bu makinede daemon Java 17'deydi; Java 25 ile derlenen sınıf yüklenemedi. Daemon artık Java 25'te. CI zaten Java 25 kuruyor.
  - **İlk ölçüm test düzeneğini ölçtü:** her ziyaret yeni bir TCP bağlantısı açtığı için ilk çalıştırmada her kademede yaklaşık 11.000 başarılı ziyaretten sonra hatalar başladı (250/sn'de %31, 2.000/sn'de %87). Kısa (15 sn) bir çalıştırma hatasız geçti ve uygulama işlemcisinin dörtte birini kullanıyordu; sınırın Mac ve Docker Desktop'taki port tükenmesi olduğu böyle anlaşıldı. Canlıda uygulamaya bağlantıları Caddy açar ve yeniden kullanır; senaryo da artık ortak bir bağlantı havuzu kullanıyor. Bu ilk sonuçlar kayıt dışı bırakıldı.
  - **Hız sınırını ölçmeyen senaryo:** tek adres senaryosu saniyede 10 istekle 50 saniye sürüyordu. Bu hızda kova (300 jeton, saniyede 5 yenileme) ancak 60 saniyede boşalırdı; senaryo üstelik 429'u başarılı sayıyordu. Artık saniyede 20 istekle 60 saniye sürüyor ve 429 oranının %30–75 arasında olmasını şart koşuyor. Ölçülen oran %55.
  - **Betikte uygulanmamış değişiklik:** betiği düzenleyen Python değiştirmesi eşleşmemişti ve sessizce geçmişti; betik yeniden yazıldı.
- Doğrulama:
  - `cd backend && loadtest/run.sh`: `RATES="1000 2000 4000 8000"`, `VIRAL_RATE=8000` ile çalıştı; bütün kademelerin doğrulamaları geçti. Ardından `limit` senaryosu saniyede 20 istekle 60 saniye çalıştı: 585 yönlendirme, 720 adet 429; doğrulama geçti.
  - Kaynak kullanımı test boyunca 5 saniyede bir `docker stats` ile örneklendi.
  - `cd backend && ./gradlew build`: 350 test, 0 başarısız; Gatling görevleri build'e girmiyor (`--dry-run` ile görüldü).
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.
- Açık konular: TLS ve Caddy yolda değildi; canlı sunucuda bileşenler aynı CPU'yu paylaşacak; önbellekte olmayan çok sayıda linke yayılan trafik ölçülmedi. Canlıda tekrar ölçülmeli (M4).

### 2026-10-15 — M3 dilim 4: Web Risk ve bildirim formu

- Kararlar:
  - K24 kesinleşti. Web Risk koşulları doğrulandı: ayda ilk 100.000 sorgu ücretsiz, sonrası 1.000 sorgu başına 0,50 $; Google Cloud projesi, faturalandırma ve API anahtarı gerekiyor. Anahtar M4'te verilecek.
  - K51: herkese açık bildirim formu `vurli.io/abuse`.
- Modül düzeni: hız sınırları `ratelimit`, denetim kaydı `audit` modülüne taşındı. Sebep: `abuse`'un linkleri engelleyebilmesi için `link`'e bağlanması gerekiyordu. Oysa `link` → `account` → `abuse` zinciri zaten vardı; taşınmasaydı döngü oluşacaktı. Davranış değişmedi; `ModularityTests` döngü bulmuyor.
- `link`:
  - `TargetSafety` arayüzü (SAFE, UNSAFE, UNKNOWN) ve `UnsafeTargetException` (422 `unsafe_target`).
  - Oluşturmada ve hedef değişiminde kontrol. Yanıt yoksa link kontrolsüz oluşturuluyor.
  - `LinkModeration`: `dueForSafetyCheck`, `recordSafe` ve `blockIfTarget` (yalnızca hedef değişmediyse), `findByCode`.
- `abuse`:
  - `WebRiskSafety` (JDK HTTP istemcisi, 3 sn zaman aşımı, anahtar başlıkta).
  - `SafetyScanner` (5 dakikada bir, çalışma başına 50 link, 7 günde bir yeniden kontrol).
  - `AbuseReports` ve `/abuse` formu (Thymeleaf, Türkçe ve İngilizce).
  - Şema: `V8__create_abuse_reports.sql`.
- Varsayılan güvenlik zinciri:
  - Oturum oluşturmuyor.
  - CSRF token'ı `VURLI_REPORT_CSRF` çerezinde (`Path=/abuse`, `SameSite=Strict`).
  - Kısa link alan adındaki sayfalara CSP `default-src 'none'`.
- Yeni hız sınırı: adres başına saatte 5 bildirim.
- `admin`: `/admin/reports` ve menüde açık bildirim sayısı.
- Panel: `unsafe_target` hata metni.
- Yol üstünde bulunan sorunlar:
  - **Taramayı kilitleyen tek URL:** tarama ilk yanıtsız sorguda duruyordu. Sıralamada kontrolsüz linkler hep başta olduğu için, servisin hep hata verdiği tek bir URL bütün taramayı kalıcı olarak durdurabilirdi. Test, başka bir testin bıraktığı yanıtsız link yüzünden kırılınca ortaya çıktı. Artık art arda 3 yanıtsız sorgu kesinti sayılıyor, tek bir yanıtsız sorgu atlanıyor; bunun ayrı bir testi var.
  - **TRUNCATE:** `abuse_reports` da `links`'e bağlı; `LinkManagementIntegrationTests` listesine eklendi.
  - **MockMvc ve SameSite:** Spring Security özniteliği çereze Servlet 6'nın genel öznitelik alanıyla ekliyor; MockMvc bunu başlık metnine yazmıyor. Test özniteliği çerez nesnesinden okuyor. Gerçek sunucunun başlığında `SameSite=Strict` olduğu `curl` ile görüldü.
- Doğrulama:
  - `cd backend && ./gradlew build`: 350 test, 0 başarısız.
    - `WebRiskSafetyTests` (5, yerel HTTP sunucusuyla): boş yanıt güvenli, `threat` tehlikeli; anahtar başlıkta, URL'de değil; hedefteki `?`, `&` ve iç içe `uri=` tek bir parametre olarak kodlanıyor; 500, 403 ve zaman aşımı "yanıt yok"; anahtar yoksa hiç istek gitmiyor.
    - `SafetyIntegrationTests` (8): tehlikeli hedef reddediliyor ve hiçbir şey yazılmıyor (API'de 422); kontrol zamanı yalnızca yanıt varsa yazılıyor; yeni hedef de kontrol ediliyor; tarama sonradan tehlikeli olan linki engelliyor (önbellekteyken bile 410) ve sistem eylemi olarak kaydediyor; kontrolsüz linkleri kontrol edip yenilere dokunmuyor; 3 yanıtsız sorguda duruyor; tek yanıtsız URL'yi atlıyor; eski hedef için verilen karar geçersiz.
    - `AbuseReportsTests` (15): yapıştırılan biçimlerden kod çıkarma ve geçersiz girdilerin reddi.
    - `AbuseReportIntegrationTests` (6): sayfa yalnızca CSRF çerezi koyuyor (oturum yok), CSP var; bildirim linkiyle birlikte saklanıyor; var olan ve olmayan link için birebir aynı yanıt; hatalı linkte girilenler korunuyor ve kayıt yazılmıyor; token'sız POST 403; adres başına 5 bildirim.
    - `AdminModerationIntegrationTests` (+1): bildirimden engelleme (410, denetim kaydında bildirim kimliği) ve işlem yapmadan kapatma; ayrıntılar escape ediliyor; kapalı bildirim tekrar kapatılamıyor.
    - Negatif kontroller (her biri ayrı, sonra geri alındı): tehlikeli hedefi kabul etmek, taramayı ilk yanıtsız sorguda durdurmak, hedefi değişmiş linki engellemek, bildirim sınırını kaldırmak, geçersiz kodu kabul etmek, CSRF'i kapatmak ve anahtarı URL'ye koymak testleri kırdı.
  - `cd frontend`: lint, typecheck, `npm test` (65), build geçti; `npm run e2e`: 3 test geçti.
  - Gerçek sunucuda: anahtar olmadığı için kontrolün kapalı olduğu uyarısı log'da. `curl` ile form başlıkları (`Set-Cookie: VURLI_REPORT_CSRF=…; Path=/abuse; HttpOnly; SameSite=Strict`, CSP, `no-store`); kısa link yanıtında çerez yok. Uygulama içi tarayıcıda form dolduruldu ve gönderildi, yöneticiyle (TOTP) bildirim listede linkin sahibi ve durumuyla görüldü; "linki engelle ve kapat" sonrası link 410 döndü; konsolda hata yok.
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.
- Açık konular:
  - Gerçek Web Risk servisiyle deneme anahtar gelince (M4); Google'ın test URL'leriyle yapılacak.
  - Tanıtım sayfasından forma bağlantı ve `abuse@vurli.io` M4'te.
  - Web Risk kotası: tarama en fazla 5 dakikada 50 sorgu yapıyor; çok sayıda kontrolsüz link birikirse günlük ücretsiz payı (yaklaşık 3.300) aşabilir. Anahtar verildiğinde biriken linkler için gözlenmeli.

### 2026-10-15 — M3 dilim 3b: yönetici işlemleri ve denetim kaydı

- Karar: K50 (Varsayılan): engeli kaldırınca link aktif olur; askıya alma girişi kapatır, oturumları bitirir ve linkleri engeller; askıyı kaldırma yalnızca girişi açar; yöneticiler askıya alınamaz; her işlem gerekçe ister.
- Şema: `V7__add_user_suspension.sql` (`users.suspended_at`).
- `link`: `LinkModeration` artık `unblock`, `blockAll` (tek `UPDATE`, sürüm artırır, commit'ten sonra önbelleği temizler), `search` (kod ya da hedef; LIKE jokerleri kaçışlı) ve `get` sunuyor. Yeni `ModeratedLink`.
- `account`:
  - `AccountAdministration`: arama, askıya alma ve kaldırma, sahip ve aktör e-postaları.
  - `VurliUser.isEnabled`.
  - Hesap durumu artık şifreden sonra denetleniyor: askıdaki hesap yanlış şifreyle aynı yanıtı alıyor ve yanıt süresi farklılaşmıyor.
- `abuse`: `AuditLog.recent`, `AuditEntry`.
- `admin`:
  - `/admin/links`, `/admin/accounts` ve `/admin/audit` sayfaları; üst menü.
  - Her işlem gerekçe istiyor (3–500 karakter) ve denetim kaydına işlemi yapan yöneticiyle yazılıyor.
  - Sayfada gösterilen arama metni, hedefler ve e-postalar escape ediliyor.
- Uçtan uca test: `e2e/admin-block.spec.ts`. Bir kullanıcı link oluşturuyor. Bir yönetici kayıt olup TOTP kuruyor (kod Node'un `crypto` modülüyle hesaplanıyor), linki arayıp gerekçeyle engelliyor. Kısa link 410 dönüyor, sahibin sayfasında "Engellendi" görünüyor, kayıt denetim kaydında. Ortak yardımcılar `e2e/support.ts`'e taşındı.
- Yol üstünde bulunan sorunlar:
  - **Önbelleği sınamayan testler:** askıya alma ve toplu engelleme testleri linki engellemeden önce ziyaret etmiyordu; önbellek temizlenmese de geçerlerdi. Önce ziyaret ediliyor; temizleme kaldırılınca iki test kırılıyor.
  - **Jokerleri sınamayan test:** arama testindeki `%25`, MockMvc'de ikinci kez kodlandığı için düz metin olarak aranıyordu ve kaçış kaldırıldığında test geçiyordu. Değer artık parametre olarak veriliyor; kaçış kaldırılınca kırılıyor. Escape testinde de aynı hata vardı, o da düzeltildi.
  - **Tekrar çalıştırılan uçtan uca test:** denetim kaydında önceki çalıştırmaların aynı gerekçesi de bulunduğu için Playwright birden fazla eşleşmede durdu. Gerekçe her çalıştırmaya özgü.
  - **Koyu tema:** kırmızı düğmelerdeki beyaz yazı okunmuyordu; renk iki temada da koyu kırmızı.
- Doğrulama:
  - `cd backend && ./gradlew build`: 315 test, 0 başarısız.
    - `AdminModerationIntegrationTests` (9):
      - Panelden engellenen link sonraki ziyarette 410, sahibi açamıyor (409 `link_blocked`); engeli kaldırınca 302; denetim kaydında yönetici ve gerekçe.
      - Gerekçesiz ya da 500 karakterden uzun gerekçeli işlem reddediliyor, hiçbir şey değişmiyor.
      - Askıya alma: sahibin API oturumu 401, iki link 410, doğru şifreyle giriş yanlış şifreyle birebir aynı 401; askı kalkınca giriş açılıyor, linkler engelli kalıyor.
      - Toplu link engelleme girişi açık bırakıyor.
      - Yönetici askıya alınamıyor.
      - `%` ve `_` düz metin olarak aranıyor.
      - Arama metni escape ediliyor.
      - Denetim kaydı ekranı.
      - Yalnızca şifreyle açılmış oturum ve CSRF'siz istek işlem yapamıyor.
    - `LinkLifecycleIntegrationTests` (+2): toplu engellemeyi sahibin eski sürümlü değişikliği geri alamıyor, başka workspace etkilenmiyor; engeli kaldırma yalnızca engelli linki değiştiriyor.
    - Negatif kontroller (her biri ayrı, sonra geri alındı), her biri testleri kırdı:
      - toplu engellemede sürüm artışını kaldırmak;
      - önbellek temizliğini kaldırmak (2 test);
      - askıda oturumları silmemek;
      - girişte askıyı yok saymak;
      - arama kaçışını kaldırmak;
      - gerekçe kontrolünü kaldırmak;
      - yöneticinin askıya alınmasına izin vermek.
  - `cd frontend && npm run lint && npm run typecheck`: geçti.
  - `npm run e2e`: 3 test geçti; yönetici testi art arda iki kez daha çalıştırıldı ve geçti. Negatif kontrol: tek link engellemede önbellek temizliği kaldırılıp backend yeniden başlatıldığında uçtan uca test 410 yerine 302 aldı ve kırıldı; kod geri yüklendi.
  - Uygulama içi tarayıcıda (gerçek sunucu): yerel yöneticiyle TOTP girişi, hesaplar, linkler ve denetim kaydı ekranları; konsolda hata yok.
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.
- Açık konular:
  - Yönetici rolünü panelden verme ya da alma yok; bilinçli olarak yalnızca başlangıç ayarıyla (K48).
  - Denetim kaydında filtre ya da sayfalama yok; en yeni 200 kayıt.

### 2026-10-15 — M3 dilim 3a: yönetici girişi ve ikinci adım

- Kararlar: K47 (TOTP kendi kodumuzla, gizli anahtar AES-GCM ile şifreli), K48 (ilk yönetici başlangıç ayarıyla), K49 (yedek kodlar ve sunucudan sıfırlama). Kullanıcının isteğiyle kurtarma için iki yol birden uygulandı.
- Şema: `V6__create_admin_security.sql` (`admin_credentials`, `admin_backup_codes`, `audit_log`).
- Yeni modüller: `admin` (giriş akışı ve sayfalar) ve `qr`. QR çizici `link.internal`'dan `qr` modülüne taşındı, çünkü TOTP kurulum sayfası da kullanıyor.
- `account`:
  - `TwoFactorAuthentication`: kurulum, onay, doğrulama, yedek kodlar.
  - `Totp` ve `Base32Codec`.
  - `SecretCipher` (AES-256-GCM).
  - `AdminBootstrap`: `vurli.admin.promote` ve `vurli.admin.reset-totp`.
- `abuse`:
  - `AuditLog`.
  - Yeni hız sınırı: `TOTP_FAILURES_PER_USER` (15 dakikada 5); K42'nin uzantısı.
- Kök paket:
  - Üçüncü güvenlik zinciri (`/admin/**`; CSRF, CSP, ikinci adım yetkisi).
  - `HostFilter`'a yönetici host'u.
  - Yönetici paneline ayrı oturum çerezi (`VURLI_ADMIN_SESSION`, `/admin`, `SameSite=Strict`).
- Yol üstünde bulunan sorunlar:
  - **Şifreyle ikinci adımı atlama riski:** `VurliUser` rolü oturuma yazdığı için, bir yöneticinin kullanıcı paneline yalnızca şifreyle açtığı oturum da `ROLE_ADMIN` taşıyor. Panel bu yüzden yalnızca ikinci adımda verilen `ADMIN_SECOND_FACTOR` yetkisini kabul ediyor. Testi var; rol kontrolüne geri dönülünce kırılıyor.
  - **CSRF hatası yönlendirmeye dönüşüyordu:** yetki reddi için yazılan "girişe yönlendir" kuralı CSRF hatalarını da yakalıyordu. CSRF hataları artık 403.
  - **Geliştirme anahtarı 33 bayttı:** ilk yazılan Base64 değer 33 bayta çözülüyordu ve uygulama başlamazdı. Doğrulanmış bir 32 baytlık anahtarla değiştirildi.
- Doğrulama:
  - `cd backend && ./gradlew build`: 304 test, 0 başarısız.
    - `TotpTests` (16): RFC 6238 ekindeki SHA-1 vektörleri (8 hane), RFC 4226 HOTP değerleri, ±1 adım tolerans ve ±2'nin reddi, kullanılmış ve daha eski kodun reddi, biçim dışı kodlar, RFC 4648 Base32 vektörleri.
    - `SecretCipherTests` (3): her şifrelemede farklı nonce, kurcalanmış veri ve yanlış anahtarın reddi, yanlış boyutta anahtarla başlamama.
    - `AdminLoginIntegrationTests` (9): ilk girişte kurulum (çerez `Path=/admin; HttpOnly; SameSite=Strict`, yenilenince aynı anahtar, yanlış kodun reddi, 10 yedek kod, `no-store`, oturum kimliğinin değişmesi, eski kimliğin çalışmaması, denetim kayıtları); kurulumda kullanılan kodun tekrar kabul edilmemesi, yedek kodun bir kez çalışması; yalnızca şifreyle açılmış oturumların (API oturumu, çerez adı değiştirilmiş API oturumu, yarı girişli oturum) panele girememesi; yönetici olmayan hesabın yanlış şifreyle birebir aynı sayfayı alması; 5 yanlış koddan sonra doğru yedek kodun da reddi; CSRF'siz isteğin 403 alması ve hiçbir kayıt bırakmaması; panelin yalnızca kendi host'unda açılması ve yönetici host'undan API'ye ulaşılamaması; çıkış; ikinci adım sayfalarının şifre adımı olmadan açılmaması.
    - `AdminBootstrapIntegrationTests` (4): doğrulanmış hesabın bir kez yükseltilip kaydedilmesi (harf büyüklüğünden bağımsız); doğrulanmamış ve bilinmeyen hesabın yükseltilmemesi; sıfırlamanın ikinci adımı ve yedek kodları silmesi ve yeni kurulumun yeni anahtarla başlaması; yönetici olmayan hesapta sıfırlamanın bir şey yapmaması.
    - Negatif kontroller (her biri ayrı, sonra geri alındı): yetkiyi `hasRole("ADMIN")`'e çevirmek, tekrar korumasını kaldırmak, yedek kodun ikinci kullanımına izin vermek, girişte oturum kimliğini yenilememek (3 test), yönetici olmayan hesabı kabul etmek, kod deneme sınırını kaldırmak ve doğrulanmamış hesabı yükseltmek testleri kırdı.
  - Gerçek sunucuda (`bootRun --args='--vurli.admin.promote=…'`): yerel test hesabı yönetici yapıldı ve log'a yazıldı. Uygulama içi tarayıcıda giriş yapılınca kurulum sayfası açıldı ve QR göründü. Sayfadaki anahtardan Python'un standart kütüphanesiyle bağımsız olarak hesaplanan kod kabul edildi; yani uygulamamız standart TOTP uygulamalarıyla uyumlu. Yedek kodlar gösterildi, ana sayfa açıldı; oturum çerezi JavaScript'ten görünmüyor; çıkıştan sonra `/admin` giriş sayfasına gidiyor; konsolda CSP ihlali yok. `curl` ile yanıt başlıkları: CSP `default-src 'none'…`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`.
  - `npm run e2e`: 2 test geçti (yeni çerez ve host kuralları kullanıcı panelini etkilemedi).
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.
- Açık konular:
  - Yarı girişli oturumun 5 dakikalık süresi otomatik testle sınanmadı (saat kontrolü gerektiriyor).
  - Yönetici paneli yalnızca Türkçe ve İngilizce metinlerle, tarayıcı diline göre; tasarımı sade.
  - Canlıda `VURLI_ADMIN_TOTP_KEY` ortam değişkeni M4'te sunucuya eklenecek; anahtar değişirse kayıtlı TOTP anahtarları çözülemez ve yöneticilerin ikinci adımı sıfırlanmalı.

### 2026-10-15 — M3 dilim 2: istatistik

- Kararlar: K44 (ülke verisi DB-IP Lite), K45 (gün UTC), K46 (grafik shadcn chart / Recharts).
- Şema: `V5__create_click_stats.sql` (`click_events`, `link_daily_stats`).
- `link`: yönlendirme sonucu artık linkin kimliğini de taşıyor (`LinkSnapshot`, `LinkResolution.Redirect`).
- `analytics` modülü:
  - `ClickRecorder`: bot, önizleme ve prefetch ayıklama.
  - `ClickWriter`: bellek kuyruğu ve saniyede bir toplu yazma.
  - `UserAgents`, `ReferrerHosts`.
  - `MmdbCountryLookup` (`com.maxmind.db:maxmind-db` 4.2.0, çalışma zamanı bağımlılığı yok).
  - `DailySummaries`: gece özeti ve 90 günlük saklama.
  - `ClickStatistics` ve `GET /api/v1/links/{id}/stats`.
- `abuse.ClientAddresses` artık adresin kendisini de veriyor; ülke araması hız sınırlarıyla aynı istemci adresini kullanıyor.
- `backend/scripts/download-geoip.sh` DB-IP'nin aylık dosyasını `backend/data/geoip/`'e indiriyor (gitignore'da). Backend CI'ı dosyayı indirmeyi deniyor; başarısız olursa uyarı yazıyor, gerçek dosya testleri atlanıyor.
- Panel: link sayfasında istatistik kartı (toplam, son 30 gün, günlük grafik, referrer, ülke ve cihaz listeleri, DB-IP atfı). Ülke adları `Intl.DisplayNames` ile dile göre yazılıyor.
- Yol üstünde bulunan sorunlar:
  - **shadcn chart ve CSP:** bileşen renkleri `dangerouslySetInnerHTML` ile satır içi `<style>` olarak yazıyordu. O parça çıkarıldı (K46). Recharts sunucuda `style` öznitelikleri ürettiği için grafik yalnızca tarayıcıda çiziliyor. Production build'de uçtan uca test konsolda hata görmedi.
  - **Görünmeyen sütunlar:** temanın `--chart-1` rengi neredeyse beyaz (oklch 0.87). Tarayıcıda sütunların var olduğu ama görünmediği ölçüldü; renk `--primary`'ye bağlandı.
  - **Anycast adres:** gerçek dosya testi ilk yazıldığında Google DNS'in IPv6 adresini ABD bekliyordu; DB-IP onu Kanada'da gösteriyor. Testte ulusal akademik ağların adresleri kullanılıyor (ULAKBİM → TR, WIDE ve Osaka Üniversitesi → JP).
  - **Testin kendi ziyaretleri:** Playwright'ın `request` aracı, testin cihaz ayarındaki masaüstü Chrome User-Agent'ını gönderiyor ve yönlendirme kontrolleri tıklama sayılıyordu. Kontrol istekleri izleme aracı User-Agent'ı gönderiyor; Playwright, Puppeteer ve Selenium da bot listesinde.
  - **TRUNCATE:** `LinkManagementIntegrationTests` her testten önce `links`'i boşaltıyor; yeni yabancı anahtarlar yüzünden yeni tablolar da listeye eklendi.
- Doğrulama:
  - `cd backend && ./gradlew build`: 272 test, 0 başarısız, 0 atlanan (DB-IP dosyası yerelde var).
    - `ClickStatisticsIntegrationTests` (5): tarayıcı ziyareti sayılıyor, bot, User-Agent'sız, prefetch ve HEAD sayılmıyor; referrer'dan yalnızca host (`?token=secret` yok) ve tabloda IP sütunu yok; özet ve canlı kayıt birlikte, bir gün iki kez sayılmadan (toplam 6, 30 günde 5, gün gün doğru sayılar); gece yarısı UTC sınırı (23:59:59.999 dün, 00:00 bugün); işin iki kez çalışması sonucu değiştirmiyor; 100 günlük ham kayıt önce özetlenip sonra siliniyor; başka hesap 404, oturumsuz 401.
    - `UserAgentsTests` (32), `ReferrerHostsTests` (15), `ClickWriterTests` (2: dolu kuyruk ve yazma hatası atıyor, istisna fırlatmıyor), `MmdbCountryLookupTests` (2, gerçek DB-IP dosyasıyla).
    - Negatif kontroller (her biri ayrı, sonra geri alındı): HEAD'in sayılması, bot ayıklamanın kaldırılması, canlı sorguda "özeti olan günleri dışla" koşulunun kaldırılması, silmenin özetten önce yapılması, istatistik uç noktasında sahiplik kontrolünün kaldırılması ve bugünün de özetlenmesi testleri kırdı.
  - `cd frontend`: lint, typecheck, `npm test` (65 test; yeni `withOther`, `percent`), build, `npm audit --omit=dev --audit-level=high` (0 açık; Recharts 3.8.0 ile 167 paketin imzası doğrulandı).
  - `npm run e2e` (production build, gerçek backend): 2 test geçti. Akışa telefon User-Agent'ı ve Instagram referrer'ı ile bir ziyaret eklendi; kartta toplam 1, `instagram.com` ve "Telefon" görünüyor; kontrol amaçlı diğer ziyaretler sayılmadı; konsolda hata yok.
  - Gerçek sunucuda: `X-Forwarded-For` ile Türkiye ve Japonya adreslerinden 7, adres belirtmeden 1 tarayıcı ziyareti ve 1 Twitterbot ziyareti. API 8 tıklama, ülkeler TR 5, JP 2, bilinmiyor 1 döndü; Twitterbot sayılmadı. Yerel veritabanına geçmiş günler için örnek tıklamalar eklenip kart tarayıcıda incelendi.
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.
- Açık konular:
  - Ana sayfadaki link listesinde tıklama sayısı yok; yalnızca link sayfasında.
  - iPadOS Safari kendini Mac olarak tanıttığı için masaüstü sayılıyor.
  - Canlı ortamda DB-IP dosyasının aylık güncellenmesi M4'te (cron veya Compose işi).

### 2026-10-15 — CI hatası: hız sınırı testlerinde zamanlama

- `68cb7d9` ve `50c5170` için Backend kırmızı ([38718274867](https://github.com/burhancabiroglu/vurli-io/actions/runs/38718274867), [38718951319](https://github.com/burhancabiroglu/vurli-io/actions/runs/38718951319)); Frontend ve E2E yeşil.
- İlk teşhis log görülmeden yapıldı (log'lar oturum açmadan okunamıyor) ve eksik çıktı:
  - `loginAttemptsAreLimitedPerAddress` testi `Retry-After` değerini tam `6` bekliyordu. Kova sürekli dolduğu için bu değer girişlerin süresine bağlı. Teste 1,5 saniyelik bekleme eklenince yerelde de kırıldı; bu gerçek bir kırılganlıktı ve `50c5170` ile düzeltildi. Test artık 1–6 aralığını kabul ediyor; kesin değerler `RateLimiterTests`'te elle ilerletilen bir saatle (Bucket4j `TimeMeter`) sınanıyor.
  - Ama CI yine kırıldı, demek ki asıl sebep bu değildi.
- Görünürlük: `b8fe593` ile Backend iş akışına, başarısız testleri herkese açık annotation olarak yazan bir adım eklendi (`.github/scripts/junit-annotations.py`). Sonraki çalışma ([38719407386](https://github.com/burhancabiroglu/vurli-io/actions/runs/38719407386)) kırılan testi gösterdi.
- Asıl kök neden: `redirectsAreLimitedPerAddress`. Döngü ilk 429'u görünce duruyor, test ardından yeni bir isteğin 429 almasını bekliyordu. Yönlendirme kovasına 200 ms'de bir jeton geri geldiği için yavaş makinede iki istek arasında jeton yetişiyor ve yeni istek 302 alıyordu. Bu hatayı yine ben yazmıştım; dayanıklı hale getirmek için eklediğim döngünün kendisi yarış durumu içeriyordu.
- Düzeltme: başlıklar (`no-store`, `Retry-After: 1`, `Location` yok) döngüyü durduran 429 yanıtının kendisinde kontrol ediliyor.
- Doğrulama: eski teste döngüden sonra 250 ms bekleme eklenince yerelde de kırıldı. Yenisi aynı beklemeyle geçti; bekleme kaldırıldı. `./gradlew build`: 216 test, 0 başarısız.
- Diğer sınır testleri gözden geçirildi: giriş, link oluşturma ve e-posta kovaları 6 saniyede veya daha seyrek doluyor. Testleri saniyeler içinde bittiği için aynı yarış onlarda yok.

### 2026-10-15 — M3 dilim 1: hız sınırları ve kotalar

- `abuse` modülü: `RateLimiter` (Bucket4j 8.21.0, bellekte, Caffeine ile sınırlı), `RateLimit` (K42'deki beş sınır), `ClientAddresses` (son `X-Forwarded-For` değeri, IPv6 için /64).
- Uygulanan yerler:
  - Giriş: IP başına sınır ve e-posta başına başarısız deneme sınırı. İkincisi şifreden önce denetleniyor, başarısız denemeden sonra kaydediliyor.
  - Kayıt, şifre sıfırlama ve doğrulama e-postası: IP başına ortak sınır.
  - Link oluşturma: kullanıcı başına dakikada 10.
  - Yönlendirme: IP başına dakikada 300. Sınırı aşan ziyaretçi 429 sayfası alıyor (`no-store`, `Retry-After`, `Location` yok).
- Günlük 50 link kotası veritabanından sayılıyor (son 24 saat, silinenler dahil). K42'de bellekte sayılması öngörülmüştü; yeniden başlatmanın kotayı sıfırlamaması için değiştirildi, karar satırına not düşüldü.
- Panel: `lib/api.ts` her Spring çağrısına ziyaretçinin adresini `X-Forwarded-For` olarak ekliyor; değer `clientAddressFrom` ile doğrulanıyor. Yeni hata mesajları: `rate_limited`, `link_quota_exceeded`.
- Dev profili yerelde adres başına giriş ve e-posta sınırlarını 1000'e çıkarıyor, böylece uçtan uca testler tekrar tekrar çalışabiliyor. Gerçek değerler entegrasyon testlerinde sınanıyor.
- Yol üstünde bulunan sorunlar:
  - **Eski IPv4 yazımı:** Java `InetAddress.ofLiteral("1.2.3")` çağrısını geçerli adres sayıyor. Uydurma `X-Forwarded-For` değerleri için IPv4 artık yalnızca dört parçalı biçimde kabul ediliyor.
  - **Testlerin birbirinin sınırını tüketmesi:** sınırlar Spring context'i boyunca yaşıyor ve bütün MockMvc istekleri `127.0.0.1`'den geliyordu. `TestcontainersConfiguration` artık her isteğe rastgele bir adres veriyor.
  - **Kota ile sayfalama testi:** sayfa boyutu testi 101 link oluşturuyor; o test sınıfı için kota yükseltildi, kotanın kendi testleri ayrı.
  - **Yenilenen jetonlar:** kovalar dakika içinde düzgün doluyor (yönlendirmede saniyede 5). Gerçek sunucuda sırayla gönderilen 300 istekten sonra 301. de geçti. Yavaş bir CI makinesinde testin rastgele kırılmaması için yönlendirme testi, 300 istekten sonra en fazla 50 ek istek içinde 429 bekliyor.
- Doğrulama:
  - `cd backend && ./gradlew build`: 214 test, 0 başarısız.
    - `RateLimitIntegrationTests` (9): IP başına giriş (11. istek 429, `Retry-After: 6`, başka adres etkilenmiyor); e-posta başına başarısız giriş (10 farklı adresten sonra doğru şifre de 429, başka hesap etkilenmiyor); kayıtlı olmayan adres aynı davranıyor; 12 başarılı giriş sınıra takılmıyor; e-posta gönderen istekler ortak sınırda (6. kayıt 429, e-posta gitmedi, hesap oluşmadı); kullanıcı başına link sınırı; günlük kota (silinen link sayılıyor, 51. link 429 `link_quota_exceeded`, 25 saat sonra yeniden açılıyor); yönlendirme sınırı; `X-Forwarded-For`'un son değerinin anahtar olması.
    - `ClientAddressesTests` (16), `RateLimiterTests` (4).
    - Negatif kontroller (her biri ayrı ayrı, sonra geri alındı): hesap sınırının kaldırılması (2 test), başarılı girişlerin de sayılması (3), kayıtta e-posta sınırının kaldırılması (1), `X-Forwarded-For`'un ilk değerinin kullanılması (3), kotada `>=` yerine `>` (1), yönlendirme sınırının kaldırılması (1) testleri kırdı.
  - `cd frontend`: lint, typecheck, `npm test` (62 test; `clientAddressFrom` için 10 yeni), build ve production denetimi (0 açık) geçti.
  - Gerçek sistemde:
    - Backend giriş sınırı 3'e düşürülerek başlatıldı. Geçici bir Playwright testi panele `X-Forwarded-For: 203.0.113.77` ile 4 kez hatalı giriş yaptı: ilk 3 deneme "e-posta veya şifre hatalı", 4. deneme "çok fazla deneme" mesajı aldı. Aynı anda `203.0.113.78` normal hata aldı; yani panel ziyaretçinin adresini iletiyor. Geçici test silindi.
    - Normal dev ayarlarıyla `npm run e2e`: 2 test geçti.
    - `curl` ile: 10 hatalı girişten sonra doğru şifre 429 ve `Retry-After: 90`; 40 paralel istekle 400 ziyarette sınırı aşanlar 429 aldı (`no-store`, `Retry-After`, `Location` yok).
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.
- Açık konular:
  - Sayaçlar bellekte; yeniden başlatma günlük kota dışındaki sınırları sıfırlıyor (K42'de kabul edildi).
  - Hesap başına başarısız giriş sınırı, bir kullanıcının bilerek dışarıda bırakılmasına izin veriyor (K42'de kabul edildi). Kötüye kullanılırsa CAPTCHA veya e-postayla giriş linki düşünülebilir.

### 2026-10-15 — M3 kararları

- Hız sınırları ve kotalar önerilen değerlerle kesinleşti (K42; A04 kapandı).
- Yönetici paneli `admin.vurli.io`'da olacak (K43). SSH tüneli seçeneği, her yeni bilgisayarda anahtar kurulumu gerektirdiği için tercih edilmedi; ikinci faktör (TOTP) ve hız sınırı zorunlu.
- M3 sırası: hız sınırları → istatistik → yönetici paneli → Web Risk ve bildirim → yük testi.
- Dependabot düzeltmesi `5e79fe6` olarak gönderildi.

### 2026-10-15 — Dependabot güncellemesinin geri alınması

- Dilim 4 (`6f1f9ce`) için Backend, Frontend ve ilk kez çalışan E2E işi yeşil.
- Dependabot'un birleştirilen ilk npm PR'ı (#1) üç büyük sürüm atlaması içeriyordu: TypeScript 5→7, ESLint 9→10, `@types/node` 22→26. PR'daki Frontend işi zaten kırmızıydı; birleştirmeden sonra `main` de kırmızıya döndü.
  - typescript-eslint TypeScript 7'yi desteklemiyor (`eslint-config-next` üzerinden geliyor; desteklediği aralık `<6.1`); lint açılırken çöküyor.
  - `eslint-config-next`'in import, react ve jsx-a11y eklentileri en fazla ESLint 9'u destekliyor.
  - `@types/node` 26, `.nvmrc`'deki Node 22 ile uyuşmuyor.
- Düzeltme: `package.json` ve `package-lock.json` `6f1f9ce`'deki haline döndü. `dependabot.yml`'de bu üç paketin büyük sürümleri yok sayılıyor; küçük sürümler ve diğer paketler gelmeye devam ediyor.
- Doğrulama: `npm ci`, `npm audit --omit=dev --audit-level=high` (0 açık), `npm run lint`, `npm run typecheck`, `npm test` (54 test) ve `npm run build` geçti.

### 2026-10-15 — M2 dilim 4: link ekranları

- Spring:
  - `HostFilter` (bütün filtrelerden önce): tek segmentli yollar yalnızca `vurli.hosts.public`'te, geri kalan her şey yalnızca `vurli.hosts.internal`'da işleniyor; diğer host'lar gövdesiz 404 alıyor. Dev profilinde ikisi de `localhost`.
  - Liste artık `LinkPage` döndürüyor (`items`, `hasNext`); sayım sorgusu yok, Spring Data `Slice` bir satır fazla okuyor.
  - Link yanıtlarında `shortUrl` (başı `vurli.short-base-url`'den).
  - QR: `GET /api/v1/links/{id}/qr?format=png|svg`. ZXing `core` 3.5.3 yalnızca modül matrisini üretiyor; PNG ve SVG kendi kodumuzla çiziliyor, ek bağımlılık yok.
- Panel:
  - Ana sayfa: oluşturma formu (hedef, özel ad, bitiş tarihi) ve sayfalı liste (durum etiketi, oluşturma zamanı).
  - `/links/{id}`: kopyala, aç, QR önizleme, PNG/SVG indirme, hedef ve bitiş tarihi değiştirme, kapatma/açma, onaylı silme.
  - Server action'lar `actions/links.ts`; QR `links/[id]/qr/route.ts` üzerinden aktarılıyor.
  - Formlardan gelen link kimliği UUID, sürüm tamsayı olarak doğrulanmadan API yoluna girmiyor (`lib/links.ts`).
  - Bitiş tarihi tarayıcının saat diliminde seçilip UTC an olarak gönderiliyor. Tarihler sunucuda UTC, tarayıcıda yerel saatle gösteriliyor.
- Uçtan uca testler: Playwright 1.64.0 (1.64.1 bir günlük olduğu için bir önceki sürüm seçildi), `frontend/e2e/`. Yeni `E2E` iş akışı Compose, `bootRun` ve panelin production build'iyle bütün sistemi çalıştırıyor; hata olursa rapor ve backend log'u artifact olarak saklanıyor.
- Yol üstünde bulunan sorunlar:
  - **Çakışmada eski değer:** sürüm çakışmasından sonra sayfa yenilendi ve sürüm güncellendi, ama hedef alanı eski değeri göstermeye devam etti; React yalnızca gönderilen formu sıfırlıyor. Alanlar sürüme bağlanarak yeniden çiziliyor; tarayıcıda tekrar denendi.
  - **Link biçimli butonlar:** `<a>` üzerinde `buttonVariants` çerçeveyi kaybediyordu (`border-transparent` ile `border-border` çakışması). Sınıflar `cn` ile birleştiriliyor.
- Doğrulama:
  - `cd backend && ./gradlew build`: 185 test, 0 başarısız.
    - `RedirectIntegrationTests` (+3): kısa link yalnızca `vurli.io`'da (büyük harf ve port kabul); `evil.com`, `localhost`, `vurli.io.evil.com` 404 ve `Location` yok; `vurli.io` üzerinden `/api/v1/links`, `//api/v1/links`, `/api/v1/auth/me` ve health 404, çerez yok.
    - `QrCodeRendererTests` (6): üretilen PNG ve SVG ZXing okuyucusuyla taranıp kısa URL'ye geri çözülüyor; SVG yalnızca modül path'lerinden oluşuyor; sessiz alan boş.
    - `LinkApiTests` (+2): QR indirme başlıkları ve `invalid_format`; IDOR testine QR eklendi; `hasNext`.
    - Negatif kontroller: Host kontrolü kaldırılınca 3 test, yol sınıflandırması önek kontrolüne çevrilince `//api` testi başarısız oldu; kod geri yüklendi.
  - `cd frontend`: `npm run lint`, `npm run typecheck`, `npm test` (5 dosya, 54 test; yeni `links.test.ts`: yol kaçırma denemeleri, sürüm ayrıştırma, süre sonu sınırı, saat dilimi dönüşümü), `npm run build` ve `npm audit --omit=dev --audit-level=high` (0 açık) geçti.
  - `npm run e2e` (production build, gerçek backend ve Mailpit): 2 test geçti. Kayıt → e-postadaki linkle doğrulama → giriş → özel adlı link → 302 → PNG indirme (dosya adı ve PNG imzası) → hedef değiştirme → 302 yeni hedefe → listede görünme → kapatma → 410 → silme → 410; konsolda hata yok. Oturumsuz sayfalar `/login`'e gidiyor. Negatif kontrol: QR'ın dosya adı başlığı kaldırılınca test başarısız oldu.
  - Uygulama içi tarayıcıda (geliştirme modu): `http(s)://` olmayan hedefte Türkçe hata ve alanlar korunuyor; bitiş tarihi 18:30 (İstanbul) olarak seçilip `15:30Z` saklandı ve formda yine 18:30 göründü; başka bir istemci linki değiştirdikten sonra eski sayfadan kapatma çakışma mesajı verdi ve güncel hedef gösterildi; kapatılan link 410; silme listeye döndü; CSP ihlali yok.
  - `curl` ile: `Host: vurli.io` ile health 404; oturumsuz QR isteği `/login`'e yönlendirme; `..%2fauth%2fme` kimliği 404.
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.
- Açık konular: dil seçici yok; hedef URL'ye script yerleştirme testi (güvenlik §5) henüz otomatik değil. React metni escape ediyor; doğrulayıcı da `<` veya `"` içeren hedefleri `MALFORMED` ile reddediyor (`curl` ile denendi).

### 2026-10-15 — M2 dilim 3: panel iskeleti (Next.js)

- Next.js 16 rehberi (`node_modules/next/dist/docs`) okundu. İki karar çıktı:
  - `cacheComponents` çerez okuyan her bileşeni `<Suspense>` içine zorluyor ve nonce'lu CSP ile uyumsuz. Panel kullanıcıya özel olduğu için kapatıldı; sayfalar dinamik (K39).
  - Çok dillilik kütüphanesiz, `Accept-Language` ile (K40).
- BFF katmanı:
  - `lib/api.ts` (`server-only`): tarayıcı çerezlerinden yalnızca `VURLI_SESSION`'ı iletir; `X-Vurli-Client` ve dil başlığını ekler; yalnızca `/api/v1/` yollarına izin verir; Spring adresi `VURLI_API_URL`'den gelir.
  - Girişte Spring'in `Set-Cookie`'sinden yalnızca kimlik değeri alınır, çerez kendi bayraklarımızla yazılır.
  - `lib/session.ts` kullanıcıyı her istekte Spring'e sorar.
- Ekranlar: giriş, kayıt, e-posta doğrulama, şifremi unuttum, şifre sıfırlama, panel (doğrulanmamış uyarısı, tekrar gönder, çıkış). Formlar React 19 `useActionState` ile server action kullanıyor.
- Token sayfaları: token `#token=` fragment'ından `useSyncExternalStore` ile okunuyor ve adres çubuğundan siliniyor. Doğrulama isteği `useRef` korumasıyla bir kez gidiyor.
- Güvenlik: `src/proxy.ts` her istekte nonce üretip CSP başlığı ekliyor ve çerez yoksa korumalı sayfadan `/login`'e yönlendiriyor (iyimser kontrol). Diğer başlıklar `next.config.ts`'te (K41).
- Yol üstünde bulunan sorunlar:
  - **Lint:** React'in `set-state-in-effect` kuralı iki yerde ihlal ediliyordu. Kural kapatılmadı; fragment okuma `useSyncExternalStore`'a, "token yok" durumu render'da türetmeye çevrildi.
  - **CSP:** geliştirme modunda Next.js geliştirme araçları satır içi stil kullandığı için ihlal veriyordu. Production build'de aynı sayfanın nonce'uyla eşleşen ihlal olmadığı tarayıcıda doğrulandı; satır içi stil izni yalnızca geliştirme moduna verildi.
  - **Form:** hatalı girişten sonra React 19 formu sıfırlıyor ve e-posta siliniyordu. Hata durumunda e-posta geri döndürülüyor; şifre hiçbir zaman döndürülmüyor.
- Doğrulama:
  - `npm run lint`, `npm run typecheck`, `npm test` (Vitest, 4 dosya, 31 test: dil seçimi, `Set-Cookie` ayrıştırma ve sızdırma denemeleri, hata kodu eşleme, CSP, iki sözlüğün aynı anahtarlara sahip olması) ve `npm run build` (bütün sayfalar dinamik) geçti.
  - Production build'de (`next start`), uygulama içi tarayıcıda, gerçek backend ve Mailpit ile:
    - oturumsuz `/` → `/login`
    - kayıt formu → Türkçe "gelen kutunu kontrol et"
    - doğrulanmadan giriş → panelde uyarı; "tekrar gönder" bir dakika dolmadan Türkçe bekleme mesajı
    - çerez JavaScript'ten görünmüyor (`HttpOnly`)
    - doğrulama linki → başarılı, token adres çubuğundan silindi; aynı link tekrar → "geçersiz"
    - çıkış → şifremi unuttum → e-postadaki link → yeni şifre → `/login?reset=1` bilgi mesajı
    - eski şifre → Türkçe hata ve e-posta alanda kaldı; yeni şifre → panel
    - CSP ihlali yok
  - Geliştirme modunda (Strict Mode): doğrulama linki başarılı, yani token iki kez gönderilmedi; yeni CSP ile ihlal yok.
  - `curl` ile: `Accept-Language: en-US` → İngilizce, `de-DE` → İngilizce, `lang` özniteliği doğru.
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.
- Açık konular:
  - Uçtan uca testler henüz otomatik değil (Playwright dilim 4'te).
  - Dil seçici yok; dil yalnızca tarayıcıdan geliyor.
  - `.claude/launch.json` uygulama içi tarayıcı önizlemesi için eklendi.

### 2026-10-15 — M2 dilim 2: hesaplar (Spring)

- Şema: `V3__create_users.sql` (`users`, `email_tokens`, `workspaces.owner_id`) ve `V4__create_spring_session.sql` (spring-session-jdbc 4.1.1'in resmi PostgreSQL şeması, değiştirilmeden).
- `account` modülü:
  - `AccountManagement`: kayıt, doğrulama, yeniden gönderme, şifre sıfırlama isteği ve onayı.
  - `AuthController` (`/api/v1/auth`): register, verify-email, verification-email, login, logout, me, password-reset, password-reset/confirm.
  - Kimlik doğrulama `AuthenticationManager` ve Argon2id ile.
- Dev API, `DevSecurityConfiguration` ve `db/dev` tohum verisi kaldırıldı. Yerine oturum isteyen `LinkApiController` (`/api/v1/links`) geldi; doğrulanmamış hesap link oluşturamıyor (`email_not_verified`).
- Güvenlik:
  - İki zincir var: `/api/**` oturum ister ve 401 döner; geri kalanında yalnızca kısa linkler ve health açık, diğer her şey reddediliyor.
  - CSRF için `X-Vurli-Client` başlığı zorunlu.
  - Hata dispatch'lerine izin veriliyor, böylece 500 yanıtları 403'e dönüşmüyor.
- Kararlar: K36 (oturum 24 saat), K37 (API CSRF yaklaşımı), K38 (token URL fragment'ında); hepsi varsayılan.
- Yol üstünde bulunan sorunlar:
  - **Oturum çerezi ayarları uygulanmıyordu:** `server.servlet.session.cookie.*` ayarları MockMvc testlerinde Spring Session'a hiç ulaşmadı; çerez `SESSION` adıyla ve bayraksız geldi. Sebep, Spring Boot 4'ün bu ayarları gömülü sunucu yapılandırmasıyla bağlantılı uygulaması. Güvenlik açısından kritik olduğu için çerez artık `SessionCookieConfiguration`'da açıkça tanımlı: `VURLI_SESSION`, `HttpOnly`, `SameSite=Lax`, `Secure` (dev profilinde kapalı).
  - **E-posta göndericisi oluşmuyordu:** Spring Boot `JavaMailSender`'ı yalnızca `spring.mail.host` varsa oluşturuyor; bu koşul testlerin dinamik ayarlarından önce değerlendiriliyor. Ana ayarlara varsayılan host eklendi.
  - **Yerel geliştirme veritabanı:** kaldırılan `R__dev_workspace.sql` yerelde uygulanmış göründüğü için Flyway doğrulaması başarısız oldu. Veritabanını silmek yerine `flyway_schema_history`'den yalnızca o satır silindi. Yalnızca bu bilgisayardaki dev veritabanını ilgilendiriyor.
- Doğrulama (`./gradlew build`): 175 test, 0 başarısız.
  - `AccountManagementIntegrationTests` (13), gerçek Mailpit konteyneriyle:
    - Türkçe ve İngilizce e-posta seçimi
    - Token'ın tek kullanımlık olması; süresi dolmuş veya bozuk token'ın reddi; yalnızca SHA-256'nın saklanması
    - Kayıtlı adrese ikinci kayıtta hesabın değişmemesi ve sahibine bildirim gitmesi
    - Aynı adrese 8 paralel kayıttan tek hesap çıkması
    - Yeniden gönderme sınırı; sıfırlamanın eski linkleri geçersiz kılması
    - Zayıf şifre ve geçersiz adres
  - `AuthApiTests` (11): yeni ve kayıtlı adrese aynı yanıt; çerez bayrakları; yanlış şifre ile bilinmeyen adresin aynı 401'i alması; doğrulanmamış hesabın girebilmesi; tekrar girişte oturum kimliğinin yenilenmesi; çıkış; şifre sıfırlamada her yerde oturum kapanması; 401 yerine yönlendirme olmaması; başlık filtresi; hata kodları.
  - `LinkApiTests` (5): oluştur, değiştir, sil ve yönlendirme; doğrulanmamış hesap 403; iki gerçek hesapla IDOR (okuma, değiştirme, kapatma, silme, liste); oturumsuz 401; geçersiz hedef 422.
  - Negatif kontroller: oturum kimliği yenileme, sıfırlamada oturum silme, token'ın tek kullanımlık olması ve başlık filtresi sırayla kaldırıldı; her seferinde ilgili test başarısız oldu, kod geri yüklendi.
  - Gerçek sunucuda (`bootRun` ve `curl`): kayıt 202 → Mailpit'te Türkçe e-posta (`#token=` linkiyle) → giriş ve çerez bayrakları → doğrulanmamışken link 403 → doğrulama 204, aynı token tekrar 400 → `/me` yeniden giriş gerekmeden `emailVerified: true` → link 201 → ziyaret 302 → çıkış 204, sonra `/me` 401. Mailpit kapalıyken kayıt yine 202 döndü ve log'a yalnızca kullanıcı kimliği yazıldı; e-posta hatasının yanıtı değiştirmediği böylece görüldü.
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.
- Açık konular: giriş, kayıt ve şifre sıfırlamada hız sınırı M3'te. O zamana kadar kayıt uç noktası başkalarının adreslerine e-posta göndermek için kötüye kullanılabilir; aynı adrese gönderim yalnızca dakikada bir ile sınırlı.

### 2026-10-15 — M2 dilim 1: monorepo ve altyapı

- Spring projesi `git mv` ile `backend/`'e taşındı (63 dosya yeniden adlandırma olarak izleniyor). Compose dosyası kökte kaldı; dev profili onu `spring.docker.compose.file=../compose.yaml` ile buluyor.
- `frontend/`: `create-next-app@16.4.1` (App Router, TypeScript, Tailwind 4, ESLint, `src/`), ardından `shadcn@4.21.5` ve Button bileşeni. Örnek içerik temizlendi; geçici bir Vurli sayfası var. Node 22 `.nvmrc` ile sabitlendi.
- Compose'a Mailpit eklendi (`axllent/mailpit:v1.31.5`; SMTP 1025, arayüz 8025, yalnızca localhost).
- CI ikiye ayrıldı: `backend.yml` (`ci.yml`'den yeniden adlandırıldı) ve `frontend.yml`; her biri yalnızca kendi klasörü değişince çalışıyor. Frontend işi sırasıyla `npm ci`, production bağımlılık denetimi, lint, tip kontrolü ve build yapıyor; `setup-node` SHA ile sabit. Dependabot `backend/` için Gradle, `frontend/` için npm izliyor.
- Yol üstünde bulunan sorunlar:
  - **Tedarik zinciri:** shadcn, `clsx` + `tailwind-merge` yerine `cn` adlı bir paket ekledi. Paket adı 2013'ten kalma ve sonradan devredilmiş olduğu için incelendi: yayıncısı shadcn (`shadcn-ui/cn` reposu), kurulumda çalışan script'i ve bağımlılığı yok. 603 paketin hepsinin npm imzası doğrulandı (`npm audit signatures`).
  - **Gereksiz çalışma zamanı bağımlılığı:** `shadcn init` CLI paketini `dependencies`'e eklemişti. CSS'i yalnızca build sırasında okunduğu için `devDependencies`'e taşındı; production denetimi yeniden temiz.
  - **Temiz checkout'ta tip kontrolü:** Next.js'in ürettiği `LayoutProps` tipi `.next/types` olmadan bulunamıyor. Yerelde önceki build sayesinde gizli kalan bu sorun, CI adımları temiz kurulumla tekrarlanınca ortaya çıktı. `typecheck` artık önce `next typegen` çalıştırıyor.
  - **Font:** shadcn `globals.css`'e kendine başvuran `--font-sans: var(--font-sans)` satırını yazmıştı; sayfa Times ile çiziliyordu (tarayıcıda ölçüldü). `--font-geist-sans`'a bağlandı. Geist'e Türkçe harfler için `latin-ext` alt kümesi eklendi.
- Doğrulama:
  - `cd backend && ./gradlew build`: 151 test, 0 başarısız.
  - Frontend, `node_modules` ve `.next` silinerek CI adımlarıyla tekrarlandı: `npm ci`, `npm audit --omit=dev --audit-level=high` (0 açık), lint, typecheck ve build başarılı.
  - `./gradlew bootRun` (backend/ içinden): PostgreSQL ve Mailpit başladı; health, Mailpit arayüzü ve dev API 200 döndü.
  - `npm run start`: sayfa tarayıcıda açıldı; font Geist ve Türkçe karakterler doğru.
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.
- Açık risk: `braces` (≤3.0.3) yığın taşması DoS açığı ([GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm)) için yama yok. Yalnızca geliştirme araçlarında (ESLint, shadcn CLI) bulunuyor ve güvenilmeyen bir glob girdisi almıyor. npm'in önerdiği "düzeltme" `eslint-config-next`'i 16'dan 14'e düşürmek olduğu için uygulanmadı; yama çıkınca Dependabot PR açacak.
- Not: en güncel Node LTS sürümü 24; yerelde 22 kurulu olduğu için 22 seçildi (Nisan 2027'ye kadar destekleniyor). Bilgisayarda Node 24'e geçilirse `.nvmrc` güncellenecek.

### 2026-10-15 — M2 kararları

- GitHub reposu silinip boş olarak yeniden oluşturuldu ve 6 commit aynen push edildi (CI yeşil, Contributors yalnızca kullanıcı).
- Kararlar ([decisions.md](decisions.md)):
  - Kullanıcı paneli Next.js BFF; kimlik, yetki ve veri Spring'de (K28, K29).
  - Yönetici paneli Spring + Thymeleaf (K30).
  - Arayüz Tailwind + shadcn/ui (K31).
  - E-posta SMTP ile; yerelde Mailpit, sağlayıcı M4'te (K32).
  - Panel Türkçe + İngilizce (K33).
  - Doğrulanmamış kullanıcı giriş yapar ama link oluşturamaz (K34).
  - Monorepo `backend/` + `frontend/` (K35, önerildi).
- Kullanıcının sorusu üzerine: Next.js'i Spring'in `resources` klasörüne koymak yalnızca statik dışa aktarımla mümkün. O durumda BFF'nin sunucu tarafı kaybolur, bu yüzden Next.js ayrı bir süreç olarak çalışacak.
- Mimari, güvenlik (BFF güven sınırı, CSRF, CSP) ve yol haritası (M2 55–85 saat, toplam 145–215 saat) güncellendi.

### 2026-10-15 — CI hatası: Linux'ta zaman hassasiyeti

- `8d36c88` commit'i CI'da başarısız oldu ([çalışma 38637757906](https://github.com/burhancabiroglu/vurli-io/actions/runs/38637757906)), yerelde ise geçiyordu. Log dosyaları oturum açmadan okunamadığı için sorun yerelde yeniden üretildi.
- Kök neden: Linux'ta `Instant.now()` nanosaniye, PostgreSQL ise mikrosaniye tutar. Oluşturma yanıtındaki `createdAt` (ve kullanıcının gönderdiği nanosaniyeli `expiresAt`) veritabanında saklanandan farklıydı. `otherWorkspaceCannotSeeOrChangeLink` testi yanıtla veritabanı kaydını karşılaştırdığı için yalnızca Linux'ta başarısız oldu. macOS saati mikrosaniye hassasiyetinde olduğu için sorun yerelde görünmüyordu.
- Doğrulama: `ClockConfiguration` geçici olarak nanosaniyeli bir saate çevrildi ve aynı test yerelde de aynı farkla başarısız oldu.
- Düzeltme: test gevşetilmedi. `Link` entity'si sakladığı bütün zamanları mikrosaniyeye yuvarlıyor, böylece döndürülen değerle saklanan değer hep aynı.
- Regresyon testi: `returnsExactlyWhatIsStoredWithNanosecondClock` işletim sisteminden bağımsız olarak nanosaniyeli sabit bir saat kullanıyor. Yuvarlama kaldırılınca bu test başarısız oluyor.
- Sonuç: 151 test hem normal saatle hem nanosaniyeli saatle geçti. GitHub Actions: bu commit yazılırken sonuç henüz yoktu.

### 2026-10-15 — M1 dilim 3: yönetim ve dev API

- `LinkManagement`: `get`, `list` (en yeni önce; sayfa boyutu en fazla 100), `changeTarget`, `changeExpiry`, `disable`, `enable`, `delete`. Bütün değişiklikler `expectedVersion` alıyor ve tek bir yoldan geçiyor:
  1. `id + workspaceId` ile yükleme (silinmiş linkler görünmüyor)
  2. Sürüm karşılaştırması
  3. Değişiklik ve `flush` (JPA `@Version` aradaki yarışı yakalıyor)
  4. Commit'ten sonra cache silme
- Yeni hedef de oluşturmadaki doğrulamadan geçiyor ve `safety_checked_at` sıfırlanıyor.
- Engellenmiş link sahibi için donmuş durumda: açılamaz, kapatılamaz, hedefi ve süresi değiştirilemez; yalnızca silinebilir.
- `LinkModeration.block`: yönetici engellemesi sürüm istemiyor; sahibin bir değişikliği araya girerse yeniden deniyor. Yetki ve denetim kaydı M3'te `abuse` modülüyle gelecek.
- Dev API (yalnızca `dev` profili): `/dev/api/links` altında JSON uç noktaları; hatalar `ProblemDetail` biçiminde `code` alanıyla dönüyor. Sabit workspace Flyway `db/dev` tohum verisinden geliyor. `./gradlew bootRun` artık `dev` profiliyle açılıyor.
- Bulunan hata: entity'deki `version()` metodu, Spring Data'nın "yeni kayıt mı" kontrolünde getter sanıldı ve ilkel `long`, `null` değerde hata verdi (29 test başarısız oldu). Metot `hasVersion(long)` olarak yeniden adlandırıldı.
- Doğrulama (`./gradlew build`): 150 test, 0 başarısız.
  - `LinkLifecycleIntegrationTests` (14): başka workspace'in linki okunamıyor, değiştirilemiyor ve listede görünmüyor; eski sürümle güncelleme reddediliyor; aynı sürümle 8 paralel güncellemeden tam 1 kazanan; engelli link sahibi tarafından değiştirilemiyor ama silinebiliyor; yönetici engellemesi sahibin sürümünü eskitiyor; silinen link görünmüyor; liste sırası ve sayfa sınırı.
  - `RedirectIntegrationTests` (+4): hedef değişikliği, kapatma, engelleme ve silme bir sonraki ziyarette etkili; dev profili kapalıyken `/dev/api/**` giriş istiyor.
  - `DevLinkApiTests` (5, dev profili): oluştur → yönlendir → değiştir → sil akışı; 409 `version_conflict` ve `alias_taken`; 422 nedeniyle birlikte; başka workspace'in linki 404.
  - Negatif kontroller: sürüm kontrolü kaldırılınca 2 test, workspace filtresi kaldırılınca 3 test başarısız oldu; kod geri yüklendi. Uygulamadaki sürüm kontrolü kaldırıldığında bile paralel yazma testi geçti; JPA `@Version` eşzamanlı yazmaları tek başına yakalıyor.
  - Gerçek sunucuda (`./gradlew bootRun` ve `curl`): oluştur → 302 → hedef değiştir (v1) → 302 yeni hedefe → eski sürümle kapatma 409 → sil 204 → ziyaret 410 → aynı adı tekrar alma 409.
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.

### 2026-10-14 — M1 dilim 2: yönlendirme

- `link` modülü dışarıya `LinkResolver.resolve(kod)` açıyor; sonuç `LinkResolution` (`Redirect`, `Gone`, `NotFound`) olarak dönüyor. Karar her istekte o anki saatle veriliyor; cache'te yalnızca `LinkSnapshot` (hedef, durum, süre sonu, silinme) tutuluyor.
- `LinkCache`: Caffeine, 60 sn TTL, 100.000 girdi; "yok" sonuçları da cache'leniyor. `LinkManagement.create` commit'ten sonra kodun cache girdisini siliyor.
- `redirect` modülü: `RedirectController` 302, 404, 410 ve 503 (veritabanı hatası) döndürüyor. Bütün yanıtlarda `Cache-Control: no-store` var. Hata sayfaları Thymeleaf şablonları.
- `SecurityConfiguration`: yalnızca tek segmentli `GET`/`HEAD` istekleri ve `/actuator/health` herkese açık; geri kalanı girişe yönlendiriliyor (M2'de gerçek girişle değişecek).
- Mimari belgesindeki "engelleme en geç 60 sn" taahhüdü, aşağıdaki test sayesinde "commit'ten sonra anında" olarak güncellendi.
- Doğrulama (`./gradlew clean build`): 128 test, 0 başarısız.
  - `LinkCacheTests` (3): engellemeden önce okunup sonra biten bir yüklemenin eski kaydı cache'e bırakamadığı bariyerli test; "yok" sonuçlarının cache'lenmesi; TTL'in sahte saatle sınanması. Negatif kontrol: cache atomik olmayan bir yüklemeyle değiştirildi, yarış testi başarısız oldu, kod geri yüklendi.
  - `LinkResolverTests` (19): süre sonunun tam sınırda (`now == expiresAt` → 410) çalışması; kapalı, engellenmiş ve silinmiş linkler; kodların büyük-küçük harf duyarlılığı; geçersiz veya rezerve kodlarda hiç sorgu yapılmaması.
  - `RedirectIntegrationTests` (8, gerçek PostgreSQL ve güvenlik zinciri): 302 ile `no-store` ve çerez olmaması; gövdesiz HEAD; `?target=` parametresinin etkisiz kalması; 404 ve 410; link oluşturulunca cache'teki "yok" sonucunun temizlenmesi; health'in açık, diğer yolların kapalı olması.
  - `RedirectControllerTests` (1): veritabanı hatasında 503 ve `Location` başlığının olmaması.
  - Gerçek sunucuda (`./gradlew bootRun` ve `curl`): 302 ile `Location`, `no-store` ve güvenlik başlıkları geldi, `Set-Cookie` yoktu; HEAD aynı yönlendirmeyi döndürdü; bilinmeyen kod 404 aldı. Yerel geliştirme veritabanına `deneme1` adlı bir test linki eklendi.
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.
- Açık: `Host` başlığı kontrolü henüz yok, M2'de panel `app.vurli.io`'ya taşınırken eklenecek.

### 2026-10-14 — M1 dilim 1: link oluşturma

- Şema: `V1__create_workspaces.sql` (izolasyon sınırı, en sade hali) ve `V2__create_links.sql` (`uk_links_code` unique constraint, durum için check constraint, workspace'e yabancı anahtar). Silinen linkler satırını koruduğu için kodları sonsuza kadar rezerve kalıyor.
- `link` modülünün dışa açık API'si: `LinkManagement.create`, `CreateLinkRequest`, `LinkView`, `LinkStatus` ve nedenleriyle hata sınıfları. Entity, repository, doğrulayıcı ve kod üretici `link.internal` içinde.
- `TargetUrlValidator`: şema, kimlik bilgisi, ters bölü ve kontrol karakterleri, IP adresi, port ve kendine dönen link kontrolleri; IDN ve normalizasyon ([mimari](architecture.md#link-oluşturma)).
- `AliasPolicy`: özel ad küçük harfe çevriliyor; biçim kuralları ve rezerve ad listesi var. `RandomCodeGenerator`: 7 karakter Base62, `SecureRandom`.
- Zaman bir `Clock` bean'inden okunuyor; süre sonu testleri sabit saatle yazıldı.
- Doğrulama (`./gradlew clean build`): 97 test, 0 başarısız.
  - `TargetUrlValidatorTests` (58): kabul ve normalizasyon örnekleri; reddedilen her neden için saldırı örnekleri (`javascript:`, `user@host`, `\@`, CRLF, `0x7f.0x1`, `[::1]`, `:8443`, `vurli.io`). Beklenen punycode değeri Python'un IDNA kodlayıcısıyla bağımsız olarak hesaplandı.
  - `AliasPolicyTests` (24), `RandomCodeGeneratorTests` (2).
  - `LinkManagementIntegrationTests` (10, gerçek PostgreSQL): 8 paralel istekte aynı ad için tam olarak 1 kazanan; silinmiş linkin adı yeniden verilmiyor; yalnızca harf büyüklüğüyle farklı ad reddediliyor; çakışan kodda yeniden deneme ve 3 denemeden sonra vazgeçme; rezerve kodun atlanması; süre sonunun tam sınırda reddedilmesi; geçersiz istekte hiç satır yazılmaması.
  - Negatif kontrol: çakışma tespiti geçici olarak bozuldu, 5 entegrasyon testi başarısız oldu, kod geri yüklendi.
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.

### 2026-10-14 — Maven'dan Gradle'a geçiş

- Karar: [K27](decisions.md#teknik); K11 (Maven) geçersiz.
- Gradle 9.8.1 Wrapper ve Kotlin DSL `build.gradle.kts` start.spring.io'dan üretildi. Bağımlılıklar Maven sürümüyle aynı (kullanılmayan Modulith runtime bağımlılıkları yine çıkarıldı). Docker Compose desteği `developmentOnly`, yani production jar'ına girmiyor.
- Wrapper jar'ın SHA-256'sı Gradle'ın yayınladığı değerle karşılaştırıldı ve eşleşti. Dağıtımın checksum'ı `distributionSha256Sum` ile sabitlendi.
- `pom.xml`, `mvnw`, `mvnw.cmd` ve `.mvn/` kaldırıldı.
- CI'da `gradle/actions/setup-gradle` kullanılıyor (SHA ile sabit, wrapper doğrulaması açık). Dependabot `maven` yerine `gradle` izliyor.
- Doğrulama:
  - `./gradlew build` (Gradle'ı çalıştıran JDK 25 ve ayrıca JDK 17; derleme her iki durumda toolchain ile JDK 25): BUILD SUCCESSFUL, test raporlarına göre 3 test geçti.
  - Negatif kontrol tekrarlandı: sınır ihlali `ModularityTests`'i başarısız yaptı. Deneme kodu silindi.
  - `./gradlew bootRun`: Compose PostgreSQL'i başlattı, uygulama 2,7 sn'de açıldı, `/actuator/health` 200 döndü.
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.

### 2026-10-14 — M0 iskelet

- Git deposu `main` dalıyla başlatıldı ve `burhancabiroglu/vurli-io`'ya gönderildi. Bu ilk sürüm Maven kullanıyordu.
- Spring Boot 4.1.1 ve Java 25 projesi start.spring.io'dan üretildi. `io.vurli` altında beş modül paketi oluşturuldu: `account`, `link`, `redirect`, `analytics`, `abuse`.
- Kullanılmayan Modulith runtime bağımlılıkları çıkarıldı.
- `compose.yaml` PostgreSQL 18 kullanıyor ve yalnızca localhost'a açık.
- `ddl-auto=validate` ve `open-in-view=false` ayarlandı. Dışarıya yalnızca actuator health açık.
- Testler: `ModularityTests` (Spring Modulith modül sınırları) ve `VurliApplicationTests` (Testcontainers ile gerçek PostgreSQL 18).
- CI: `.github/workflows/ci.yml` her push ve PR'da `./mvnw verify` çalıştırıyor. Action'lar SHA ile sabitlendi ve token yalnızca okuma yetkili. Dependabot Maven, Actions ve Compose için ayarlandı.
- Doğrulama:
  - `./mvnw -B clean verify` (JDK 25.0.3, Docker 29.8.2): 3 test geçti, BUILD SUCCESS.
  - Negatif kontrol: `redirect` modülü `link`'in iç paketine geçici olarak bağlandı ve `ModularityTests` ihlali yakalayıp başarısız oldu. Deneme kodu silindi.
  - `./mvnw spring-boot:run`: Compose PostgreSQL'i başlattı (`127.0.0.1:<rastgele>`), uygulama 2,8 sn'de açıldı, `/actuator/health` 200 döndü.
  - GitHub Actions: commit `6ff9cc6` için yeşil.

### 2026-10-14 — Planlama

- Codex'in 2026-10-08 tarihli 37 planlama belgesi fazla parçalı ve tekrarlıydı, çok az karar içeriyordu. Yerel `archive/codex-2026-10-08/` klasörüne taşındı; repoya dahil değil.
- Ürün ve teknik kararlar birlikte verildi ([decisions.md](decisions.md)). Belgeler altı dosyaya indirildi.

### 2026-10-08

- Codex ilk planlama belgelerini hazırladı. Vurli adı ve vurli.io alan adı seçildi.

## Açık konular

- Açık kararlar [decisions.md](decisions.md#açık-sorular) içinde (A01–A05). Hiçbiri M1'i engellemiyor.
- Testlerde JDK, Mockito'nun dinamik agent yüklemesi için uyarı veriyor. İleride bir JDK sürümü bunu engelleyecek. Mockito, Gradle'ın `test` görevinde `-javaagent` olarak tanımlanmalı.
- Beklenen uyarılar: Flyway "No migrations found" (ilk migration M1'de), Thymeleaf "template location yok" (şablonlar M2'de).
- Global git ayarı (`~/.gitignore_global`) `.gitignore` dosyasını yok sayıyor. Bu dosya değiştiğinde `git add -f .gitignore` gerekir.
