Unicode true
!ifndef APP_VERSION
  !error "APP_VERSION must be supplied by build.ps1 from the project Version"
!endif
Name "咖啡桌宠"
OutFile "${PROJECT_ROOT}\release\CoffeeDesktop-Setup-win-x64.exe"
InstallDir "$LOCALAPPDATA\Programs\CoffeeDesktop"
RequestExecutionLevel user
SetCompressor /SOLID lzma
SetDatablockOptimize on
ShowInstDetails show
ShowUninstDetails show
VIProductVersion "${APP_VERSION_MAJOR}.${APP_VERSION_MINOR}.${APP_VERSION_PATCH}.0"
VIAddVersionKey "ProductName" "咖啡桌宠"
VIAddVersionKey "FileDescription" "咖啡桌宠"
VIAddVersionKey "LegalCopyright" "Copyright (c) Coffee Desktop"
VIAddVersionKey "ProductVersion" "${APP_VERSION}"
VIAddVersionKey "FileVersion" "${APP_VERSION}"
!include "${PROJECT_ROOT}\installer\generated-files.nsh"

Page instfiles
UninstPage uninstConfirm
UninstPage instfiles

Section "Install"
  SetOutPath "$INSTDIR"
  File /r "${PUBLISH_DIR}\*.*"
  CreateDirectory "$SMPROGRAMS\咖啡桌宠"
  CreateShortcut "$SMPROGRAMS\咖啡桌宠\咖啡.lnk" "$INSTDIR\CoffeeDesktop.exe"
  CreateShortcut "$SMPROGRAMS\咖啡桌宠\卸载咖啡.lnk" "$INSTDIR\Uninstall.exe"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\CoffeeDesktop" "DisplayName" "咖啡桌宠"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\CoffeeDesktop" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\CoffeeDesktop" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\CoffeeDesktop" "DisplayVersion" "${APP_VERSION}"
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\CoffeeDesktop" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\CoffeeDesktop" "NoRepair" 1
  ExecShell "open" "$INSTDIR\CoffeeDesktop.exe"
SectionEnd

Section "Uninstall"
  IfFileExists "$INSTDIR\CoffeeDesktop.exe" 0 +2
    ExecWait '"$INSTDIR\CoffeeDesktop.exe" --uninstall-cleanup'
  !insertmacro RemoveInstalledFiles
  Delete "$INSTDIR\Uninstall.exe"
  Delete "$SMPROGRAMS\咖啡桌宠\咖啡.lnk"
  Delete "$SMPROGRAMS\咖啡桌宠\卸载咖啡.lnk"
  RMDir "$SMPROGRAMS\咖啡桌宠"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\CoffeeDesktop"
  RMDir "$INSTDIR"
SectionEnd
