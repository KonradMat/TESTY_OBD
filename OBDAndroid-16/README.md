# OBD2 Reader - Aplikacja Android (Kotlin + Jetpack Compose)

Aplikacja Android do odczytu danych z kostki OBD2 przez Bluetooth.
Odpowiednik Twojego projektu Python (`ObdConnection`), przepisany natywnie na Android.

---

## 📁 Struktura projektu

```
OBDAndroid/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml          ← Uprawnienia Bluetooth
│       ├── java/com/obdreader/app/
│       │   ├── bluetooth/
│       │   │   └── ObdBluetoothManager.kt  ← odpowiednik obd_connector.py
│       │   ├── obd/
│       │   │   ├── ObdCommands.kt          ← wszystkie PIDy OBD2
│       │   │   └── ObdResponseParser.kt    ← parser hex → wartości
│       │   ├── viewmodel/
│       │   │   └── ObdViewModel.kt         ← logika stanu aplikacji
│       │   └── ui/
│       │       └── MainActivity.kt         ← cały UI (Jetpack Compose)
│       └── res/values/themes.xml
├── build.gradle                            ← projekt główny
├── app/build.gradle                        ← zależności aplikacji
└── settings.gradle
```

---

## 🚀 Jak otworzyć w Android Studio

1. **Otwórz Android Studio** (min. Hedgehog 2023.1.1)
2. `File → Open` → wybierz folder `OBDAndroid`
3. Poczekaj na synchronizację Gradle (pobierze zależności)
4. Jeśli pojawi się błąd Kotlin: upewnij się że masz **Kotlin 1.9.x** (`Settings → Plugins → Kotlin`)

### Wymagania:
- Android Studio Hedgehog (2023.1.1) lub nowszy
- Kotlin 1.9.20
- minSdk 23 (Android 6.0)
- targetSdk 34 (Android 14)

---

## 📱 Jak używać aplikacji

### 1. Sparuj kostkę OBD2 z telefonem
- Włącz zapłon w samochodzie (lub ustaw na ACC)
- Wejdź w **Ustawienia → Bluetooth** na telefonie
- Znajdź kostkę (zwykle: `OBDII`, `ELM327`, `V-LINK`, `iCar Pro`)
- Sparuj (PIN: zwykle `1234` lub `0000`)

### 2. Uruchom aplikację
- Ekran główny pokaże listę sparowanych urządzeń
- Kostki OBD są automatycznie oznaczone etykietą **OBD**
- Kliknij urządzenie → aplikacja nawiąże połączenie

### 3. Odczyt danych
- **Dashboard** – najważniejsze parametry (RPM, prędkość, temperatury, paliwo)
- **Czujniki** – pełna lista wszystkich wykrytych PIDów z filtrowaniem po kategorii
- **Logi** – historia połączeń i błędów

---

## 🔄 Odpowiedniki Python → Kotlin

| Python (`obd_connector.py`) | Kotlin (`ObdBluetoothManager.kt`) |
|---|---|
| `obd.OBD("COM5")` | `BluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID)` |
| `connect(max_retries=3)` | `connect(device, maxRetries=3)` |
| `_detect_all_supported_commands()` | `detectSupportedCommands()` |
| `read_single_scan()` | `readSingleScan()` |
| `connection.query(obd.commands.RPM)` | `sendObdCommand(ObdCommand.ENGINE_RPM)` |
| Port COM (Windows) | Bluetooth SPP UUID `00001101-...` |
| `pyserial` | Android `BluetoothSocket` |
| `python-obd` | Własny parser (`ObdResponseParser.kt`) |

### Dlaczego nie port COM?
Na Androidzie nie istnieją porty COM. Zamiast tego:
- Windows: `COM5` → wirtualny port COM przez Bluetooth
- Android: **RFCOMM socket** z UUID SPP `00001101-0000-1000-8000-00805F9B34FB`

Oba podejścia wysyłają **identyczne bajty** do kostki ELM327 – różni się tylko warstwa transportowa.

---

## 📡 Obsługiwane PIDy OBD2

### Tryb 01 – dane na żywo
| Kategoria | PIDy |
|---|---|
| **Silnik** | RPM, obciążenie, temp. chłodnicy, temp. oleju, wyprzedzenie zapłonu, czas pracy |
| **Ruch** | Prędkość, pozycja przepustnicy, bezwzgl. obciążenie, pedał gazu |
| **Paliwo** | Poziom, zużycie L/h, ciśnienie, korekty mieszanki (STFT/LTFT), timing wtrysku |
| **Powietrze** | MAF, ciśnienie dolotowe, ciśnienie atmosferyczne, temp. powietrza |
| **Tlen/Lambda** | Sondy O2 bank1/bank2 sensor1/sensor2, prądy wide-range |
| **Katalizator** | Temperatury B1S1, B1S2, B2S1, B2S2 |
| **EGR/EVAP** | Polecenie EGR, błąd EGR, oczyszczanie EVAP, ciśnienie EVAP |
| **Diagnostyka** | Status MIL/DTC, dystans z MIL, napięcie modułu, standard OBD |

### Tryb 09 – informacje o pojeździe
- VIN, nazwa ECU, ID kalibracji, CVN

> **Uwaga:** Nie każdy samochód obsługuje wszystkie PIDy. Aplikacja automatycznie wykrywa które PIDy są dostępne (tak jak Twój Python robiył `_detect_all_supported_commands()`).

---

## 🐛 Rozwiązywanie problemów

### "Nie udało się połączyć po 3 próbach"
1. Sprawdź czy kostka jest sparowana w ustawieniach BT telefonu
2. Sprawdź czy zapłon jest włączony
3. Sprawdź czy inna aplikacja nie używa kostki (np. Torque)
4. Spróbuj wyłączyć/włączyć Bluetooth
5. Niektóre kostki wymagają kanału `2` zamiast `1` – jeśli nie działa, sprawdź sekcję "Fallback connection" w kodzie

### "Brak danych" dla większości PIDów
- Normalnie jeśli samochód nie obsługuje danego PID
- Sprawdź zakładkę **Czujniki → Wszystkie** – tylko wspierane mają wartości

### Uprawnienia Bluetooth na Android 12+
- Aplikacja poprosi o `BLUETOOTH_CONNECT` i `BLUETOOTH_SCAN`
- Bez tych uprawnień nie można połączyć z kostką

---

## 🔧 Dalszy rozwój (sugestie)

- **Wykres w czasie** – biblioteka `MPAndroidChart` lub `Vico`
- **Zapis danych do CSV** – odpowiednik `file_manager.py`
- **Alerty progowe** – np. alarm przy temp. chłodnicy > 100°C
- **Kody DTC** – odczyt i kasowanie kodów usterek (tryb 03/04)
- **Widżet licznika** – animowany gauge dla RPM/prędkości
- **Background service** – ciągły odczyt w tle

---

## 📄 Licencja
MIT – możesz swobodnie używać i modyfikować.
