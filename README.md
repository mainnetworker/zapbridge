# ZapBridge

PC ↔ Android, Wi-Fi orqali tez fayl uzatish. (Keyingi bosqichlar: telefon = kamera, telefon = mikrofon.)

## PC
```powershell
python pc/server.py
```
- Ulashilgan papka: `~/ZapBridge` (PC'dan telefonga yuboriladigan fayllarni shu yerga qo'ying; telefondan kelganlar ham shu yerga tushadi).
- Ekranda chiqqan 6 xonali PIN'ni telefonga kiriting.
- Windows Firewall so'rasa — **Private network** uchun ruxsat bering (TCP 8765, UDP 8766).
- Tekshiruv: `python pc/test_server.py`

## Android
1. Android Studio'da `android/` papkasini oching, telefonni USB bilan ulab ▶ Run bosing.
2. PIN'ni kiriting → **PC'ni topish**.
3. **PC'ga fayl yuborish**, yoki istalgan ilovada *Ulashish → ZapBridge*.
4. Ro'yxatdagi faylni bossangiz — telefonning *Downloads* papkasiga tushadi.

## Telefon = PC mikrofoni
1. PC'ga bir marta [VB-Audio Virtual Cable](https://vb-audio.com/Cable/) o'rnating va `pip install sounddevice`.
2. Ilovada **🎙 Telefon = PC mikrofoni** ni bosing (ekran o'chsa ham ishlayveradi).
3. Zoom / Discord / OBS'da mikrofon sifatida **CABLE Output** ni tanlang.

Firewall: TCP 8767 ham ochiq bo'lsin.
