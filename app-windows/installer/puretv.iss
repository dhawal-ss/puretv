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
CloseApplications=force
CloseApplicationsFilter=PureTV for Twitch.exe
RestartApplications=no

[Files]
; Never delete the currently runnable app before its replacement is complete.
; Copy the full payload first while preserving the old launcher config, then
; replace the config LAST as the commit marker. If setup is interrupted, the old
; config and its referenced jars remain runnable; a completed install points the
; launcher at the newly copied content-hashed jar set.
;
; BeforeInstall runs once per file on a wildcard entry, so every file is checked
; for a lock right before Setup replaces it (see ReleaseFileFromPureTV).
Source: "..\build\compose\binaries\main-release\app\PureTV for Twitch\*"; DestDir: "{app}"; Excludes: "app\PureTV for Twitch.cfg"; Flags: recursesubdirs createallsubdirs ignoreversion; BeforeInstall: ReleaseFileFromPureTV
Source: "..\build\compose\binaries\main-release\app\PureTV for Twitch\app\PureTV for Twitch.cfg"; DestDir: "{app}\app"; Flags: ignoreversion; BeforeInstall: ReleaseFileFromPureTV

[Icons]
Name: "{group}\PureTV"; Filename: "{app}\{#MyAppExe}"
Name: "{autodesktop}\PureTV"; Filename: "{app}\{#MyAppExe}"

[UninstallDelete]
; Written after install (see BuildVlcPluginCache), so Setup does not know it.
Type: files; Name: "{app}\app\resources\vlc\plugins\plugins.dat"

[Registry]
; "Start PureTV with Windows" (Settings > Notifications) writes this value itself;
; the installer never creates it, only removes it on uninstall so nothing is left
; trying to start a PureTV that is gone.
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueName: "PureTV"; ValueType: none; Flags: uninsdeletevalue

[Run]
; postinstall  -> offered as a (checked) final-page action after a normal install
; nowait       -> the installer closes without waiting for the app
; skipifsilent -> do NOT auto-launch during a silent in-app update; the updater
;                 handles its own relaunch
Filename: "{app}\{#MyAppExe}"; Description: "Launch PureTV now"; Flags: nowait postinstall skipifsilent

[Code]
// PrepareToInstall runs BEFORE the file phase (before the Files
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

// PrepareToInstall only covers PureTV processes that exist BEFORE the copy. The
// in-app update runs silently, so to the user the app just closes, and reopening
// it a few seconds later lands mid-copy: the old launcher loads its jars, Setup
// hits "file in use" on the next one it replaces, gives up after 4 retries and
// rolls the whole update back. The user ends up on the old version, still being
// offered the update, every time (v1.11.2 -> v1.12.0).
//
// So before each file is replaced, check whether PureTV is running at all, and
// whether anything holds that file open. If either, close PureTV again and wait.
// Checking the file alone is not enough: a PureTV that is still starting opens
// its jars one by one, so a jar that is free at the check can be locked by the
// time Setup deletes it (seen with jna-jpms on a real 1.11.2 -> 1.12.1 run).
// Anything other than PureTV holding a file still fails the normal way after the
// cap, rather than hanging Setup.
const
  GENERIC_READ = $80000000;
  GENERIC_WRITE = $40000000;
  FILE_SHARE_ALL = 7;
  OPEN_EXISTING = 3;
  ERROR_SHARING_VIOLATION = 32;
  INVALID_HANDLE_VALUE = -1;
  LOCK_WAIT_CAP_MS = 10000;

function CreateFile(lpFileName: String; dwDesiredAccess, dwShareMode, lpSecurityAttributes,
  dwCreationDisposition, dwFlagsAndAttributes, hTemplateFile: Cardinal): THandle;
  external 'CreateFileW@kernel32.dll stdcall';
function CloseHandle(hObject: THandle): BOOL;
  external 'CloseHandle@kernel32.dll stdcall';

// Opens the file with the given access/sharing and closes it again; True only if
// that fails with a sharing violation. Nothing is read or written. A missing file
// or any other error counts as not in use.
function OpenFailsWithSharingViolation(const FileName: String; Access, Share: Cardinal): Boolean;
var
  Handle: THandle;
begin
  Result := False;
  if not FileExists(FileName) then Exit;
  Handle := CreateFile(FileName, Access, Share, 0, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, 0);
  if Handle = THandle(INVALID_HANDLE_VALUE) then
    Result := DLLGetLastError = ERROR_SHARING_VIOLATION
  else
    CloseHandle(Handle);
end;

// True when another process has the file open: an exclusive (share mode 0) open
// fails with a sharing violation.
function IsFileLocked(const FileName: String): Boolean;
begin
  Result := OpenFailsWithSharingViolation(FileName, GENERIC_READ, 0);
end;

// True while any PureTV launched from this install is running. Windows refuses
// write access to a running executable even when every sharing mode is allowed,
// while a read-only exclusive open of it succeeds, so this probes for write.
function IsPureTVRunning(): Boolean;
begin
  Result := OpenFailsWithSharingViolation(ExpandConstant('{app}\{#MyAppExe}'), GENERIC_WRITE, FILE_SHARE_ALL);
