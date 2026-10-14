# Vurli

Küçük işletmeler ve içerik üreticileri için güvenli link kısaltma servisi. Java ve Spring Boot ile geliştiriliyor. Alan adı: **vurli.io**.

**Durum (2026-10-15):** M1 (link çekirdeği) tamamlandı: link oluşturma, güvenli yönlendirme, yönetim ve engelleme testlerle doğrulandı. Sıradaki kilometre taşı M2: hesaplar ve panel. Ayrıntı: [ilerleme](docs/progress.md).

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

Gerekenler: kurulu bir JDK 25 ve çalışan bir Docker. Gradle kurmaya gerek yok; repodaki Gradle Wrapper (`./gradlew`) kendi sürümünü indirir. Gradle'ı çalıştıran Java 17 veya üstü olabilir; kod her durumda toolchain üzerinden JDK 25 ile derlenir.

Derle ve bütün testleri çalıştır (Testcontainers gerçek bir PostgreSQL 18 başlatır):

```bash
./gradlew build
```

Uygulamayı yerelde çalıştır (Spring Boot `compose.yaml` ile PostgreSQL'i kendisi başlatır):

```bash
./gradlew bootRun
```

`bootRun` uygulamayı `dev` profiliyle açar. Giriş sistemi gelene kadar (M2) linkler, yalnızca bu profilde var olan bir JSON API ile denenebilir:

```bash
curl -s -X POST localhost:8080/dev/api/links -H 'Content-Type: application/json' -d '{"targetUrl":"https://example.com","alias":"deneme"}'
```

```bash
curl -i localhost:8080/deneme
```

Diğer uç noktalar `DevLinkApiController` içinde: liste, hedef ve süre değiştirme, kapatma, açma, silme. Değişiklik isteklerinde son görülen `version` gönderilir; uyuşmazsa 409 döner.

Her push ve PR'da GitHub Actions aynı `build` komutunu çalıştırır ([ci.yml](.github/workflows/ci.yml)).
