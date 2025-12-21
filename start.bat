@echo off
title Vinted Favorites
chcp 65001 > nul

REM Se déplacer vers le dossier du script (important pour le double-clic)
cd /d "%~dp0"

echo.
echo ===============================================
echo    VINTED FAVORITES - Demarrage
echo ===============================================
echo.
echo Dossier: %CD%
echo.

REM Vérifier si le fichier .env existe
if not exist ".env" (
    echo [ERREUR] Le fichier .env n'existe pas!
    echo.
    echo Suivez ces etapes:
    echo 1. Copiez le fichier ".env.example" en ".env"
    echo 2. Ouvrez le fichier ".env" avec un editeur de texte
    echo 3. Modifiez VINTED_USER_ID avec votre ID Vinted
    echo 4. Ajoutez vos cookies Vinted
    echo 5. Relancez ce script
    echo.
    pause
    exit /b 1
)

echo [OK] Fichier .env trouve
echo.

REM Chercher Docker
set DOCKER_PATH=
set DOCKER_FOUND=0

REM Essayer dans le PATH
where docker.exe > nul 2>&1
if %errorlevel% equ 0 (
    for /f "delims=" %%i in ('where docker.exe') do set DOCKER_PATH=%%i
    set DOCKER_FOUND=1
    echo [OK] Docker trouve dans le PATH
    echo     %DOCKER_PATH%
)

REM Si pas trouvé, essayer Program Files
if %DOCKER_FOUND% equ 0 (
    echo Docker pas dans le PATH, recherche dans Program Files...
    if exist "C:\Program Files\Docker\Docker\resources\bin\docker.exe" (
        set "DOCKER_PATH=C:\Program Files\Docker\Docker\resources\bin\docker.exe"
        set DOCKER_FOUND=1
        echo [OK] Docker trouve dans Program Files
    )
)

if %DOCKER_FOUND% equ 0 (
    echo [ERREUR] Docker n'est pas installe ou introuvable!
    echo.
    echo Installez Docker Desktop depuis:
    echo https://www.docker.com/products/docker-desktop/
    echo.
    pause
    exit /b 1
)

echo.
echo Test de la version Docker...
"%DOCKER_PATH%" --version
echo.

echo Verification que Docker est demarre...
echo (Commande: docker info)
echo.
"%DOCKER_PATH%" info
set DOCKER_INFO_RESULT=%errorlevel%

if %DOCKER_INFO_RESULT% neq 0 (
    echo.
    echo [ERREUR] Docker n'est pas pret! Code erreur: %DOCKER_INFO_RESULT%
    echo.
    echo Solutions possibles:
    echo 1. Ouvrez Docker Desktop
    echo 2. Attendez que l'icone baleine soit stable (pas d'animation)
    echo 3. Essayez de redemarrer Docker Desktop
    echo.
    pause
    exit /b 1
)

echo.
echo [OK] Docker est demarre
echo.
echo ===============================================
echo    Demarrage de l'application...
echo    (Cela peut prendre 5-10 minutes la premiere fois)
echo ===============================================
echo.

REM Lancer docker compose et afficher la sortie
"%DOCKER_PATH%" compose up --build -d
set BUILD_RESULT=%errorlevel%

echo.
echo -----------------------------------------------
echo.

if %BUILD_RESULT% neq 0 (
    echo [ERREUR] Probleme lors du demarrage! Code: %BUILD_RESULT%
    echo.
    echo Verifiez les messages d'erreur ci-dessus.
    echo.
    pause
    exit /b 1
)

echo ===============================================
echo    APPLICATION DEMARREE AVEC SUCCES!
echo ===============================================
echo.
echo L'application est accessible sur:
echo    http://localhost:3000
echo.
echo Pour arreter l'application:
echo    Executez "stop.bat"
echo.
echo -----------------------------------------------
echo Appuyez sur une touche pour ouvrir l'application...
pause > nul
start http://localhost:3000

REM Garder la fenêtre ouverte
echo.
echo Fenetre ouverte. Appuyez sur une touche pour fermer...
pause > nul
