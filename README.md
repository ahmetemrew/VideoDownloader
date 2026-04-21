# Video Downloader

Birden fazla platformu destekleyen, açık kaynaklı Android video indirme uygulaması.

## Uyarı

**Bu proje yalnızca eğitsel amaçlarla sunulmaktadır.**

Bu uygulamanın geliştiricileri şu konulardan **sorumlu değildir**:
- Kullanıcıların bu yazılımı nasıl kullandığı
- Telif hakkı ihlalleri veya hizmet koşulu ihlalleri
- Bu yazılımın kullanımından doğabilecek hukuki sonuçlar
- İndirilen içerikler ve bu içeriklerin kullanım şekli

**Kullanıcılar, uygulamayı kullanırken şunlara uygun hareket etmekten tamamen kendileri sorumludur:**
- Yerel yasalar ve yönetmelikler
- İçerik indirilen platformların hizmet koşulları
- Telif hakkı ve fikri mülkiyet kuralları

**Bu yazılımı kullanarak, yalnızca indirmeye hukuken hakkınız olan içerikleri indireceğinizi kabul etmiş olursunuz.**

---

## Özellikler

- **Akıllı bağlantı algılama** - Instagram, TikTok, Twitter/X, YouTube, Facebook ve Pinterest bağlantılarını tanır
- **Kuyruk sistemi** - Aynı anda birden fazla videoyu indirebilir, en fazla 2 indirmeyi eşzamanlı çalıştırır
- **Bildirimler** - İlerleme, tamamlanma ve hata durumlarını bildirir
- **Gizlilik odaklı** - Reklam, takip ve veri toplama içermez
- **Çevrimdışı işleme** - İşlerin tamamı cihaz üzerinde gerçekleşir

## Teknoloji Yığını

- **Dil:** Kotlin
- **Mimari:** MVVM
- **Veritabanı:** Room
- **Ağ katmanı:** OkHttp + Jsoup
- **Arayüz:** Material 3, Navigation Component
- **Asenkron işler:** Kotlin Coroutines

## Derleme

```bash
# Depoyu klonlayın
git clone https://github.com/ahmetemrew/VideoDownloader.git

# Android Studio ile açın veya komut satırından debug derlemesi alın
./gradlew assembleDebug
```

Arm64 release APK üretmek için:

```bash
./gradlew assembleRelease -PappAbiFilters=arm64-v8a
```

## Lisans

Bu proje MIT Lisansı ile lisanslanmıştır. Ayrıntılar için [LICENSE](LICENSE) dosyasına bakabilirsiniz.

## Katkı

Katkılar memnuniyetle karşılanır. İsterseniz bir Pull Request gönderebilirsiniz.

## Hukuki Not

Bu uygulama telif hakkıyla korunan herhangi bir içeriği barındırmaz. Uygulama yalnızca herkese açık içeriklerin indirilebilmesi için bir araç sunar. İndirilen içeriklerin nasıl kullanıldığına dair tüm sorumluluk kullanıcıya aittir.

**Lütfen sorumlu ve yasal şekilde kullanın.**
