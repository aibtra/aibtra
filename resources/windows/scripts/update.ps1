$ErrorActionPreference = "Stop"
$ProgressPreference = 'SilentlyContinue'

function Remove-Tree {
    param (
        [string]$root
    )

    $updateFiles = Join-Path $root "update.files"
    $filesContent = Get-Content -Path $updateFiles
    $filesContent | ForEach-Object {
        $relativePath = $_.Trim()
        if ($relativePath) {
            $filePath = Join-Path $root $relativePath
            if (Test-Path $filePath) {
                Remove-Item -Path $filePath -Force
            } else {
                Write-Warning "File not found: $filePath"
            }
        }
    }
    Remove-Item -Path $updateFiles -Force -ErrorAction SilentlyContinue

    Get-ChildItem -Path $root -Recurse -Directory | Sort-Object FullName -Descending | ForEach-Object {
        if (-not (Get-ChildItem -Path $_.FullName)) {
            Remove-Item -Path $_.FullName -Force -ErrorAction SilentlyContinue
        }
    }

    $remainingItems = Get-ChildItem -Path $root -Recurse
    foreach ($item in $remainingItems) {
        Write-Warning "Item not removed: $($item.FullName)"
    }
}

function Remove-Directory-Safe {
    param (
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (Test-Path $Path) {
        if ((Get-ChildItem -Path $Path -Force | Measure-Object).Count -eq 0) {
            try {
                Remove-Item -Path $Path -Force -ErrorAction Stop
            } catch {
                Write-Warning "Could not remove $Path. It might be in use."
            }
        } else {
            Write-Warning "Directory $Path is not empty. Skipping removal."
        }
    } else {
        Write-Warning "Directory $Path does not exist. Nothing to remove."
    }
}

if (Get-Process -Name "aibtra" -ErrorAction SilentlyContinue) {
    Write-Warning "aibtra.exe is currently running. Please Exit it before updating."
    Read-Host -Prompt "Press Enter to exit"
    exit 1
}

try {
    $root = $PSScriptRoot
    $bundleTypeFile = Join-Path $root "app\aibtra.bundletype"

    if (-Not (Test-Path $bundleTypeFile)) {
        throw "Bundle type file not found at path: $bundleTypeFile"
    }

    $updateFiles = Join-Path $root "update.files"
    if (-Not (Test-Path $updateFiles)) {
        throw "update.files not found at path: $updateFiles"
    }

    $bundleType = (Get-Content -Path $bundleTypeFile -Raw).Trim()
    switch ($bundleType.ToUpper()) {
        "STABLE" { $tag = "stable" }
        "LATEST" { $tag = "latest" }
        "EXPERIMENTAL" { $tag = "experimental" }
        Default { throw "Invalid bundle type: '$bundleType'. Expected 'STABLE', 'LATEST', or 'EXPERIMENTAL'." }
    }

    $downloadUrls = @(
        "https://www.aibtra.dev/downloads/windows-$tag.zip",
        "https://github.com/aibtra/aibtra/releases/download/$tag/aibtra-$tag-windows.zip"
    )
    $uniqueId = [System.Guid]::NewGuid().ToString()
    $tempDir = Join-Path $env:TEMP "aibtra_update_$uniqueId"
    $zipPath = Join-Path $tempDir "aibtra-$tag-windows.zip"

    New-Item -Path $tempDir -ItemType Directory | Out-Null
    Write-Host "Using temp directory: $tempDir"

    # Sanity check
    $files = Get-ChildItem -Path $tempDir
    if ($files) {
        $files | ForEach-Object { Write-Host $_.FullName }
        throw "Temp directory is not empty."
    }

    $downloadSuccess = $false
    foreach ($url in $downloadUrls) {
        try {
            Write-Host "Attempting to download from: $url"
            Start-BitsTransfer -Source $url -Destination $zipPath -ErrorAction Stop
            Write-Host "Download completed from: $url"
            $downloadSuccess = $true
            break
        } catch {
            Write-Warning "Failed to download from: $url. Error: $_"
        }
    }

    if (-not $downloadSuccess) {
        throw "Failed to download the file from all provided URLs."
    }

    Write-Host "Starting extraction..."
    $extractPath = Join-Path $tempDir "extracted"
    New-Item -Path $extractPath -ItemType Directory | Out-Null

    # Custom extraction progress
    Write-Host "Extracting ZIP archive to: $extractPath"
    Expand-Archive -Path $zipPath -DestinationPath $extractPath -Force
    Write-Host "Extraction completed."

    $sourceDir = Join-Path $extractPath "aibtra"
    if (-Not (Test-Path $sourceDir -PathType Container)) {
        throw "Top-level 'aibtra' directory not found in the extracted archive."
    }

    Write-Host "Replacing content in $root"

    Remove-Tree -root $root

    Write-Host "Copying new files..."
    Get-ChildItem -Path $sourceDir -Recurse | ForEach-Object {
        $targetPath = $_.FullName.Replace($sourceDir, $root)
        if ($_.PSIsContainer) {
            if (-Not (Test-Path $targetPath)) {
                New-Item -Path $targetPath -ItemType Directory | Out-Null
                Write-Host "Created directory: $targetPath"
            }
        }
        else {
            Copy-Item -Path $_.FullName -Destination $targetPath -Force
            Write-Host "Copied file: $targetPath"
        }
    }

    Write-Host "Cleanup: Removing temporary files and directories."
    Remove-Tree -root $sourceDir
    Remove-Directory-Safe -Path $sourceDir
    Remove-Item -Path (Join-Path $extractPath "README.md") -Force -ErrorAction SilentlyContinue
    Remove-Directory-Safe -Path $extractPath
    Remove-Item -Path $zipPath -Force -ErrorAction SilentlyContinue
    Remove-Directory-Safe -Path $tempDir

    Write-Host "Update process completed successfully."
} catch {
    Write-Error "An error occurred: $_"
    exit 1
}
