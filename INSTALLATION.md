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

### 2.3 Récupérer vos cookies Vinted

1. Ouvrez Chrome
2. Allez sur https://www.vinted.fr et connectez-vous
3. Appuyez sur **F12** (ouvre les outils développeur)
4. Cliquez sur l'onglet **"Réseau"** (ou "Network")
5. Rechargez la page (**F5**)
6. Dans la liste à gauche, cliquez sur la première ligne "www.vinted.fr"
7. À droite, dans **"En-têtes de requête"**, trouvez la ligne qui commence par **"Cookie:"**
8. Cliquez dessus et faites **Ctrl+C** pour copier toute la valeur

### 2.4 Modifier le fichier .env

Ouvrez le fichier `.env` avec le Bloc-notes et modifiez :

```
VINTED_USER_ID=12345678
VINTED_COOKIES=collez_vos_cookies_ici
```

Enregistrez le fichier.

---

## Étape 3 : Lancer l'application

1. Assurez-vous que **Docker Desktop** est ouvert
2. Double-cliquez sur **start.bat**
3. Attendez que le téléchargement et le démarrage se terminent (2-3 min la première fois)
4. L'application s'ouvre automatiquement dans votre navigateur

**L'application est accessible sur :** http://localhost:8080

---

## Arrêter l'application

Double-cliquez sur **stop.bat**

---

## Si ça ne fonctionne plus

Les cookies Vinted expirent après quelques jours. Si l'application affiche des erreurs :

1. Refaites l'étape **2.3** pour récupérer de nouveaux cookies
2. Mettez à jour le fichier `.env`
3. Relancez avec **start.bat**

---

## Aide

En cas de problème :
- Vérifiez que Docker Desktop est bien lancé (icône dans la barre des tâches)
- Vérifiez que le fichier `.env` existe (pas `.env.example`)
- Vérifiez que vous avez bien copié vos cookies en entier
