@echo off
setlocal
call "%~dp0env-java21.bat"
if errorlevel 1 exit /b 1

cd /d "%~dp0.."
if not exist "target\minio-learn-0.0.1-SNAPSHOT.jar" (
  echo [web] jar not found, building first...
  call "%~dp0build.bat"
  if errorlevel 1 exit /b 1
)

echo.
echo [web] http://127.0.0.1:8080
echo [web] Press Ctrl+C to stop.
echo.
"%JAVA_HOME%\bin\java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 ^
  -jar "target\minio-learn-0.0.1-SNAPSHOT.jar"
exit /b %ERRORLEVEL%
