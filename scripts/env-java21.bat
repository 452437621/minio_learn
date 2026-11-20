@echo off
rem Must be used as: call scripts\env-java21.bat
rem Do NOT add setlocal: the caller must inherit JAVA_HOME and PATH.

if not defined JAVA21_HOME set "JAVA21_HOME=D:\ProgramFiles\JAVA\graalvm-jdk-21.0.7"

if not exist "%JAVA21_HOME%\bin\java.exe" (
  echo [ERROR] JDK 21 not found at "%JAVA21_HOME%".
  echo         Install a JDK 21 or set JAVA21_HOME to its home directory.
  exit /b 1
)

set "JAVA_HOME=%JAVA21_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
chcp 65001 >nul

echo [env] JAVA_HOME=%JAVA_HOME%
"%JAVA_HOME%\bin\java" -version
exit /b 0
