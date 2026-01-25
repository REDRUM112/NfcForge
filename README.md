## NfcForge — Android NFC Reader/Writer + History

NfcForge is a **production-style Android app** that reads NFC tags, writes NDEF records, and stores a full **transaction history**. It also lets you **save** a read payload as a reusable **“NFC card”** template and **replay** it later (arm write → tap a tag).

### Tech stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + **Material 3** (dark theme by default)
- **Architecture**: **MVVM** (`ViewModel` + `StateFlow`)
- **Persistence**: Room (history + saved cards)
- **NFC (required components)**:
  - `NfcAdapter`
  - `PendingIntent`
  - `IntentFilter`
  - `Ndef`
  - **Foreground dispatch**

### Android versions

- **minSdk**: 21
- **targetSdk**: 35
- **compileSdk**: 35

> NFC tag reading/writing is best tested on a **real NFC device**. Many emulators do not support real tag “tap” flows.

---

## Features

### NFC Read (auto on tap)

- **Tag UID** (hex)
- **Tech list** (NfcA, NfcB, IsoDep, NfcF, NfcV)
- **NDEF payload** (parsed & displayed)
- Supported NDEF record types:
  - **Text** (NFC Forum RTD_TEXT)
  - **URL** (NFC Forum RTD_URI)
  - **MIME** types
  - **External** types
  - **Android Application Records** (AAR)

### Comprehensive NFC Tag/Card Support

- **MIFARE Classic** (1K/4K): Sector/block structure reading, displays all accessible blocks
- **MIFARE Ultralight/Ultralight C**: Page-based memory reading, NDEF parsing
- **NTAG**: Full NDEF record parsing (Text, URI, MIME, External, AAR)
- **MIFARE DESFire** (EV1/EV2/EV3): Application/file enumeration, secure data detection
- **EMV Contactless Bank Cards** (Visa, Mastercard, AmEx):
  - Full EMV discovery flow (SELECT PPSE, SELECT AID, READ RECORD)
  - Parses and displays: Card number (PAN), Expiry date, Cardholder name, AID, Issuer country
  - TLV decoding for all EMV data
- **ISO 15693 (NfcV)**: Block/page-based reads, memory size and raw data display
- **FeliCa (NfcF)**: System code identification, public block reading, transit metadata

### Data Display

- **Structured NDEF records** with human-readable formatting
- **Raw binary data** views: Hex dump and ASCII representation
- **EMV card details**: Formatted card numbers, expiry dates, full JSON metadata
- **Memory layouts**: Sector/block structures for MIFARE cards

### NFC Write

- Write **Text** or **URL** NDEF records
- “Arm write” UX: tap **Arm Write** → tap a tag → write occurs
- Handles common failures:
  - **Read-only tags**
  - **Unsupported tag types** (no `Ndef` / not `NdefFormatable`)
  - **Tag too small**
  - **I/O / format errors**

### History (Transactions)

- Every NFC interaction is stored as a **transaction**:
  - **READ**: stores parsed NDEF record data, EMV card JSON, and other card data
  - **WRITE**: stores attempted record type + payload + status (success/failure)
- Transaction detail view:
  - Inspect UID/status/records
  - View full EMV card details (PAN, expiry, cardholder name, etc.)
  - View raw NDEF data (Hex, Base64, UTF-8)
  - For **READ** transactions:
    - **Replay** (for EMV/read-only cards: triggers re-read; for NDEF: arms write)
    - **Save as Card** (create a reusable template, supports EMV cards)
    - **Delete Transaction**

### Saved NFC Cards (Templates)

- Save any Text/URL record or EMV card data as a "card" template
- Reuse later:
  - **NDEF cards**: **Saved Cards → Arm Write → tap a tag**
  - **EMV/Read-only cards**: **Saved Cards → Replay → tap card again** (triggers read)
- **Continuous Replay**: Replay a saved card continuously until cancelled
- Delete saved cards

---

## Getting started

### Prerequisites

- Android Studio (latest stable recommended)
- Android SDK installed (via SDK Manager)
- If building from CLI: a JDK (Android Studio’s embedded JDK works great)

### Run in Android Studio

