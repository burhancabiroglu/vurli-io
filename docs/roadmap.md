# Yol haritası

Kapasite: haftada 5–10 saat, tek geliştirici ve AI desteği. Süreler kaba tahmin. M1 bittiğinde gerçek hıza göre güncellenecek.

## M0 — İskelet (10–15 saat) — tamamlandı, 2026-10-14

- Git deposu ve GitHub, Gradle Wrapper, Spring Boot projesi, modül paketleri
- Yerel PostgreSQL için Docker Compose; Flyway kurulumu (ilk gerçek migration M1'deki `links` tablosu)
- Testcontainers ile ilk entegrasyon testi; Spring Modulith modül sınırı testi
- GitHub Actions: her push'ta build ve test

**Bitti sayılır:** temiz bir klonda `./gradlew build` geçiyor ve CI yeşil.

## M1 — Link çekirdeği (25–35 saat) — tamamlandı, 2026-10-15

- `links` tablosu, kod üretimi, özel ad, URL doğrulama
- Yönlendirme: `302`, `404`, `410`, `HEAD` ve süre sonu kontrolü; cache ve değişiklikte cache silme
- Geçici çözüm: `dev` profilinde sabit bir test kullanıcısı (giriş M2'de gelecek)
- Testler:
  - aynı ad için paralel iki istekten yalnız biri kazanır
  - silinmiş bir kod yeniden kullanılamaz
  - süre sonu tam sınırda doğru çalışır
  - URL doğrulayıcı sınır değerleri geçer
  - engellemeden sonra yönlendirme durur

**Bitti sayılır:** HTTP isteğiyle link oluşturulup yönlendirme yapılabiliyor ve bütün testler geçiyor. Bu, mülakatta gösterilebilecek ilk dilim.

## M2 — Hesaplar ve kullanıcı paneli (55–85 saat)

Kullanıcı paneli Next.js BFF olarak yapılıyor ([K28](decisions.md#teknik)); ilk tahmine göre yaklaşık %30–50 ek süre. Dilimler:

1. **Monorepo ve altyapı:** Spring `backend/`'e taşınır, Next.js `frontend/` olarak kurulur. CI iki işe ayrılır; Compose'a Mailpit eklenir.
2. **Hesaplar (Spring):** `users` tablosu, kayıt, Argon2 şifre hash'i, e-posta doğrulama ve şifre sıfırlama token'ları, iki dilli e-postalar, giriş ve çıkış (JSON API, Spring Session JDBC). Kayıtta kişisel workspace oluşur. Dev API ve geçici güvenlik ayarı kaldırılır.
3. **Panel iskeleti (Next.js):** App Router, Tailwind + shadcn/ui, Türkçe/İngilizce, Spring'e çerez ileten BFF katmanı; kayıt, giriş, doğrulama ve şifre sıfırlama ekranları.
4. **Link ekranları:** liste, oluşturma, düzenleme, kapatma, silme, QR indirme; iki hesaplı IDOR testleri. `Host` başlığı kontrolü.

**Bitti sayılır:** yeni bir kullanıcı kayıttan QR indirmeye kadar bütün akışı tarayıcıda tamamlayabiliyor ve iki hesap birbirinin verisine erişemiyor.

## M3 — İstatistik ve kötüye kullanım (35–50 saat)

Dilimler, bu sırayla:

1. **Hız sınırları ve kotalar** ([K42](decisions.md#teknik)): giriş, kayıt, şifre sıfırlama, link oluşturma ve yönlendirme. Ziyaretçi IP'si Next.js'ten Spring'e iletilir.
2. **İstatistik:** tıklama kuyruğu, bot ayıklama, GeoIP, günlük özet ve paneldeki grafikler.
3. **Yönetici paneli** (Spring + Thymeleaf, cPanel benzeri, `admin.vurli.io`, [K43](decisions.md#teknik)): TOTP ile giriş, link ve hesap arama, engelleme, denetim kaydı.
4. **Web Risk ve bildirim:** oluşturmada ve hedef değişiminde kontrol, periyodik tarama, kötüye kullanım bildirim formu.
5. **İlk yük testi** (k6 veya Gatling): yönlendirme gecikmesi ve kapasitesi ölçülür.

**Bitti sayılır:** yönetici engellemesi uçtan uca (panel → yönlendirme) testle doğrulandı ve yük testi sonuçları kaydedildi.

## M4 — Yayın ve kapalı beta (20–30 saat)

- Alan adı, DNS, VPS, Caddy; e-posta için SPF, DKIM ve DMARC kayıtları
- GitHub Actions ile deploy, yedekleme ve bir geri yükleme denemesi, uptime izleme
- Gizlilik politikası, kullanım koşulları, hesap silme, yönetici için iki adımlı doğrulama
- 10–20 tanıdık kullanıcıyla kapalı beta

**Bitti sayılır:** `vurli.io` yayında, geri yükleme denenmiş ve beta kullanıcıları link oluşturuyor.

## Toplam

145–215 saat (M2'nin Next.js ile büyümesi dahil). Haftada 10 saatle yaklaşık 4–5 ay, haftada 5 saatle yaklaşık 7–10 ay.

## MVP sonrası

Sıra henüz belli değil:

- gelir modeli ([A03](decisions.md#açık-sorular))
- özel alan adı
- ekipler
- API anahtarları
- Google ile giriş
- link-in-bio sayfası

## Çalışma düzeni

Her oturum aynı adımları izler: [progress.md](progress.md)'yi oku, tek bir küçük davranışı testiyle yap, progress.md'yi güncelle. Her kilometre taşı birden fazla küçük PR'dan oluşur.
