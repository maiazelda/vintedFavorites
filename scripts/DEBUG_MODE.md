# Mode Debug - Vinted Session Manager

## Pourquoi utiliser le mode debug ?

Quand les sélecteurs CSS changent sur le site Vinted, le script automatique ne peut plus trouver les boutons/champs de connexion. Le mode debug vous permet :
- De **voir** la fenêtre du navigateur en temps réel
- D'**intervenir manuellement** si le script ne trouve pas un élément
- D'**identifier** les nouveaux sélecteurs à utiliser

## Comment utiliser le mode debug (RECOMMANDÉ - Hors Docker)

Pour voir la fenêtre du navigateur, il faut exécuter le script **en dehors de Docker** :

### 1. Installation des dépendances (première fois uniquement)

```bash
cd scripts
npm install playwright
npx playwright install chromium
```

### 2. Lancer le script en mode debug

```bash
cd scripts
HEADLESS=false DEBUG_MODE=true VINTED_EMAIL="votre@email.com" VINTED_PASSWORD="votre_mot_de_passe" node vinted-session-manager.js
```

### 3. Que se passe-t-il ?

Le script va :
1. **Ouvrir une fenêtre Chrome visible**
2. **Afficher des messages à chaque étape** avec des pauses :
   - ÉTAPE 1 : Recherche du bouton "S'inscrire | Se connecter" (pause 15s)
   - ÉTAPE 1.5 : Clic sur le lien "e-mail" dans la popup "Bienvenue !" (pause 10s)
   - ÉTAPE 2 : Remplissage email/mot de passe (pause 10s)
   - ÉTAPE 3 : Clic sur "Continuer" (pause 10s)
   - ÉTAPE 4 : Vérification de la connexion (pause 15s)

3. **Vous laisser intervenir** si les sélecteurs automatiques échouent

### 4. Si le script ne trouve pas un élément

Quand vous voyez un message comme :
```
⚠️  Aucun bouton trouvé automatiquement.
Veuillez cliquer manuellement sur "Se connecter"
Pause de 20 secondes...
```

➡️ **Cliquez manuellement** sur l'élément dans la fenêtre du navigateur
➡️ Le script continuera automatiquement après la pause

### 5. Après une connexion réussie

Une fois connecté, le script va :
- Extraire automatiquement tous les cookies
- Les envoyer à l'API backend
- Fermer le navigateur

## Option alternative : Mode debug dans Docker (complexe)

Si vous voulez absolument utiliser Docker, il faut configurer X11 forwarding :

```bash
# Sur Linux uniquement
xhost +local:docker

# Modifier docker-compose.yml pour ajouter :
environment:
  DISPLAY: $DISPLAY
  HEADLESS: "false"
  DEBUG_MODE: "true"
volumes:
  - /tmp/.X11-unix:/tmp/.X11-unix
```

⚠️ **Cette méthode est complexe et ne fonctionne que sur Linux avec un serveur X11.**

## Identifier de nouveaux sélecteurs

Si vous voulez m'indiquer les nouveaux sélecteurs CSS :

1. **Ouvrez DevTools** dans la fenêtre du navigateur (F12)
2. **Cliquez sur l'icône de sélection** (en haut à gauche de DevTools)
3. **Cliquez sur le bouton/champ** qui pose problème
4. **Regardez dans l'onglet Elements** pour voir :
   - Les attributs `data-testid`
   - Les classes CSS
   - Les attributs `name`, `id`, etc.

5. **Communiquez-moi** ces informations, par exemple :
   ```
   Le bouton "Se connecter" a maintenant :
   - data-testid="auth-login-button"
   - class="Button__primary--new"
   ```

Je pourrai alors mettre à jour les sélecteurs dans le script !

## Raccourcis

### Mode normal (headless, sans debug)
```bash
cd scripts
VINTED_EMAIL="votre@email.com" VINTED_PASSWORD="votre_mot_de_passe" node vinted-session-manager.js
```

### Mode visible mais sans pauses debug
```bash
cd scripts
HEADLESS=false VINTED_EMAIL="votre@email.com" VINTED_PASSWORD="votre_mot_de_passe" node vinted-session-manager.js
```

### Mode debug complet (visible + pauses longues)
```bash
cd scripts
HEADLESS=false DEBUG_MODE=true VINTED_EMAIL="votre@email.com" VINTED_PASSWORD="votre_mot_de_passe" node vinted-session-manager.js
```
