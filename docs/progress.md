# İlerleme

Son güncelleme: 2026-10-14

## Şu an

M0 tamamlandı ve GitHub'da (`burhancabiroglu/vurli-io`, CI yeşil). **M1, link çekirdeği** devam ediyor ([yol haritası](roadmap.md)). M1 üç dilime bölündü:

1. **Link oluşturma: tamamlandı** (aşağıdaki kayıt).
2. **Yönlendirme:** `GET`/`HEAD /{kod}` → 302, 404 veya 410; süre sonu, silinmiş ve engellenmiş link; Caffeine cache ve değişiklikte cache silme.
3. **Yönetim:** hedef değiştirme, kapatma, silme, iyimser kilit (`version`); `dev` profilinde sabit workspace ile HTTP API.

## Sıradaki adım

1. Dilim 2'ye başla. İlk test: süresi tam o anda dolan bir linkin 410 döndürmesi (sabit `Clock` ile). Ardından engellenen linkin cache'te kalmadığını gösteren test.

## Kayıt

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
