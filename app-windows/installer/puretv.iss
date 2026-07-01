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

[InstallDelete]
; Wipe the previous app jars BEFORE copying the new ones. jpackage names every
; jar with a content hash (app-windows-<hash>.jar), so an in-place upgrade would
; otherwise LEAVE every old version's jars behind — they pile up over upgrades
; and can confuse the classpath. The dir is repopulated by [Files] below; the
; runtime/ and launcher have stable names and are overwritten, so only app\ needs this.
Type: filesandordirs; Name: "{app}\app"

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

[Code]
// PrepareToInstall runs BEFORE the file phase (before the InstallDelete and Files
// sections), so this is where we make sure no PureTV process is holding app files.
// Older in-app updaters do not reliably wait for the app to exit: its process can
// linger on background threads (the local stream proxy / VLC) and keep the app
// folder locked, so the in-place upgrade wipes that folder but fails to re-copy it,
// leaving the launcher without its .cfg so the app will not start. Force-closing the
// process here makes the upgrade succeed even when the OLD updater did not wait,
// which is what lets users on a pre-fix build update cleanly.
//
// taskkill by IMAGE NAME with NO /T on purpose: during an in-app update the script
// that launched this installer is a descendant of the very app we are killing, so a
// tree-kill would terminate the installer itself. Best-effort: if PureTV is not
// running, taskkill simply no-ops.
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
begin
  Exec('taskkill.exe', '/F /IM "PureTV for Twitch.exe"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Sleep(1500);
  Result := '';
end;
