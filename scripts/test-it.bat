@echo off
setlocal
call "%~dp0env-java21.bat"
if errorlevel 1 exit /b 1

cd /d "%~dp0.."
set "MINIO_IT=true"
echo.
echo [test] MINIO_IT=true, running integration tests against http://127.0.0.1:9000
call mvn -B verify
exit /b %ERRORLEVEL%
