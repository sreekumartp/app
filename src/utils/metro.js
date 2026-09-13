/**
 * Namma Metro (Bengaluru) network data and journey planning helpers.
 *
 * Covers the three lines in revenue service: Purple, Green and Yellow.
 * Distances are modelled with a per-line average inter-station gap, so fares
 * and durations are close estimates rather than official BMRCL figures.
 */

export const INTERCHANGE_MINUTES = 5;
export const MINUTES_PER_STOP = 2.1;

export const LINES = {
  purple: {
    id: 'purple',
    name: 'Purple Line',
    color: '#7b1fa2',
    // ~43.5 km across 36 gaps
    avgGapKm: 1.21,
    stations: [
      'Whitefield (Kadugodi)',
      'Hopefarm Channasandra',
      'Kadugodi Tree Park',
      'Pattandur Agrahara',
      'Sri Sathya Sai Hospital',
      'Nallurhalli',
      'Kundalahalli',
      'Seetharampalya',
      'Hoodi',
      'Garudacharapalya',
      'Singayyanapalya',
      'Krishnarajapura',
      'Benniganahalli',
      'Baiyappanahalli',
      'Swami Vivekananda Road',
      'Indiranagar',
      'Halasuru',
      'Trinity',
      'Mahatma Gandhi Road',
      'Cubbon Park',
      'Vidhana Soudha',
      'Sir M. Visvesvaraya Station, Central College',
      'Nadaprabhu Kempegowda Station, Majestic',
      'Krantivira Sangolli Rayanna Railway Station',
      'Magadi Road',
      'Sri Balagangadharanatha Swamiji Station, Hosahalli',
      'Vijayanagara',
      'Attiguppe',
      'Deepanjali Nagara',
      'Mysuru Road',
      'Pantharapalya - Nayandahalli',
      'Rajarajeshwari Nagar',
      'Jnanabharathi',
      'Pattanagere',
      'Kengeri Bus Terminal',
      'Kengeri',
      'Challaghatta',
    ],
  },
  green: {
    id: 'green',
    name: 'Green Line',
    color: '#2e7d32',
    // ~31.5 km across 31 gaps
    avgGapKm: 1.02,
    stations: [
      'Madavara',
      'Chikkabidarakallu',
      'Manjunathanagara',
      'Nagasandra',
      'Dasarahalli',
      'Jalahalli',
      'Peenya Industry',
      'Peenya',
      'Goraguntepalya',
      'Yeshwanthpur',
      'Sandal Soap Factory',
      'Mahalakshmi',
      'Rajajinagara',
      'Mahakavi Kuvempu Road',
      'Srirampura',
      'Mantri Square Sampige Road',
      'Nadaprabhu Kempegowda Station, Majestic',
      'Chickpete',
      'Krishna Rajendra Market',
      'National College',
      'Lalbagh',
      'South End Circle',
      'Jayanagara',
      'Rashtreeya Vidyalaya Road',
      'Banashankari',
      'Jayaprakash Nagara',
      'Yelachenahalli',
      'Konanakunte Cross',
      'Doddakallasandra',
      'Vajarahalli',
      'Thalaghattapura',
      'Silk Institute',
    ],
  },
  yellow: {
    id: 'yellow',
    name: 'Yellow Line',
    color: '#f9a825',
    // ~19.15 km across 15 gaps
    avgGapKm: 1.28,
    stations: [
      'Rashtreeya Vidyalaya Road',
      'Ragigudda',
      'Jayadeva Hospital',
      'BTM Layout',
      'Central Silk Board',
      'Bommanahalli',
      'Hongasandra',
      'Kudlu Gate',
      'Singasandra',
      'Hosa Road',
      'Beratena Agrahara',
      'Electronic City',
      'Konappana Agrahara',
      'Huskur Road',
      'Hebbagodi',
      'Bommasandra',
    ],
  },
};

export const LINE_IDS = Object.keys(LINES);

