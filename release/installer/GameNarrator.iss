#define AppName "GameNarrator"
#ifndef AppVersion
#define AppVersion "2.2.1"
#endif

[Setup]
AppId={{9F083C75-7485-4BC2-A00E-39D3039E2B73}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher=GameNarrator 开源项目
AppPublisherURL=https://github.com/longer23333/game_narrator
AppSupportURL=https://github.com/longer23333/game_narrator/issues
AppComments=本地优先的视频解析、分镜、解说与成片生成工具
VersionInfoVersion={#AppVersion}.0
VersionInfoDescription=GameNarrator 视频智能剪辑安装程序
VersionInfoProductName=GameNarrator 视频智能剪辑工作台
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DefaultDirName={localappdata}\Programs\GameNarrator
UsePreviousAppDir=yes
DefaultGroupName=GameNarrator
OutputDir=..\..\dist
OutputBaseFilename=GameNarrator-Setup-{#AppVersion}
Compression=lzma2/max
SolidCompression=no
PrivilegesRequired=lowest
WizardStyle=modern
DisableDirPage=no
DisableReadyPage=no
DisableFinishedPage=no
ShowLanguageDialog=no
SetupLogging=yes
Uninstallable=yes
CreateUninstallRegKey=yes
CloseApplications=force
RestartApplications=no
UninstallDisplayName={#AppName} {#AppVersion}
UninstallFilesDir={app}
UninstallDisplayIcon={app}\GameNarrator.exe
SetupIconFile=..\..\launcher\assets\GameNarrator.ico

[Languages]
Name: "chinesesimplified"; MessagesFile: "..\cache\ChineseSimplified.isl"

[Files]
Source: "..\staging\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Components]
Name: "cloud"; Description: "云端 AI（默认，不下载大型本地模型）"; Types: full compact custom; Flags: fixed
Name: "localai"; Description: "本地 AI（首次启动时确认并下载约 2GB 模型）"; Types: full custom

[Dirs]
Name: "{app}\data"; Permissions: users-modify
Name: "{app}\data\config"; Permissions: users-modify
Name: "{app}\data\logs"; Permissions: users-modify
Name: "{app}\data\models"; Permissions: users-modify
Name: "{app}\data\storage"; Permissions: users-modify

[Icons]
Name: "{autoprograms}\GameNarrator"; Filename: "{app}\GameNarrator.exe"
Name: "{autoprograms}\卸载 GameNarrator"; Filename: "{uninstallexe}"; IconFilename: "{app}\GameNarrator.exe"
Name: "{autodesktop}\GameNarrator"; Filename: "{app}\GameNarrator.exe"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "快捷方式："; Flags: checkedonce

[Run]
Filename: "{app}\prerequisites\MicrosoftEdgeWebview2Setup.exe"; Parameters: "/silent /install"; StatusMsg: "正在准备桌面应用运行环境..."; Flags: waituntilterminated skipifdoesntexist
Filename: "{app}\GameNarrator.exe"; Description: "启动 GameNarrator"; Flags: nowait postinstall skipifsilent


[Code]
var
  NewFolderButton: TNewButton;

procedure NewFolderButtonClick(Sender: TObject);
var
  SelectedDir: String;
begin
  SelectedDir := WizardDirValue;
  if BrowseForFolder('请选择安装文件夹；可在窗口中点击“新建文件夹”。',
    SelectedDir, True) then
    WizardForm.DirEdit.Text := SelectedDir;
end;

procedure InitializeWizard;
begin
  NewFolderButton := TNewButton.Create(WizardForm);
  NewFolderButton.Parent := WizardForm.SelectDirPage;
  NewFolderButton.Caption := '选择 / 新建文件夹...';
  NewFolderButton.Width := ScaleX(145);
  NewFolderButton.Height := WizardForm.DirBrowseButton.Height;
  NewFolderButton.Left := WizardForm.DirBrowseButton.Left -
    NewFolderButton.Width - ScaleX(8);
  NewFolderButton.Top := WizardForm.DirBrowseButton.Top;
  NewFolderButton.OnClick := @NewFolderButtonClick;
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  Mode, Config: String;
begin
  if CurStep <> ssPostInstall then
    exit;
  if FileExists(ExpandConstant('{app}\data\config\ai-settings.json')) then
    exit;
  if WizardIsComponentSelected('localai') then
    Mode := 'LOCAL'
  else
    Mode := 'CLOUD';
  ForceDirectories(ExpandConstant('{app}\data\config'));
  Config := '{"mode":"' + Mode + '","provider":"DASHSCOPE",' +
    '"apiKey":"","baseUrl":"https://dashscope.aliyuncs.com/compatible-mode/v1",' +
    '"visionModel":"qwen-vl-plus","textModel":"qwen-plus"}';
  SaveStringToFile(ExpandConstant('{app}\data\config\ai-settings.json'), Config, False);
end;
