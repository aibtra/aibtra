@echo off
REM Get the directory of the batch file
set BATCH_DIR=%~dp0

REM Check if the .jdk file exists
if not exist "%BATCH_DIR%.jdk" (
    echo ERROR: .jdk file not found in %BATCH_DIR%.
    exit /b 1
)

REM Read the .jdk file to set JAVA_HOME
setlocal enabledelayedexpansion
for /f "delims=" %%a in (%BATCH_DIR%.jdk) do (
    set JAVA_HOME=%%a
)

REM Verify that JAVA_HOME was set
if "%JAVA_HOME%"=="" (
    echo ERROR: Failed to set JAVA_HOME from .jdk file.
    exit /b 1
)

REM Export JAVA_HOME to parent script
endlocal & set "JAVA_HOME=%JAVA_HOME%"
exit /b 0
