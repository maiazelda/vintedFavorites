@echo off
title Vinted Favorites - Arret
chcp 65001 > nul

REM Se déplacer vers le dossier du script
cd /d "%~dp0"

echo.
echo ===============================================
echo    VINTED FAVORITES - Arret
echo ===============================================
echo.

REM Chercher Docker
set DOCKER_COMPOSE_CMD=docker compose

where docker > nul 2>&1
if errorlevel 1 (
    if exist "C:\Program Files\Docker\Docker\resources\bin\docker.exe" (
        set DOCKER_COMPOSE_CMD="C:\Program Files\Docker\Docker\resources\bin\docker.exe" compose
    )
)

%DOCKER_COMPOSE_CMD% down

echo.
echo Application arretee.
echo.
pause
