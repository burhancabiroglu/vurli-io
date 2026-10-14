# İlerleme

Son güncelleme: 2026-10-14

## Şu an

M0 (iskelet) tamamlandı ve ilk commit atıldı. GitHub remote henüz olmadığı için CI hiç çalışmadı. Sıradaki iş: **M1, link çekirdeği** ([yol haritası](roadmap.md)).

## Sıradaki adım

1. GitHub'da repo oluştur, push et ve ilk CI çalışmasının yeşil olduğunu doğrula.
2. M1'e başla: `links` tablosu için ilk Flyway migration'ı (`V1__create_links.sql`) ve ilk test olarak paralel iki istekte aynı adın yalnızca bir kez alınabilmesi.

## Kayıt

### 2026-10-14 — M0 iskelet

- Git deposu `main` dalıyla başlatıldı (commit yok).
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
  - GitHub Actions: **çalıştırılmadı** (remote yok).

### 2026-10-14 — Planlama

- Codex'in 2026-10-08 tarihli 37 planlama belgesi fazla parçalı ve tekrarlıydı, çok az karar içeriyordu. Yerel `archive/codex-2026-10-08/` klasörüne taşındı; repoya dahil değil.
- Ürün ve teknik kararlar birlikte verildi ([decisions.md](decisions.md)). Belgeler altı dosyaya indirildi.

### 2026-10-08

- Codex ilk planlama belgelerini hazırladı. Vurli adı ve vurli.io alan adı seçildi.

## Açık konular

- Açık kararlar [decisions.md](decisions.md#açık-sorular) içinde (A01–A05). Hiçbiri M1'i engellemiyor.
- Testlerde JDK, Mockito'nun dinamik agent yüklemesi için uyarı veriyor. İleride bir JDK sürümü bunu engelleyecek. Mockito surefire'da `-javaagent` olarak tanımlanmalı.
- Beklenen uyarılar: Flyway "No migrations found" (ilk migration M1'de), Thymeleaf "template location yok" (şablonlar M2'de).
- Global git ayarı (`~/.gitignore_global`) `.gitignore` dosyasını yok sayıyor. Bu dosya değiştiğinde `git add -f .gitignore` gerekir.
