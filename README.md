<p align="center">
  <img src="src/main/resources/images/vaultx_logo.png" width="1000" alt="Logo">
</p>

---
# VaultX - Secure Encrypted File Vault

VaultX is a cross-platform desktop application for securely encrypting and managing your personal files. Store files, images, videos, documents, and other sensitive information inside a protected vault that is encrypted locally with your master password. VaultX ships with built-in viewers for images, PDFs, text, audio, and video, plus folder protection for vault directories on Windows.

- **Platform**: Windows, macOS, Linux (Java 25+)
- **License**: [MIT](LICENSE)
- **Latest release**: [v1.0.2](https://github.com/HabbashX/VaultX/releases/tag/v1.0.2)

---

## Features

### Core
- **Secure encryption** - every file is encrypted with AES-256-GCM before it touches disk.
- **Vault management** - create, open, lock, unlock, rename, and delete vaults.
- **Master password security** - a single master password protects the whole vault.
- **No cloud backend** - your vault stays on your computer; nothing is uploaded anywhere.
- **Offline-first** - full functionality with no internet connection.
- **Backup** - one-click and scheduled backups to a folder of your choice, with the last backup time remembered per vault.

### File operations
- Import files or entire folders (encrypted on import), including drag-and-drop from your operating system.
- Export decrypted copies back to disk.
- Create, rename, and move folders inside the vault.
- Rename, move, or drag items between folders inside the vault.
- **Trash** - deleted items go to a trash that can be restored, emptied, or auto-purged after a configurable retention period.
- **Secure delete** - vault blobs are overwritten with random data before removal.
- Real-time search filtering across vault contents, including recursive whole-vault search with filters for file type, size, and modification date.

### Built-in viewers
- **Image viewer** - fast zoom (Ctrl+scroll), zoom-to-cursor, drag-to-pan, fit-to-window, 100% view, and export copy.
- **PDF viewer** - page navigation, zoom presets (50%-150%), and thumbnail rendering.
- **Text editor** - full syntax highlighting for dozens of languages, themable.
- **Media player** - VLC-backed playback with seek, volume, mute, fullscreen, and keyboard shortcuts.
- **RAM-only preview** - images and text under 8 MB are previewed entirely in memory and never written to disk.

### Usability
- Modern FlatLaf UI with multiple themes, selectable fonts, and adjustable sizes.
- Real-time **password strength meter** (Weak / Fair / Strong) when creating a vault.
- Animated progress dialog (with a cat!) during long operations.
- Recent vaults list and last-used directory remembered.

### Windows folder protection
- Hides the vault directory and marks it with **Hidden + System + Read-Only** attributes via the `attrib` command.
- Applies **NTFS ACL deny permissions** to prevent casual deletion.
- Optionally uses a bundled **Rust DLL** (`vaultx_folder_protector.dll`) for enhanced protection.
- Applied automatically when creating or opening a vault; can be turned off in Settings and removed from the app or by running as Administrator.

### Brute-force protection
- **Lockout** - after wrong password attempts the app enforces progressively longer delays (squared, capped at 5 minutes).
- **Self-destruct** - optionally erases the vault entirely after a configurable number of failed attempts (off by default).

---

## Security model

| Aspect | Specification |
| --- | --- |
| File encryption | AES-256-GCM (128-bit authentication tag, 12-byte nonce) |
| Key derivation | PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte random salt |
| Key management | Master password derives a master key; data keys derived via HKDF |
| Streaming | 8 KiB buffered streaming encryption for memory-efficient large files |
| Hygiene | Password character arrays are zeroed after use |
| Storage | Vault configuration kept in a `.vaultx` directory next to your data |

**Design notes**
- Every file gets a unique random nonce/salt, so identical plaintext files produce different ciphertexts.
- There is deliberately no backdoor or recovery mechanism - if you lose the master password, the vault cannot be opened.

---

## System requirements

### Installer (Windows)
- Windows 10 or 11 (64-bit).
- No Java installation required - the installer bundles the Java 25 runtime.
- Free **VLC 3.x (64-bit)** recommended for audio/video playback.

### Running from source
- JDK 25+ (Maven build uses `maven.compiler.release 25`).
- Maven 3.9+.
- macOS / Linux supported when building from source.

---

## Installation

### Windows (recommended)
1. Download the latest installer from the [Releases page](https://github.com/HabbashX/VaultX/releases) - e.g. `VaultX-1.0.2.exe`.
2. Run the installer and follow the prompts (choose the install directory and start-menu shortcut).
3. Launch **VaultX** from the Start Menu or desktop shortcut.

> The installer is currently unsigned, so Windows SmartScreen may show a warning. Choose "More info" -> "Run anyway" if you trust the source.

> Media playback requires VLC. If VLC is not installed, the app shows a **Download VLC** button that opens the official download page. Everything else (encryption, images, PDFs, text) works without VLC.

### macOS / Linux (from source)
See [Building from source](#building-from-source).

---

## Quick start

1. Open VaultX.
2. **Create a New Vault** - choose a name, a location on disk, and a strong master password.
3. Use the **Open Vault** tab to unlock an existing vault with its master password.
4. **Import Files / Import Folder** to encrypt and add content.
5. Browse, preview, search, rename, or export items as needed.

---

## Supported file types

| Category | Extensions |
| --- | --- |
| Images | jpg, jpeg, png, gif, bmp, webp, ico, tif, tiff |
| Audio | mp3, m4a, m4b, aac, wav, flac, ogg, opus, wma, aiff |
| Video | mp4, m4v, mkv, avi, mov, wmv, flv, webm, mpeg, mpg, ts, mts, 3gp |
| Documents | pdf |
| Text / code | txt, md, json, xml, html, css, scss, js, ts, tsx, java, kt, py, rb, go, rs, c, cpp, cs, sql, sh, bat, ps1, yaml, yml, toml, csv, log, and more |

Any other file type can still be stored and exported; it simply has no built-in preview.

---

## Building from source

Requirements: JDK 25+, Maven 3.9+.

```bash
# Clone the repository
git clone https://github.com/HabbashX/VaultX.git
cd VaultX

# Compile, run tests, and build the jar
mvn clean package

# Run the application (Windows)
run.bat

# Run the application manually (Windows)
java -cp "target\vaultx.jar;target\lib\*" com.habbashx.vaultx.App

# Run the application manually (macOS / Linux)
java -cp "target/vaultx.jar:target/lib/*" com.habbashx.vaultx.App
```

### Building the Windows installer (EXE)

```bash
mvn clean package

# Stage the jar and its dependencies for jpackage
mkdir target/app-input
cp target/vaultx.jar target/app-input/
cp target/lib/* target/app-input/

# Create a standalone installer that bundles the Java runtime (needs JDK 25 and WiX on Windows).
# IMPORTANT: jpackage must be the one from the SAME JDK that compiled the code (JDK 25).
# Using an older jpackage (e.g. JDK 22 from PATH) bundles a runtime that cannot load the app classes.
jpackage \
  --type exe \
  --name VaultX \
  --app-version 1.0.2 \
  --vendor "HabbashX" \
  --input target/app-input \
  --main-jar vaultx.jar \
  --main-class com.habbashx.vaultx.App \
  --icon exe/vaultx.ico \
  --win-dir-chooser \
  --win-menu \
  --win-shortcut \
  --dest exe
```

The resulting `exe/VaultX-1.0.2.exe` is a self-contained installer - users do **not** need Java installed.

---

## Project structure

```
src/main/java/com/habbashx/vaultx/
├── App.java                    # application entry point
├── core/
│   ├── CryptoUtils.java        # AES-256-GCM encryption, PBKDF2 key derivation
│   ├── VaultManager.java       # vault lifecycle and file operations
│   ├── VaultItem.java          # vault item model
│   ├── Manifest.java           # vault metadata persistence (Gson)
│   ├── FileTypes.java          # file category detection and MIME types
│   ├── Fonts.java              # bundled font registration
│   ├── TempFiles.java          # encrypted temp-file staging and cleanup
│   └── Progress.java           # progress reporting interface
├── ui/
│   ├── LoginDialog.java        # create / open vault screen
│   ├── MainFrame.java          # main window with toolbar and status bar
│   ├── VaultBrowser.java       # vault contents list
│   ├── SettingsDialog.java     # theme / font preferences
│   ├── AppSettings.java        # persisted preferences
│   ├── Branding.java           # logo and icon loading
│   └── viewer/
│       ├── ImageViewer.java    # high-performance image viewer
│       ├── PdfViewer.java      # PDF viewer (PDFBox)
│       ├── TextViewer.java     # syntax-highlighting editor
│       └── MediaPlayerFrame.java # VLC-based audio/video player

src/main/resources/
├── images/                     # logo and application icons
├── fonts/                      # bundled JetBrains Mono fonts
└── native/                     # optional Rust folder-protector DLL
```

---

## Technology stack

| Area | Technology |
| --- | --- |
| Language | Java 25 |
| Build tool | Maven 3.9+ |
| GUI | Swing with FlatLaf 3.6 themes |
| Encryption | AES-GCM / PBKDF2 (JDK cryptography), JNA 5.16 |
| PDF rendering | Apache PDFBox 2.0 |
| Media playback | vlcj 4.11 (VLC 3.x) |
| Syntax highlighting | RSyntaxTextArea 3.6 |
| Persistence | Gson 2.11 |
| Testing | JUnit 5 |
| Native folder protection | Optional Rust DLL |

---

## License

VaultX is released under the **MIT License**. See the [LICENSE](LICENSE) file for details.

---

*VaultX - your data, encrypted. Your privacy, protected.*
