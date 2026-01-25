# GitHub Setup Guide for NfcForge

This guide will help you upload your NfcForge project to GitHub.

## Option 1: Using GitHub Desktop (Easiest)

1. **Download GitHub Desktop** (if you don't have it):
   - Visit: https://desktop.github.com/
   - Download and install GitHub Desktop

2. **Create a new repository on GitHub.com**:
   - Go to https://github.com/new
   - Repository name: `NfcForge` (or any name you prefer)
   - Description: "Android NFC Reader/Writer app with transaction history"
   - Choose **Public** or **Private**
   - **DO NOT** initialize with README, .gitignore, or license (we already have these)
   - Click **Create repository**

3. **Clone/Add the repository in GitHub Desktop**:
   - Open GitHub Desktop
   - Click **File → Add Local Repository**
   - Browse to: `C:\Users\o.hijah\OneDrive - American International University\Desktop\Projects\Android\NfcForge`
   - Click **Add**

4. **If prompted to create a repository**:
   - Click **Publish repository**
   - Choose the repository you created on GitHub.com
   - Click **Publish**

5. **Commit and push your files**:
   - GitHub Desktop will show all your files
   - In the bottom left, type a commit message: "Initial commit: NfcForge Android NFC app"
   - Click **Commit to main** (or master)
   - Click **Push origin** to upload to GitHub

## Option 2: Using Command Line (Git)

### Step 1: Install Git (if not installed)

1. Download Git for Windows: https://git-scm.com/download/win
2. Install with default settings
3. Restart your terminal/PowerShell

### Step 2: Create Repository on GitHub

1. Go to https://github.com/new
2. Repository name: `NfcForge`
3. Description: "Android NFC Reader/Writer app with transaction history"
4. Choose **Public** or **Private**
5. **DO NOT** initialize with README, .gitignore, or license
6. Click **Create repository**

### Step 3: Initialize Git and Push

Open PowerShell in the project folder and run:

```powershell
# Navigate to project
cd "C:\Users\o.hijah\OneDrive - American International University\Desktop\Projects\Android\NfcForge"

# Initialize git repository
git init

# Add all files
git add .

# Create initial commit
git commit -m "Initial commit: NfcForge Android NFC app with comprehensive tag support"

# Add remote repository (replace YOUR_USERNAME with your GitHub username)
git remote add origin https://github.com/YOUR_USERNAME/NfcForge.git

# Rename branch to main (if needed)
git branch -M main

# Push to GitHub
git push -u origin main
```

**Note**: You'll be prompted for your GitHub username and password (or personal access token).

## Option 3: Using Android Studio

1. **Open the project in Android Studio**

2. **Enable Version Control**:
   - Go to **VCS → Enable Version Control Integration**
   - Select **Git**
   - Click **OK**

3. **Create repository on GitHub.com** (same as Option 1, Step 2)

4. **Add files and commit**:
   - Right-click on the project root → **Git → Add**
   - Go to **VCS → Commit**
   - Enter commit message: "Initial commit: NfcForge Android NFC app"
   - Click **Commit**

5. **Push to GitHub**:
   - Go to **VCS → Git → Push**
   - Click **Define remote**
   - Enter: `https://github.com/YOUR_USERNAME/NfcForge.git`
   - Click **Push**

## What Gets Uploaded

The `.gitignore` file ensures these are **NOT** uploaded:
- Build files (`build/`, `.gradle/`)
- Local configuration (`local.properties`)
- IDE files (`.idea/`)
- Compiled files (`.apk`, `.dex`, `.class`)
- User-specific build directory

## What Gets Uploaded

✅ All source code (`.kt` files)
✅ Gradle configuration files
✅ Resource files (XML, drawables)
✅ README.md
✅ .gitignore
✅ Project structure

## After Uploading

Your repository will be available at:
`https://github.com/YOUR_USERNAME/NfcForge`

You can share this link with others or use it to clone the project on other machines.

