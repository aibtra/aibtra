if ($args.Count -lt 2) {
    Write-Output "Usage: $($MyInvocation.MyCommand.Name) <GitBashPath> <GitRepoRoot>"
    exit 1
}

$gitBashPath = $args[0]
$gitRepoRoot = $args[1]

$overviewPath = ""
$scriptPath = Join-Path $PSScriptRoot "app\git-compose-conflict-overview.sh"
$executablePath = Join-Path $PSScriptRoot "aibtra.exe"

& "$gitBashPath" "$scriptPath" "$gitRepoRoot" | ForEach-Object {
    Write-Output $_
    if ($_ -like "CONFLICT-OVERVIEW: *") {
        $overviewPath = $_ -replace "CONFLICT-OVERVIEW: ", ""
    }
}

if ($overviewPath -ne "") {
    & "$executablePath" "--resolver" "$overviewPath"
} else {
    Write-Host "Something went wrong. No overview path found." -ForegroundColor Red
    Read-Host -Prompt "Press Enter to continue"
}
