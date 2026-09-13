import React, { useState } from 'react';
import './NetworkMap.css';
import {
  LINES,
  LINE_IDS,
  MAP_LAYOUT,
  getRouteGeometry,
  getStation,
  shortName,
} from '../utils/metro';

const toPath = (points) => points.map((p) => `${p.x},${p.y}`).join(' ');

/** Terminals and interchanges carry a permanent label; the rest label on tap. */
function alwaysLabelled() {
  const names = new Set();
  LINE_IDS.forEach((id) => {
    const stations = LINES[id].stations;
    names.add(stations[0]);
    names.add(stations[stations.length - 1]);
  });
  return names;
}

const PERMANENT = alwaysLabelled();

/** Label padding from the edge of the drawing, in viewBox units. */
const EDGE = 120;

function NetworkMap({ journey, onPlanFrom, onPlanTo, onOpenStation }) {
  const [selected, setSelected] = useState(null);
  const [minX, , mapWidth] = MAP_LAYOUT.viewBox.split(' ').map(Number);
  const route = getRouteGeometry(journey);
  const onRoute = new Set(route.flatMap((leg) => leg.points.map((p) => p.station)));

  return (
    <div className="map">
      <div className="map-head">
        <h2>Network map</h2>
        <p className="hint">
          {journey
            ? `Showing ${shortName(journey.from)} → ${shortName(journey.to)}.`
            : 'A schematic, not to scale — but the compass directions are real.'}{' '}
          Tap any station for its details.
        </p>
      </div>

      <div className="map-frame">
        <svg
          viewBox={MAP_LAYOUT.viewBox}
          className="map-svg"
          role="img"
          aria-label="Namma Metro network schematic"
        >
          {/* Base network, dimmed when a route is highlighted */}
          <g className={route.length ? 'network dimmed' : 'network'}>
            {LINE_IDS.map((id) => (
              <polyline
                key={id}
                points={toPath(MAP_LAYOUT.lines[id])}
                fill="none"
                stroke={LINES[id].color}
                strokeWidth="9"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            ))}
          </g>

          {/* Highlighted journey */}
          {route.map((leg, i) => (
            <polyline
              key={`${leg.line}-${i}`}
              points={toPath(leg.points)}
              fill="none"
              stroke={leg.color}
              strokeWidth="14"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          ))}

          {/* Station markers */}
          {LINE_IDS.map((id) =>
            MAP_LAYOUT.lines[id].map((point) => {
              const station = getStation(point.station);
              const isInterchange = station.lines.length > 1;
              // An interchange is drawn once, by its first line.
              if (isInterchange && station.lines[0] !== id) return null;
              const highlighted = onRoute.has(point.station);

              return (
                <g
                  key={`${id}-${point.station}`}
                  className={`marker ${highlighted ? 'on-route' : ''} ${
                    route.length && !highlighted ? 'dimmed' : ''
                  }`}
                  onClick={() => setSelected(point.station)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      setSelected(point.station);
                    }
                  }}
                  aria-label={point.station}
                >
                  <circle cx={point.x} cy={point.y} r="16" className="marker-hit" />
                  <circle
                    cx={point.x}
                    cy={point.y}
                    r={isInterchange ? 10 : 6}
                    className={`marker-dot ${isInterchange ? 'interchange' : ''}`}
                    stroke={LINES[id].color}
                  />
                </g>
              );
            })
          )}

          {/* Labels: terminals, interchanges, the selected station and the route ends */}
          {LINE_IDS.map((id) =>
            MAP_LAYOUT.lines[id].map((point) => {
              const station = getStation(point.station);
              if (station.lines[0] !== id) return null;
              const show =
                PERMANENT.has(point.station) ||
                station.lines.length > 1 ||
                selected === point.station ||
                (journey && (journey.from === point.station || journey.to === point.station));
              if (!show) return null;

              const vertical = id === 'green';
              const isEnd = journey && (journey.from === point.station || journey.to === point.station);

              // Keep labels inside the drawing: anchor the ones near an edge
              // inward instead of centring them over the edge.
              let anchor = vertical ? 'start' : 'middle';
              if (!vertical) {
                if (point.x < minX + EDGE) anchor = 'start';
                else if (point.x > minX + mapWidth - EDGE) anchor = 'end';
              }

              // Route endpoints drop below the line so they never collide with
              // the terminal and interchange labels sitting above it.
              const dy = vertical ? 5 : isEnd ? 30 : -20;

              return (
                <text
                  key={`label-${point.station}`}
                  x={vertical ? point.x + 18 : point.x}
                  y={point.y + dy}
                  textAnchor={anchor}
                  className={`map-label ${selected === point.station ? 'selected' : ''} ${
                    isEnd ? 'endpoint' : ''
                  }`}
                >
                  {shortName(point.station)}
                </text>
              );
            })
          )}
        </svg>
      </div>

      <div className="map-legend">
        {LINE_IDS.map((id) => (
          <span className="legend-item" key={id}>
            <span className="legend-swatch" style={{ background: LINES[id].color }} />
            {LINES[id].name}
          </span>
        ))}
        <span className="legend-item">
          <span className="legend-swatch ring" />
          Interchange
        </span>
      </div>

      {selected && (
        <div className="map-selection" role="dialog" aria-label={`Actions for ${selected}`}>
          <div className="map-selection-head">
            <strong>{selected}</strong>
            <button className="map-close" onClick={() => setSelected(null)} aria-label="Close">
              ✕
            </button>
          </div>
          <div className="map-actions">
            <button onClick={() => onPlanFrom(selected)}>Travel from here</button>
            <button onClick={() => onPlanTo(selected)}>Travel to here</button>
            <button onClick={() => onOpenStation(selected)}>Station details</button>
          </div>
        </div>
      )}
    </div>
  );
}

export default NetworkMap;
