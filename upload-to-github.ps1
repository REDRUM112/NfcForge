# Upload NfcForge to GitHub using GitHub CLI
# Make sure GitHub CLI is installed first: winget install --id GitHub.cli

$ErrorActionPreference = "Stop"

# Navigate to project directory
$projectDir = "C:\Users\o.hijah\OneDrive - American International University\Desktop\Projects\Android\NfcForge"
Set-Location $projectDir

Write-Host "=== NfcForge GitHub Upload Script ===" -ForegroundColor Cyan
Write-Host ""

# Check if GitHub CLI is installed
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: GitHub CLI (gh) is not installed!" -ForegroundColor Red
    Write-Host "Please install it first:" -ForegroundColor Yellow
    Write-Host "  winget install --id GitHub.cli" -ForegroundColor Yellow
    Write-Host "Or run: .\install-gh-cli.ps1" -ForegroundColor Yellow
    exit 1
}

Write-Host "GitHub CLI found!" -ForegroundColor Green
gh --version
Write-Host ""

# Check if already logged in
Write-Host "Checking GitHub authentication..." -ForegroundColor Cyan
$authStatus = gh auth status 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Not logged in to GitHub. Starting login process..." -ForegroundColor Yellow
    gh auth login
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: GitHub authentication failed!" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "Already authenticated with GitHub!" -ForegroundColor Green
}
Write-Host ""

# Check if git repository is initialized
if (-not (Test-Path ".git")) {
    Write-Host "Initializing git repository..." -ForegroundColor Cyan
    git init
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Git is not installed!" -ForegroundColor Red
        Write-Host "Please install Git: https://git-scm.com/download/win" -ForegroundColor Yellow
        exit 1
    }
} else {
    Write-Host "Git repository already initialized." -ForegroundColor Green
}
Write-Host ""

# Add all files
Write-Host "Adding all files to git..." -ForegroundColor Cyan
git add .
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Failed to add files!" -ForegroundColor Red
    exit 1
}
Write-Host "Files added successfully!" -ForegroundColor Green
Write-Host ""

# Check if there are changes to commit
$status = git status --porcelain
if ([string]::IsNullOrWhiteSpace($status)) {
    Write-Host "No changes to commit. Repository is up to date." -ForegroundColor Yellow
    Write-Host "Checking if remote exists..." -ForegroundColor Cyan
    
    $remote = git remote get-url origin 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Remote repository exists: $remote" -ForegroundColor Green
        Write-Host "Pushing to GitHub..." -ForegroundColor Cyan
        git push -u origin main
        if ($LASTEXITCODE -eq 0) {
            Write-Host "Successfully pushed to GitHub!" -ForegroundColor Green
        } else {
            Write-Host "Trying 'master' branch instead..." -ForegroundColor Yellow
            git push -u origin master
        }
        exit 0
    }
} else {
    # Create initial commit
    Write-Host "Creating initial commit..." -ForegroundColor Cyan
    git commit -m "Initial commit: NfcForge Android NFC app with comprehensive tag support"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Failed to create commit!" -ForegroundColor Red
        exit 1
    }
    Write-Host "Commit created successfully!" -ForegroundColor Green
    Write-Host ""
}

# Create GitHub repository
Write-Host "Creating GitHub repository..." -ForegroundColor Cyan
Write-Host "Repository will be created as PUBLIC. Press Ctrl+C to cancel." -ForegroundColor Yellow
Start-Sleep -Seconds 3

$repoName = "NfcForge"
$description = "Android NFC Reader/Writer app with comprehensive tag support (EMV, MIFARE, NTAG, etc.)"

Write-Host "Creating repository: $repoName" -ForegroundColor Cyan
gh repo create $repoName --public --description $description --source=. --remote=origin --push

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "SUCCESS! Repository uploaded to GitHub!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    
    # Get the repository URL
    $username = gh api user --jq .login
    $repoUrl = "https://github.com/$username/$repoName"
    Write-Host "Repository URL: $repoUrl" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "You can view it in your browser or share it with others!" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "ERROR: Failed to create or push to GitHub repository!" -ForegroundColor Red
    Write-Host "You may need to:" -ForegroundColor Yellow
    Write-Host "1. Check your GitHub authentication: gh auth status" -ForegroundColor Yellow
    Write-Host "2. Make sure the repository name doesn't already exist" -ForegroundColor Yellow
    Write-Host "3. Try creating it manually: gh repo create $repoName --public" -ForegroundColor Yellow
    exit 1
}

