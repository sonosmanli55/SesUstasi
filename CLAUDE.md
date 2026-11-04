# Ses Ustası: proje notları (Claude Code için)

Android (Kotlin, minSdk 26, targetSdk 34), bağımlılıksız, Gradle Kotlin DSL. Kullanıcı Türk; arayüz ve açıklamalar Türkçe.
Kullanıcı bilgisayarına Android Studio kurmayacak: APK'yı GitHub Actions üretir (.github/workflows/build.yml).

## Ürün kuralları (değiştirme)
- İki mod: EBEVEYN (şifresiz, tam yetki) ve ÇOCUK (uygulamayı açmak için PIN veya desen gerekir; arka plana gidince tekrar kilitlenir).
- Ses yükseltme (%100 üstü, LoudnessEnhancer) YALNIZCA ebeveyn modunda. Çocuk modu en fazla %100 (telefonun normal en yüksek sesi); çocuk sadece azaltabilir ya da %100'e çıkarabilir. Bu sınır Store.level/setLevel içinde de zorlanır.
- Tek kaydırıcı: 0–100 sistem medya sesi kilidi; 100–500 sistem sesi maksimum + yükseltme (500 ≈ +14 dB).
- VolumeService (foreground, specialUse) sesi kilitler (400 ms yoklama + ContentObserver + VOLUME_CHANGED_ACTION), kesintisiz çalışmalı; BootReceiver ile açılışta başlar.
- Normal ↔ Özel geçişi: ana ekran düğmesi, bildirim eylemi, hızlı ayar kutucuğu (son ikisi çocuk modunda devre dışı). Normale dönünce özel moda girerken kaydedilen telefon sesi geri yüklenir.
- Uygulama silinmesine karşı DeviceAdmin (force-lock politikası) seçeneği var.

## Dosyalar
Store.kt (ayarlar, PIN/desen özetleri), VolumeService.kt (çekirdek), VolumeMath.kt (saf ses hesapları, birim testli), MainActivity.kt (tüm arayüz, kodla), Ui.kt (palet + yapı taşları), PatternView.kt, SpecialTile.kt, BootReceiver.kt, AdminReceiver.kt, Updater.kt + InstallReceiver.kt (uygulama içi güncelleme).

## Çalışma kuralları
- Her değişiklikten sonra GitHub Actions derlemesinin yeşil olduğunu doğrula (`gh run watch`).
- Gizli bilgi (keystore, parola) repoya commit edilmez; GitHub Secrets kullanılır.

## Tasarım (Impeccable, Operate modu)
- Sıcak nötr yüzeyler, tek vurgu rengi (yeşil-turkuaz), yükseltme bölgesi için ayrı amber. Açık ve koyu şema Ui.kt içindeki Palette'te; sistem temasını izler.
- Kart içinde kart yok: bölüm = tek yüzey, içinde satırlar. Dokunma hedefi en az 48 dp. Emoji yok, simgeler çizilir.
- Renk/boyut değerleri Ui.kt'den gelir; ekranlarda elle renk yazma.

## Güncelleme denetleyicisi
- CI her derlemede `v<run_number>` etiketli GitHub Release yayınlar; APK'nın versionCode'u aynı sayıdır. Uygulama /releases/latest'i okur, etiket sayısı > versionCode ise "Güncelle ve kur" çıkar.
- İndirme + PackageInstaller ile kurulum; Android kullanıcıdan bir onay ister (atlanamaz). Çocuk modunda bölüm gizlidir.
- Özel depo için kullanıcı, uygulamada "Güncelleme kaynağı"na salt-okunur (Contents: Read) bir token girer.
- Güncellemelerin kurulabilmesi için APK sabit anahtarla imzalanmalı (KEYSTORE_B64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD secret'ları).
