@echo off
if exist "%USERPROFILE%\.jdks\ms-21.0.12.1\bin\java.exe" set "JAVA_HOME=%USERPROFILE%\.jdks\ms-21.0.12.1"
call "%~dp0gradlew.bat" generateDLIConfig runClient
pause
