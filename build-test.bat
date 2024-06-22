call %~dp0set-java-home.bat || exit /b
rmdir /s /q %~dp0build\jpackage
call gradlew test jpackage
pause