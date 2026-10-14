# İlerleme

Son güncelleme: 2026-10-14

## Şu an

M0 (iskelet) tamamlandı ve GitHub'da (`burhancabiroglu/vurli-io`). Build aracı Maven'dan Gradle'a çevrildi ve GitHub'a gönderildi. Sıradaki iş: **M1, link çekirdeği** ([yol haritası](roadmap.md)).

## Sıradaki adım

1. Gradle geçişi commit'inin GitHub Actions sonucunu kontrol et.
2. M1'e başla: `links` tablosu için ilk Flyway migration'ı (`V1__create_links.sql`) ve ilk test olarak paralel iki istekte aynı adın yalnızca bir kez alınabilmesi.

## Kayıt

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
