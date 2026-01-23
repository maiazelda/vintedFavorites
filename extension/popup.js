/**
 * popup.js - Logique du popup de l'extension
 *
 * Ce fichier gère :
 * 1. La vérification de la connexion à Vinted
 * 2. La sauvegarde de la config (URL du serveur)
 * 3. Le déclenchement de la synchronisation
 */

// ============================================
// ÉLÉMENTS DU DOM
// ============================================
const elements = {
  status: document.getElementById('status'),
  statusIcon: document.getElementById('status-icon'),
  statusText: document.getElementById('status-text'),
  serverUrl: document.getElementById('server-url'),
  apiKey: document.getElementById('api-key'),
  saveConfig: document.getElementById('save-config'),
  syncBtn: document.getElementById('sync-btn'),
  syncIcon: document.getElementById('sync-icon'),
  syncText: document.getElementById('sync-text'),
  result: document.getElementById('result'),
  resultText: document.getElementById('result-text'),
  openApp: document.getElementById('open-app')
};

// ============================================
// CONFIGURATION
// ============================================
const VINTED_DOMAIN = 'www.vinted.fr';

/**
 * Charge la configuration sauvegardée
 */
async function loadConfig() {
  const { serverUrl, apiKey } = await chrome.storage.local.get(['serverUrl', 'apiKey']);
  if (serverUrl) {
    elements.serverUrl.value = serverUrl;
    elements.openApp.href = serverUrl;
    elements.openApp.classList.remove('hidden');
  }
  if (apiKey) {
    elements.apiKey.value = apiKey;
  }
}

/**
 * Sauvegarde la configuration
 */
async function saveConfig() {
  const serverUrl = elements.serverUrl.value.trim();
  const apiKey = elements.apiKey.value.trim();

  if (!serverUrl) {
    showResult('Veuillez entrer une URL', 'error');
    return;
  }
  if (!apiKey) {
    showResult('Veuillez entrer la clé API', 'error');
    return;
  }

  try {
    new URL(serverUrl); // Valide l'URL
    await chrome.storage.local.set({ serverUrl, apiKey });
    elements.openApp.href = serverUrl;
    elements.openApp.classList.remove('hidden');
    showResult('Configuration sauvegardée !', 'success');
  } catch (e) {
    showResult('URL invalide', 'error');
  }
}

// ============================================
// VÉRIFICATION CONNEXION VINTED
// ============================================

/**
 * Vérifie si l'utilisateur est connecté à Vinted
 * en regardant les cookies de session
 */
async function checkVintedConnection() {
  try {
    // Récupère les cookies Vinted
    const cookies = await chrome.cookies.getAll({ domain: VINTED_DOMAIN });

    // Cherche le cookie d'authentification
    // Vinted utilise "_vinted_fr_session" ou "access_token_web"
    const sessionCookie = cookies.find(c =>
      c.name === '_vinted_fr_session' ||
      c.name === 'access_token_web' ||
      c.name.includes('session')
    );

    if (sessionCookie) {
      setStatus('connected', '✅', 'Connecté à Vinted');
      elements.syncBtn.disabled = false;
      return true;
    } else {
      setStatus('disconnected', '❌', 'Non connecté à Vinted');
      elements.syncBtn.disabled = true;
      return false;
    }
  } catch (error) {
    console.error('Erreur vérification cookies:', error);
    setStatus('disconnected', '⚠️', 'Erreur de vérification');
    elements.syncBtn.disabled = true;
    return false;
  }
}

/**
 * Met à jour l'affichage du statut
 */
function setStatus(state, icon, text) {
  elements.status.className = `status ${state}`;
  elements.statusIcon.textContent = icon;
  elements.statusText.textContent = text;
}

// ============================================
// SYNCHRONISATION
// ============================================

/**
 * Lance la synchronisation des favoris
 * Envoie un message au background.js qui fait le travail
 */
async function startSync() {
  const serverUrl = elements.serverUrl.value.trim();
  const apiKey = elements.apiKey.value.trim();

  if (!serverUrl) {
    showResult('Configure d\'abord l\'URL du serveur', 'error');
    return;
  }
  if (!apiKey) {
    showResult('Configure d\'abord la clé API', 'error');
    return;
  }

  // UI: état chargement
  elements.syncBtn.classList.add('loading');
  elements.syncBtn.disabled = true;
  elements.syncText.textContent = 'Synchronisation...';
  elements.result.classList.add('hidden');

  try {
    // Envoie le message au service worker (background.js)
    const response = await chrome.runtime.sendMessage({
      action: 'syncFavorites',
      serverUrl: serverUrl,
      apiKey: apiKey
    });

    if (response.success) {
      showResult(`✅ ${response.count} favoris synchronisés !`, 'success');
    } else {
      showResult(`❌ Erreur: ${response.error}`, 'error');
    }
  } catch (error) {
    console.error('Erreur sync:', error);
    showResult(`❌ Erreur: ${error.message}`, 'error');
  } finally {
    // UI: reset
    elements.syncBtn.classList.remove('loading');
    elements.syncBtn.disabled = false;
    elements.syncText.textContent = 'Synchroniser';
  }
}

/**
 * Affiche un message de résultat
 */
function showResult(message, type) {
  elements.result.className = `result ${type}`;
  elements.resultText.textContent = message;
  elements.result.classList.remove('hidden');
}

// ============================================
// INITIALISATION
// ============================================

document.addEventListener('DOMContentLoaded', async () => {
  // Charge la config sauvegardée
  await loadConfig();

  // Vérifie la connexion Vinted
  await checkVintedConnection();

  // Event listeners
  elements.saveConfig.addEventListener('click', saveConfig);
  elements.syncBtn.addEventListener('click', startSync);

  // Sauvegarde aussi avec Entrée dans le champ URL
  elements.serverUrl.addEventListener('keypress', (e) => {
    if (e.key === 'Enter') saveConfig();
  });
});
