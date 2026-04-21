#define MyAppName "Abrechnung"
#define MyAppPublisher "Bernelius"
#define MyAppURL "https://github.com/bernelius/abrechnung"

[Setup]
AppId={{A1B2C3D4-E5F6-7890-1234-567890ABCDEF}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=no
PrivilegesRequired=admin
PrivilegesRequiredOverridesAllowed=dialog
OutputDir={#MyBuildDir}\..\..\distributions
OutputBaseFilename=abrechnung-{#MyAppVersion}-setup
SetupIconFile={#MyBuildDir}\abrechnung_dllar_logo.ico
UninstallDisplayIcon={app}\abrechnung_dllar_logo.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "german"; MessagesFile: "compiler:Languages\German.isl"

[Messages]
ConfirmUninstall=Are you sure you want to uninstall %1?

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: checkedonce

[Files]
Source: "{#MyBuildDir}\abrechnung.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#MyBuildDir}\launch.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#MyBuildDir}\abrechnung_dllar_logo.ico"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\launch.bat"; IconFilename: "{app}\abrechnung_dllar_logo.ico"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\launch.bat"; IconFilename: "{app}\abrechnung_dllar_logo.ico"; Tasks: desktopicon

[Registry]
Root: HKLM; Subkey: "Software\{#MyAppPublisher}\{#MyAppName}"; ValueType: string; ValueName: "InstallDir"; ValueData: "{app}"; Flags: uninsdeletekey
Root: HKLM; Subkey: "Software\{#MyAppPublisher}\{#MyAppName}"; ValueType: string; ValueName: "Version"; ValueData: "{#MyAppVersion}"

[Run]
Filename: "{app}\launch.bat"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Always delete logs directory
Type: filesandordirs; Name: "{localappdata}\Abrechnung\logs"

[Code]
var
  DeleteData: Boolean;

function InitializeUninstall(): Boolean;
var
  ResultCode: Integer;
begin
  DeleteData := False;
  
  // Ask about database deletion only
  ResultCode := MsgBox('Do you want to delete the database and application data?' + #13#10 + #13#10 +
                       'Location: %APPDATA%\Abrechnung\data\' + #13#10 +
                       'This contains your SQLite database file.' + #13#10 + #13#10 +
                       'Click Yes to delete, No to keep.', 
                       mbConfirmation, MB_YESNO);
  
  if ResultCode = IDYES then
    DeleteData := True;
  
  // Always return True to continue uninstall
  Result := True;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  DataDir: string;
begin
  if CurUninstallStep = usUninstall then
  begin
    DataDir := ExpandConstant('{userappdata}\Abrechnung\data');

    // Delete database and application data if user chose to
    if DeleteData then
    begin
      if DirExists(DataDir) then
      begin
        DelTree(DataDir, True, True, True);
      end;
    end;
  end;
end;
