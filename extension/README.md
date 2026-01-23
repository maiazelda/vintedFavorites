# Extension Vinted Favorites Sync

Extension Chrome pour synchroniser tes favoris Vinted vers ton application.

## Structure des fichiers

```
extension/
├── manifest.json    ← Configuration de l'extension (permissions, etc.)
├── popup.html       ← Interface quand on clique sur l'icône
├── popup.css        ← Styles de l'interface
├── popup.js         ← Logique de l'interface (boutons, statut)
├── background.js    ← Service worker (appels API, sync)
├── icons/           ← Icônes de l'extension
└── README.md        ← Ce fichier
```

## Comment ça fonctionne

### 1. manifest.json
C'est la "carte d'identité" de l'extension. Il définit :
- Le nom et la version
- Les **permissions** (accès aux cookies, stockage local)
- Les **host_permissions** (sites autorisés : vinted.fr)
- Le **popup** (interface utilisateur)
- Le **service worker** (code qui tourne en arrière-plan)

### 2. popup.html / popup.css / popup.js
C'est l'interface qui apparaît quand tu cliques sur l'icône de l'extension :
- Vérifie si tu es connecté à Vinted (en regardant les cookies)
- Permet de configurer l'URL de ton serveur
- Bouton "Synchroniser" pour lancer la sync

### 3. background.js (Service Worker)
C'est le "cerveau" de l'extension qui fait le vrai travail :
- Récupère les cookies Vinted du navigateur
- Appelle l'API Vinted pour récupérer tes favoris
- Envoie tout ça à ton serveur backend

## Installation (mode développeur)

1. Ouvre Chrome
2. Va dans `chrome://extensions/`
3. Active le **Mode développeur** (toggle en haut à droite)
4. Clique **"Charger l'extension non empaquetée"**
5. Sélectionne le dossier `extension/`

## Utilisation

1. Va sur [vinted.fr](https://www.vinted.fr) et connecte-toi
2. Clique sur l'icône de l'extension 🧩
3. Entre l'URL de ton serveur (ex: `https://mon-app.railway.app`)
4. Entre la **clé API** (la même que `APP_SECURITY_API_KEY` sur ton serveur)
5. Clique "Sauvegarder"
6. Clique "Synchroniser"
7. Tes favoris sont envoyés à ton serveur !

## Sécurité

L'extension utilise une **clé API** pour s'authentifier auprès du backend.
Cette clé doit correspondre à la variable `APP_SECURITY_API_KEY` configurée sur le serveur.

L'extension envoie cette clé dans le header HTTP : `X-API-Key: <ta-clé>`

## Permissions expliquées

| Permission | Pourquoi ? |
|------------|-----------|
| `cookies` | Lire les cookies Vinted pour s'authentifier à l'API |
| `storage` | Sauvegarder l'URL du serveur localement |
| `host_permissions: vinted.fr` | Autoriser les appels à l'API Vinted |

## Endpoint backend requis

L'extension envoie les données vers `POST /api/extension/sync` avec ce format :

```json
{
  "favorites": [
    {
      "vintedId": "123456",
      "title": "T-shirt Nike",
      "brand": "Nike",
      "price": 15.00,
      "imageUrl": "https://...",
      "productUrl": "https://www.vinted.fr/items/123456",
      "sold": false
    }
  ],
  "cookies": [
    { "name": "_vinted_fr_session", "value": "...", "domain": ".vinted.fr" }
  ]
}
```
