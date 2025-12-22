import React, { useState, useEffect } from 'react';
import { Plus, X, Edit2, Trash2, ShoppingBag, AlertCircle, Menu, ChevronLeft, Tag, Users, Grid, TrendingUp, RefreshCw, Search } from 'lucide-react';

// URL de l'API - utilise une URL relative pour fonctionner avec nginx en production
// ou la variable d'environnement REACT_APP_API_URL pour le développement local
const API_BASE_URL = process.env.REACT_APP_API_URL || '/api/favorites';

const VintedFavoritesApp = () => {
  // ========================================
  // STATE - Les données qui changent dans l'application
  // ========================================
  
  const [favorites, setFavorites] = useState([]);
  const [filteredFavorites, setFilteredFavorites] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [syncing, setSyncing] = useState(false);

  // NOUVEAU : State pour la sidebar
  // Comme une variable boolean en Java qui contrôle l'affichage
  const [sidebarOpen, setSidebarOpen] = useState(true);

  // State pour la recherche
  const [searchQuery, setSearchQuery] = useState('');
  
  // Filtres
  const [filters, setFilters] = useState({
    brand: '',
    gender: '',
    category: '',
    sold: ''
  });
  
  // Modal
  const [showModal, setShowModal] = useState(false);
  const [editingFavorite, setEditingFavorite] = useState(null);
  const [formData, setFormData] = useState({
    title: '',
    brand: '',
    price: '',
    size: '',
    gender: '',
    category: '',
    productUrl: '',
    sold: false
  });

  // ========================================
  // useEffect - Chargement des données
  // ========================================
  
  useEffect(() => {
    fetchFavorites();
  }, []);

  useEffect(() => {
    applyFilters();
  }, [favorites, filters, searchQuery]);

  // ========================================
  // FONCTIONS API
  // ========================================
  
  const fetchFavorites = async () => {
    try {
      setLoading(true);
      const response = await fetch(API_BASE_URL);
      if (!response.ok) throw new Error('Erreur lors du chargement');
      const data = await response.json();
      setFavorites(data);
      setError(null);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  const addFavorite = async (favoriteData) => {
    try {
      const response = await fetch(API_BASE_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(favoriteData)
      });
      if (!response.ok) throw new Error('Erreur lors de l\'ajout');
      await fetchFavorites();
      closeModal();
    } catch (err) {
      setError(err.message);
    }
  };

  const updateFavorite = async (id, favoriteData) => {
    try {
      const response = await fetch(`${API_BASE_URL}/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(favoriteData)
      });
      if (!response.ok) throw new Error('Erreur lors de la modification');
      await fetchFavorites();
      closeModal();
    } catch (err) {
      setError(err.message);
    }
  };

  const deleteFavorite = async (id) => {
    if (!window.confirm('Supprimer ce favori ?')) return;
    try {
      const response = await fetch(`${API_BASE_URL}/${id}`, {
        method: 'DELETE'
      });
      if (!response.ok) throw new Error('Erreur lors de la suppression');
      await fetchFavorites();
    } catch (err) {
      setError(err.message);
    }
  };

  const syncVintedFavorites = async () => {
    try {
      setSyncing(true);
      setError(null);
      const response = await fetch('/api/vinted/sync', {
        method: 'POST'
      });
      if (!response.ok) throw new Error('Erreur lors de la synchronisation');
      await fetchFavorites();
    } catch (err) {
      setError(err.message);
    } finally {
      setSyncing(false);
    }
  };

  // ========================================
  // FONCTIONS DE FILTRAGE
  // ========================================
  
  const applyFilters = () => {
    let result = [...favorites];

    // Recherche globale
    if (searchQuery) {
      const query = searchQuery.toLowerCase();
      result = result.filter(fav =>
        fav.title?.toLowerCase().includes(query) ||
        fav.brand?.toLowerCase().includes(query) ||
        fav.category?.toLowerCase().includes(query) ||
        fav.gender?.toLowerCase().includes(query)
      );
    }

    // Filtres de sidebar
    if (filters.brand) {
      result = result.filter(fav =>
        fav.brand?.toLowerCase().includes(filters.brand.toLowerCase())
      );
    }
    if (filters.gender) {
      result = result.filter(fav => fav.gender === filters.gender);
    }
    if (filters.category) {
      result = result.filter(fav =>
        fav.category?.toLowerCase().includes(filters.category.toLowerCase())
      );
    }
    if (filters.sold !== '') {
      result = result.filter(fav => fav.sold === (filters.sold === 'true'));
    }

    setFilteredFavorites(result);
  };

  const resetFilters = () => {
    setFilters({
      brand: '',
      gender: '',
      category: '',
      sold: ''
    });
  };

  // ========================================
  // FONCTIONS MODAL
  // ========================================
  
  const openAddModal = () => {
    setEditingFavorite(null);
    setFormData({
      title: '',
      brand: '',
      price: '',
      size: '',
      gender: '',
      category: '',
      productUrl: '',
      sold: false
    });
    setShowModal(true);
  };

  const openEditModal = (favorite) => {
    setEditingFavorite(favorite);
    setFormData({
      title: favorite.title || '',
      brand: favorite.brand || '',
      price: favorite.price || '',
      size: favorite.size || '',
      gender: favorite.gender || '',
      category: favorite.category || '',
      productUrl: favorite.productUrl || '',
      sold: favorite.sold || false
    });
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setEditingFavorite(null);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (editingFavorite) {
      updateFavorite(editingFavorite.id, formData);
    } else {
      addFavorite(formData);
    }
  };

  // ========================================
  // EXTRACTION DES VALEURS UNIQUES
  // ========================================
  
  const uniqueGenres = [...new Set(favorites.map(f => f.gender).filter(Boolean))];
    const uniqueMarques = [...new Set(favorites.map(f => f.brand).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'fr', { sensitivity: 'base' }));
    const uniqueCategories = [...new Set(favorites.map(f => f.category).filter(Boolean))];

  // Statistiques pour la sidebar
  const stats = {
    total: favorites.length,
    disponibles: favorites.filter(f => !f.sold).length,
    vendus: favorites.filter(f => f.sold).length,
    prixMoyen: favorites.length > 0
      ? (favorites.reduce((acc, f) => acc + (parseFloat(f.price) || 0), 0) / favorites.length).toFixed(2)
      : 0
  };

  // ========================================
  // RENDU JSX
  // ========================================
  
  if (loading) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh',
        background: '#0a0e27',
        color: '#00ff9d',
        fontFamily: '"Roboto Mono", monospace',
        fontSize: '18px',
        letterSpacing: '2px'
      }}>
        CHARGEMENT...
      </div>
    );
  }

  return (
    <div style={{
      minHeight: '100vh',
      background: '#0a0e27',
      color: '#e4e7eb',
      fontFamily: '"Inter", sans-serif',
      display: 'flex'
    }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Rajdhani:wght@600;700&family=Roboto+Mono:wght@400;500;700&family=Inter:wght@400;500;600;700&display=swap');
        
        * {
          margin: 0;
          padding: 0;
          box-sizing: border-box;
        }
        
        body {
          overflow-x: hidden;
        }
        
        .sidebar {
          transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
        }
        
        .card-dark {
          background: linear-gradient(135deg, #1a1f3a 0%, #0f1729 100%);
          border: 1px solid rgba(0, 255, 157, 0.1);
          border-radius: 4px;
          transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
          position: relative;
          overflow: hidden;
        }
        
        .card-dark::before {
          content: '';
          position: absolute;
          top: 0;
          left: 0;
          right: 0;
          height: 2px;
          background: linear-gradient(90deg, transparent, #00ff9d, transparent);
          opacity: 0;
          transition: opacity 0.3s;
        }
        
        .card-dark:hover::before {
          opacity: 1;
        }
        
        .card-dark:hover {
          transform: translateY(-4px);
          border-color: rgba(0, 255, 157, 0.3);
          box-shadow: 0 8px 32px rgba(0, 255, 157, 0.1);
        }
        
        .btn-dark {
          border: none;
          padding: 12px 24px;
          border-radius: 2px;
          font-weight: 600;
          cursor: pointer;
          transition: all 0.2s;
          font-family: 'Roboto Mono', monospace;
          font-size: 13px;
          text-transform: uppercase;
          letter-spacing: 1px;
          position: relative;
          overflow: hidden;
        }
        
        .btn-dark::before {
          content: '';
          position: absolute;
          top: 0;
          left: -100%;
          width: 100%;
          height: 100%;
          background: rgba(255, 255, 255, 0.1);
          transition: left 0.3s;
        }
        
        .btn-dark:hover::before {
          left: 100%;
        }
        
        .btn-primary-dark {
          background: linear-gradient(135deg, #00ff9d 0%, #00b8ff 100%);
          color: #0a0e27;
          box-shadow: 0 4px 15px rgba(0, 255, 157, 0.3);
        }
        
        .btn-primary-dark:hover {
          transform: translateY(-2px);
          box-shadow: 0 6px 20px rgba(0, 255, 157, 0.4);
        }
        
        .btn-danger-dark {
          background: linear-gradient(135deg, #ff4757 0%, #ff6348 100%);
          color: white;
        }
        
        .btn-secondary-dark {
          background: rgba(255, 255, 255, 0.05);
          color: #e4e7eb;
          border: 1px solid rgba(255, 255, 255, 0.1);
        }
        
        .btn-secondary-dark:hover {
          background: rgba(255, 255, 255, 0.1);
          border-color: rgba(0, 255, 157, 0.3);
        }
        
        input, select {
          width: 100%;
          padding: 12px 16px;
          background: rgba(255, 255, 255, 0.05);
          border: 1px solid rgba(255, 255, 255, 0.1);
          border-radius: 2px;
          font-family: 'Inter', sans-serif;
          font-size: 14px;
          color: #e4e7eb;
          transition: all 0.2s;
        }
        
        input:focus, select:focus {
          outline: none;
          border-color: #00ff9d;
          box-shadow: 0 0 0 3px rgba(0, 255, 157, 0.1);
          background: rgba(255, 255, 255, 0.08);
        }
        
        input::placeholder {
          color: rgba(228, 231, 235, 0.4);
        }
        
        .badge-dark {
          display: inline-block;
          padding: 6px 12px;
          border-radius: 2px;
          font-size: 11px;
          font-weight: 700;
          text-transform: uppercase;
          letter-spacing: 1px;
          font-family: 'Roboto Mono', monospace;
        }
        
        .badge-sold-dark {
          background: rgba(255, 71, 87, 0.2);
          color: #ff4757;
          border: 1px solid rgba(255, 71, 87, 0.3);
        }
        
        .badge-available-dark {
          background: rgba(0, 255, 157, 0.2);
          color: #00ff9d;
          border: 1px solid rgba(0, 255, 157, 0.3);
        }
        
        .sidebar-item {
          padding: 12px 20px;
          cursor: pointer;
          transition: all 0.2s;
          border-left: 3px solid transparent;
          display: flex;
          align-items: center;
          gap: 12px;
          font-size: 14px;
        }
        
        .sidebar-item:hover {
          background: rgba(0, 255, 157, 0.05);
          border-left-color: #00ff9d;
          padding-left: 24px;
        }
        
        .sidebar-item.active {
          background: rgba(0, 255, 157, 0.1);
          border-left-color: #00ff9d;
          color: #00ff9d;
        }
        
        @keyframes slideInLeft {
          from {
            opacity: 0;
            transform: translateX(-30px);
          }
          to {
            opacity: 1;
            transform: translateX(0);
          }
        }

        @keyframes spin {
          from {
            transform: rotate(0deg);
          }
          to {
            transform: rotate(360deg);
          }
        }

        .animate-in-left {
          animation: slideInLeft 0.4s ease-out forwards;
        }
        
        /* Scrollbar personnalisée */
        ::-webkit-scrollbar {
          width: 8px;
        }
        
        ::-webkit-scrollbar-track {
          background: #0a0e27;
        }
        
        ::-webkit-scrollbar-thumb {
          background: rgba(0, 255, 157, 0.3);
          border-radius: 4px;
        }
        
        ::-webkit-scrollbar-thumb:hover {
          background: rgba(0, 255, 157, 0.5);
        }
      `}</style>

      {/* ========================================
          SIDEBAR RÉTRACTABLE
          Concept : Rendu conditionnel basé sur le state sidebarOpen
          transform: translateX(-100%) cache la sidebar hors de l'écran
          ======================================== */}
      <div 
        className="sidebar"
        style={{
          width: '280px',
          background: 'linear-gradient(180deg, #0f1729 0%, #0a0e27 100%)',
          borderRight: '1px solid rgba(0, 255, 157, 0.1)',
          height: '100vh',
          position: 'fixed',
          left: 0,
          top: 0,
          zIndex: 100,
          transform: sidebarOpen ? 'translateX(0)' : 'translateX(-100%)',
          overflowY: 'auto'
        }}
      >
        {/* Header Sidebar */}
        <div style={{
          padding: '24px 20px',
          borderBottom: '1px solid rgba(0, 255, 157, 0.1)',
          background: 'rgba(0, 255, 157, 0.05)'
        }}>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '8px'
          }}>
            <h2 style={{
              fontSize: '20px',
              fontFamily: '"Rajdhani", sans-serif',
              fontWeight: 700,
              color: '#00ff9d',
              letterSpacing: '1px',
              textTransform: 'uppercase'
            }}>
              VINTED TRACKER
            </h2>
            <button
              onClick={() => setSidebarOpen(false)}
              style={{
                background: 'none',
                border: 'none',
                color: '#e4e7eb',
                cursor: 'pointer',
                padding: '4px'
              }}
            >
              <ChevronLeft size={20} />
            </button>
          </div>
          <p style={{
            fontSize: '11px',
            color: 'rgba(228, 231, 235, 0.6)',
            fontFamily: '"Roboto Mono", monospace',
            letterSpacing: '0.5px'
          }}>
            SYSTÈME DE GESTION
          </p>
        </div>

        {/* Statistiques */}
        <div style={{ padding: '20px' }}>
          <h3 style={{
            fontSize: '11px',
            color: 'rgba(228, 231, 235, 0.5)',
            fontFamily: '"Roboto Mono", monospace',
            letterSpacing: '1px',
            marginBottom: '16px',
            textTransform: 'uppercase'
          }}>
            Statistiques
          </h3>
          <div style={{
            display: 'grid',
            gap: '12px'
          }}>
            <div style={{
              background: 'rgba(0, 255, 157, 0.05)',
              padding: '12px',
              borderRadius: '2px',
              border: '1px solid rgba(0, 255, 157, 0.1)'
            }}>
              <div style={{
                fontSize: '24px',
                fontWeight: 700,
                color: '#00ff9d',
                fontFamily: '"Rajdhani", sans-serif'
              }}>
                {stats.total}
              </div>
              <div style={{
                fontSize: '11px',
                color: 'rgba(228, 231, 235, 0.6)',
                textTransform: 'uppercase',
                letterSpacing: '0.5px'
              }}>
                Total articles
              </div>
            </div>
            <div style={{
              background: 'rgba(255, 255, 255, 0.03)',
              padding: '12px',
              borderRadius: '2px',
              border: '1px solid rgba(255, 255, 255, 0.05)'
            }}>
              <div style={{
                fontSize: '24px',
                fontWeight: 700,
                color: '#00b8ff',
                fontFamily: '"Rajdhani", sans-serif'
              }}>
                {stats.disponibles}
              </div>
              <div style={{
                fontSize: '11px',
                color: 'rgba(228, 231, 235, 0.6)',
                textTransform: 'uppercase',
                letterSpacing: '0.5px'
              }}>
                Disponibles
              </div>
            </div>
            <div style={{
              background: 'rgba(255, 255, 255, 0.03)',
              padding: '12px',
              borderRadius: '2px',
              border: '1px solid rgba(255, 255, 255, 0.05)'
            }}>
              <div style={{
                fontSize: '24px',
                fontWeight: 700,
                color: '#ff6348',
                fontFamily: '"Rajdhani", sans-serif'
              }}>
                {stats.prixMoyen}€
              </div>
              <div style={{
                fontSize: '11px',
                color: 'rgba(228, 231, 235, 0.6)',
                textTransform: 'uppercase',
                letterSpacing: '0.5px'
              }}>
                Prix moyen
              </div>
            </div>
          </div>
        </div>

        {/* Filtres par Genre */}
        <div style={{ padding: '20px', borderTop: '1px solid rgba(255, 255, 255, 0.05)' }}>
          <h3 style={{
            fontSize: '11px',
            color: 'rgba(228, 231, 235, 0.5)',
            fontFamily: '"Roboto Mono", monospace',
            letterSpacing: '1px',
            marginBottom: '12px',
            textTransform: 'uppercase',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            <Users size={14} />
            Genre
          </h3>
          <div
            className="sidebar-item"
            onClick={() => setFilters({...filters, gender: ''})}
            style={{ color: filters.gender === '' ? '#00ff9d' : '#e4e7eb' }}
          >
            <Grid size={16} />
            Tous
          </div>
          {uniqueGenres.map(gender => (
            <div
              key={gender}
              className="sidebar-item"
              onClick={() => setFilters({...filters, gender})}
              style={{ color: filters.gender === gender ? '#00ff9d' : '#e4e7eb' }}
            >
              {gender}
            </div>
          ))}
        </div>

        {/* Filtres par Marque */}
        <div style={{ padding: '20px', borderTop: '1px solid rgba(255, 255, 255, 0.05)' }}>
          <h3 style={{
            fontSize: '11px',
            color: 'rgba(228, 231, 235, 0.5)',
            fontFamily: '"Roboto Mono", monospace',
            letterSpacing: '1px',
            marginBottom: '12px',
            textTransform: 'uppercase',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            <Tag size={14} />
            Marques
          </h3>
          <div
            className="sidebar-item"
            onClick={() => setFilters({...filters, brand: ''})}
            style={{ color: filters.brand === '' ? '#00ff9d' : '#e4e7eb' }}
          >
            <Grid size={16} />
            Toutes
          </div>
          {uniqueMarques.slice(0, 3000).map(brand => (
            <div
              key={brand}
              className="sidebar-item"
              onClick={() => setFilters({...filters, brand})}
              style={{ color: filters.brand === brand ? '#00ff9d' : '#e4e7eb' }}
            >
              {brand}
            </div>
          ))}
        </div>

        {/* Filtres par Catégorie */}
        <div style={{ padding: '20px', borderTop: '1px solid rgba(255, 255, 255, 0.05)' }}>
          <h3 style={{
            fontSize: '11px',
            color: 'rgba(228, 231, 235, 0.5)',
            fontFamily: '"Roboto Mono", monospace',
            letterSpacing: '1px',
            marginBottom: '12px',
            textTransform: 'uppercase',
            display: 'flex',
            alignItems: 'center',
            gap: '8px'
          }}>
            <TrendingUp size={14} />
            Catégories
          </h3>
          <div
            className="sidebar-item"
            onClick={() => setFilters({...filters, category: ''})}
            style={{ color: filters.category === '' ? '#00ff9d' : '#e4e7eb' }}
          >
            <Grid size={16} />
            Toutes
          </div>
          {uniqueCategories.slice(0, 8).map(cat => (
            <div
              key={cat}
              className="sidebar-item"
              onClick={() => setFilters({...filters, category: cat})}
              style={{ color: filters.category === cat ? '#00ff9d' : '#e4e7eb' }}
            >
              {cat}
            </div>
          ))}
        </div>

        {/* Statut */}
        <div style={{ padding: '20px', borderTop: '1px solid rgba(255, 255, 255, 0.05)' }}>
          <h3 style={{
            fontSize: '11px',
            color: 'rgba(228, 231, 235, 0.5)',
            fontFamily: '"Roboto Mono", monospace',
            letterSpacing: '1px',
            marginBottom: '12px',
            textTransform: 'uppercase'
          }}>
            Statut
          </h3>
          <div
            className="sidebar-item"
            onClick={() => setFilters({...filters, sold: ''})}
            style={{ color: filters.sold === '' ? '#00ff9d' : '#e4e7eb' }}
          >
            <Grid size={16} />
            Tous
          </div>
          <div
            className="sidebar-item"
            onClick={() => setFilters({...filters, sold: 'false'})}
            style={{ color: filters.sold === 'false' ? '#00ff9d' : '#e4e7eb' }}
          >
            Disponible
          </div>
          <div
            className="sidebar-item"
            onClick={() => setFilters({...filters, sold: 'true'})}
            style={{ color: filters.sold === 'true' ? '#00ff9d' : '#e4e7eb' }}
          >
            Vendu
          </div>
        </div>

        {/* Reset Button */}
        <div style={{ padding: '20px' }}>
          <button 
            className="btn-dark btn-secondary-dark"
            onClick={resetFilters}
            style={{ width: '100%' }}
          >
            Réinitialiser
          </button>
        </div>
      </div>

      {/* ========================================
          CONTENU PRINCIPAL
          margin-left change selon l'état de la sidebar
          ======================================== */}
      <div style={{
        flex: 1,
        marginLeft: sidebarOpen ? '280px' : '0',
        transition: 'margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
        minHeight: '100vh'
      }}>
        {/* Header avec bouton menu */}
        <div style={{
          background: 'linear-gradient(135deg, #0f1729 0%, #1a1f3a 100%)',
          borderBottom: '1px solid rgba(0, 255, 157, 0.1)',
          padding: '20px 32px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          position: 'sticky',
          top: 0,
          zIndex: 50,
          backdropFilter: 'blur(10px)',
          gap: '20px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
            {!sidebarOpen && (
              <button
                onClick={() => setSidebarOpen(true)}
                style={{
                  background: 'rgba(0, 255, 157, 0.1)',
                  border: '1px solid rgba(0, 255, 157, 0.3)',
                  color: '#00ff9d',
                  padding: '10px',
                  borderRadius: '2px',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center'
                }}
              >
                <Menu size={20} />
              </button>
            )}
            <div>
              <h1 style={{
                fontSize: '24px',
                fontFamily: '"Rajdhani", sans-serif',
                fontWeight: 700,
                color: '#00ff9d',
                letterSpacing: '2px'
              }}>
                MES FAVORIS
              </h1>
              <p style={{
                fontSize: '12px',
                color: 'rgba(228, 231, 235, 0.6)',
                fontFamily: '"Roboto Mono", monospace'
              }}>
                {filteredFavorites.length} / {favorites.length} articles
              </p>
            </div>
          </div>

          {/* Barre de recherche et bouton sync */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flex: 1, maxWidth: '600px' }}>
            <div style={{ position: 'relative', flex: 1 }}>
              <Search
                size={18}
                style={{
                  position: 'absolute',
                  left: '12px',
                  top: '50%',
                  transform: 'translateY(-50%)',
                  color: 'rgba(228, 231, 235, 0.4)'
                }}
              />
              <input
                type="text"
                placeholder="Rechercher un article..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                style={{
                  width: '100%',
                  padding: '12px 16px 12px 42px',
                  background: 'rgba(255, 255, 255, 0.05)',
                  border: '1px solid rgba(255, 255, 255, 0.1)',
                  borderRadius: '2px',
                  color: '#e4e7eb',
                  fontSize: '14px',
                  fontFamily: '"Inter", sans-serif'
                }}
              />
            </div>
            <button
              className="btn-dark btn-primary-dark"
              onClick={syncVintedFavorites}
              disabled={syncing}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                opacity: syncing ? 0.6 : 1,
                cursor: syncing ? 'not-allowed' : 'pointer',
                whiteSpace: 'nowrap'
              }}
            >
              <RefreshCw size={18} style={{ animation: syncing ? 'spin 1s linear infinite' : 'none' }} />
              {syncing ? 'Sync...' : 'Synchroniser'}
            </button>
          </div>
        </div>

        {/* Erreur */}
        {error && (
          <div style={{
            background: 'rgba(255, 71, 87, 0.1)',
            color: '#ff4757',
            padding: '16px 32px',
            margin: '20px 32px',
            borderRadius: '2px',
            display: 'flex',
            alignItems: 'center',
            gap: '12px',
            border: '1px solid rgba(255, 71, 87, 0.3)'
          }}>
            <AlertCircle size={20} />
            <span>{error}</span>
          </div>
        )}

        {/* Grille des favoris */}
        <div style={{ padding: '32px' }}>
          {filteredFavorites.length === 0 ? (
            <div style={{
              textAlign: 'center',
              padding: '80px 20px',
              color: 'rgba(228, 231, 235, 0.4)'
            }}>
              <ShoppingBag size={64} style={{ opacity: 0.3, marginBottom: '16px' }} />
              <h3 style={{ fontSize: '20px', marginBottom: '8px', fontFamily: '"Rajdhani", sans-serif' }}>
                AUCUN ARTICLE TROUVÉ
              </h3>
              <p style={{ fontFamily: '"Roboto Mono", monospace', fontSize: '13px' }}>
                Ajoutez votre premier favori ou modifiez vos filtres
              </p>
            </div>
          ) : (
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(340px, 1fr))',
              gap: '24px'
            }}>
              {filteredFavorites.map((favorite, index) => (
                <div
                  key={favorite.id}
                  className="card-dark animate-in-left"
                  style={{
                    padding: '24px',
                    animationDelay: `${index * 0.05}s`
                  }}
                >
                  {/* Photo du favori */}
                  {favorite.imageUrl && (
                    <div style={{
                      marginBottom: '16px',
                      borderRadius: '4px',
                      overflow: 'hidden',
                      background: 'rgba(0, 0, 0, 0.3)'
                    }}>
                      <img
                        src={favorite.imageUrl}
                        alt={favorite.title}
                        style={{
                          width: '100%',
                          height: '200px',
                          objectFit: 'cover',
                          display: 'block'
                        }}
                        onError={(e) => {
                          e.target.style.display = 'none';
                        }}
                      />
                    </div>
                  )}
                  <div style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-start',
                    marginBottom: '20px'
                  }}>
                    <h3 style={{
                      fontSize: '18px',
                      fontWeight: 700,
                      color: '#e4e7eb',
                      flex: 1,
                      marginRight: '12px',
                      fontFamily: '"Rajdhani", sans-serif',
                      letterSpacing: '0.5px'
                    }}>
                      {favorite.title}
                    </h3>
                    <span className={favorite.sold ? 'badge-dark badge-sold-dark' : 'badge-dark badge-available-dark'}>
                      {favorite.sold ? 'Vendu' : 'Dispo'}
                    </span>
                  </div>

                  <div style={{
                    marginBottom: '20px',
                    padding: '16px',
                    background: 'rgba(0, 0, 0, 0.2)',
                    borderRadius: '2px',
                    border: '1px solid rgba(255, 255, 255, 0.05)'
                  }}>
                    <div style={{
                      display: 'grid',
                      gridTemplateColumns: '1fr 1fr',
                      gap: '16px',
                      fontSize: '13px'
                    }}>
                      <div>
                        <span style={{
                          display: 'block',
                          color: 'rgba(228, 231, 235, 0.5)',
                          fontSize: '11px',
                          textTransform: 'uppercase',
                          letterSpacing: '1px',
                          marginBottom: '4px',
                          fontFamily: '"Roboto Mono", monospace'
                        }}>
                          Marque
                        </span>
                        <div style={{ color: '#e4e7eb', fontWeight: 600 }}>
                          {favorite.brand || 'N/A'}
                        </div>
                      </div>
                      <div>
                        <span style={{
                          display: 'block',
                          color: 'rgba(228, 231, 235, 0.5)',
                          fontSize: '11px',
                          textTransform: 'uppercase',
                          letterSpacing: '1px',
                          marginBottom: '4px',
                          fontFamily: '"Roboto Mono", monospace'
                        }}>
                          Prix
                        </span>
                        <div style={{
                          fontSize: '20px',
                          fontWeight: 700,
                          color: '#00ff9d',
                          fontFamily: '"Rajdhani", sans-serif'
                        }}>
                          {favorite.price ? `${favorite.price}€` : 'N/A'}
                        </div>
                      </div>
                      <div>
                        <span style={{
                          display: 'block',
                          color: 'rgba(228, 231, 235, 0.5)',
                          fontSize: '11px',
                          textTransform: 'uppercase',
                          letterSpacing: '1px',
                          marginBottom: '4px',
                          fontFamily: '"Roboto Mono", monospace'
                        }}>
                          Taille
                        </span>
                        <div style={{ color: '#e4e7eb', fontWeight: 600 }}>
                          {favorite.size || 'N/A'}
                        </div>
                      </div>
                      <div>
                        <span style={{
                          display: 'block',
                          color: 'rgba(228, 231, 235, 0.5)',
                          fontSize: '11px',
                          textTransform: 'uppercase',
                          letterSpacing: '1px',
                          marginBottom: '4px',
                          fontFamily: '"Roboto Mono", monospace'
                        }}>
                          Genre
                        </span>
                        <div style={{ color: '#e4e7eb', fontWeight: 600 }}>
                          {favorite.gender || 'N/A'}
                        </div>
                      </div>
                    </div>
                    {favorite.category && (
                      <div style={{ marginTop: '16px', paddingTop: '16px', borderTop: '1px solid rgba(255, 255, 255, 0.05)' }}>
                        <span style={{
                          display: 'block',
                          color: 'rgba(228, 231, 235, 0.5)',
                          fontSize: '11px',
                          textTransform: 'uppercase',
                          letterSpacing: '1px',
                          marginBottom: '4px',
                          fontFamily: '"Roboto Mono", monospace'
                        }}>
                          Catégorie
                        </span>
                        <div style={{ color: '#e4e7eb', fontWeight: 600 }}>
                          {favorite.category}
                        </div>
                      </div>
                    )}
                  </div>

                  {favorite.productUrl && (
                    <a
                      href={favorite.productUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{
                        width: '100%',
                        padding: '12px',
                        background: 'rgba(0, 184, 255, 0.1)',
                        color: '#00b8ff',
                        textDecoration: 'none',
                        borderRadius: '2px',
                        textAlign: 'center',
                        fontSize: '12px',
                        fontWeight: 700,
                        textTransform: 'uppercase',
                        letterSpacing: '1px',
                        transition: 'all 0.2s',
                        border: '1px solid rgba(0, 184, 255, 0.3)',
                        fontFamily: '"Roboto Mono", monospace',
                        display: 'block'
                      }}
                      onMouseEnter={(e) => {
                        e.target.style.background = 'rgba(0, 184, 255, 0.2)';
                        e.target.style.transform = 'translateY(-2px)';
                      }}
                      onMouseLeave={(e) => {
                        e.target.style.background = 'rgba(0, 184, 255, 0.1)';
                        e.target.style.transform = 'translateY(0)';
                      }}
                    >
                      VOIR SUR VINTED
                    </a>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* MODAL - Identique au précédent mais avec style sombre */}
      {showModal && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          background: 'rgba(0, 0, 0, 0.8)',
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          zIndex: 1000,
          padding: '20px',
          backdropFilter: 'blur(8px)'
        }}
        onClick={closeModal}>
          <div 
            className="card-dark"
            style={{
              maxWidth: '600px',
              width: '100%',
              padding: '40px',
              maxHeight: '90vh',
              overflowY: 'auto'
            }}
            onClick={(e) => e.stopPropagation()}>
            <div style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: '32px'
            }}>
              <h2 style={{
                fontSize: '24px',
                fontWeight: 700,
                color: '#00ff9d',
                fontFamily: '"Rajdhani", sans-serif',
                letterSpacing: '1px',
                textTransform: 'uppercase'
              }}>
                {editingFavorite ? 'Modifier' : 'Ajouter'}
              </h2>
              <button
                onClick={closeModal}
                style={{
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  padding: '8px',
                  color: 'rgba(228, 231, 235, 0.6)'
                }}
              >
                <X size={24} />
              </button>
            </div>

            <form onSubmit={handleSubmit}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
                <div>
                  <label style={{ 
                    display: 'block', 
                    marginBottom: '8px', 
                    fontSize: '11px', 
                    fontWeight: 600,
                    color: 'rgba(228, 231, 235, 0.7)',
                    textTransform: 'uppercase',
                    letterSpacing: '1px',
                    fontFamily: '"Roboto Mono", monospace'
                  }}>
                    Titre *
                  </label>
                  <input
                    type="text"
                    required
                    value={formData.title}
                    onChange={(e) => setFormData({...formData, title: e.target.value})}
                    placeholder="Ex: Veste en jean vintage"
                  />
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <div>
                    <label style={{ 
                      display: 'block', 
                      marginBottom: '8px', 
                      fontSize: '11px', 
                      fontWeight: 600,
                      color: 'rgba(228, 231, 235, 0.7)',
                      textTransform: 'uppercase',
                      letterSpacing: '1px',
                      fontFamily: '"Roboto Mono", monospace'
                    }}>
                      Marque
                    </label>
                    <input
                      type="text"
                      value={formData.brand}
                      onChange={(e) => setFormData({...formData, brand: e.target.value})}
                      placeholder="Ex: Levi's"
                    />
                  </div>

                  <div>
                    <label style={{
                      display: 'block',
                      marginBottom: '8px',
                      fontSize: '11px',
                      fontWeight: 600,
                      color: 'rgba(228, 231, 235, 0.7)',
                      textTransform: 'uppercase',
                      letterSpacing: '1px',
                      fontFamily: '"Roboto Mono", monospace'
                    }}>
                      Prix (€)
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      value={formData.price}
                      onChange={(e) => setFormData({...formData, price: e.target.value})}
                      placeholder="Ex: 25.00"
                    />
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '16px' }}>
                  <div>
                    <label style={{ 
                      display: 'block', 
                      marginBottom: '8px', 
                      fontSize: '11px', 
                      fontWeight: 600,
                      color: 'rgba(228, 231, 235, 0.7)',
                      textTransform: 'uppercase',
                      letterSpacing: '1px',
                      fontFamily: '"Roboto Mono", monospace'
                    }}>
                      Taille
                    </label>
                    <input
                      type="text"
                      value={formData.size}
                      onChange={(e) => setFormData({...formData, size: e.target.value})}
                      placeholder="Ex: M, 38, L"
                    />
                  </div>

                  <div>
                    <label style={{
                      display: 'block',
                      marginBottom: '8px',
                      fontSize: '11px',
                      fontWeight: 600,
                      color: 'rgba(228, 231, 235, 0.7)',
                      textTransform: 'uppercase',
                      letterSpacing: '1px',
                      fontFamily: '"Roboto Mono", monospace'
                    }}>
                      Genre
                    </label>
                    <select
                      value={formData.gender}
                      onChange={(e) => setFormData({...formData, gender: e.target.value})}
                    >
                      <option value="">Sélectionner</option>
                      <option value="Homme">Homme</option>
                      <option value="Femme">Femme</option>
                      <option value="Enfant">Enfant</option>
                      <option value="Unisexe">Unisexe</option>
                    </select>
                  </div>
                </div>

                <div>
                  <label style={{ 
                    display: 'block', 
                    marginBottom: '8px', 
                    fontSize: '11px', 
                    fontWeight: 600,
                    color: 'rgba(228, 231, 235, 0.7)',
                    textTransform: 'uppercase',
                    letterSpacing: '1px',
                    fontFamily: '"Roboto Mono", monospace'
                  }}>
                    Catégorie
                  </label>
                  <input
                    type="text"
                    value={formData.category}
                    onChange={(e) => setFormData({...formData, category: e.target.value})}
                    placeholder="Ex: Vestes, Chaussures, Accessoires"
                  />
                </div>

                <div>
                  <label style={{
                    display: 'block',
                    marginBottom: '8px',
                    fontSize: '11px',
                    fontWeight: 600,
                    color: 'rgba(228, 231, 235, 0.7)',
                    textTransform: 'uppercase',
                    letterSpacing: '1px',
                    fontFamily: '"Roboto Mono", monospace'
                  }}>
                    URL Vinted
                  </label>
                  <input
                    type="url"
                    value={formData.productUrl}
                    onChange={(e) => setFormData({...formData, productUrl: e.target.value})}
                    placeholder="https://www.vinted.fr/..."
                  />
                </div>

                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  padding: '16px',
                  background: 'rgba(0, 255, 157, 0.05)',
                  borderRadius: '2px',
                  border: '1px solid rgba(0, 255, 157, 0.1)'
                }}>
                  <input
                    type="checkbox"
                    id="sold"
                    checked={formData.sold}
                    onChange={(e) => setFormData({...formData, sold: e.target.checked})}
                    style={{ width: 'auto', cursor: 'pointer' }}
                  />
                  <label
                    htmlFor="sold"
                    style={{ 
                      fontSize: '13px', 
                      fontWeight: 600,
                      color: '#e4e7eb',
                      cursor: 'pointer'
                    }}
                  >
                    Marquer comme vendu
                  </label>
                </div>

                <div style={{
                  display: 'flex',
                  gap: '12px',
                  marginTop: '16px'
                }}>
                  <button
                    type="button"
                    className="btn-dark btn-secondary-dark"
                    onClick={closeModal}
                    style={{ flex: 1 }}
                  >
                    Annuler
                  </button>
                  <button
                    type="submit"
                    className="btn-dark btn-primary-dark"
                    style={{ flex: 1 }}
                  >
                    {editingFavorite ? 'Modifier' : 'Ajouter'}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default VintedFavoritesApp;