#define AppName "GameNarrator Demo Lite"
#ifndef AppVersion
#define AppVersion "2.2.44"
#endif

[Setup]
AppId={{B86615D4-5A6C-4E89-915D-572838B044A3}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher=GameNarrator
DefaultDirName={localappdata}\Programs\GameNarratorDemoLite
DefaultGroupName=GameNarrator Demo Lite
OutputDir=..\..\dist
OutputBaseFilename=GameNarrator-Demo-Lite-Setup-{#AppVersion}
Compression=lzma2/max
SolidCompression=yes
PrivilegesRequired=lowest
WizardStyle=modern
Uninstallable=yes
SetupIconFile=..\..\launcher\assets\GameNarrator.ico

[Languages]
Name: "chinesesimplified"; MessagesFile: "..\cache\ChineseSimplified.isl"

[Files]
Source: "..\staging-lite\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\GameNarrator 展示轻量版"; Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\GameNarrator-Demo-Lite.ps1"""; WorkingDir: "{app}"
Name: "{autodesktop}\GameNarrator 展示轻量版"; Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\GameNarrator-Demo-Lite.ps1"""; WorkingDir: "{app}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; Flags: checkedonce

[Run]
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""{app}\GameNarrator-Demo-Lite.ps1"""; Description: "启动 GameNarrator 展示轻量版"; Flags: nowait postinstall skipifsilent