1. Android Studio → **File → Open…** → select the `NfcForge/` folder
2. Wait for **Gradle Sync**
3. Install Android SDK for **API 35** (SDK Manager) if prompted
4. Click **Run ▶**

### Run from the command line (Windows PowerShell)

From the `NfcForge/` folder:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_SDK_ROOT="$env:LOCALAPPDATA\Android\Sdk"
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:ANDROID_SDK_ROOT\emulator;$env:Path"

.\gradlew.bat :app:assembleDebug --no-daemon
```

#### Install & launch on a connected device/emulator

```powershell
adb devices
.\gradlew.bat :app:installDebug --no-daemon
adb shell monkey -p com.aiu.nfcforge -c android.intent.category.LAUNCHER 1
```

#### Start an emulator (example)

List AVDs:

```powershell
& "$env:ANDROID_SDK_ROOT\emulator\emulator.exe" -list-avds
```

Start one:

```powershell
& "$env:ANDROID_SDK_ROOT\emulator\emulator.exe" -avd Pixel_4 -gpu host
```

---

## NFC implementation (foreground dispatch)

NfcForge uses foreground dispatch so tag intents are delivered to the active activity while the app is in the foreground.

### Lifecycle

- `MainActivity.onCreate()`
  - obtains `NfcAdapter`
  - creates a `PendingIntent` targeting the same activity (`launchMode="singleTop"`)
  - registers `IntentFilter`s for tag discovery
  - sets a tech list prioritizing `Ndef` / `NdefFormatable`
- `MainActivity.onResume()`
  - enables foreground dispatch
- `MainActivity.onPause()`
  - disables foreground dispatch
- `MainActivity.onNewIntent(intent)`
  - forwards the intent to `NfcViewModel.onNewNfcIntent(intent)`

### Read vs write routing

`NfcViewModel` decides what to do on each tap:
- **Read** (default): `NfcReader.read(tag)`
- **Write** (when armed): `NfcWriter.write(tag, message)`

### NDEF parsing/encoding

- Parsing/encoding lives in `nfc/NdefCodec.kt`
  - Text decoding per NFC Forum Text RTD payload format
  - URI decoding per NFC Forum URI prefix table
- Writing:
  - Text: manual `NdefRecord(TNF_WELL_KNOWN, RTD_TEXT, ...)` (no deprecated APIs)
  - URL: `NdefRecord.createUri(url)`

---

## Database (Room): History & Saved Cards

Entities:
- `NfcTransactionEntity`: one row per NFC interaction (read or write attempt)
- `NfcRecordEntity`: parsed NDEF record rows linked to a transaction
- `SavedCardEntity`: reusable templates (“NFC cards”) created from read records

Repository:
- `data/NfcRepository.kt` maps NFC models → Room and exposes `Flow` streams for the UI.

---

## Project structure

- `com.aiu.nfcforge.ui`
  - `screens/`: Home, Read Result, Write, History, Transaction Detail, Saved Cards
  - `components/`: scan indicator, chips, reusable UI elements
  - `theme/`: Material 3 dark-by-default theme
- `com.aiu.nfcforge.viewmodel`
  - `NfcViewModel` + UI state/events + `NfcViewModelFactory`
- `com.aiu.nfcforge.nfc`
  - intent parsing, read/write, NDEF codec, typed failures
  - `EmvCardParser.kt`: EMV bank card parsing with full metadata extraction
  - `MifareReader.kt`: MIFARE Classic/Ultralight/NTAG/DESFire reading
  - `AdvancedNfcReader.kt`: Comprehensive NFC tag technology detection and parsing
- `com.aiu.nfcforge.data`
  - Room repository and DB (`data/db`)
- `com.aiu.nfcforge.utils`
  - hex/byte formatting helpers

---

## Permissions & manifest

`AndroidManifest.xml` includes:
- `android.permission.NFC`
- `uses-feature android.hardware.nfc` (`required=false` so the app can still install on non-NFC devices)
- `MainActivity` uses `launchMode="singleTop"` (required for `onNewIntent` delivery)

---

## Notes for contributors

- `local.properties` is **machine-specific** (Android SDK path) and should not be committed.
- If you build inside a **synced/protected folder** (e.g., cloud-synced Desktop), file permission issues may occur. This project redirects build outputs to a safe folder under your user profile via `settings.gradle.kts`.




