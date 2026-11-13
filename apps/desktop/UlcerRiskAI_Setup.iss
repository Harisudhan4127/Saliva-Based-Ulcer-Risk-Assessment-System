; ─── UlcerRiskAI_Setup.iss ──────────────────────────────────────────────────
; Inno Setup 6.x script
; Compile with: ISCC.exe UlcerRiskAI_Setup.iss
; Output:       Output\UlcerRiskAI_Setup_v2.0.0.exe
; ─────────────────────────────────────────────────────────────────────────────

#define AppName      "Ulcer Risk AI"
#define AppVersion   "2.0.0"
#define AppPublisher "Harisudhan"
#define AppPublisher "Harisudhan"
#define AppPublisher "Harisudhan"
#define AppURL       "https://github.com/Harisudhan4127/Saliva-Based-Ulcer-Risk-Assessment-System"
#define AppExeName   "UlcerRiskAI.exe"
; Path to the PyInstaller one-folder build:
#define BuildDir     "dist\UlcerRiskAI"

[Setup]
; ── Identification ─────────────────────────────────────────────────────────
AppId={{A7F3E2B1-4C9D-4A2F-8E6B-3D1F5C7A9E2B}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} v{#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}/issues
AppUpdatesURL={#AppURL}/releases

; ── Installer behaviour ────────────────────────────────────────────────────
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
AllowNoIcons=yes
; Require admin only if needed; uncomment for per-machine install:
; PrivilegesRequired=admin
PrivilegesRequired=lowest          
; per-user install (no UAC prompt needed)
PrivilegesRequiredOverridesAllowed=dialog

; ── Output ────────────────────────────────────────────────────────────────
OutputDir=Output
OutputBaseFilename=UlcerRiskAI_Setup_v{#AppVersion}
SetupIconFile=assets\app.ico
UninstallDisplayIcon={app}\{#AppExeName}

; ── Compression (LZMA solid = best ratio) ─────────────────────────────────
Compression=lzma2/ultra64
SolidCompression=yes
LZMAUseSeparateProcess=yes
LZMANumBlockThreads=4

; ── UI ────────────────────────────────────────────────────────────────────
WizardStyle=modern
WizardSizePercent=110
DisableProgramGroupPage=yes

; ── Misc ──────────────────────────────────────────────────────────────────
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=yes
RestartApplications=no
MinVersion=10.0                    
; Windows 10+

; ── Signing (optional — uncomment and fill if you have a code-signing cert) ─
; SignTool=signtool sign /fd SHA256 /tr http://timestamp.sectigo.com /td SHA256 /f "cert.pfx" /p "password" $f

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon";    Description: "{cm:CreateDesktopIcon}";    GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "startupicon";    Description: "Launch {#AppName} on Windows startup"; GroupDescription: "Startup:"; Flags: unchecked

[Files]
; ── Main application (entire PyInstaller output folder) ───────────────────
Source: "{#BuildDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}";         Filename: "{app}\{#AppExeName}"
Name: "{group}\Uninstall {#AppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}";   Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Registry]
; Auto-start at Windows login (optional task)
Root: HKCU; Subkey: "SOFTWARE\Microsoft\Windows\CurrentVersion\Run"; \
  ValueType: string; ValueName: "{#AppName}"; \
  ValueData: """{app}\{#AppExeName}"""; \
  Flags: uninsdeletevalue; Tasks: startupicon

[Run]
; Offer to launch after install
Filename: "{app}\{#AppExeName}"; \
  Description: "{cm:LaunchProgram,{#StringChange(AppName, '&', '&&')}}"; \
  Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Clean up the user-data folder on uninstall (optional — remove if unwanted)
; Type: filesandordirs; Name: "{app}\User_data"

[Code]
// ── Detect and close a running instance before updating ────────────────────
function InitializeSetup(): Boolean;
var
  ResultCode: Integer;
begin
  Result := True;
  // If the app is already running, ask user to close it
  if CheckForMutexes('{#AppName}_SingleInstance') then
  begin
    if MsgBox('Ulcer Risk AI is currently running.' + #13#10 +
              'Please close it before installing the update.',
              mbInformation, MB_OKCANCEL) = IDCANCEL then
      Result := False;
  end;
end;
