import React, { useState, useMemo } from 'react';
import './StationDirectory.css';
import { LINES, getAllStations, findStations, getStationDetail, shortName } from '../utils/metro';

function StationDirectory({ selected, onSelect, favourites, onToggleStar, onPlanFrom, onPlanTo }) {
  const [query, setQuery] = useState('');
  const all = useMemo(() => getAllStations(), []);
  const results = query.trim() ? findStations(query) : all;
  const detail = selected ? getStationDetail(selected) : null;

  if (detail) {
    return (
      <StationPage
        detail={detail}
        starred={favourites.starred.includes(detail.name)}
        onBack={() => onSelect(null)}
        onToggleStar={() => onToggleStar(detail.name)}
        onPlanFrom={() => onPlanFrom(detail.name)}
        onPlanTo={() => onPlanTo(detail.name)}
        onOpen={onSelect}
      />
    );
  }

  return (
    <div className="directory">
      <label className="eyebrow" htmlFor="station-search">
        Find a station
      </label>
      <input
        id="station-search"
        type="search"
        className="search"
        placeholder="Try “majestic”, “mg road”, “silk board”"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />

      {favourites.starred.length > 0 && !query.trim() && (
        <section className="directory-section">
          <h2 className="eyebrow">Saved</h2>
          <ul className="station-list">
            {favourites.starred.map((name) => (
              <StationRow key={name} name={name} onSelect={onSelect} />
            ))}
          </ul>
        </section>
      )}

      <section className="directory-section">
        <h2 className="eyebrow">
          {query.trim() ? `${results.length} matching` : `All ${all.length} stations`}
        </h2>
        {results.length === 0 ? (
          <p className="hint">
            No station matches “{query}”. Try a shorter word, or a landmark like “majestic”.
          </p>
        ) : (
          <ul className="station-list">
            {results.map((station) => (
              <StationRow key={station.name} name={station.name} onSelect={onSelect} />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function StationRow({ name, onSelect }) {
  const station = getAllStations().find((s) => s.name === name);
  if (!station) return null;
  return (
    <li>
      <button className="station-row" onClick={() => onSelect(name)}>
        <span className="station-rails" aria-hidden="true">
          {station.lines.map((id) => (
            <span key={id} className="rail" style={{ background: LINES[id].color }} />
          ))}
        </span>
        <span className="station-name">{name}</span>
        {station.lines.length > 1 && <span className="tag">Interchange</span>}
      </button>
    </li>
  );
}

function StationPage({ detail, starred, onBack, onToggleStar, onPlanFrom, onPlanTo, onOpen }) {
  return (
    <div className="station-page">
      <button className="back" onClick={onBack}>
        ← All stations
      </button>

      <header className="station-header">
        <h2>{detail.name}</h2>
        <div className="station-badges">
          {detail.lines.map((line) => (
            <span key={line.line} className="line-badge" style={{ background: line.color }}>
              {line.lineName}
            </span>
          ))}
          {detail.isInterchange && <span className="tag">Interchange</span>}
        </div>
      </header>

      <div className="station-actions">
        <button onClick={onPlanFrom}>Travel from here</button>
        <button onClick={onPlanTo}>Travel to here</button>
        <button className={starred ? 'starred' : ''} onClick={onToggleStar}>
          {starred ? '★ Saved' : '☆ Save'}
        </button>
      </div>

      {detail.lines.map((line) => (
        <section className="station-section" key={line.line}>
          <h3 className="eyebrow">On the {line.lineName}</h3>
          <p className="station-position tnum">
            Station {line.position} of {line.total} · {line.kmFromStart} km from{' '}
            {shortName(line.terminals[0])}
          </p>
          <div className="neighbours">
            <Neighbour label="Previous" name={line.previous} onOpen={onOpen} />
            <Neighbour label="Next" name={line.next} onOpen={onOpen} />
          </div>
        </section>
      ))}

      <section className="station-section">
        <h3 className="eyebrow">First &amp; last train</h3>
        <div className="timetable-scroll">
          <table className="timetable">
            <thead>
              <tr>
                <th>Towards</th>
                <th>First</th>
                <th>Sunday</th>
                <th>Last</th>
              </tr>
            </thead>
            <tbody>
              {detail.timetable.map((row) => (
                <tr key={`${row.line}-${row.towards}`}>
                  <td>
                    <span className="rail inline" style={{ background: row.color }} />
                    {shortName(row.towards)}
                  </td>
                  <td className="tnum">{row.first}</td>
                  <td className="tnum">{row.firstSunday}</td>
                  <td className="tnum">{row.last}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="hint">
          Derived from terminal departure times and this station's position on the line — treat them
          as within a few minutes, not to the second.
        </p>
      </section>
    </div>
  );
}

function Neighbour({ label, name, onOpen }) {
  return (
    <div className="neighbour">
      <span className="eyebrow">{label}</span>
      {name ? (
        <button onClick={() => onOpen(name)}>{name}</button>
      ) : (
        <span className="hint">End of the line</span>
      )}
    </div>
  );
}

export default StationDirectory;