/** Popular landmarks / colloquial names mapped to official station names. */
const ALIASES = {
  majestic: 'Nadaprabhu Kempegowda Station, Majestic',
  'kempegowda': 'Nadaprabhu Kempegowda Station, Majestic',
  'bus stand': 'Nadaprabhu Kempegowda Station, Majestic',
  'city railway station': 'Krantivira Sangolli Rayanna Railway Station',
  'mg road': 'Mahatma Gandhi Road',
  'rv road': 'Rashtreeya Vidyalaya Road',
  'jp nagar': 'Jayaprakash Nagara',
  'kr market': 'Krishna Rajendra Market',
  'kr puram': 'Krishnarajapura',
  'ulsoor': 'Halasuru',
  'silk board': 'Central Silk Board',
  'biec': 'Madavara',
  'airport': null, // Blue Line to KIA is still under construction
};

/** Operating information shown alongside a planned journey. */
export const SERVICE_INFO = {
  firstTrainWeekday: '05:00',
  firstTrainSunday: '07:00',
  lastTrainFromTerminal: '23:00',
  peakHeadwayMinutes: 5,
  offPeakHeadwayMinutes: 10,
};

/**
 * Every station once, with the lines it sits on.
 * @returns {Array<{name: string, lines: string[]}>}
 */
export function getAllStations() {
  const map = new Map();
  LINE_IDS.forEach((lineId) => {
    LINES[lineId].stations.forEach((name) => {
      if (!map.has(name)) map.set(name, { name, lines: [] });
      map.get(name).lines.push(lineId);
    });
  });
  return Array.from(map.values());
}

const STATION_INDEX = getAllStations();

/** Stations served by more than one line. */
export function getInterchanges() {
  return STATION_INDEX.filter((s) => s.lines.length > 1).map((s) => s.name);
}

export function getStation(name) {
  return STATION_INDEX.find((s) => s.name === name) || null;
}

/**
 * Loose lookup for typed input: exact name, known alias, or substring match.
 */
export function findStations(query) {
  const q = (query || '').trim().toLowerCase();
  if (!q) return [];
  const aliased = ALIASES[q];
  if (aliased) return [getStation(aliased)].filter(Boolean);
  const starts = [];
  const contains = [];
  STATION_INDEX.forEach((station) => {
    const name = station.name.toLowerCase();
    if (name.startsWith(q)) starts.push(station);
    else if (name.includes(q)) contains.push(station);
  });
  return [...starts, ...contains];
}

function nodeKey(station, lineId) {
  return `${station}||${lineId}`;
}

function neighbours(station, lineId) {
  const stations = LINES[lineId].stations;
  const i = stations.indexOf(station);
  const out = [];
  if (i > 0) out.push(stations[i - 1]);
  if (i >= 0 && i < stations.length - 1) out.push(stations[i + 1]);
  return out;
}

/**
 * Dijkstra over (station, line) pairs. Riding one stop costs its travel time;
 * changing lines at an interchange costs INTERCHANGE_MINUTES, so the planner
 * naturally prefers fewer changes when two routes are similar in length.
 */
function shortestPath(from, to) {
  const start = getStation(from);
  const end = getStation(to);
  if (!start || !end) return null;

  const dist = new Map();
  const prev = new Map();
  const visited = new Set();

  start.lines.forEach((lineId) => {
    dist.set(nodeKey(from, lineId), 0);
  });

  while (true) {
    let current = null;
    let best = Infinity;
    dist.forEach((value, key) => {
      if (!visited.has(key) && value < best) {
        best = value;
        current = key;
      }
    });
    if (current === null) break;
    visited.add(current);

    const [station, lineId] = current.split('||');
    if (station === to) {
      // Rebuild the (station, line) chain back to the origin.
      const chain = [];
      let cursor = current;
      while (cursor) {
        const [s, l] = cursor.split('||');
        chain.unshift({ station: s, line: l });
        cursor = prev.get(cursor);
      }
      return chain;
    }

    const relax = (key, weight) => {
      const candidate = best + weight;
      if (candidate < (dist.has(key) ? dist.get(key) : Infinity)) {
        dist.set(key, candidate);
        prev.set(key, current);
      }
    };

    neighbours(station, lineId).forEach((next) => {
      relax(nodeKey(next, lineId), MINUTES_PER_STOP);
    });

    const stationInfo = getStation(station);
    stationInfo.lines.forEach((otherLine) => {
      if (otherLine !== lineId) relax(nodeKey(station, otherLine), INTERCHANGE_MINUTES);
    });
  }

  return null;
}

