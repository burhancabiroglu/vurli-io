# Vurli

Küçük işletmeler ve içerik üreticileri için güvenli link kısaltma servisi. Java ve Spring Boot ile geliştiriliyor. Alan adı: **vurli.io**.

**Durum (2026-10-14):** Ürün ve teknik kararlar verildi, kod henüz yazılmadı. Sıradaki adım M0 (proje iskeleti).

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

Gerekenler: JDK 25 ve çalışan bir Docker. Maven kurmaya gerek yok; repodaki Maven Wrapper (`./mvnw`) kendi sürümünü indirir.

Sistemdeki varsayılan Java 25 değilse önce `JAVA_HOME`'u ayarla (macOS):

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

Bütün testleri çalıştır (Testcontainers gerçek bir PostgreSQL 18 başlatır):

```bash
./mvnw verify
```

Uygulamayı yerelde çalıştır (Spring Boot `compose.yaml` ile PostgreSQL'i kendisi başlatır):

```bash
./mvnw spring-boot:run
```

Her push ve PR'da GitHub Actions aynı `verify` komutunu çalıştırır ([ci.yml](.github/workflows/ci.yml)).
