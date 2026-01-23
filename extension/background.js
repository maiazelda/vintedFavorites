/**
 * background.js - Service Worker de l'extension
 *
 * Ce fichier tourne en arrière-plan et gère :
 * 1. La récupération des cookies Vinted
 * 2. Les appels à l'API Vinted pour récupérer les favoris
 * 3. L'envoi des favoris vers ton serveur backend
 *
 * C'est le "cerveau" de l'extension.
 */

// ============================================
// CONFIGURATION
// ============================================
const VINTED_DOMAIN = 'www.vinted.fr';
const VINTED_API_BASE = 'https://www.vinted.fr/api/v2';

// ============================================
// GESTION DES MESSAGES
// ============================================

/**
 * Écoute les messages venant du popup
 */
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.action === 'syncFavorites') {
    // On doit retourner true pour indiquer qu'on répond de manière asynchrone
    handleSyncFavorites(message.serverUrl)
      .then(sendResponse)
      .catch(error => sendResponse({ success: false, error: error.message }));
    return true; // Important: indique une réponse asynchrone
  }
});

// ============================================
// SYNCHRONISATION DES FAVORIS
// ============================================

/**
 * Fonction principale de synchronisation
 * @param {string} serverUrl - URL du backend
 */
async function handleSyncFavorites(serverUrl) {
  console.log('🚀 Début synchronisation vers:', serverUrl);

  try {
    // Étape 1: Récupérer les cookies Vinted
    const cookies = await getVintedCookies();
    console.log('🍪 Cookies récupérés:', cookies.length);

    // Étape 2: Récupérer l'ID utilisateur
    const userId = await getCurrentUserId(cookies);
    console.log('👤 User ID:', userId);

    // Étape 3: Récupérer tous les favoris (avec pagination)
    const favorites = await fetchAllFavorites(cookies, userId);
    console.log('❤️ Favoris récupérés:', favorites.length);

    // Étape 4: Envoyer au backend
    await sendToBackend(serverUrl, favorites, cookies);
    console.log('✅ Envoyé au backend');

    return { success: true, count: favorites.length };

  } catch (error) {
    console.error('❌ Erreur sync:', error);
    return { success: false, error: error.message };
  }
}

// ============================================
// COOKIES
// ============================================

/**
 * Récupère tous les cookies Vinted
 */
async function getVintedCookies() {
  const cookies = await chrome.cookies.getAll({ domain: VINTED_DOMAIN });

  if (cookies.length === 0) {
    throw new Error('Aucun cookie Vinted trouvé. Es-tu connecté ?');
  }

  return cookies;
}

/**
 * Construit le header Cookie à partir des cookies
 */
function buildCookieHeader(cookies) {
  return cookies.map(c => `${c.name}=${c.value}`).join('; ');
}

// ============================================
// API VINTED
// ============================================

/**
 * Récupère l'ID de l'utilisateur connecté
 */
async function getCurrentUserId(cookies) {
  const cookieHeader = buildCookieHeader(cookies);

  // Appel à l'API "whoami" ou session de Vinted
  const response = await fetch(`${VINTED_API_BASE}/users/current`, {
    headers: {
      'Cookie': cookieHeader,
      'Accept': 'application/json',
    },
    credentials: 'include'
  });

  if (!response.ok) {
    // Fallback: essayer de lire l'ID depuis un cookie
    const userIdCookie = cookies.find(c => c.name === 'v_uid' || c.name.includes('user_id'));
    if (userIdCookie) {
      return userIdCookie.value;
    }
    throw new Error('Impossible de récupérer ton ID utilisateur');
  }

  const data = await response.json();
  return data.user?.id || data.id;
}

/**
 * Récupère tous les favoris avec pagination
 */
async function fetchAllFavorites(cookies, userId) {
  const cookieHeader = buildCookieHeader(cookies);
  const allFavorites = [];
  let page = 1;
  let hasMore = true;

  while (hasMore) {
    console.log(`📄 Récupération page ${page}...`);

    const url = `${VINTED_API_BASE}/users/${userId}/items/favourites?page=${page}&per_page=96`;

    const response = await fetch(url, {
      headers: {
        'Cookie': cookieHeader,
        'Accept': 'application/json',
      },
      credentials: 'include'
    });

    if (!response.ok) {
      if (response.status === 401) {
        throw new Error('Session expirée. Reconnecte-toi à Vinted.');
      }
      throw new Error(`Erreur API Vinted: ${response.status}`);
    }

    const data = await response.json();
    const items = data.items || data.favourite_items || [];

    if (items.length === 0) {
      hasMore = false;
    } else {
      // Transforme les items au format attendu par le backend
      const formattedItems = items.map(item => formatFavorite(item));
      allFavorites.push(...formattedItems);
      page++;

      // Sécurité: max 50 pages (4800 favoris)
      if (page > 50) {
        console.warn('⚠️ Limite de pages atteinte');
        hasMore = false;
      }
    }

    // Petit délai pour ne pas surcharger l'API
    await sleep(500);
  }

  return allFavorites;
}

/**
 * Formate un favori pour le backend
 */
function formatFavorite(item) {
  return {
    vintedId: item.id?.toString(),
    title: item.title,
    brand: item.brand_title || item.brand?.title || null,
    category: item.catalog_id?.toString() || null,
    price: parseFloat(item.price?.amount || item.price || 0),
    size: item.size_title || item.size?.title || null,
    condition: item.status || null,
    imageUrl: item.photo?.url || item.photos?.[0]?.url || null,
    productUrl: item.url || `https://www.vinted.fr/items/${item.id}`,
    seller: item.user?.login || null,
    sold: item.is_closed || item.status === 'sold' || false
  };
}

// ============================================
// ENVOI AU BACKEND
// ============================================

/**
 * Envoie les favoris au serveur backend
 */
async function sendToBackend(serverUrl, favorites, cookies) {
  // Normalise l'URL
  const baseUrl = serverUrl.replace(/\/$/, '');

  // Envoie aussi les cookies pour que le backend puisse faire des appels enrichis
  const payload = {
    favorites: favorites,
    cookies: cookies.map(c => ({
      name: c.name,
      value: c.value,
      domain: c.domain,
      path: c.path,
      expirationDate: c.expirationDate
    }))
  };

  const response = await fetch(`${baseUrl}/api/extension/sync`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Erreur serveur: ${response.status} - ${errorText}`);
  }

  return response.json();
}

// ============================================
// UTILITAIRES
// ============================================

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// Log au démarrage
console.log('🧩 Vinted Favorites Sync - Service Worker démarré');
