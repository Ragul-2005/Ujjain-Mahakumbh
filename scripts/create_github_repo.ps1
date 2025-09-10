<#
PowerShell helper to create a GitHub repository and push the current project.
Usage:
  1) Open PowerShell, cd to the project root (where this script resides).
  2) Run with optional parameters, for example:
     .\create_github_repo.ps1 -RepoName mahakumbh -Visibility public -UseSsh $true

Notes:
- This script does not run automatically; it prints and runs commands locally on your machine.
- It supports the GitHub CLI (recommended) or falls back to standard git push (you must create the remote on github.com first).
- It will NOT upload files listed in .gitignore (good: google-services.json is ignored).
#>
param(
    [string]$RepoName = (Split-Path -Leaf (Get-Location)),
    [ValidateSet('public','private')][string]$Visibility = 'public',
    [bool]$UseSsh = $true
)

function Write-Heading($s){ Write-Host "==> $s" -ForegroundColor Cyan }

Write-Heading "Create GitHub repo: $RepoName (visibility: $Visibility)"

$gh = Get-Command gh -ErrorAction SilentlyContinue
if ($gh) {
    Write-Heading "GitHub CLI detected. Ensure you're logged in (gh auth status)"
    gh auth status 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "You are not logged in to gh. Running 'gh auth login' now..." -ForegroundColor Yellow
        gh auth login
    }

    # Create repo and push current branch
    $remoteType = if ($UseSsh) { 'ssh' } else { 'https' }
    Write-Host "Creating repository on GitHub via gh (remote: origin, push after create)..." -ForegroundColor Green
    gh repo create $RepoName --$Visibility --source=. --remote=origin --push --confirm
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Repository created and pushed. Remote 'origin' set." -ForegroundColor Green
    } else {
        Write-Host "gh failed or repo might already exist. Check output above." -ForegroundColor Red
    }
} else {
    Write-Heading "GitHub CLI not found. Falling back to manual instructions."
    Write-Host "1) Create a repository named '$RepoName' on https://github.com/new (choose $Visibility)."
    if ($UseSsh) {
        Write-Host "2) Add remote and push (SSH):"
        Write-Host "   git remote add origin git@github.com:<USERNAME>/$RepoName.git"
    } else {
        Write-Host "2) Add remote and push (HTTPS):"
        Write-Host "   git remote add origin https://github.com/<USERNAME>/$RepoName.git"
    }
    Write-Host "3) Then push the current branch:" 
    Write-Host "   git branch -M main" 
    Write-Host "   git push -u origin main"
}

Write-Heading "Done. If you want, replace <USERNAME> in the commands above with your GitHub username and run the commands."
