# ZapBridge

PC ↔ Android, Wi-Fi orqali tez fayl uzatish. (Keyingi bosqichlar: telefon = kamera, telefon = mikrofon.)

## PC
`ZapBridge.exe` ni ishga tushiring — oynada PIN chiqadi. Yoki manba koddan:
```powershell
python pc/app.py
```
.exe'ni o'zingiz yig'ish: `pip install pyinstaller` → `python -m PyInstaller --onefile --noconsole --name ZapBridge pc/app.py`
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

## Telefon = PC web-kamerasi
1. PC'ga bir marta [OBS Studio](https://obsproject.com) o'rnating (virtual kamera driveri uchun) va `pip install pyvirtualcam numpy pillow`.
2. Ilovada **📷 Orqa kamera** yoki **🤳 Old kamera** ni bosing (qayta bosish — to'xtatadi).
3. Zoom / Discord / Chrome'da kamera sifatida **OBS Virtual Camera** ni tanlang. OBS'ning o'zini ochish shart emas.

Firewall: TCP 8768 ham ochiq bo'lsin.
