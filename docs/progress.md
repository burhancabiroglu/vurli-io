# İlerleme

Son güncelleme: 2026-10-15

## Şu an

M0 ve M1 tamamlandı; repo `burhancabiroglu/vurli-io`, CI yeşil. **M2 (hesaplar ve kullanıcı paneli) planlandı**, kod henüz yok. Kullanıcı paneli Next.js BFF, yönetici paneli Spring + Thymeleaf olacak ([K28–K35](decisions.md#teknik)). M2 dört dilime bölündü ([yol haritası](roadmap.md)).

## Sıradaki adım

1. M2 dilim 1, monorepo ve altyapı:
   - Spring projesini `git mv` ile `backend/`'e taşı.
   - `frontend/` altında Next.js'i kur (App Router, TypeScript, Tailwind, shadcn/ui).
   - CI'ı yol filtreli iki işe ayır.
   - Compose'a Mailpit ekle.
   - README, AGENTS.md ve Dependabot yollarını güncelle.
2. Ardından dilim 2: hesaplar (Spring). Dev API ve `DevSecurityConfiguration` bu dilimde kaldırılır.

## Kayıt

### 2026-10-15 — M2 kararları

- GitHub reposu silinip boş olarak yeniden oluşturuldu ve 6 commit aynen push edildi (CI yeşil, Contributors yalnızca kullanıcı).
- Kararlar ([decisions.md](decisions.md)):
  - Kullanıcı paneli Next.js BFF; kimlik, yetki ve veri Spring'de (K28, K29).
  - Yönetici paneli Spring + Thymeleaf (K30).
  - Arayüz Tailwind + shadcn/ui (K31).
  - E-posta SMTP ile; yerelde Mailpit, sağlayıcı M4'te (K32).
  - Panel Türkçe + İngilizce (K33).
  - Doğrulanmamış kullanıcı giriş yapar ama link oluşturamaz (K34).
  - Monorepo `backend/` + `frontend/` (K35, önerildi).
- Kullanıcının sorusu üzerine: Next.js'i Spring'in `resources` klasörüne koymak yalnızca statik dışa aktarımla mümkün. O durumda BFF'nin sunucu tarafı kaybolur, bu yüzden Next.js ayrı bir süreç olarak çalışacak.
- Mimari, güvenlik (BFF güven sınırı, CSRF, CSP) ve yol haritası (M2 55–85 saat, toplam 145–215 saat) güncellendi.

### 2026-10-15 — CI hatası: Linux'ta zaman hassasiyeti

- `8d36c88` commit'i CI'da başarısız oldu ([çalışma 38637757906](https://github.com/burhancabiroglu/vurli-io/actions/runs/38637757906)), yerelde ise geçiyordu. Log dosyaları oturum açmadan okunamadığı için sorun yerelde yeniden üretildi.
- Kök neden: Linux'ta `Instant.now()` nanosaniye, PostgreSQL ise mikrosaniye tutar. Oluşturma yanıtındaki `createdAt` (ve kullanıcının gönderdiği nanosaniyeli `expiresAt`) veritabanında saklanandan farklıydı. `otherWorkspaceCannotSeeOrChangeLink` testi yanıtla veritabanı kaydını karşılaştırdığı için yalnızca Linux'ta başarısız oldu. macOS saati mikrosaniye hassasiyetinde olduğu için sorun yerelde görünmüyordu.
- Doğrulama: `ClockConfiguration` geçici olarak nanosaniyeli bir saate çevrildi ve aynı test yerelde de aynı farkla başarısız oldu.
- Düzeltme: test gevşetilmedi. `Link` entity'si sakladığı bütün zamanları mikrosaniyeye yuvarlıyor, böylece döndürülen değerle saklanan değer hep aynı.
- Regresyon testi: `returnsExactlyWhatIsStoredWithNanosecondClock` işletim sisteminden bağımsız olarak nanosaniyeli sabit bir saat kullanıyor. Yuvarlama kaldırılınca bu test başarısız oluyor.
- Sonuç: 151 test hem normal saatle hem nanosaniyeli saatle geçti. GitHub Actions: bu commit yazılırken sonuç henüz yoktu.

### 2026-10-15 — M1 dilim 3: yönetim ve dev API

- `LinkManagement`: `get`, `list` (en yeni önce; sayfa boyutu en fazla 100), `changeTarget`, `changeExpiry`, `disable`, `enable`, `delete`. Bütün değişiklikler `expectedVersion` alıyor ve tek bir yoldan geçiyor:
  1. `id + workspaceId` ile yükleme (silinmiş linkler görünmüyor)
  2. Sürüm karşılaştırması
  3. Değişiklik ve `flush` (JPA `@Version` aradaki yarışı yakalıyor)
  4. Commit'ten sonra cache silme
- Yeni hedef de oluşturmadaki doğrulamadan geçiyor ve `safety_checked_at` sıfırlanıyor.
- Engellenmiş link sahibi için donmuş durumda: açılamaz, kapatılamaz, hedefi ve süresi değiştirilemez; yalnızca silinebilir.
- `LinkModeration.block`: yönetici engellemesi sürüm istemiyor; sahibin bir değişikliği araya girerse yeniden deniyor. Yetki ve denetim kaydı M3'te `abuse` modülüyle gelecek.
- Dev API (yalnızca `dev` profili): `/dev/api/links` altında JSON uç noktaları; hatalar `ProblemDetail` biçiminde `code` alanıyla dönüyor. Sabit workspace Flyway `db/dev` tohum verisinden geliyor. `./gradlew bootRun` artık `dev` profiliyle açılıyor.
- Bulunan hata: entity'deki `version()` metodu, Spring Data'nın "yeni kayıt mı" kontrolünde getter sanıldı ve ilkel `long`, `null` değerde hata verdi (29 test başarısız oldu). Metot `hasVersion(long)` olarak yeniden adlandırıldı.
- Doğrulama (`./gradlew build`): 150 test, 0 başarısız.
  - `LinkLifecycleIntegrationTests` (14): başka workspace'in linki okunamıyor, değiştirilemiyor ve listede görünmüyor; eski sürümle güncelleme reddediliyor; aynı sürümle 8 paralel güncellemeden tam 1 kazanan; engelli link sahibi tarafından değiştirilemiyor ama silinebiliyor; yönetici engellemesi sahibin sürümünü eskitiyor; silinen link görünmüyor; liste sırası ve sayfa sınırı.
  - `RedirectIntegrationTests` (+4): hedef değişikliği, kapatma, engelleme ve silme bir sonraki ziyarette etkili; dev profili kapalıyken `/dev/api/**` giriş istiyor.
  - `DevLinkApiTests` (5, dev profili): oluştur → yönlendir → değiştir → sil akışı; 409 `version_conflict` ve `alias_taken`; 422 nedeniyle birlikte; başka workspace'in linki 404.
  - Negatif kontroller: sürüm kontrolü kaldırılınca 2 test, workspace filtresi kaldırılınca 3 test başarısız oldu; kod geri yüklendi. Uygulamadaki sürüm kontrolü kaldırıldığında bile paralel yazma testi geçti; JPA `@Version` eşzamanlı yazmaları tek başına yakalıyor.
  - Gerçek sunucuda (`./gradlew bootRun` ve `curl`): oluştur → 302 → hedef değiştir (v1) → 302 yeni hedefe → eski sürümle kapatma 409 → sil 204 → ziyaret 410 → aynı adı tekrar alma 409.
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.

### 2026-10-14 — M1 dilim 2: yönlendirme

- `link` modülü dışarıya `LinkResolver.resolve(kod)` açıyor; sonuç `LinkResolution` (`Redirect`, `Gone`, `NotFound`) olarak dönüyor. Karar her istekte o anki saatle veriliyor; cache'te yalnızca `LinkSnapshot` (hedef, durum, süre sonu, silinme) tutuluyor.
- `LinkCache`: Caffeine, 60 sn TTL, 100.000 girdi; "yok" sonuçları da cache'leniyor. `LinkManagement.create` commit'ten sonra kodun cache girdisini siliyor.
- `redirect` modülü: `RedirectController` 302, 404, 410 ve 503 (veritabanı hatası) döndürüyor. Bütün yanıtlarda `Cache-Control: no-store` var. Hata sayfaları Thymeleaf şablonları.
- `SecurityConfiguration`: yalnızca tek segmentli `GET`/`HEAD` istekleri ve `/actuator/health` herkese açık; geri kalanı girişe yönlendiriliyor (M2'de gerçek girişle değişecek).
- Mimari belgesindeki "engelleme en geç 60 sn" taahhüdü, aşağıdaki test sayesinde "commit'ten sonra anında" olarak güncellendi.
- Doğrulama (`./gradlew clean build`): 128 test, 0 başarısız.
  - `LinkCacheTests` (3): engellemeden önce okunup sonra biten bir yüklemenin eski kaydı cache'e bırakamadığı bariyerli test; "yok" sonuçlarının cache'lenmesi; TTL'in sahte saatle sınanması. Negatif kontrol: cache atomik olmayan bir yüklemeyle değiştirildi, yarış testi başarısız oldu, kod geri yüklendi.
  - `LinkResolverTests` (19): süre sonunun tam sınırda (`now == expiresAt` → 410) çalışması; kapalı, engellenmiş ve silinmiş linkler; kodların büyük-küçük harf duyarlılığı; geçersiz veya rezerve kodlarda hiç sorgu yapılmaması.
  - `RedirectIntegrationTests` (8, gerçek PostgreSQL ve güvenlik zinciri): 302 ile `no-store` ve çerez olmaması; gövdesiz HEAD; `?target=` parametresinin etkisiz kalması; 404 ve 410; link oluşturulunca cache'teki "yok" sonucunun temizlenmesi; health'in açık, diğer yolların kapalı olması.
  - `RedirectControllerTests` (1): veritabanı hatasında 503 ve `Location` başlığının olmaması.
  - Gerçek sunucuda (`./gradlew bootRun` ve `curl`): 302 ile `Location`, `no-store` ve güvenlik başlıkları geldi, `Set-Cookie` yoktu; HEAD aynı yönlendirmeyi döndürdü; bilinmeyen kod 404 aldı. Yerel geliştirme veritabanına `deneme1` adlı bir test linki eklendi.
  - GitHub Actions: bu commit yazılırken sonuç henüz yoktu.
- Açık: `Host` başlığı kontrolü henüz yok, M2'de panel `app.vurli.io`'ya taşınırken eklenecek.

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
