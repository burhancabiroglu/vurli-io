# Vurli

Küçük işletmeler ve içerik üreticileri için güvenli link kısaltma servisi. Java ve Spring Boot ile geliştiriliyor. Alan adı: **vurli.io**.

**Durum (2026-10-15):** M1 (link çekirdeği) ve M2 (hesaplar ve kullanıcı paneli) tamamlandı: kayıttan QR indirmeye kadar bütün akış tarayıcıda çalışıyor ve uçtan uca testle doğrulanıyor. Sıradaki kilometre taşı M3: istatistik ve kötüye kullanım. Ayrıntı: [ilerleme](docs/progress.md).

## Belgeler

| Belge | İçerik |
|---|---|
| [Ürün](docs/product.md) | Kimin için, ne yapıyor, ilk sürümde ne var ne yok |
| [Kararlar](docs/decisions.md) | Verilen kararlar, gerekçeleri ve açık sorular |
| [Mimari](docs/architecture.md) | Sistem yapısı, veri modeli, yönlendirme akışı |
| [Güvenlik](docs/security.md) | Gerçek tehditler, önlemler ve testler |
| [Yol haritası](docs/roadmap.md) | Kilometre taşları ve tahmini süreler |
| [İlerleme](docs/progress.md) | Şu an neredeyiz, sıradaki iş |

AI araçlarıyla çalışma kuralları [AGENTS.md](AGENTS.md) içinde. Codex'in hazırladığı ilk planlama belgeleri repoya dahil değil; yalnızca yerel `archive/` klasöründe başvuru için tutuluyor.

## Geliştirme

Monorepo: `backend/` (Spring Boot), `frontend/` (Next.js), kökte belgeler ve yerel geliştirme için `compose.yaml` (PostgreSQL, Mailpit).

Gerekenler: JDK 25, Node.js 22 (`frontend/.nvmrc`) ve çalışan bir Docker. Gradle kurmaya gerek yok; `backend/gradlew` kendi sürümünü indirir.

### Backend

Derle ve bütün testleri çalıştır (Testcontainers gerçek bir PostgreSQL 18 başlatır):

```bash
cd backend && ./gradlew build
```

Yerelde çalıştır. Spring Boot kökteki `compose.yaml` ile PostgreSQL ve Mailpit'i kendisi başlatır; gönderilen e-postalar http://localhost:8025 adresinde görünür:

```bash
cd backend && ./gradlew bootRun
```

`bootRun` uygulamayı `dev` profiliyle açar; kısa linkler de `localhost:8080` üzerinden çalışır. API panelden bağımsız olarak `curl` ile de denenebilir. Değişiklik isteklerinde `X-Vurli-Client` başlığı zorunlu.

Kayıt ol; doğrulama e-postası http://localhost:8025 adresindeki Mailpit'e düşer:

```bash
curl -i -X POST localhost:8080/api/v1/auth/register -H 'X-Vurli-Client: curl' -H 'Content-Type: application/json' -d '{"email":"ben@example.com","password":"en az on karakter"}'
```

E-postadaki linkin `#token=` sonrasını doğrulamaya gönder:

```bash
curl -i -X POST localhost:8080/api/v1/auth/verify-email -H 'X-Vurli-Client: curl' -H 'Content-Type: application/json' -d '{"token":"<e-postadaki token>"}'
```

Giriş yap (oturum çerezi `cookies.txt` dosyasına yazılır):

```bash
curl -i -c cookies.txt -X POST localhost:8080/api/v1/auth/login -H 'X-Vurli-Client: curl' -H 'Content-Type: application/json' -d '{"email":"ben@example.com","password":"en az on karakter"}'
```

Link oluştur ve dene:

```bash
curl -s -b cookies.txt -X POST localhost:8080/api/v1/links -H 'X-Vurli-Client: curl' -H 'Content-Type: application/json' -d '{"targetUrl":"https://example.com","alias":"deneme"}'
```

```bash
curl -i localhost:8080/deneme
```

Diğer uç noktalar `AuthController` (`/api/v1/auth`: çıkış, `/me`, şifre sıfırlama) ve `LinkApiController` (`/api/v1/links`: liste, QR, hedef ve süre değiştirme, kapatma, açma, silme) içinde. Değişiklik isteklerinde son görülen `version` gönderilir; uyuşmazsa 409 döner.

### Frontend

İlk kurulum:

```bash
cd frontend && npm ci
```

Geliştirme sunucusu (http://localhost:3000). Panel Spring'i `VURLI_API_URL` adresinde arar; varsayılanı `http://localhost:8080`, yani önce backend'i başlat:

```bash
cd frontend && npm run dev
```

Kayıt ol; doğrulama e-postası http://localhost:8025 adresindeki Mailpit'e düşer, oradaki linke tıklayınca adres doğrulanır.

CI'daki kontrollerin aynısı:

```bash
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
```

Uçtan uca testler (Playwright) gerçek sisteme karşı çalışır: backend `bootRun` ile ve Mailpit açık olmalı, panelin production build'ini Playwright kendisi başlatır. İlk seferde tarayıcıyı indir:

```bash
cd frontend && npx playwright install chromium
```

```bash
cd frontend && npm run build && npm run e2e
```

### CI

Her push ve PR'da yalnızca değişen tarafın işi çalışır: [backend.yml](.github/workflows/backend.yml) ve [frontend.yml](.github/workflows/frontend.yml). [e2e.yml](.github/workflows/e2e.yml) iki tarafın her değişikliğinde bütün sistemi ayağa kaldırıp uçtan uca testleri çalıştırır.
