# Installation - Known Issues

If starting `aibtra.exe` results in a "Searching for app in the store?" message, right-click `aibtra.exe`, open the Properties, and clear the *Read-Only* attribute and select *Unblock*.

Alternatively, run the following PowerShell command in the installation root directory:

```
powershell -Command "Set-ItemProperty -Path aibtra.exe -Name IsReadOnly -Value $false; Unblock-File -Path aibtra.exe"
```