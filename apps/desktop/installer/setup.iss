[Setup]
AppName=Ulcer AI
AppVersion=1.0
DefaultDirName={pf}\UlcerAI
DefaultGroupName=UlcerAI
OutputDir=output
OutputBaseFilename=UlcerAI_Setup
SetupIconFile=..\assets\app.ico
Compression=lzma
SolidCompression=yes

[Files]
Source: "..\dist\main\*"; DestDir: "{app}"; Flags: recursesubdirs

[Icons]
Name: "{group}\Ulcer AI"; Filename: "{app}\main.exe"; IconFilename: "{app}\main.exe"
Name: "{commondesktop}\Ulcer AI"; Filename: "{app}\main.exe"; IconFilename: "{app}\main.exe"

[Run]
Filename: "{app}\main.exe"; Description: "Launch Ulcer AI"; Flags: nowait postinstall skipifsilent