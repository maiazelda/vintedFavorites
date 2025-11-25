# Intégration avec le Front React

## Endpoints disponibles

### 1. Vérifier le statut de la session
```javascript
const checkSession = async () => {
  const response = await fetch('http://localhost:8080/api/vinted/session/status');
  const data = await response.json();
  console.log('Session valide:', data.valid);
  return data.valid;
};
```

### 2. Configurer les cookies (au premier lancement)
```javascript
const configureCookies = async (cookieString, anonId) => {
  const response = await fetch('http://localhost:8080/api/vinted/cookies', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      rawCookies: cookieString, // La chaîne complète de cookies
      anonId: anonId // Ex: "90da4fb5-8fb5-43a5-8ba2-e4fe532512e3"
    })
  });

  const data = await response.json();
  return data;
};
```

### 3. Synchroniser les favoris
```javascript
const syncFavorites = async () => {
  const response = await fetch('http://localhost:8080/api/vinted/sync', {
    method: 'POST',
  });

  const data = await response.json();
  console.log(`${data.newCount} nouveaux favoris, ${data.totalCount} au total`);
  return data;
};
```

### 4. Forcer l'enrichissement des favoris incomplets
```javascript
const enrichFavorites = async () => {
  const response = await fetch('http://localhost:8080/api/vinted/favorites/enrich', {
    method: 'POST',
  });

  const data = await response.json();
  console.log(`${data.enriched} favoris enrichis sur ${data.total}`);
  return data;
};
```

### 5. Récupérer tous les favoris
```javascript
const getFavorites = async () => {
  const response = await fetch('http://localhost:8080/api/favorites');
  const favorites = await response.json();
  return favorites;
};
```

### 6. Filtrer les favoris
```javascript
const filterFavorites = async (filters) => {
  const params = new URLSearchParams();
  if (filters.brand) params.append('brand', filters.brand);
  if (filters.gender) params.append('gender', filters.gender);
  if (filters.category) params.append('category', filters.category);
  if (filters.sold !== undefined) params.append('sold', filters.sold);

  const response = await fetch(`http://localhost:8080/api/favorites/filter?${params}`);
  const favorites = await response.json();
  return favorites;
};
```

## Exemple de composant React complet

```jsx
import React, { useState, useEffect } from 'react';

