# Ses Ustası

Medya sesini kilitleyen / %100'ün üzerine yükselten, ebeveyn ve çocuk modlu Android uygulaması.

## APK nasıl üretilir (GitHub ile, bilgisayara program kurmadan)

1. github.com'da (ücretsiz hesapla) **özel (private)** yeni bir depo açın, örn. `SesUstasi`.
2. Bu klasördeki tüm dosyaları depoya yükleyin (`.github` klasörü dahil).
3. Depoda **Actions** sekmesi → **APK derle** → derleme bitince **Releases** bölümünde `SesUstasi.apk` oluşur.
4. APK'yı telefona indirip açın ("Bilinmeyen kaynaklardan yüklemeye izin ver" gerekir).

## Güncelleme
Kodda değişiklik yapıp `main` dalına gönderdiğinizde yeni bir sürüm otomatik yayınlanır.
Telefonda uygulamayı açın → **Güncelleme** bölümü → **Güncellemeleri denetle** → **Güncelle ve kur**.
(Özel depoda önce "Güncelleme kaynağı"na salt-okunur bir GitHub erişim anahtarı girin.)
Sabit imza için depoya KEYSTORE_B64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD secret'larını ekleyin.

## İlk kurulum (her cihazda)
1. Uygulamayı açın, bildirim iznini verin.
2. "Pil kısıtlamasını kaldır" düğmesine basıp izin verin (kesintisiz çalışma için şart).
3. "Uygulama aktif" anahtarını açın.
4. Çocuk cihazında: PIN ve/veya desen belirleyin → "Silinmeye karşı korumayı aç" → "Çocuk moduna geç".
5. Samsung/Xiaomi/Huawei gibi markalarda uygulamayı "uyutulmayacak / otomatik başlat" listesine de ekleyin.
