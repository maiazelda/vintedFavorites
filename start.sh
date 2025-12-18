#!/bin/bash

echo ""
echo "==============================================="
echo "   VINTED FAVORITES - Démarrage"
echo "==============================================="
echo ""

# Vérifier si le fichier .env existe
if [ ! -f ".env" ]; then
    echo "[ERREUR] Le fichier .env n'existe pas!"
    echo ""
    echo "Suivez ces étapes:"
    echo "1. Copiez le fichier '.env.example' en '.env'"
    echo "   cp .env.example .env"
    echo "2. Ouvrez le fichier '.env' avec un éditeur"
    echo "3. Modifiez VINTED_USER_ID avec votre ID Vinted"
    echo "4. Ajoutez vos cookies Vinted"
    echo "5. Relancez ce script"
    echo ""
    exit 1
fi

# Vérifier si Docker est installé
if ! command -v docker &> /dev/null; then
    echo "[ERREUR] Docker n'est pas installé!"
    echo ""
    echo "Installez Docker Desktop depuis:"
    echo "https://www.docker.com/products/docker-desktop/"
    echo ""
    exit 1
fi

# Vérifier si Docker est démarré
if ! docker info &> /dev/null; then
    echo "[ERREUR] Docker n'est pas démarré!"
    echo ""
    echo "Lancez Docker Desktop et réessayez."
    echo ""
    exit 1
fi

echo "[OK] Docker est installé et démarré"
echo ""
echo "Démarrage de l'application..."
echo "(Cela peut prendre quelques minutes la première fois)"
echo ""

docker-compose up --build -d

if [ $? -ne 0 ]; then
    echo ""
    echo "[ERREUR] Problème lors du démarrage!"
    exit 1
fi

echo ""
echo "==============================================="
echo "   APPLICATION DÉMARRÉE AVEC SUCCÈS!"
echo "==============================================="
echo ""
echo "L'application est accessible sur:"
echo "   http://localhost:8080"
echo ""
echo "Pour arrêter l'application:"
echo "   ./stop.sh"
echo ""
