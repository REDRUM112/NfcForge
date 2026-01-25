# Install GitHub CLI for Windows
# This script will download and install GitHub CLI if not already installed

Write-Host "Checking for GitHub CLI..." -ForegroundColor Cyan

# Check if gh is already installed
if (Get-Command gh -ErrorAction SilentlyContinue) {
    Write-Host "GitHub CLI is already installed!" -ForegroundColor Green
    gh --version
    exit 0
}

Write-Host "GitHub CLI not found. Installing..." -ForegroundColor Yellow

# Try winget first (Windows 10/11)
if (Get-Command winget -ErrorAction SilentlyContinue) {
    Write-Host "Installing GitHub CLI using winget..." -ForegroundColor Cyan
    winget install --id GitHub.cli
    if ($LASTEXITCODE -eq 0) {
        Write-Host "GitHub CLI installed successfully!" -ForegroundColor Green
        Write-Host "Please restart your terminal/PowerShell and run the upload script again." -ForegroundColor Yellow
        exit 0
    }
}

# Try chocolatey
if (Get-Command choco -ErrorAction SilentlyContinue) {
    Write-Host "Installing GitHub CLI using Chocolatey..." -ForegroundColor Cyan
    choco install gh -y
    if ($LASTEXITCODE -eq 0) {
        Write-Host "GitHub CLI installed successfully!" -ForegroundColor Green
        Write-Host "Please restart your terminal/PowerShell and run the upload script again." -ForegroundColor Yellow
        exit 0
    }
}

# Manual installation instructions
Write-Host "`nAutomatic installation failed. Please install manually:" -ForegroundColor Red
Write-Host "1. Download from: https://cli.github.com/" -ForegroundColor Yellow
Write-Host "2. Or use: winget install --id GitHub.cli" -ForegroundColor Yellow
Write-Host "3. After installation, restart PowerShell and run the upload script again." -ForegroundColor Yellow

