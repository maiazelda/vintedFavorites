# Installation Vinted Favorites

Guide d'installation simple pour utiliser l'application sur votre ordinateur.

---

## Étape 1 : Installer Docker Desktop

1. Allez sur https://www.docker.com/products/docker-desktop/
2. Téléchargez **Docker Desktop** pour Windows
3. Installez-le (suivez les instructions)
4. Redémarrez votre ordinateur si demandé
5. Lancez Docker Desktop

**Important** : Docker Desktop doit être ouvert pour que l'application fonctionne.

---

## Étape 2 : Configurer l'application

### 2.1 Créer le fichier de configuration

1. Dans le dossier de l'application, trouvez le fichier `.env.example`
2. **Copiez** ce fichier et renommez la copie en `.env` (sans le "example")

### 2.2 Trouver votre ID Vinted

1. Allez sur https://www.vinted.fr
2. Connectez-vous à votre compte
3. Cliquez sur votre profil
4. Regardez l'URL : `https://www.vinted.fr/member/12345678`
5. Le numéro (12345678) est votre ID Vinted

### 2.3 Configurer l'authentification

Vous avez **2 options** :

#### Option A : Login automatique (RECOMMANDÉ)

Ouvrez le fichier `.env` et renseignez :

```
VINTED_USER_ID=12345678
VINTED_EMAIL=votre@email.com
VINTED_PASSWORD=votremotdepasse
```

L'application se connectera automatiquement à Vinted et rafraîchira les cookies quand nécessaire.

#### Option B : Cookies manuels

Si l'option A ne fonctionne pas (CAPTCHA, 2FA...) :

1. Ouvrez Chrome
2. Allez sur https://www.vinted.fr et connectez-vous
3. Appuyez sur **F12** (ouvre les outils développeur)
4. Cliquez sur l'onglet **"Réseau"** (ou "Network")
5. Rechargez la page (**F5**)
6. Dans la liste à gauche, cliquez sur la première ligne "www.vinted.fr"
7. À droite, dans **"En-têtes de requête"**, trouvez la ligne qui commence par **"Cookie:"**
8. Cliquez dessus et faites **Ctrl+C** pour copier toute la valeur

Puis dans `.env` :

```
VINTED_USER_ID=12345678
VINTED_COOKIES=collez_vos_cookies_ici
```

---

## Étape 3 : Lancer l'application

1. Assurez-vous que **Docker Desktop** est ouvert
2. Double-cliquez sur **start.bat**
3. Attendez que le téléchargement et le démarrage se terminent
   - **Première fois : 10-15 minutes** (téléchargement de Chromium pour le login auto)
   - **Ensuite : 1-2 minutes**
4. L'application s'ouvre automatiquement dans votre navigateur

**L'application est accessible sur :** http://localhost:8080

---

## Utilisation

Une fois l'application lancée :
- Vos favoris Vinted se synchronisent automatiquement
- Cliquez sur **"Synchroniser"** pour récupérer les nouveaux favoris
- Utilisez les filtres pour trier par marque, catégorie, genre...
- Utilisez le tri par **Date** ou **Prix**
- Cliquez sur un article pour l'ouvrir sur Vinted

---

## Arrêter l'application

Double-cliquez sur **stop.bat**

---

## Si ça ne fonctionne plus

### Avec le login automatique (Option A)
L'application devrait se reconnecter automatiquement. Si ça ne fonctionne pas :
1. Vérifiez vos identifiants dans `.env`
2. Relancez avec **start.bat**

### Avec les cookies manuels (Option B)
Les cookies expirent après quelques jours :
1. Refaites l'étape **2.3 Option B** pour récupérer de nouveaux cookies
2. Mettez à jour le fichier `.env`
3. Relancez avec **start.bat**

---

## Commandes utiles

```bash
# Lancer l'application
docker compose up -d

# Arrêter l'application
docker compose down

# Reconstruire après modification
docker compose up --build -d

# Voir les logs
docker compose logs -f backend
```

---

## Aide

En cas de problème :
- Vérifiez que Docker Desktop est bien lancé (icône dans la barre des tâches)
- Vérifiez que le fichier `.env` existe (pas `.env.example`)
- Vérifiez que vous avez bien renseigné `VINTED_USER_ID`
- Consultez les logs : `docker compose logs -f backend`

---

## Architecture

L'application se compose de 3 services Docker :
- **PostgreSQL** : Base de données pour stocker vos favoris
- **Backend** : API Spring Boot + Playwright (login automatique)
- **Frontend** : Interface web React accessible sur le port 8080