function VintedFavorites() {
  const [favorites, setFavorites] = useState([]);
  const [loading, setLoading] = useState(false);
  const [sessionValid, setSessionValid] = useState(false);
  const [filters, setFilters] = useState({
    brand: '',
    gender: '',
    category: '',
    sold: null
  });

  // Vérifier la session au chargement
  useEffect(() => {
    checkSession();
  }, []);

  const checkSession = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/vinted/session/status');
      const data = await response.json();
      setSessionValid(data.valid);

      if (data.valid) {
        loadFavorites();
      }
    } catch (error) {
      console.error('Erreur vérification session:', error);
    }
  };

  const loadFavorites = async () => {
    setLoading(true);
    try {
      const response = await fetch('http://localhost:8080/api/favorites');
      const data = await response.json();
      setFavorites(data);
    } catch (error) {
      console.error('Erreur chargement favoris:', error);
    } finally {
      setLoading(false);
    }
  };

  const syncFavorites = async () => {
    setLoading(true);
    try {
      const response = await fetch('http://localhost:8080/api/vinted/sync', {
        method: 'POST'
      });
      const data = await response.json();

      if (data.success) {
        alert(`✅ ${data.newCount} nouveaux favoris synchronisés !`);
        loadFavorites(); // Recharger la liste
      } else {
        alert(`❌ Erreur: ${data.message}`);
      }
    } catch (error) {
      console.error('Erreur sync:', error);
      alert('❌ Erreur lors de la synchronisation');
    } finally {
      setLoading(false);
    }
  };

  const enrichIncomplete = async () => {
    setLoading(true);
    try {
      const response = await fetch('http://localhost:8080/api/vinted/favorites/enrich', {
        method: 'POST'
      });
      const data = await response.json();

      if (data.success) {
        alert(`✅ ${data.enriched} favoris enrichis !`);
        loadFavorites(); // Recharger la liste
      } else {
        alert(`❌ Erreur: ${data.message}`);
      }
    } catch (error) {
      console.error('Erreur enrichissement:', error);
      alert('❌ Erreur lors de l\'enrichissement');
    } finally {
      setLoading(false);
    }
  };

  const applyFilters = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (filters.brand) params.append('brand', filters.brand);
      if (filters.gender) params.append('gender', filters.gender);
      if (filters.category) params.append('category', filters.category);
      if (filters.sold !== null) params.append('sold', filters.sold);

      const response = await fetch(`http://localhost:8080/api/favorites/filter?${params}`);
      const data = await response.json();
      setFavorites(data);
    } catch (error) {
      console.error('Erreur filtrage:', error);
    } finally {
      setLoading(false);
    }
  };

  const resetFilters = () => {
    setFilters({ brand: '', gender: '', category: '', sold: null });
    loadFavorites();
  };

  if (!sessionValid) {
    return (
      <div className="alert alert-warning">
        ⚠️ Session non valide. Veuillez configurer vos cookies Vinted.
      </div>
    );
  }

  return (
    <div className="container">
      <h1>Mes Favoris Vinted</h1>

      {/* Actions */}
      <div className="actions mb-3">
        <button
          onClick={syncFavorites}
          disabled={loading}
          className="btn btn-primary me-2"
        >
          🔄 Synchroniser
        </button>
        <button
          onClick={enrichIncomplete}
          disabled={loading}
          className="btn btn-secondary me-2"
        >
          ✨ Enrichir favoris incomplets
        </button>
        <button
          onClick={loadFavorites}
          disabled={loading}
          className="btn btn-info"
        >
          🔃 Rafraîchir
        </button>
      </div>

      {/* Filtres */}
      <div className="filters mb-3">
        <h3>Filtres</h3>
        <div className="row">
          <div className="col-md-3">
            <input
              type="text"
              className="form-control"
              placeholder="Marque"
              value={filters.brand}
              onChange={(e) => setFilters({...filters, brand: e.target.value})}
            />
          </div>
          <div className="col-md-3">
            <select
              className="form-control"
              value={filters.gender}
              onChange={(e) => setFilters({...filters, gender: e.target.value})}
            >
              <option value="">Tous les genres</option>
              <option value="Femme">Femme</option>
              <option value="Homme">Homme</option>
              <option value="Enfant">Enfant</option>
            </select>
          </div>
          <div className="col-md-3">
            <input
              type="text"
              className="form-control"
              placeholder="Catégorie"
              value={filters.category}
              onChange={(e) => setFilters({...filters, category: e.target.value})}
            />
          </div>
          <div className="col-md-3">
            <select
              className="form-control"
              value={filters.sold === null ? '' : filters.sold}
              onChange={(e) => setFilters({
                ...filters,
                sold: e.target.value === '' ? null : e.target.value === 'true'
              })}
            >
              <option value="">Tous</option>
              <option value="false">Disponibles</option>
              <option value="true">Vendus</option>
            </select>
          </div>
        </div>
        <div className="mt-2">
          <button onClick={applyFilters} className="btn btn-success me-2">
            Appliquer filtres
          </button>
          <button onClick={resetFilters} className="btn btn-outline-secondary">
            Réinitialiser
          </button>
        </div>
      </div>

      {/* Liste des favoris */}
      {loading && <div className="spinner-border" role="status"></div>}

      <div className="row">
        {favorites.map(favorite => (
          <div key={favorite.id} className="col-md-4 mb-3">
            <div className="card">
              {favorite.imageUrl && (
                <img
                  src={favorite.imageUrl}
                  className="card-img-top"
                  alt={favorite.title}
                />
              )}
              <div className="card-body">
                <h5 className="card-title">{favorite.title}</h5>
                <p className="card-text">
                  <strong>Marque:</strong> {favorite.brand || 'N/A'}<br/>
                  <strong>Genre:</strong> {favorite.gender || '⚠️ Non défini'}<br/>
                  <strong>Catégorie:</strong> {favorite.category || '⚠️ Non défini'}<br/>
                  <strong>Prix:</strong> {favorite.price} €<br/>
                  <strong>Taille:</strong> {favorite.size || 'N/A'}<br/>
                  <strong>État:</strong> {favorite.condition || 'N/A'}<br/>
                  <strong>Vendeur:</strong> {favorite.sellerName || 'N/A'}<br/>
                  {favorite.sold && <span className="badge bg-danger">Vendu</span>}
                </p>
                {favorite.productUrl && (
                  <a
                    href={favorite.productUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="btn btn-primary"
                  >
                    Voir sur Vinted
                  </a>
                )}
              </div>
            </div>
          </div>
        ))}
      </div>

      {favorites.length === 0 && !loading && (
        <div className="alert alert-info">
          Aucun favori trouvé. Cliquez sur "Synchroniser" pour importer vos favoris Vinted.
        </div>
      )}
    </div>
  );
}

export default VintedFavorites;
```

## Configuration

### application.properties

Ajoutez dans votre `src/main/resources/application.properties` :

```properties
# Port du front React (CORS)
app.cors.allowed-origins=http://localhost:3000,http://localhost:3001

# User ID Vinted (trouvable dans l'URL de votre profil)
vinted.api.user-id=11494046

# Synchronisation automatique au démarrage
vinted.sync.on-startup=false

# Intervalle de synchronisation (en ms, 30 minutes par défaut)
vinted.sync.interval=1800000
```

## Dépannage

### Les favoris n'ont pas de category/genre

1. **Synchronisez d'abord** : `POST /api/vinted/sync`
2. **Forcez l'enrichissement** : `POST /api/vinted/favorites/enrich`
3. **Vérifiez les logs** pour voir quels champs manquent

### Session expirée

Si vous voyez "Session expirée", reconfigurez vos cookies :
```javascript
await configureCookies(newCookieString, anonId);
```

Pour obtenir vos cookies :
1. Ouvrez Vinted dans votre navigateur
2. Ouvrez les DevTools (F12) → Network
3. Rafraîchissez la page
4. Copiez le header `Cookie` d'une requête à www.vinted.fr