/** BMRCL-style distance slabs (single-journey token, in rupees). */
const FARE_SLABS = [
  { maxKm: 2, fare: 10 },
  { maxKm: 4, fare: 20 },
  { maxKm: 6, fare: 30 },
  { maxKm: 8, fare: 40 },
  { maxKm: 10, fare: 50 },
  { maxKm: 15, fare: 60 },
  { maxKm: 20, fare: 70 },
  { maxKm: 25, fare: 80 },
  { maxKm: Infinity, fare: 90 },
];

/**
 * @param {number} km distance travelled
 * @returns {{token: number, smartCard: number, offPeak: number}} fares in rupees
 */
export function calculateFare(km) {
  const slab = FARE_SLABS.find((s) => km <= s.maxKm);
  const token = slab.fare;
  return {
    token,
    smartCard: Math.round(token * 0.95),
    offPeak: Math.round(token * 0.9),
  };
}

/**
 * Plan a journey between two stations.
 *
 * @param {string} from origin station name
 * @param {string} to destination station name
 * @returns {null|{legs: Array, totalStops: number, totalKm: number,
 *   durationMinutes: number, interchanges: string[], fare: object}}
 */
export function planJourney(from, to) {
  if (!from || !to || from === to) return null;
  const chain = shortestPath(from, to);
  if (!chain) return null;

  const legs = [];
  chain.forEach((node, i) => {
    const previous = chain[i - 1];
    if (!previous) {
      legs.push({ line: node.line, stations: [node.station] });
      return;
    }
    if (previous.line === node.line) {
      legs[legs.length - 1].stations.push(node.station);
    } else {
      // Interchange: same platform station, new line.
      legs.push({ line: node.line, stations: [node.station] });
    }
  });

  // A change of line records a one-station leg on the old line; drop those.
  const realLegs = legs
    .filter((leg) => leg.stations.length > 1)
    .map((leg) => {
      const stops = leg.stations.length - 1;
      const km = +(stops * LINES[leg.line].avgGapKm).toFixed(2);
      return {
        line: leg.line,
        lineName: LINES[leg.line].name,
        color: LINES[leg.line].color,
        from: leg.stations[0],
        to: leg.stations[leg.stations.length - 1],
        towards: directionTowards(leg.line, leg.stations[0], leg.stations[1]),
        stations: leg.stations,
        stops,
        km,
        durationMinutes: Math.round(stops * MINUTES_PER_STOP),
      };
    });

  if (realLegs.length === 0) return null;

  const interchanges = realLegs.slice(1).map((leg) => leg.from);
  const totalStops = realLegs.reduce((sum, leg) => sum + leg.stops, 0);
  const totalKm = +realLegs.reduce((sum, leg) => sum + leg.km, 0).toFixed(2);
  const durationMinutes =
    realLegs.reduce((sum, leg) => sum + leg.durationMinutes, 0) +
    interchanges.length * INTERCHANGE_MINUTES;

  return {
    from,
    to,
    legs: realLegs,
    interchanges,
    totalStops,
    totalKm,
    durationMinutes,
    fare: calculateFare(totalKm),
  };
}

/** Terminal station a train is heading towards, for platform signage. */
function directionTowards(lineId, current, next) {
  const stations = LINES[lineId].stations;
  const forward = stations.indexOf(next) > stations.indexOf(current);
  return forward ? stations[stations.length - 1] : stations[0];
}

/** Handy origin/destination pairs offered as one-tap shortcuts. */
export const POPULAR_ROUTES = [
  { from: 'Nadaprabhu Kempegowda Station, Majestic', to: 'Whitefield (Kadugodi)', label: 'Majestic → Whitefield' },
  { from: 'Mahatma Gandhi Road', to: 'Electronic City', label: 'MG Road → Electronic City' },
  { from: 'Indiranagar', to: 'Jayanagara', label: 'Indiranagar → Jayanagar' },
  { from: 'Yeshwanthpur', to: 'Silk Institute', label: 'Yeshwanthpur → Silk Institute' },
  { from: 'Krishnarajapura', to: 'Rashtreeya Vidyalaya Road', label: 'KR Puram → RV Road' },
];
