@echo off
setlocal

where java >nul 2>nul
if errorlevel 1 (
  echo Java 17 ou superior nao foi encontrado no PATH.
  exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
  echo Apache Maven nao foi encontrado no PATH.
  exit /b 1
)

call mvn clean package
if errorlevel 1 exit /b 1

if exist dist rmdir /s /q dist

jpackage ^
  --type app-image ^
  --name "Assinador ICP Brasil" ^
  --app-version 1.0.0 ^
  --vendor "Privacy Tools" ^
  --input target ^
  --main-jar assinador-icp-brasil-1.0.0.jar ^
  --main-class br.com.privacytools.assinador.App ^
  --dest dist ^
  --java-options "-Dfile.encoding=UTF-8"

if errorlevel 1 exit /b 1

echo.
echo Aplicativo gerado em:
echo dist\Assinador ICP Brasil\Assinador ICP Brasil.exe
endlocal
