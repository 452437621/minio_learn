@echo off
setlocal
call "%~dp0env-java21.bat"
if errorlevel 1 exit /b 1

cd /d "%~dp0.."
if "%~1"=="" (
  echo Usage: scripts\demo.bat ^<topic^> [--keep=true]
  echo        scripts\demo.bat list
  echo        scripts\demo.bat all
  exit /b 2
)

if not exist "target\minio-learn-0.0.1-SNAPSHOT.jar" (
  echo [demo] jar not found, building first...
  call "%~dp0build.bat"
  if errorlevel 1 exit /b 1
)

echo.
"%JAVA_HOME%\bin\java" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 ^
  -jar "target\minio-learn-0.0.1-SNAPSHOT.jar" --demo=%1 %2 %3
exit /b %ERRORLEVEL%
