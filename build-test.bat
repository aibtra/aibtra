call %~dp0set-java-home.bat || exit /b
call gradlew test jpackage
pause