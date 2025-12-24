# Vinted Favorites - Mode d'emploi simple

## Installation (une seule fois)

### 1. Installer Docker Desktop
- Télécharger sur https://www.docker.com/products/docker-desktop/
- Installer et redémarrer l'ordinateur

### 2. Configurer l'application
1. Copier le fichier `.env.example` → le renommer `.env`
2. Ouvrir `.env` avec le Bloc-notes
3. Remplacer les valeurs :
   ```
   VINTED_USER_ID=ton_numero_vinted
   VINTED_EMAIL=ton@email.com
   VINTED_PASSWORD=tonmotdepasse
   ```
4. Sauvegarder et fermer

---

## Utilisation quotidienne

### Démarrer l'application
1. **Ouvrir Docker Desktop** (attendre qu'il soit prêt)
2. **Double-cliquer sur `start.bat`**
3. Attendre que la fenêtre s'ouvre automatiquement

### Arrêter l'application
- **Double-cliquer sur `stop.bat`**

---

## En cas de problème

| Problème | Solution |
|----------|----------|
| "Docker introuvable" | Ouvrir Docker Desktop et attendre qu'il démarre |
| "Session expirée" | Attendre 30 sec et cliquer sur "Synchroniser" |
| Rien ne s'affiche | Aller sur http://localhost:8080 dans le navigateur |

---

## Résumé en 3 étapes

```
1. Ouvrir Docker Desktop
2. Double-cliquer start.bat
3. Utiliser l'application sur http://localhost:8080
```
