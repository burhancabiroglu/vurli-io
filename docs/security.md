# Güvenlik

Güvenlik bu projenin birinci önceliği. Aşağıda URL kısaltıcıların gerçekte karşılaştığı tehditler var; her biri için önlem ve o önlemi doğrulayacak test yazılı. Henüz hiçbiri uygulanmadı.

## 1. Zararlı link barındırma (en büyük risk)

**Neden önemli:** Kısaltıcılar phishing ve zararlı yazılım linklerini gizlemek için kullanılır. Yeterince zararlı link barındırırsak `vurli.io` alan adının kendisi Google Safe Browsing, e-posta sağlayıcıları ve Spamhaus gibi kara listelere girer. O zaman tek tek kötü linkler değil, **bütün kullanıcıların bütün linkleri** çalışmaz hale gelir.

**Önlemler:**

- Yalnızca e-posta doğrulamalı hesaplar link oluşturabilir. Yeni hesapların günlük kotası düşüktür.
- Link oluşturulurken ve hedef değiştiğinde Web Risk kontrolü yapılır. Aktif linkler haftalık olarak yeniden taranır.
- Tanıtım sayfasında "Kötüye kullanım bildir" formu ve `abuse@vurli.io` adresi bulunur.
- Yönetici bir linki, bir hesabı veya bir hesabın bütün linklerini tek işlemle engelleyebilir. Her engelleme denetim kaydına yazılır.
- `vurli.io`'nun kara liste durumu düzenli kontrol edilir (Google Safe Browsing site durumu, Spamhaus DBL).

**Testler:**

- Tehlikeli işaretli URL reddedilir (Web Risk'in test URL'leriyle).
- Engellenen link en geç 60 saniyede yönlendirmeyi durdurur.
- Link sahibi engeli kaldıramaz.

## 2. Başka hesabın verisine erişim (IDOR)

**Önlemler:**

- Her link sorgusu oturumdaki kullanıcının workspace'iyle filtrelenir. Workspace kimliği istekten alınmaz.
- Başka bir workspace'e ait kaynak istendiğinde `404` döner.

**Test:** İki hesap açılır. A hesabı, B'nin link ID'siyle okuma, düzenleme, silme, istatistik ve QR işlemlerini dener; hepsi `404` almalı. Yeni eklenen her endpoint bu teste dahil edilir.

## 3. Hesap ele geçirme

**Önlemler:**

- Şifreler Argon2id ile hash'lenir (Spring Security `DelegatingPasswordEncoder`). Şifre en az 10 karakter olmalı.
- Giriş denemelerine hem IP hem hesap bazında hız sınırı uygulanır.
- Hata mesajı "e-posta veya şifre hatalı" şeklindedir; hangisinin yanlış olduğu söylenmez. Kayıt ve şifre sıfırlama da bir hesabın var olup olmadığını belli etmez.
- Doğrulama ve sıfırlama token'ları rastgele üretilir, tek kullanımlıktır, hash'li saklanır ve kısa ömürlüdür (24 saat ve 1 saat).
- Şifre değişince diğer bütün oturumlar kapatılır.
- Yönetici hesaplarında TOTP ile iki adımlı doğrulama zorunludur (M4'ten önce).

**Testler:**

- Kaba kuvvet denemeleri hız sınırına takılır.
- Kullanılmış veya süresi dolmuş token reddedilir.
- Kayıt ve sıfırlama yanıtları, var olan ve olmayan hesap için aynıdır.

## 4. URL hileleri ve yönlendirme bütünlüğü

**Önlemler:**

- Hedef her zaman veritabanındaki kayıttır ve istek parametresiyle değişmez. Açık yönlendirme yoktur.
- Kullanıcı bilgisi içeren URL'ler, `javascript:` ve `data:` şemaları, IP adresleri ve `vurli.io`'ya geri dönen linkler reddedilir.
- `Host` başlığına güvenilmez; yalnızca tanımlı host'lar işlenir. Caddy dışından gelen proxy başlıkları dikkate alınmaz.
- `Location` başlığına normalize edilmiş URL yazılır; CR/LF karakterleri içeremez.

**Test:** URL doğrulayıcı geniş bir sınır değer setiyle test edilir: Unicode ve punycode host'lar, encode edilmiş karakterler, IPv6, kullanıcı bilgisi ve aşırı uzun URL'ler.

## 5. Panelde XSS ve CSRF

**Önlemler:**

- Thymeleaf varsayılan olarak escape eder. `th:utext` kullanılmaz.
- CSP ile script'ler yalnızca kendi origin'imizden yüklenir; inline script yoktur.
- Spring Security CSRF koruması açıktır.
- Oturum çerezi host-only, `Secure`, `HttpOnly` ve `SameSite=Lax`'tır. Kısa link alan adı (`vurli.io`) oturum çerezini hiç görmez.

**Testler:**

- Hedef URL'ye veya özel ada yerleştirilen bir script panelde çalışmaz.
- CSRF token'ı olmayan bir `POST` isteği `403` alır.

## 6. Kaynak tüketimi

- Kayıt, giriş, şifre sıfırlama ve link oluşturma işlemlerine hız sınırı uygulanır (Bucket4j, bellekte). Yönlendirmede IP başına geniş bir sınır vardır.
- Tıklama kuyruğu sınırlıdır; dolarsa olay atılır ama yönlendirme etkilenmez.
- Listeleme sayfalıdır ve sayfa boyutunun üst sınırı vardır. İstatistik sorguları tarih aralığıyla sınırlıdır.
- Ciddi bir DDoS saldırısında Cloudflare proxy'si devreye alınabilir. MVP'de kurulmayacak.

## 7. Kişisel veri

- IP adresi veritabanına yazılmaz. Caddy erişim logları 7 gün tutulur.
- Hedef URL'ler loglanmaz, çünkü query string'lerinde token bulunabilir.
- Gizlilik politikası ve hesap silme özelliği kapalı betadan önce hazır olur.

## 8. Sırlar ve tedarik zinciri

- Sırlar repoda tutulmaz. Sunucuda `600` izinli `.env` dosyasında, CI'da GitHub Actions secrets'ta bulunur.
- Bağımlılık güncellemeleri Dependabot ile yapılır. Üçüncü taraf GitHub Actions commit SHA'sıyla sabitlenir.
- Fork'tan gelen PR'lar secret'lara erişemez.
- Docker image'ı root olmayan bir kullanıcıyla ve minimal bir base image üzerinde çalışır.

## Kapsam dışı (şimdilik)

Bağımsız pentest, uyum sertifikaları (ASVS, SOC 2) ve WAF şimdilik kapsam dışı. Kapalı betadan önce yukarıdaki testlerin hepsinin geçmesi gerekiyor.
