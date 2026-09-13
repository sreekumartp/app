# Namma Metro · Bengaluru

A journey planner for Bengaluru's Namma Metro. Pick where you're getting on and
where you're going, and it works out the route, the fare, how long it takes and
where to change lines — plus a network map, per-station timetables and saved
stations for your daily commute.

Built with React. No backend, no API keys, no network calls: the whole network
is modelled in the app, so it works offline once loaded.

## Network coverage

| Line | Route | Stations |
| --- | --- | --- |
| Purple | Whitefield (Kadugodi) ↔ Challaghatta | 37 |
| Green | Madavara ↔ Silk Institute | 32 |
| Yellow | Rashtreeya Vidyalaya Road ↔ Bommasandra | 16 |

85 unique stations, with two interchanges: **Nadaprabhu Kempegowda Station,
Majestic** (Purple ↔ Green) and **Rashtreeya Vidyalaya Road** (Green ↔ Yellow).

## Features

### Plan
- **Route finding** — the quickest route between any two stations, using
  Dijkstra's algorithm over (station, line) pairs. Changing lines costs a
  5-minute penalty, so a direct train wins when two routes are close in length.
- **Fare** — token and discounted smart-card price from BMRCL-style distance
  slabs (₹10–₹90).
- **Travel time** — riding time plus interchange walking time, with an
  estimated arrival clock time.
- **Step-by-step directions** — which line to board, the terminal shown on
  platform signage ("towards Challaghatta"), where to change, where to get off.
  Each leg expands to list the stations in between.
- **Saved commute** — set home and work once; your morning and evening trips
  are then one tap each.
- **Recent journeys** — your last four trips, kept in local storage.

### Map
- Schematic of all three lines. It isn't to scale, but the compass directions
  are real: Purple runs east–west, Green north–south, Yellow south-east from
  RV Road.
- A planned route is drawn over the dimmed network, with its endpoints labelled.
- Tap any station to travel from it, travel to it, or open its details.
- Station positions are derived rather than hand-placed, so the interchanges
  land exactly where the lines cross.

### Stations
- Search all 85 stations, including landmark names — "majestic", "mg road",
  "silk board", "kr puram" all resolve.
- Per-station page: lines served, position along each line, distance from the
  terminal, previous and next stations, and first/last train times in every
  direction (weekday and Sunday).
- Save stations you use often.

## Accuracy

Distances are modelled from per-line average inter-station gaps rather than a
surveyed distance table, so **fares, distances and travel times are close
estimates**. Timetables are derived from terminal departure times and how far
along the line a station sits — good to within a few minutes, not to the
second. There is no live data of any kind: no train positions, no delays, no
crowding. Check BMRCL for official figures.

Three lines are in revenue service today. The Blue Line to the airport and the
Pink Line are still under construction and are not included.

## Getting started

```bash
npm install
npm start
```

Opens http://localhost:3000 with hot reload.

| Command | What it does |
| --- | --- |
| `npm start` | Dev server on :3000 |
| `npm test` | Jest in watch mode |
| `CI=true npm test` | Single run, no watch |
| `npm run build` | Production bundle into `build/` |

## Project structure

```
src/
├── components/
│   ├── JourneyPlanner.js     # Plan tab: pickers, commute, fare, directions
│   ├── JourneyPlanner.css
│   ├── NetworkMap.js         # Map tab: SVG schematic + route highlight
│   ├── NetworkMap.css
│   ├── StationDirectory.js   # Stations tab: search, station pages, timetables
│   └── StationDirectory.css
├── utils/
│   ├── metro.js              # Network data, routing, fares, timetables, map geometry
│   ├── metro.test.js
│   └── useLocalStorage.js    # Persistence that tolerates blocked storage
├── App.js                    # Shell: header, tabs, shared state
├── App.css
├── App.test.js               # Integration tests through the real app
├── index.js
└── index.css                 # Design tokens, both themes
```

## Testing

```bash
CI=true npm test
```

48 tests covering network data integrity (station counts, interchanges,
no duplicates), route finding and interchange detection, fare slabs, derived
timetables, map geometry (every station placed, interchanges coincident, all
points inside the viewBox), and the app's own flows — planning a journey,
expanding a leg, swapping, saved commutes, map interaction and station pages.

## Themes

Light and dark, switched from the header and remembered. The first visit
follows your system preference.
