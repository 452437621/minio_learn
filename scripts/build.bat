@echo off
setlocal
call "%~dp0env-java21.bat"
if errorlevel 1 exit /b 1

cd /d "%~dp0.."
echo.
echo [build] mvn clean package
call mvn -B -DskipTests clean package
if errorlevel 1 (
  echo [build] FAILED
  exit /b 1
)
echo.
echo [build] OK: target\minio-learn-0.0.1-SNAPSHOT.jar
exit /b 0
