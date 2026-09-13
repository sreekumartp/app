import React, { useState, useEffect, useMemo } from 'react';
import './JourneyPlanner.css';
import { SERVICE_INFO, POPULAR_ROUTES, getAllStations, shortName } from '../utils/metro';
import { useLocalStorage } from '../utils/useLocalStorage';

const MAX_RECENT = 4;

function JourneyPlanner({
  from,
  to,
  journey,
  onFromChange,
  onToChange,
  favourites,
  onFavouritesChange,
  onOpenStation,
  onShowOnMap,
}) {
  const [expandedLeg, setExpandedLeg] = useState(null);
  const [recent, setRecent] = useLocalStorage('metroRecentJourneys', []);
  const stations = useMemo(() => getAllStations(), []);

  useEffect(() => {
    if (!journey) return;
    setRecent((prev) =>
      [
        { from: journey.from, to: journey.to },
        ...prev.filter((r) => !(r.from === journey.from && r.to === journey.to)),
      ].slice(0, MAX_RECENT)
    );
  }, [journey, setRecent]);

  const setRoute = (nextFrom, nextTo) => {
    onFromChange(nextFrom);
    onToChange(nextTo);
    setExpandedLeg(null);
  };

  const { home, work } = favourites;

  const options = (exclude) =>
    stations
      .filter((station) => station.name !== exclude)
      .map((station) => (
        <option key={station.name} value={station.name}>
          {station.name}
          {station.lines.length > 1 ? ' ⇄' : ''}
        </option>
      ));

  return (
    <div className="planner">
      <section className="picker" aria-label="Choose stations">
        <div className="picker-field">
          <label className="eyebrow" htmlFor="metro-from">
            From
          </label>
          <select
            id="metro-from"
            value={from}
            onChange={(e) => {
              onFromChange(e.target.value);
              setExpandedLeg(null);
            }}
          >
            <option value="">Boarding station</option>
            {options(to)}
          </select>
        </div>

        <button
          className="picker-swap"
          onClick={() => setRoute(to, from)}
          title="Swap stations"
          aria-label="Swap stations"
        >
          ⇅
        </button>

        <div className="picker-field">
          <label className="eyebrow" htmlFor="metro-to">
            To
          </label>
          <select
            id="metro-to"
            value={to}
            onChange={(e) => {
              onToChange(e.target.value);
              setExpandedLeg(null);
            }}
          >
            <option value="">Destination station</option>
            {options(from)}
          </select>
        </div>
      </section>

      {home && work && (
        <section className="commute" aria-label="Daily commute">
          <button className="commute-button" onClick={() => setRoute(home, work)}>
            <span className="eyebrow">Morning</span>
            <span className="commute-route">
              {shortName(home)} → {shortName(work)}
            </span>
          </button>
          <button className="commute-button" onClick={() => setRoute(work, home)}>
            <span className="eyebrow">Evening</span>
            <span className="commute-route">
              {shortName(work)} → {shortName(home)}
            </span>
          </button>
        </section>
      )}

      {journey ? (
        <Result
          journey={journey}
          expandedLeg={expandedLeg}
          onToggleLeg={(i) => setExpandedLeg(expandedLeg === i ? null : i)}
          onOpenStation={onOpenStation}
          onShowOnMap={onShowOnMap}
        />
      ) : (
        <Suggestions
          recent={recent}
          onPick={setRoute}
          favourites={favourites}
          onFavouritesChange={onFavouritesChange}
          stations={stations}
        />
      )}
    </div>
  );
}

function Suggestions({ recent, onPick, favourites, onFavouritesChange, stations }) {
  const setPlace = (place, value) => onFavouritesChange({ ...favourites, [place]: value });

  return (
    <>
      {recent.length > 0 && (
        <section className="shortcuts">
          <h2 className="eyebrow">Recent</h2>
          <div className="chips">
            {recent.map((route) => (
              <button
                key={`${route.from}-${route.to}`}
                className="chip"
                onClick={() => onPick(route.from, route.to)}
              >
                {shortName(route.from)} → {shortName(route.to)}
              </button>
            ))}
          </div>
        </section>
      )}

      <section className="shortcuts">
        <h2 className="eyebrow">Popular journeys</h2>
        <div className="chips">
          {POPULAR_ROUTES.map((route) => (
            <button key={route.label} className="chip" onClick={() => onPick(route.from, route.to)}>
              {route.label}
            </button>
          ))}
        </div>
      </section>

      <section className="commute-setup">
        <h2 className="eyebrow">Your stations</h2>
        <p className="hint">
          Set home and work once, then your daily trip is one tap away — in both directions.
        </p>
        {['home', 'work'].map((place) => (
          <div className="commute-row" key={place}>
            <label htmlFor={`place-${place}`}>{place === 'home' ? 'Home' : 'Work'}</label>
            <select
              id={`place-${place}`}
              value={favourites[place]}
              onChange={(e) => setPlace(place, e.target.value)}
            >
              <option value="">Not set</option>
              {stations.map((station) => (
                <option key={station.name} value={station.name}>
                  {station.name}
                </option>
              ))}
            </select>
          </div>
        ))}
      </section>
    </>
  );
}

