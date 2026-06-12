; Inno Setup script for PureTV.
;
; Wraps the jpackage app image (produced by `gradle :app-windows:createReleaseDistributable`)
; into a per-user installer that, unlike a plain jpackage MSI, LAUNCHES THE APP
; automatically when the install finishes (see the [Run] section).
;
; Version is passed in from CI:  ISCC.exe /DMyAppVersion=1.2.3 puretv.iss

#ifndef MyAppVersion
  #define MyAppVersion "0.0.0"
#endif
#define MyAppName "PureTV"
#define MyAppExe "PureTV for Twitch.exe"

[Setup]
; Stable identity so future versions upgrade in place rather than stacking.
AppId={{a75efb70-c7e9-424a-99b8-b6d9a98a0799}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher=dhawal-ss
DefaultDirName={localappdata}\Programs\PureTV
DefaultGroupName=PureTV
DisableProgramGroupPage=yes
; Per-user install: no UAC prompt, mirrors the previous packaging.
PrivilegesRequired=lowest
OutputDir=..\build\installer
OutputBaseFilename=PureTV-Setup-{#MyAppVersion}
SetupIconFile=..\src\main\resources\icon.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64

[Files]
Source: "..\build\compose\binaries\main-release\app\PureTV for Twitch\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\PureTV"; Filename: "{app}\{#MyAppExe}"
Name: "{autodesktop}\PureTV"; Filename: "{app}\{#MyAppExe}"

[Run]
; postinstall  -> offered as a (checked) final-page action after a normal install
; nowait       -> the installer closes without waiting for the app
; skipifsilent -> do NOT auto-launch during a silent in-app update; the updater
;                 handles its own relaunch
Filename: "{app}\{#MyAppExe}"; Description: "Launch PureTV now"; Flags: nowait postinstall skipifsilent
