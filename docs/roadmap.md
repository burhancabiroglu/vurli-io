# Yol haritası

Kapasite: haftada 5–10 saat, tek geliştirici ve AI desteği. Süreler kaba tahmin. M1 bittiğinde gerçek hıza göre güncellenecek.

## M0 — İskelet (10–15 saat)

- Git deposu ve GitHub, Maven Wrapper, Spring Boot projesi, modül paketleri
- Yerel PostgreSQL için Docker Compose; Flyway kurulumu (ilk gerçek migration M1'deki `links` tablosu)
- Testcontainers ile ilk entegrasyon testi; Spring Modulith modül sınırı testi
- GitHub Actions: her push'ta build ve test

**Bitti sayılır:** temiz bir klonda `./mvnw verify` geçiyor ve CI yeşil.

## M1 — Link çekirdeği (25–35 saat)

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

## M2 — Hesaplar ve panel (40–60 saat)

- Kayıt, e-posta doğrulama, giriş ve çıkış, şifre sıfırlama (e-posta servisi seçimi: [A01](decisions.md#açık-sorular))
- Workspace ve izolasyon; iki hesaplı IDOR testleri
- Panel: link listesi, oluşturma, düzenleme, kapatma, silme, QR indirme

**Bitti sayılır:** yeni bir kullanıcı kayıttan QR indirmeye kadar bütün akışı tarayıcıda tamamlayabiliyor.

## M3 — İstatistik ve kötüye kullanım (35–50 saat)

- Tıklama kuyruğu, bot ayıklama, GeoIP, günlük özet ve paneldeki grafikler
- Web Risk entegrasyonu, periyodik tarama, bildirim formu, yönetici engellemesi, denetim kaydı
- Hız sınırları ve kotalar
- İlk yük testi (k6 veya Gatling): yönlendirme gecikmesi ve kapasitesi ölçülür

**Bitti sayılır:** engellenen linkin en geç 60 saniyede durduğu testle doğrulandı ve yük testi sonuçları kaydedildi.

## M4 — Yayın ve kapalı beta (20–30 saat)

- Alan adı, DNS, VPS, Caddy; e-posta için SPF, DKIM ve DMARC kayıtları
- GitHub Actions ile deploy, yedekleme ve bir geri yükleme denemesi, uptime izleme
- Gizlilik politikası, kullanım koşulları, hesap silme, yönetici için iki adımlı doğrulama
- 10–20 tanıdık kullanıcıyla kapalı beta

**Bitti sayılır:** `vurli.io` yayında, geri yükleme denenmiş ve beta kullanıcıları link oluşturuyor.

## Toplam

130–190 saat. Haftada 10 saatle yaklaşık 3–5 ay, haftada 5 saatle yaklaşık 6–9 ay.

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