function Result({ journey, expandedLeg, onToggleLeg, onOpenStation, onShowOnMap }) {
  const arrival = new Date(Date.now() + journey.durationMinutes * 60000).toLocaleTimeString(
    'en-IN',
    { hour: '2-digit', minute: '2-digit' }
  );

  return (
    <>
      <section className="summary" aria-label="Journey summary">
        <div className="summary-fare">
          <span className="eyebrow">Fare</span>
          <span className="summary-fare-value tnum">₹{journey.fare.token}</span>
          <span className="summary-fare-note tnum">₹{journey.fare.smartCard} on smart card</span>
        </div>
        <dl className="summary-facts">
          <div>
            <dt className="eyebrow">Time</dt>
            <dd className="tnum">{journey.durationMinutes} min</dd>
            <span className="tnum">arrive ~{arrival}</span>
          </div>
          <div>
            <dt className="eyebrow">Stops</dt>
            <dd className="tnum">{journey.totalStops}</dd>
            <span className="tnum">≈{journey.totalKm} km</span>
          </div>
          <div>
            <dt className="eyebrow">Changes</dt>
            <dd className="tnum">{journey.interchanges.length}</dd>
            <span>
              {journey.interchanges.length === 0
                ? 'direct train'
                : journey.interchanges.map(shortName).join(', ')}
            </span>
          </div>
        </dl>
      </section>

      <button className="map-link" onClick={onShowOnMap}>
        Show this route on the map
      </button>

      <section className="route" aria-label="Step by step directions">
        {journey.legs.map((leg, index) => (
          <div className="leg" key={`${leg.line}-${leg.from}`}>
            {index > 0 && (
              <p className="change">
                Change at <strong>{leg.from}</strong> — walk to the {leg.lineName} platform, about 5
                min
              </p>
            )}

            <div className="leg-line" style={{ '--leg': leg.color }}>
              <p className="leg-head">
                <span className="line-badge" style={{ background: leg.color }}>
                  {leg.lineName}
                </span>
                <span className="leg-direction">towards {leg.towards}</span>
              </p>

              <button className="stop" onClick={() => onOpenStation(leg.from)}>
                <span className="stop-dot" />
                <span className="stop-text">
                  <span className="eyebrow">Board</span>
                  {leg.from}
                </span>
              </button>

              <button className="leg-toggle" onClick={() => onToggleLeg(index)}>
                {leg.stops} {leg.stops === 1 ? 'stop' : 'stops'} · {leg.durationMinutes} min
                <span aria-hidden="true">{expandedLeg === index ? ' ▲' : ' ▼'}</span>
              </button>

              {expandedLeg === index && (
                <ol className="leg-stations">
                  {leg.stations.slice(1, -1).map((station) => (
                    <li key={station}>
                      <button onClick={() => onOpenStation(station)}>{station}</button>
                    </li>
                  ))}
                  {leg.stations.length === 2 && <li className="hint">No stations in between</li>}
                </ol>
              )}

              <button className="stop" onClick={() => onOpenStation(leg.to)}>
                <span className="stop-dot filled" />
                <span className="stop-text">
                  <span className="eyebrow">
                    {index === journey.legs.length - 1 ? 'Get off' : 'Alight'}
                  </span>
                  {leg.to}
                </span>
              </button>
            </div>
          </div>
        ))}
      </section>

      <p className="service-note">
        Trains run {SERVICE_INFO.firstTrainWeekday}–{SERVICE_INFO.lastTrainFromTerminal} (Sundays
        from {SERVICE_INFO.firstTrainSunday}), every {SERVICE_INFO.peakHeadwayMinutes}–
        {SERVICE_INFO.offPeakHeadwayMinutes} min. Fares and times are estimates — check BMRCL for
        official figures.
      </p>
    </>
  );
}

export default JourneyPlanner;
