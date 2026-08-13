# VaultX - Secure Encrypted File Vault

VaultX is a Java desktop application for securely encrypting and managing your personal files. Store files, images, videos, documents, passwords, and other sensitive information in a protected vault with a modern UI.

## 🛠 Latest Enhancements

### 1. Password Strength Validation
- Real-time strength scoring as you type
- Weak/Fair/Strong indicators with color-coded feedback
- Encourages passwords with mixed character types (uppercase, lowercase, digits, symbols)

### 2. Cute Cat Progress Dialog
- Animated ASCII cat during indeterminate operations
- 6-frame animation that plays while operations complete
- Cat resets when progress becomes determinate
- Appears in the progress dialog during file import/export operations

### 3. Folder Protection
- **Hidden + System + Read-Only attributes** via Windows `attrib` command
- **NTFS ACL deny permissions** to prevent casual deletion
- Runs with Java `Runtime.exec()` or bundled Rust DLL for enhanced protection
- Automatically applied when creating or opening a vault
- Can be removed via the app or by running as Administrator

## 📦 Features

- **Secure Encryption**: AES-256-GCM with PBKDF2 key derivation
- **Multiple File Types**: Images, PDFs, Text, Audio, Video playback built-in
- **Vault Management**: Create, open, lock, unlock vaults
- **File Operations**: Import, export, rename, move, delete folders and files
- **Search**: Filter vault contents in real-time
- **Theming**: FlatLaf modern UI with customizable fonts and themes
- **Media Viewers**: Built-in image, PDF, text, and media (VLC) viewers
- **Cross-platform**: Works on Windows, macOS, Linux (with Java 21+)

## 🔒 Privacy & Security

- All data encrypted locally with your master password
- No cloud backend - your vault stays on your computer
- Password-derived keys using PBKDF2-HMAC-SHA256
- AES-256-GCM for file encryption
- Secure zeroing of password arrays

## 🚀 Quick Start

1. **Download** and run VaultX
2. **Create a new vault** - choose a location and master password
3. **Set a strong password** - use the strength meter guidance
4. **Start adding files** - import files into your vault
5. **Explore** - use viewers to preview files, or export when needed

## 🛠 Technical Details

- **Language**: Java 21+ with Maven
- **GUI**: Swing with FlatLaf modern theming
- **Encryption**: BouncyCastle cryptography library
- **PDF Viewing**: Apache PDFBox
- **Media**: VLCJ (VLC media player library)
- **Syntax Highlighting**: RSyntaxTextArea
- **Build**: Maven with native Rust DLL integration (optional)

## 📦 Building from Source

```bash
# Clone the repository
git clone https://github.com/HabbashX/VaultX.git
cd VaultX

# Build with Maven
mvn clean package

# Run the application
mvn javafx:run
```

## 📄 License

VaultX is licensed under the **MIT License**. See the LICENSE file for details.

---

*VaultX - Your data, encrypted. Your privacy, protected.*