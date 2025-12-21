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

REM Chercher Docker - d'abord dans le PATH, sinon dans le chemin par défaut
set DOCKER_CMD=docker
set DOCKER_COMPOSE_CMD=docker compose

where docker > nul 2>&1
if errorlevel 1 (
    REM Docker pas dans le PATH, chercher dans le chemin par défaut
    if exist "C:\Program Files\Docker\Docker\resources\bin\docker.exe" (
        set DOCKER_CMD="C:\Program Files\Docker\Docker\resources\bin\docker.exe"
        set DOCKER_COMPOSE_CMD="C:\Program Files\Docker\Docker\resources\bin\docker.exe" compose
    ) else (
        echo [ERREUR] Docker n'est pas installe ou introuvable!
        echo.
        echo Installez Docker Desktop depuis:
        echo https://www.docker.com/products/docker-desktop/
        echo.
        echo Si Docker est deja installe, redemarrez votre ordinateur.
        echo.
        pause
        exit /b 1
    )
)

REM Vérifier si Docker est démarré
%DOCKER_CMD% info > nul 2>&1
if errorlevel 1 (
    echo [ERREUR] Docker n'est pas demarre!
    echo.
    echo Lancez Docker Desktop et attendez qu'il soit pret.
    echo (L'icone baleine doit etre visible en bas a droite)
    echo.
    pause
    exit /b 1
)

echo [OK] Docker est installe et demarre
echo.
echo Demarrage de l'application...
echo (Cela peut prendre 5-10 minutes la premiere fois)
echo.

%DOCKER_COMPOSE_CMD% up --build -d

if errorlevel 1 (
    echo.
    echo [ERREUR] Probleme lors du demarrage!
    echo.
    echo Essayez de relancer le script.
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
