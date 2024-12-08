@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0git-conflict-solver.ps1" %*
