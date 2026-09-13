import React, { useState, useEffect, useMemo, useCallback } from 'react';
import './App.css';
import JourneyPlanner from './components/JourneyPlanner';
import NetworkMap from './components/NetworkMap';
import StationDirectory from './components/StationDirectory';
import { planJourney } from './utils/metro';
import { useLocalStorage } from './utils/useLocalStorage';

const TABS = [
  { id: 'plan', label: 'Plan', glyph: '⇄' },
  { id: 'map', label: 'Map', glyph: '◉' },
  { id: 'stations', label: 'Stations', glyph: '☰' },
];

function preferredTheme() {
  try {
    if (window.matchMedia('(prefers-color-scheme: dark)').matches) return 'dark';
  } catch (e) {
    /* matchMedia unavailable */
  }
  return 'light';
}

function App() {
  const [tab, setTab] = useState('plan');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [selectedStation, setSelectedStation] = useState(null);
  const [theme, setTheme] = useLocalStorage('metroTheme', preferredTheme());
  const [favourites, setFavourites] = useLocalStorage('metroFavourites', {
    home: '',
    work: '',
    starred: [],
  });

  const journey = useMemo(() => planJourney(from, to), [from, to]);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  const openStation = useCallback((name) => {
    setSelectedStation(name);
    setTab('stations');
  }, []);

  const planFrom = useCallback((name) => {
    setFrom(name);
    setTab('plan');
  }, []);

  const planTo = useCallback((name) => {
    setTo(name);
    setTab('plan');
  }, []);

  const toggleStar = useCallback(
    (name) => {
      setFavourites((prev) => {
        const starred = prev.starred.includes(name)
          ? prev.starred.filter((s) => s !== name)
          : [...prev.starred, name];
        return { ...prev, starred };
      });
    },
    [setFavourites]
  );

  return (
    <div className="app">
      <header className="app-header">
        <div className="app-identity">
          <span className="app-mark" aria-hidden="true">
            <span className="app-mark-bar purple" />
            <span className="app-mark-bar green" />
            <span className="app-mark-bar yellow" />
          </span>
          <div>
            <h1>Namma Metro</h1>
            <p className="app-sub">Bengaluru · Purple, Green &amp; Yellow lines</p>
          </div>
        </div>
        <button
          className="theme-toggle"
          onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          title={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
          aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
        >
          {theme === 'dark' ? '☀' : '☾'}
        </button>
      </header>

      <main className="app-main">
        {tab === 'plan' && (
          <JourneyPlanner
            from={from}
            to={to}
            journey={journey}
            onFromChange={setFrom}
            onToChange={setTo}
            favourites={favourites}
            onFavouritesChange={setFavourites}
            onOpenStation={openStation}
            onShowOnMap={() => setTab('map')}
          />
        )}

        {tab === 'map' && (
          <NetworkMap
            journey={journey}
            onPlanFrom={planFrom}
            onPlanTo={planTo}
            onOpenStation={openStation}
          />
        )}

        {tab === 'stations' && (
          <StationDirectory
            selected={selectedStation}
            onSelect={setSelectedStation}
            favourites={favourites}
            onToggleStar={toggleStar}
            onPlanFrom={planFrom}
            onPlanTo={planTo}
          />
        )}
      </main>

      <nav className="app-tabs" aria-label="Sections">
        {TABS.map((item) => (
          <button
            key={item.id}
            className={`app-tab ${tab === item.id ? 'active' : ''}`}
            onClick={() => setTab(item.id)}
            aria-current={tab === item.id ? 'page' : undefined}
          >
            <span className="app-tab-glyph" aria-hidden="true">
              {item.glyph}
            </span>
            {item.label}
          </button>
        ))}
      </nav>
    </div>
  );
}

export default App;
