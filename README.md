# FTP Client - README

## What this is

This is a simple FTP client written in Java for my Bachelor project. It can connect to any FTP server, browse files, upload/download files, and do basic file management. It also has a basic GUI built with JavaFX.

---

## Requirements

- Java 11 or higher (tested on Java 17)
- JavaFX SDK (only needed for the GUI part)
- No other libraries needed — the FTP logic only uses standard Java

---

## How to build

### Option 1: Using IntelliJ IDEA (recommended)

1. Open IntelliJ IDEA
2. Click **File → Open** and select the `FTP_Server` folder
3. IntelliJ should automatically detect the project structure
4. If JavaFX is not set up:
   - Go to **File → Project Structure → Libraries**
   - Add the JavaFX SDK lib folder (download from https://openjfx.io if needed)
5. Click the green Run button on `gui/App.java`

### Option 2: Compile manually from terminal

First compile the FTP core classes:

```bash
javac -d out/production/FTP_Server src/ftp/*.java
```

Then compile the GUI (you need JavaFX on the classpath — replace the path with your own):

```bash
javac --module-path /path/to/javafx-sdk/lib \
      --add-modules javafx.controls \
      -cp out/production/FTP_Server \
      -d out/production/FTP_Server \
      src/gui/App.java
```

---

## How to run

### With GUI (recommended)

```bash
java --module-path /path/to/javafx-sdk/lib \
     --add-modules javafx.controls \
     -cp out/production/FTP_Server \
     gui.App
```

A login window will open. Enter the FTP server hostname, your username and password, then click **Connect & Login**.

### Public FTP server to test with

You can test with `ftp.gnu.org` using anonymous login:

- Host: `ftp.gnu.org`
- Username: `anonymous`
- Password: (anything, e.g. your email)

---

## What you can do in the GUI

After connecting, you get a two-panel view (remote on the left, local on the right):

- **Double-click a folder** → navigate into it
- **cd ..** → go up one folder
- **Download** → download the selected remote file to your current local folder
- **Upload** → upload the selected local file to the current remote folder
- **New folder** → type a name and click to create a folder on the server
- **Delete** → delete the selected remote file or folder
- **Disconnect** → sends QUIT and goes back to the login screen
- **Log ▼** → toggle the protocol log at the bottom (shows all FTP commands and responses)

---

## Project structure

```
FTP_Server/
├── src/
│   ├── ftp/
│   │   ├── FTPClient.java      ← main FTP logic (connect, login, ls, get, put, etc.)
│   │   ├── FTPResponse.java    ← parses server responses (handles multiline too)
│   │   ├── FTPException.java   ← custom exception that stores the FTP error code
│   │   └── FTPParser.java ← parses the PASV ip+port response
│   └── gui/
│       └── App.java            ← JavaFX GUI (login screen + main two-panel view)
└── out/                        ← compiled .class files
```

---

## Known limitations

- Only supports passive mode (PASV) for data connections — no active mode
- Cannot download or upload entire folders, only individual files
- The PASV IP from the server is ignored; the control connection's address is used instead (this is normal and handles NAT correctly)
