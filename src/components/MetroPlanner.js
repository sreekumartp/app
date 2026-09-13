import React, { useState, useEffect, useMemo } from 'react';
import './MetroPlanner.css';
import {
  LINES,
  LINE_IDS,
  SERVICE_INFO,
  POPULAR_ROUTES,
  getAllStations,
  planJourney,
} from '../utils/metro';

const RECENT_KEY = 'metroRecentJourneys';
const MAX_RECENT = 5;

function MetroPlanner() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [expandedLeg, setExpandedLeg] = useState(null);
  const [recent, setRecent] = useState([]);

  const stations = useMemo(() => getAllStations(), []);
  const journey = useMemo(() => planJourney(from, to), [from, to]);

  useEffect(() => {
    const saved = localStorage.getItem(RECENT_KEY);
    if (saved) {
      try {
        setRecent(JSON.parse(saved));
      } catch (e) {
        setRecent([]);
      }
    }
  }, []);

  useEffect(() => {
    if (!journey) return;
    setRecent((prev) => {
      const next = [
        { from: journey.from, to: journey.to },
        ...prev.filter((r) => !(r.from === journey.from && r.to === journey.to)),
      ].slice(0, MAX_RECENT);
      localStorage.setItem(RECENT_KEY, JSON.stringify(next));
      return next;
    });
  }, [journey]);

  const swap = () => {
    setFrom(to);
    setTo(from);
    setExpandedLeg(null);
  };

  const pickRoute = (route) => {
    setFrom(route.from);
    setTo(route.to);
    setExpandedLeg(null);
  };

  const arrivalTime = () => {
    const arrival = new Date(Date.now() + journey.durationMinutes * 60000);
    return arrival.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });
  };

  const stationOptions = (exclude) =>
    stations
      .filter((station) => station.name !== exclude)
      .map((station) => (
        <option key={station.name} value={station.name}>
          {station.name}
          {station.lines.length > 1 ? ' ⇄' : ''}
        </option>
      ));

  return (
    <div className="metro-planner">
      <p className="metro-tagline">
        Plan a Namma Metro trip across Bengaluru — fare, travel time and where to change lines.
      </p>

      <div className="metro-search">
        <div className="metro-field">
          <label htmlFor="metro-from">From</label>
          <select
            id="metro-from"
            value={from}
            onChange={(e) => {
              setFrom(e.target.value);
              setExpandedLeg(null);
            }}
          >
            <option value="">Select boarding station</option>
            {stationOptions(to)}
          </select>
        </div>

        <button className="metro-swap" onClick={swap} title="Swap stations" aria-label="Swap stations">
          ⇅
        </button>

        <div className="metro-field">
          <label htmlFor="metro-to">To</label>
          <select
            id="metro-to"
            value={to}
            onChange={(e) => {
              setTo(e.target.value);
              setExpandedLeg(null);
            }}
          >
            <option value="">Select destination station</option>
            {stationOptions(from)}
          </select>
        </div>
      </div>

      {!journey && (
        <div className="metro-suggestions">
          <h3>Popular journeys</h3>
          <div className="metro-chips">
            {POPULAR_ROUTES.map((route) => (
              <button key={route.label} className="metro-chip" onClick={() => pickRoute(route)}>
                {route.label}
              </button>
            ))}
          </div>

          {recent.length > 0 && (
            <>
              <h3>Recent</h3>
              <div className="metro-chips">
                {recent.map((route) => (
                  <button
                    key={`${route.from}-${route.to}`}
                    className="metro-chip recent"
                    onClick={() => pickRoute(route)}
                  >
                    {shortName(route.from)} → {shortName(route.to)}
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
      )}

      {journey && (
        <div className="metro-result">
          <div className="metro-summary">
            <div className="metro-stat primary">
              <span className="metro-stat-label">Fare (token)</span>
              <span className="metro-stat-value">₹{journey.fare.token}</span>
              <span className="metro-stat-note">₹{journey.fare.smartCard} on smart card</span>
            </div>
            <div className="metro-stat">
              <span className="metro-stat-label">Travel time</span>
              <span className="metro-stat-value">{journey.durationMinutes} min</span>
              <span className="metro-stat-note">Arrive around {arrivalTime()}</span>
            </div>
            <div className="metro-stat">
              <span className="metro-stat-label">Stations</span>
              <span className="metro-stat-value">{journey.totalStops}</span>
              <span className="metro-stat-note">approx. {journey.totalKm} km</span>
            </div>
            <div className="metro-stat">
              <span className="metro-stat-label">Line changes</span>
              <span className="metro-stat-value">{journey.interchanges.length}</span>
              <span className="metro-stat-note">
                {journey.interchanges.length === 0
                  ? 'Direct train'
                  : `at ${journey.interchanges.map(shortName).join(', ')}`}
              </span>
            </div>
          </div>

          <div className="metro-journey">
            {journey.legs.map((leg, index) => (
              <div className="metro-leg" key={`${leg.line}-${leg.from}`}>
                {index > 0 && (
                  <div className="metro-change">
                    <span className="metro-change-icon">⇄</span>
                    Change at <strong>{leg.from}</strong> — walk to the {leg.lineName} platform
                    (about 5 min)
                  </div>
                )}

                <div className="metro-leg-head" style={{ borderLeftColor: leg.color }}>
                  <span className="metro-line-badge" style={{ background: leg.color }}>
                    {leg.lineName}
                  </span>
                  <span className="metro-direction">towards {leg.towards}</span>
                </div>

                <div className="metro-leg-body" style={{ borderLeftColor: leg.color }}>
                  <div className="metro-stop board">
                    <span className="metro-dot" style={{ borderColor: leg.color }} />
                    <span>
                      Board at <strong>{leg.from}</strong>
                    </span>
                  </div>

                  <button
                    className="metro-toggle"
                    onClick={() => setExpandedLeg(expandedLeg === index ? null : index)}
                  >
                    {leg.stops} {leg.stops === 1 ? 'stop' : 'stops'} · {leg.durationMinutes} min
                    {expandedLeg === index ? ' ▲' : ' ▼'}
                  </button>

                  {expandedLeg === index && (
                    <ol className="metro-stop-list">
                      {leg.stations.slice(1, -1).map((station) => (
                        <li key={station}>{station}</li>
                      ))}
                      {leg.stations.length === 2 && <li className="muted">No stations in between</li>}
                    </ol>
                  )}

                  <div className="metro-stop alight">
                    <span className="metro-dot filled" style={{ background: leg.color, borderColor: leg.color }} />
                    <span>
                      {index === journey.legs.length - 1 ? 'Get off at' : 'Alight at'}{' '}
                      <strong>{leg.to}</strong>
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>

          <p className="metro-note">
            Trains run from {SERVICE_INFO.firstTrainWeekday} to {SERVICE_INFO.lastTrainFromTerminal}{' '}
            (Sundays from {SERVICE_INFO.firstTrainSunday}), every{' '}
            {SERVICE_INFO.peakHeadwayMinutes}–{SERVICE_INFO.offPeakHeadwayMinutes} minutes. Fares and
            times are estimates — check BMRCL for the official figures.
          </p>
        </div>
      )}

      <div className="metro-network">
        <h3>Network</h3>
        <div className="metro-lines">
          {LINE_IDS.map((lineId) => {
            const line = LINES[lineId];
            return (
              <div className="metro-line-card" key={lineId} style={{ borderTopColor: line.color }}>
                <span className="metro-line-badge" style={{ background: line.color }}>
                  {line.name}
                </span>
                <p>
                  {line.stations[0]} ↔ {line.stations[line.stations.length - 1]}
                </p>
                <span className="metro-line-count">{line.stations.length} stations</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

/** Trim the long official names down for chips and one-line summaries. */
function shortName(name) {
  return name
    .replace('Nadaprabhu Kempegowda Station, ', '')
    .replace('Krantivira Sangolli Rayanna Railway Station', 'City Railway Station')
    .replace('Sri Balagangadharanatha Swamiji Station, ', '')
    .replace('Sir M. Visvesvaraya Station, ', '')
    .replace('Rashtreeya Vidyalaya Road', 'RV Road');
}

export default MetroPlanner;
