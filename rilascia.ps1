# Pubblica una nuova versione di LanQuiz.
#
#   .\rilascia.ps1 1.1 "Aggiunta la modalita' esame"
#
# Cosa fa, nell'ordine: alza la versione nell'app Android e nella web app,
# compila l'APK firmato, committa, crea il tag e pubblica la Release su GitHub
# con l'APK allegato. Da quel momento l'app installata propone l'aggiornamento
# da sola e GitHub Pages serve la web app aggiornata.

param(
    [Parameter(Mandatory = $true)][string]$Versione,
    [string]$Note = ""
)

$ErrorActionPreference = "Stop"
$radice = $PSScriptRoot
$gradle = "C:\Users\marco\.gradle\dist\gradle-8.13\bin\gradle.bat"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

if ($Versione -notmatch '^\d+\.\d+(\.\d+)?$') {
    throw "La versione va scritta come 1.1 oppure 1.1.2, non '$Versione'."
}

# Windows PowerShell legge in ANSI e scrive UTF-8 con BOM: entrambe le cose
# rovinerebbero le lettere accentate e la prima riga dei file. Si legge e si
# scrive UTF-8 esplicitamente, senza BOM.
$senzaBom = New-Object System.Text.UTF8Encoding $false

function Leggi($percorso) {
    [System.IO.File]::ReadAllText($percorso, [System.Text.Encoding]::UTF8)
}

function Scrivi($percorso, $contenuto) {
    [System.IO.File]::WriteAllText($percorso, $contenuto, $senzaBom)
}

# git e gh scrivono avvisi innocui su stderr (per esempio quello sui fine riga),
# e con ErrorActionPreference = Stop PowerShell li scambia per errori fatali.
# Qui conta solo il codice di uscita.
function Esegui($eseguibile, [string[]]$argomenti) {
    $prima = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $eseguibile @argomenti
    $codice = $LASTEXITCODE
    $ErrorActionPreference = $prima
    if ($codice -ne 0) {
        throw "$eseguibile $($argomenti -join ' ') e' uscito con codice $codice"
    }
}

$buildGradle = Join-Path $radice "app\build.gradle"
$testo = Leggi $buildGradle

if ($testo -notmatch 'versionCode (\d+)') { throw "versionCode non trovato in app/build.gradle" }
$codiceNuovo = [int]$Matches[1] + 1

$testo = $testo -replace 'versionCode \d+', "versionCode $codiceNuovo"
$testo = $testo -replace 'versionName "[^"]*"', "versionName `"$Versione`""
Scrivi $buildGradle $testo

# La web app mostra la sua versione in fondo alla pagina: va tenuta allineata.
$appJs = Join-Path $radice "docs\app.js"
$js = Leggi $appJs
$js = $js -replace 'const VERSION = "[^"]*";', "const VERSION = `"$Versione`";"
Scrivi $appJs $js

Write-Host "Versione $Versione (versionCode $codiceNuovo) - compilo..." -ForegroundColor Cyan
& $gradle -p $radice assembleRelease
if ($LASTEXITCODE -ne 0) { throw "compilazione fallita" }

$apkSorgente = Join-Path $radice "app\build\outputs\apk\release\app-release.apk"
$apk = Join-Path $radice "LanQuiz-$Versione.apk"
Copy-Item $apkSorgente $apk -Force

$messaggio = if ($Note) { $Note } else { "Versione $Versione" }

Esegui git @("-C", $radice, "add", "app/build.gradle", "docs/app.js")
Esegui git @("-C", $radice, "commit", "-m", $messaggio)
Esegui git @("-C", $radice, "tag", "v$Versione")
Esegui git @("-C", $radice, "push")
Esegui git @("-C", $radice, "push", "--tags")

Esegui gh @("release", "create", "v$Versione", $apk, "--repo", "zeo93/LanQuiz",
    "--title", "LanQuiz $Versione", "--notes", $messaggio)

Write-Host "Fatto: https://github.com/zeo93/LanQuiz/releases/tag/v$Versione" -ForegroundColor Green
Write-Host "Web app: https://zeo93.github.io/LanQuiz/" -ForegroundColor Green
