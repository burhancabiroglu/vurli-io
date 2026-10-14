# Ürün

## Vurli nedir?

Uzun linkleri kısa ve güvenli linklere çeviren bir web servisi. Kullanıcı `vurli.io/kafe-menu` gibi bir link oluşturur, QR kodunu indirir ve kaç kişinin tıkladığını görür.

Linkin hedefi sonradan değiştirilebilir. Masalara basılmış bir QR kod, yeniden basılmadan yeni menü sayfasına yönlendirilebilir. Bu, ürünün en önemli vaadi: **bir kez paylaşılan link yıllarca çalışır ve sahibinin kontrolünde kalır.**

## Kimin için?

İlk hedef küçük işletmeler ve içerik üreticileri:

- masalarına QR menü koyan bir kafe,
- Instagram biyografisine link koyan bir içerik üreticisi,
- broşür bastıran bir butik.

Bu kişilerin ihtiyacı: hızlıca link oluşturmak, QR almak, basit istatistik görmek ve hedefi sonradan değiştirebilmek. Kurumsal ekipler, ajanslar ve geliştirici API'si şimdilik hedef değil.

## Projenin amacı

İki hedef var ve sırası önemli:

1. **Önce:** çalışan, testli ve mimari kararları savunulabilir bir çekirdek. Mülakatta gösterilebilecek düzeyde.
2. **Sonra:** gerçek kullanıcılara açılabilen bir ürün.

İlk sürümde gelir modeli yok. Ürün kullanıcı kazandıktan sonra seçilecek ([A03](decisions.md#açık-sorular)).

## Kullanıcı yolculuğu

1. **Kayıt:** e-posta ve şifreyle kayıt olur, gelen doğrulama linkine tıklar.
2. **Link oluşturma:** hedef URL'yi girer. İsterse özel bir ad (`vurli.io/kafe-menu`) ve bitiş tarihi seçer. Vurli URL'yi güvenlik kontrolünden geçirir, kısa linki ve QR'ı gösterir.
3. **Paylaşma:** kısa linki kopyalar ya da QR'ı PNG veya SVG olarak indirir.
4. **Ziyaret:** biri linke tıklar ve hemen hedefe gider. Ara sayfa veya reklam yoktur.
5. **Takip:** panelde günlük tıklama sayısını, ziyaretçilerin nereden geldiğini, ülke ve cihaz dağılımını görür.
6. **Yönetim:** hedefi değiştirir, linki geçici olarak kapatır veya siler.

## İlk sürüm (MVP)

**Var:**

- Hesap: kayıt, e-posta doğrulama, giriş, şifre sıfırlama
- Link: oluşturma, listeleme, düzenleme, kapatma, silme
- Rastgele kod veya özel ad, isteğe bağlı bitiş tarihi
- QR kod (PNG ve SVG)
- Tıklama istatistiği: günlük sayılar, kaynak site, ülke, cihaz türü
- Kötüye kullanım bildirimi ve yönetici engellemesi

**Bilinçli olarak ertelenenler:**

- özel alan adı
- ekip veya çoklu kullanıcı
- API anahtarı
- ücretli plan ve reklam
- hesapsız link oluşturma
- mobil uygulama
- toplu içe aktarma

## Ürün kuralları

Bu kurallar her zaman geçerli. Her biri otomatik testle korunacak.

1. Bir kullanıcı başka bir hesabın linklerini göremez ve değiştiremez. Varlıklarını bile öğrenemez.
2. Kısa kod tektir. Silinen bir linkin kodu başka birine verilmez; basılmış eski QR'lar başkasının sitesine gitmemeli.
3. Kapatılmış, engellenmiş, silinmiş veya süresi dolmuş link yönlendirme yapmaz.
4. Yönetici engellemesini link sahibi kaldıramaz.
5. Ziyaretçi her zaman sunucuda kayıtlı hedefe gider. İstek parametreleriyle hedef değiştirilemez.
6. İstatistikler yaklaşık değerdir. Botlar ve önizleme istekleri sayılmaz, küçük kayıplar kabul edilir.

## MVP başarı ölçütü

Çekirdek akış (kayıt → link → QR → yönlendirme → istatistik) uçtan uca testlerle doğrulanmış olmalı. 10–20 tanıdık kullanıcı kapalı betada ürünü gerçekten kullanıyor olmalı.