end;

function PureTVInTheWay(const Dest: String): Boolean;
begin
  Result := IsPureTVRunning() or IsFileLocked(Dest);
end;

// Every file this Setup installs (lowercase), so that once the install has succeeded
// the VLC plugins an older version shipped and this one does not can be removed
// (see PruneStaleVlcPlugins).
var
  InstalledFiles: TStringList;

procedure NoteInstalled(const Dest: String);
begin
  if InstalledFiles = nil then
    InstalledFiles := TStringList.Create;
  InstalledFiles.Add(Lowercase(Dest));
end;

procedure ReleaseFileFromPureTV();
var
  Dest: String;
  ResultCode, Waited: Integer;
begin
  Dest := ExpandConstant(CurrentFileName);
  NoteInstalled(Dest);
  if not PureTVInTheWay(Dest) then Exit;
  Log('PureTV running or file in use, closing PureTV before replacing: ' + Dest);
  Waited := 0;
  repeat
    Exec('taskkill.exe', '/F /IM "PureTV for Twitch.exe"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
    Sleep(500);
    Waited := Waited + 500;
  until (not PureTVInTheWay(Dest)) or (Waited >= LOCK_WAIT_CAP_MS);
  if PureTVInTheWay(Dest) then
    Log('Still in use after ' + IntToStr(Waited) + ' ms: ' + Dest)
  else
    Log('Released after ' + IntToStr(Waited) + ' ms');
end;

// Removes every file under Dir that this Setup did not install, and the folders left
// empty. Setup only adds and replaces files, and libVLC opens every DLL in its plugins
// folder when it starts: 52 plugins left by an older install were opened (and failed)
// on every start of the app. Their old plugin cache goes too.
procedure PruneFolder(const Dir: String);
var
  FindRec: TFindRec;
  Path: String;
begin
  if not FindFirst(Dir + '\*', FindRec) then Exit;
  try
    repeat
      if (FindRec.Name <> '.') and (FindRec.Name <> '..') then
      begin
        Path := Dir + '\' + FindRec.Name;
        if FindRec.Attributes and FILE_ATTRIBUTE_DIRECTORY <> 0 then
        begin
          PruneFolder(Path);
          RemoveDir(Path);
        end
        else if InstalledFiles.IndexOf(Lowercase(Path)) < 0 then
        begin
          if DeleteFile(Path) then
            Log('Removed a file this version does not ship: ' + Path)
          else
            Log('Could not remove: ' + Path);
        end;
      end;
    until not FindNext(FindRec);
  finally
    FindClose(FindRec);
  end;
end;

// Only after a successful install (a rolled-back one never gets here, so the old app
// keeps its plugins), and only when this Setup installed plugins itself.
procedure PruneStaleVlcPlugins();
var
  Plugins: String;
  I, Shipped: Integer;
begin
  if InstalledFiles = nil then Exit;
  Plugins := ExpandConstant('{app}\app\resources\vlc\plugins');
  Shipped := 0;
  for I := 0 to InstalledFiles.Count - 1 do
    if Pos(Lowercase(Plugins) + '\', InstalledFiles[I]) = 1 then Shipped := Shipped + 1;
  if Shipped = 0 then Exit;
  PruneFolder(Plugins);
end;

// Writes libVLC's plugin cache (plugins.dat) for the files just installed. Without it
// libVLC opens all of its plugin DLLs (264) every time the app starts the player, which
// is slow right after a boot, when each one is read from disk and scanned first. With
// it, libVLC opens only what a stream uses. If this fails, the app writes the cache
// itself on its first start (NativeLibs.vlcPluginCacheArgs).
procedure BuildVlcPluginCache();
var
  Gen: String;
  ResultCode: Integer;
begin
  Gen := ExpandConstant('{app}\app\resources\vlc\vlc-cache-gen.exe');
  if not FileExists(Gen) then Exit;
  // Takes 20 to 30 s on freshly written files: say so, or the progress window looks stuck
  // at the end and people reopen PureTV mid-install (the in-app update runs /SILENT).
  WizardForm.StatusLabel.Caption := 'Preparing the video player (this can take half a minute)...';
  WizardForm.FilenameLabel.Caption := '';
  if Exec(Gen, '"' + ExpandConstant('{app}\app\resources\vlc\plugins') + '"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
    Log('vlc-cache-gen exit code ' + IntToStr(ResultCode))
  else
    Log('vlc-cache-gen did not start: ' + SysErrorMessage(ResultCode));
end;

// A successful installer must never leave a launcher without its config. Raise
// an installer failure before the updater can relaunch if that invariant is not
// satisfied. The detached updater also checks this file as a second barrier.
procedure CurStepChanged(CurStep: TSetupStep);
var
  LauncherConfig: String;
begin
  if CurStep = ssPostInstall then
  begin
    LauncherConfig := ExpandConstant('{app}\app\PureTV for Twitch.cfg');
    if not FileExists(LauncherConfig) then
      RaiseException('Installation validation failed: launcher configuration is missing.');
    PruneStaleVlcPlugins();
    BuildVlcPluginCache();
  end;
end;
