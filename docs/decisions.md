# Kararlar

Her kararın durumu şunlardan biri:

- **Kesin:** birlikte karar verildi.
- **Varsayılan:** önerilen seçim. İtiraz edilmedikçe geçerli, değiştirmek serbest.
- **Açık:** henüz seçilmedi. Hangi aşamada gerektiği yazılı.

Bir karar değişirse satır silinmez. Durumu "Değişti → Kxx" yapılır ve yeni satır eklenir.

## Ürün

| ID | Karar | Durum | Gerekçe |
|---|---|---|---|
| K01 | Önce çalışan ve testli çekirdek, sonra kullanıcıya açık ürün | Kesin, 2026-10-14 | Mülakatta gösterilebilir somut bir sistem, eksiksiz bir plandan daha değerli |
| K02 | Hedef kullanıcı: küçük işletme ve içerik üreticisi | Kesin, 2026-10-14 | Panel, QR ve basit istatistik bu kitle için değerli. API ve takım özellikleri ertelenebilir |
| K03 | İlk sürümde gelir modeli yok | Kesin, 2026-10-14 | Reklam ekranı trafik olmadan gelir getirmez ve kısaltıcının güvenilirliğini zedeler. Abonelik ancak kullanıcı varken anlamlı. Veri modeli plan ve kota eklemeye açık bırakılır |
| K04 | Link oluşturmak için e-posta doğrulamalı hesap gerekir | Kesin, 2026-10-14 | Hesapsız kısaltıcılar spam ve phishing'in ana kaynağıdır. Doğrulama ve düşük başlangıç kotası kötüye kullanımı makul sınırda tutar |
| K05 | MVP'de özel ad, tıklama istatistiği ve QR var; özel alan adı yok | Kesin, 2026-10-14 | Özel alan adı DNS doğrulama ve otomatik TLS gerektirir; MVP'den sonraya kaldı |
| K06 | Ürün adı Vurli, alan adı vurli.io | Kesin, 2026-10-08 | Alan adı kaydı ve DNS kurulumu M4'te yapılacak |

## Teknik

