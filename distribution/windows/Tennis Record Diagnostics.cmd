@echo off
setlocal
set "APP_HOME=%~dp0"
set "APP_HOME=%APP_HOME:~0,-1%"
"%APP_HOME%\runtime\bin\java.exe" "-Dtennis.record.appDir=%APP_HOME%" "-Dtennis.record.version=@APP_VERSION@" -Dfile.encoding=UTF-8 -cp "%APP_HOME%\app\*" org.litvin.SwingMainApp --diagnostics
exit /b %ERRORLEVEL%
