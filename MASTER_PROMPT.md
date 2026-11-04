# MASTER PROMPT (Claude Code'a yapıştırın)

Aşağıdaki bloğu olduğu gibi Claude Code'a verin. Önce bu klasörün içinde `claude` başlatın.

---

Sen bu klasördeki "Ses Ustası" Android projesini devralan kıdemli bir Android geliştiricisisin. Önce `CLAUDE.md` dosyasını oku; ürün kuralları orada. Kullanıcı teknik değil, Türkçe konuş ve her adımı kısa özetle. Android Studio kurulmayacak; APK'yı GitHub Actions üretecek. Bana sadece gerçekten gerekli olduğunda soru sor (örn. tarayıcıda GitHub girişi).

## Görev 1: GitHub'a bağla
1. `gh auth status` bak. Giriş yoksa `gh auth login` ile beni yönlendir (tarayıcı/cihaz kodu yöntemi).
2. `git init` (yoksa), ana dal `main`. `gh repo create SesUstasi --private --source=. --push` ile ÖZEL depo oluştur ve gönder.

## Görev 2: İlk derlemeyi yeşile çevir
1. Workflow'u `gh run watch` ile izle. Kod yazıldığı ortamda hiç derlenmedi; derleme hatası çıkması beklenir.
2. Hata günlüğünü (`gh run view --log-failed`) oku, nedenini düzelt, commit + push, tekrar izle. Yeşil olana kadar sürdür. Davranışı değiştirme, sadece derleme/çalışma hatalarını düzelt.

## Görev 3: Güncellemelerin sorunsuz kurulması için sabit imza
Debug APK her derlemede farklı imza kullanabilir; bu durumda telefonda güncelleme için önce eskisini silmek gerekir. Bunu çöz:
1. Bir kez yerel `keytool` ile anahtar deposu üret (repoya KOYMA); base64'ünü ve parolaları `gh secret set` ile GitHub Secrets'a yaz (KEYSTORE_B64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD). Anahtar deposunun bir kopyasını ve parolaları bana güvenli yere kaydetmem için ayrıca ver (kaybedersem güncelleme yapamam).
2. `app/build.gradle.kts` içine, secret'lar varsa kullanan bir `signingConfig` ekle; workflow'u `assembleRelease` üretecek ve imzalayacak şekilde güncelle.
3. Workflow sonunda APK'yı GitHub Release olarak yayınla (`gh release create` ya da softprops/action-gh-release; "latest"), böylece telefondan release sayfasını açıp APK'yı doğrudan indirebileyim. Depo özel olduğu için indirme linkinin telefonda nasıl açılacağını (GitHub mobil uygulaması / tarayıcıda giriş) bana anlat.

## Görev 4: Kod incelemesi (derlenmeden yazıldı, dikkatli ol)
Şunları gerçekten kontrol et ve gerekirse düzelt:
- VolumeService: Android 12–14'te önplan servisi başlatma kısıtları, `specialUse` türü, bildirim kanalı, `START_STICKY` ile yeniden başlama, `onTaskRemoved` sonrası devamlılık.
- Ses kilidi: ses tuşuna basılınca geri çekme gecikmesi/titremesi; Bluetooth/kulaklık (mutlak ses) durumları; sesin 0'a kilitlenmesi senaryosu.
- LoudnessEnhancer(0) genel efekti çalışmazsa uygulama bazlı yedek yolu. Durum metni (`Store.boostStatus`) doğru mu?
- Çocuk modunda yükseltmenin KESİNLİKLE olmadığını (Store + servis + arayüz) doğrula.
- PIN/desen: hatalı deneme sınırı, kilidin arka plana gidince yeniden devreye girmesi, çocuk modundayken ebeveyn ayarlarına ulaşılamaması.
- Pil/OEM kısıtları için kullanıcıya gösterilecek yönergeler yeterli mi; gerekiyorsa uygulamaya cihaz markasına göre (Samsung/Xiaomi/Huawei) kısa yönerge ekranı ekle.
- Mümkünse birim testleri: `Store` mantığı (sınırlar, hash), seviye→ses/ kazanç hesabı (servisten saf fonksiyona çıkar).

## Görev 5: Teslim
1. Yeşil derlemeden sonra son APK'nın indirme yolunu ve kurulum adımlarını (Türkçe, madde madde) ver.
2. Telefonda yapacağım kısa test listesi hazırla: yükseltme çalışıyor mu (YouTube ile), ses kilidi ses tuşuna direniyor mu, yeniden başlatınca devam ediyor mu, çocuk modu kilidi ve %100 üstüne çıkamama, Normal↔Özel geçişi, hızlı ayar kutucuğu.
3. Test sonuçlarımı bana bildirdiğimde hataları düzeltmeye hazır ol.

Kurallar: gizli bilgileri commit etme; her değişiklikten sonra derlemenin yeşil olduğunu doğrula; yapmadan önce silme/geri alınamaz işlem için sor.