| ID | Karar | Durum | Gerekçe |
|---|---|---|---|
| K10 | Java 25 (LTS) ve Spring Boot'un güncel kararlı 4.x sürümü | Varsayılan | Java/Spring kullanıcı tercihi. Kesin sürümler M0'da doğrulanır |
| K11 | Build aracı: Maven Wrapper | Değişti → K27 | Kurumsal Java projelerinde en yaygın build aracı olduğu için seçilmişti |
| K12 | Modüler monolit, tek uygulama, tek deploy | Varsayılan | Tek geliştirici ve tek sunucu için en sade yapı. Modül sınırları testle korunur ([mimari](architecture.md#modüller)) |
| K13 | PostgreSQL, şema değişiklikleri Flyway ile | Varsayılan | Unique constraint, transaction ve JSONB desteği yeterli; tek veritabanı |
| K14 | Redis yok; uygulama içi cache (Caffeine) | Varsayılan | Tek instance'ta Redis yalnızca işletme yükü getirir. İkinci instance gerektiğinde yeniden değerlendirilir |
| K15 | Panel: Thymeleaf + htmx | Değişti → K28, K29 | Tek uygulama ve tek deploy olduğu için seçilmişti |
| K16 | Kimlik doğrulama: e-posta + şifre ve Spring Security oturumu. Oturumlar Spring Session JDBC ile veritabanında | Yöntem kesin (2026-10-14), oturum deposu varsayılan | Oturumlar veritabanında tutulursa deploy sonrası kullanıcıların oturumu kapanmaz |
| K17 | Barındırma: tek VPS üzerinde Docker Compose ile Caddy, uygulama ve PostgreSQL | Kesin, 2026-10-14 | Aylık yaklaşık 5–10 €, şeffaf ve öğretici. Yedekleme sorumluluğu bizde ([mimari](architecture.md#dağıtım)) |
| K18 | Yönlendirme: `302` + `Cache-Control: no-store` | Varsayılan | Hedef değiştirilebilir. Tarayıcılar `301`'i kalıcı olarak cache'ler; sonradan değişen hedef eski ziyaretçilere ulaşmaz |
| K19 | Kısa kod: 7 karakter Base62, kriptografik rastgele | Varsayılan | 62⁷ ≈ 3,5 trilyon olasılık. Tahmin edilemeyen kodlar link taramasını zorlaştırır. Çakışmada unique hatası alınır ve yeniden denenir |
| K20 | Kısa linkler ve tanıtım sayfası `vurli.io`'da (Spring), kullanıcı paneli `app.vurli.io`'da (Next.js) | Varsayılan | Panelin oturum çerezi yalnızca `app.vurli.io`'ya gider. Kısa link sayfaları oturum çerezini hiç görmez. Yönetici panelinin host'u M3'te seçilir |
| K21 | Her hesabın bir workspace'i var ve linkler workspace'e bağlı | Varsayılan | MVP'de takım yok, ama izolasyon sınırı baştan doğru yerde olur. Takım eklemek sonradan büyük migration gerektirmez |
| K22 | Tıklamalar bellekteki bir kuyruktan toplu yazılır. Ham kayıt 90 gün, günlük özet süresiz tutulur | Varsayılan | Yönlendirme istatistik yazımını beklemez. Çökme anında birkaç saniyelik tıklama kaybı kabul edilir |
| K23 | IP adresi saklanmaz. Ülke GeoIP ile bulunur ve IP atılır | Varsayılan | Kişisel veriyi en aza indirmek (GDPR) |
| K24 | URL güvenlik kontrolü: Google Web Risk API | Varsayılan | Google Safe Browsing API ticari kullanıma kapalı; ticari karşılığı Web Risk. Ücret ve kota koşulları M3'te doğrulanacak |
| K25 | Git, GitHub ve GitHub Actions | Varsayılan | Repo M0'da açılır |
| K26 | Testler: JUnit 5 ve Testcontainers ile gerçek PostgreSQL | Varsayılan | Unique constraint, transaction ve izolasyon davranışı sahte veritabanıyla doğrulanamaz |
| K27 | Build aracı: Gradle Wrapper, Kotlin DSL (`build.gradle.kts`) | Kesin, 2026-10-14 | Spring Initializr'ın ve Spring Boot'un kendi varsayılanı; yeni projelerde payı artıyor. Java toolchain JDK 25'i kendisi bulur ve artımlı build hızlıdır. Wrapper jar'ı ve dağıtımı checksum ile doğrulanır. Maven'ın kurumsal dünyadaki yaygınlığı bilinçli olarak göze alındı; iki araç da bilinmeli |
| K28 | Kullanıcı paneli Next.js (App Router), BFF olarak: tarayıcı yalnızca Next.js ile konuşur, Next.js sunucu tarafında Spring REST API'yi çağırır | Kesin, 2026-10-15 | "Spring Boot API + React/Next.js" kurumsal hedef için değerli bir yığın. Spring API internete açılmaz, tarayıcıda token tutulmaz, tek origin olduğu için CORS gerekmez. Bedeli: ikinci dil ve deploy, M2'de yaklaşık %30–50 ek süre |
| K29 | Kimlik, yetki ve verinin tek sahibi Spring. Next.js oturum çerezini Spring'e iletir ve iş kuralı içermez | Kesin, 2026-10-15 | Güvenlik mantığı tek yerde, test edilen Java tarafında kalır. "API'yi gizlemek" tek başına güvenlik sayılmaz: Next.js'in uç noktaları da dışa açıktır, yetkiyi yine Spring denetler |
| K30 | Yönetici paneli (cPanel benzeri) Spring içinde Thymeleaf ile | Kesin, 2026-10-15 | Yalnızca bizim kullandığımız iç araç; ayrı uygulamaya gerek yok ve kullanıcı paneliyle aynı saldırı yüzeyini paylaşmaz. M3'te |
| K31 | Kullanıcı paneli arayüzü: Tailwind CSS + shadcn/ui | Kesin, 2026-10-15 | Next.js ekosisteminin fiili standardı; erişilebilir bileşenler hazır gelir. Kurulumda shadcn'in güncel varsayılanı olan Base UI (`@base-ui/react`) geldi, Radix değil |
| K32 | E-posta SMTP ile gönderilir; yerelde Mailpit yakalar, gerçek sağlayıcı M4'te bağlanır | Kesin, 2026-10-15 | Bütün sağlayıcılar SMTP desteklediği için kod sağlayıcıdan bağımsız kalır; geliştirmede gerçek e-posta gönderilmez |
| K33 | Panel Türkçe ve İngilizce; dil tarayıcıdan seçilir, metinler baştan mesaj dosyalarında | Kesin, 2026-10-15 | Sonradan çok dillilik eklemek her ekranı elden geçirmeyi gerektirir. Doğrulama ve şifre sıfırlama e-postaları da iki dilli |
| K34 | E-postasını doğrulamamış kullanıcı giriş yapabilir ama link oluşturamaz | Kesin, 2026-10-15 | "E-posta gelmedi" durumunda kullanıcı takılı kalmaz, panelden yeniden gönderebilir; K04'teki kötüye kullanım koruması korunur |
| K35 | Monorepo: `backend/` (Spring) ve `frontend/` (Next.js); kökte belgeler, Compose ve CI | Varsayılan, 2026-10-15 | Her aracın kendi klasörü olur; CI işleri yol filtresiyle ayrışır, Docker build bağlamları ayrı kalır. Taşıma tek commit, git geçmişi korunur |
| K36 | Oturum 24 saat hareketsizlikte sona erer | Varsayılan, 2026-10-15 | Panel günlük kullanılır; daha uzun süre çalınan bir çerezin değerini artırır. "Beni hatırla" istenirse ayrıca tasarlanır |
| K37 | Spring API'de CSRF: token yerine zorunlu `X-Vurli-Client` başlığı + `SameSite=Lax` çerez | Varsayılan, 2026-10-15 | API'nin tek istemcisi BFF; token tabanlı CSRF'i Next.js üzerinden taşımak karmaşık. Özel başlık tarayıcıdan başka bir siteye CORS'suz eklenemez (OWASP) |
| K38 | E-posta token'ları linkte URL fragment'ında (`#token=`) | Varsayılan, 2026-10-15 | Fragment sunucuya gönderilmez: token erişim loglarına, proxy'lere ve `Referer` başlığına düşmez. Next.js sayfası token'ı okuyup POST ile gönderir |
| K39 | Panel sayfaları dinamik render edilir; Next.js `cacheComponents` ve `partialPrefetching` kapalı | Varsayılan, 2026-10-15 | Panelin her sayfası kullanıcıya özel. Nonce tabanlı CSP dinamik render gerektiriyor ve Next'in statik kabuk (PPR) yaklaşımıyla uyumsuz (Next.js CSP rehberi) |
| K40 | Çok dillilik kütüphanesiz: dil `Accept-Language`'den seçilir, sözlükler sunucuda JSON, URL'de dil öneki yok | Varsayılan, 2026-10-15 | Next.js rehberi kütüphane şart koşmuyor; daha az bağımlılık, daha küçük tedarik zinciri. Panel giriş arkasında, arama motoru için dil öneki gerekmiyor. Dil seçici gerekirse çerezle eklenir |
| K41 | Panelde nonce tabanlı CSP (`src/proxy.ts`); geliştirme modunda yalnızca `unsafe-eval` ve satır içi stile izin | Varsayılan, 2026-10-15 | Script'ler yalnızca kendi kaynağımızdan ve nonce'la çalışır. Geliştirme gevşetmeleri Next.js geliştirme araçları için gerekli; production sayfası tarayıcıda ihlalsiz doğrulandı |
| K42 | Hız sınırları ve kotalar: giriş IP başına dakikada 10 ve hesap başına 15 dakikada 10 başarısız deneme; kayıt, şifre sıfırlama ve doğrulama e-postası IP başına saatte 5; link oluşturma hesap başına günde 50 ve dakikada 10; yönlendirme IP başına dakikada 300. Sayaçlar bellekte (Bucket4j); günlük link kotası ise veritabanından sayılır | Kesin, 2026-10-15 | Kayıt uç noktası başkalarının adreslerine e-posta göndermek için kötüye kullanılabiliyor; kapalı beta için yeterince sıkı, normal kullanıcıyı engellemeyecek kadar geniş. Tek instance olduğu için bellek yeterli. API'yi Next.js çağırdığından ziyaretçi IP'si Next.js tarafından iletilir; Spring bu bilgiye yalnızca iç ağdan geldiği için güvenir. Günlük kota uygulanırken veritabanına taşındı: yeniden başlatma onu sıfırlamamalı ve silinen linkler de sayılmalı |
| K43 | Yönetici paneli `admin.vurli.io`'da, Caddy üzerinden internete açık; giriş TOTP ile iki adımlı, Spring Security CSRF açık, oturum çerezi yalnızca bu host'a ait, girişte hız sınırı | Kesin, 2026-10-15 | SSH tüneli daha küçük bir saldırı yüzeyi sunardı ama her yeni bilgisayarda anahtar kurulumu gerektirir; kullanıcı her yerden erişimi tercih etti. Bu yüzden ikinci faktör ve hız sınırı zorunlu, tek savunma hattı bunlar |
| K44 | Ülke verisi: DB-IP IP to Country Lite (aylık, `.mmdb`), MaxMind'ın `maxmind-db` okuyucusuyla | Kesin, 2026-10-15 | Hesap ve lisans anahtarı gerekmez, ücretsiz. Lisans CC BY 4.0: istatistik kartında "IP Geolocation by DB-IP" atfı zorunlu. Dosya biçimi MaxMind GeoLite2 ile aynı, geçiş tek ayar. Dosya repoda değil, `scripts/download-geoip.sh` ile indirilir; yoksa tıklamalar ülkesiz sayılır |
| K45 | Günlük istatistiklerde gün UTC'ye göre | Kesin, 2026-10-15 | Her yerde aynı ve yaz saati sorunu yok. Bedeli: Türkiye'deki kullanıcı için gün 03:00'te başlar; panel bunu belirtir |
| K46 | Panel grafikleri shadcn chart (Recharts) ile | Kesin, 2026-10-15 | Hazır, erişilebilir ve etkileşimli bileşen. shadcn'in renkleri satır içi `<style>` ile yazan parçası CSP ve `dangerouslySetInnerHTML` yasağı yüzünden çıkarıldı; renkler Tailwind sınıflarıyla veriliyor. Grafik yalnızca tarayıcıda çizilir, çünkü Recharts'ın sunucuda ürettiği `style` öznitelikleri production CSP'sine takılır |

## Açık sorular

| ID | Soru | Ne zaman gerekli | Öneri |
|---|---|---|---|
| A01 | E-posta gönderim servisi | M4 (kod SMTP ile sağlayıcıdan bağımsız, bkz. K32) | Postmark veya Resend: kurulumu basit, ücretsiz katmanı başlangıç için yeterli |
| A02 | VPS sağlayıcısı ve bölgesi | M4 | Hetzner, AB bölgesi (Almanya veya Finlandiya) |
| A03 | Gelir modeli | MVP sonrası, kullanıcı geri bildirimiyle | İlk aday freemium abonelik: ücretsiz plan limitli, ücretli planda özel alan adı ve gelişmiş istatistik |
| A04 | Kota ve hız sınırları | Kapandı → K42 (2026-10-15) | Yeni hesaba günde 50 link, ileride güvenilir hesaplar için artırılır |
| A05 | Şirket, gizlilik politikası ve kullanım koşulları | M4 öncesi | Kapalı beta öncesinde hazır olmalı |

## Eski plandan çıkarılanlar

Codex'in 2026-10-08 planında olup bilinçli olarak çıkarılanlar:

- **4 saniyelik sponsorlu reklam ekranı.** Trafik yokken gelir getirmez. Kısaltıcıya karşı güveni zedeler. Reklam içeriği güvenliği ve dolandırıcılık kontrolü tek başına ayrı bir ürün kadar iş çıkarır. Gelir modeli konuşulurken yeniden değerlendirilebilir.
- **Kubernetes, Kafka, Redis, çok bölgeli dağıtım.** Bu ölçekte gerekmiyor.
- **OWASP ASVS L2 uyum matrisi, SBOM ve provenance, kademeli canlıya alma.** Tek kişilik bir pilot için orantısız. Güvenlik önlemleri [security.md](security.md) içindeki somut tehditlere göre seçildi.
