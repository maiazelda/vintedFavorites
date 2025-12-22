@echo off
setlocal enabledelayedexpansion
title Vinted Favorites
chcp 65001 > nul

REM Se déplacer vers le dossier du script
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

REM Test Docker
echo Test Docker...
docker --version
if !errorlevel! neq 0 (
    echo [ERREUR] Docker introuvable!
    pause
    exit /b 1
)

echo.
echo [OK] Docker est installe
echo.
echo ===============================================
echo    Demarrage de l'application...
echo    (5-10 minutes la premiere fois)
echo ===============================================
echo.

REM Lancer docker compose
docker compose up --build -d

if !errorlevel! neq 0 (
    echo.
    echo [ERREUR] Probleme lors du demarrage!
    echo.
    pause
    exit /b 1
)

echo.
echo ===============================================
echo    APPLICATION DEMARREE AVEC SUCCES!
echo ===============================================
echo.
echo L'application est accessible sur:
echo    http://localhost:8080
echo.
echo Pour arreter: executez "stop.bat"
echo.
echo Appuyez sur une touche pour ouvrir l'application...
pause > nul
start http://localhost:8080
