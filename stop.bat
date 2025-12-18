@echo off
title Vinted Favorites - Arret
chcp 65001 > nul

echo.
echo ===============================================
echo    VINTED FAVORITES - Arret
echo ===============================================
echo.

docker-compose down

echo.
echo Application arretee.
echo.
pause
