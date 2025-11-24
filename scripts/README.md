# Vinted Session Manager

Script automatisé pour gérer la session Vinted avec Playwright.

## Installation

### Prérequis
- Node.js >= 18.0.0
- npm ou yarn

### Étapes

```bash
cd scripts

# Installer les dépendances
npm install

# Installer le navigateur Chromium pour Playwright
npm run install-browsers
```

## Configuration

### Option 1: Variables d'environnement
```bash
export VINTED_EMAIL="votre-email@example.com"
export VINTED_PASSWORD="votre-mot-de-passe"
export API_URL="http://localhost:8080"  # URL de l'API backend
```

### Option 2: Arguments en ligne de commande
```bash
node vinted-session-manager.js --email "votre-email" --password "votre-mot-de-passe"
```

## Utilisation

### Rafraîchir la session manuellement
```bash
# Mode headless (sans interface graphique)
npm run refresh

# Mode visible (avec navigateur visible - utile pour debug)
npm run refresh:visible
```

### Via l'API REST

1. **Configurer les identifiants** (une seule fois) :
```bash
curl -X POST http://localhost:8080/api/vinted/credentials \
  -H "Content-Type: application/json" \
  -d '{
    "email": "votre-email@example.com",
    "password": "votre-mot-de-passe",
    "userId": "VOTRE_USER_ID"  # Optionnel, trouvable dans l'URL de votre profil Vinted
  }'
```

2. **Déclencher un rafraîchissement** :
```bash
curl -X POST http://localhost:8080/api/vinted/session/refresh
```

3. **Vérifier le statut** :
```bash
curl http://localhost:8080/api/vinted/session/refresh/status
```

## Fonctionnement

1. Le script ouvre un navigateur Chromium
2. Se connecte à Vinted avec vos identifiants
3. Extrait les cookies et tokens de session
4. Envoie automatiquement ces données à l'API backend

## Automatisation

### Avec cron (Linux/Mac)
```bash
# Rafraîchir toutes les 6 heures
0 */6 * * * cd /path/to/vintedFavorites/scripts && npm run refresh >> /var/log/vinted-refresh.log 2>&1
```

### Avec Task Scheduler (Windows)
Créez une tâche planifiée qui exécute :
```
node C:\path\to\vintedFavorites\scripts\vinted-session-manager.js
```

## Dépannage

### "Login failed"
- Vérifiez que vos identifiants sont corrects
- Vinted peut demander une vérification CAPTCHA - utilisez le mode visible (`npm run refresh:visible`)
- Vérifiez si Vinted a changé son interface de connexion

### "Could not find login button"
- L'interface Vinted peut avoir changé
- Regardez le fichier `login-failed.png` généré pour voir ce qui s'est passé

### Erreur de connexion à l'API
- Vérifiez que le backend Java est en cours d'exécution
- Vérifiez la variable `API_URL`
