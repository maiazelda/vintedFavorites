@echo off
title Vinted Favorites
chcp 65001 > nul

echo.
echo ===============================================
echo    VINTED FAVORITES - Demarrage
echo ===============================================
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

REM Vérifier si Docker est installé
docker --version > nul 2>&1
if errorlevel 1 (
    echo [ERREUR] Docker n'est pas installe!
    echo.
    echo Installez Docker Desktop depuis:
    echo https://www.docker.com/products/docker-desktop/
    echo.
    pause
    exit /b 1
)

REM Vérifier si Docker est démarré
docker info > nul 2>&1
if errorlevel 1 (
    echo [ERREUR] Docker n'est pas demarre!
    echo.
    echo Lancez Docker Desktop et reessayez.
    echo.
    pause
    exit /b 1
)

echo [OK] Docker est installe et demarre
echo.
echo Demarrage de l'application...
echo (Cela peut prendre quelques minutes la premiere fois)
echo.

docker-compose up --build -d

if errorlevel 1 (
    echo.
    echo [ERREUR] Probleme lors du demarrage!
    pause
    exit /b 1
)

echo.
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
echo Appuyez sur une touche pour ouvrir l'application...
pause > nul
start http://localhost:3000
