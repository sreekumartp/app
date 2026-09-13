import {
  LINES,
  LINE_IDS,
  MAP_LAYOUT,
  getAllStations,
  getInterchanges,
  getMapPoint,
  getRouteGeometry,
  getStationDetail,
  getStationTimetable,
  findStations,
  calculateFare,
  planJourney,
  shortName,
  POPULAR_ROUTES,
  INTERCHANGE_MAJESTIC,
  INTERCHANGE_RV_ROAD,
} from './metro';

describe('Namma Metro network data', () => {
  test('has the three operational lines', () => {
    expect(Object.keys(LINES)).toEqual(['purple', 'green', 'yellow']);
  });

  test('station counts match the running network', () => {
    expect(LINES.purple.stations).toHaveLength(37);
    expect(LINES.green.stations).toHaveLength(32);
    expect(LINES.yellow.stations).toHaveLength(16);
  });

  test('no line repeats a station', () => {
    Object.values(LINES).forEach((line) => {
      expect(new Set(line.stations).size).toBe(line.stations.length);
    });
  });

  test('Majestic and RV Road are the interchanges', () => {
    expect(getInterchanges().sort()).toEqual([
      'Nadaprabhu Kempegowda Station, Majestic',
      'Rashtreeya Vidyalaya Road',
    ]);
  });

  test('unique station list merges shared stations', () => {
    const all = getAllStations();
    expect(all).toHaveLength(37 + 32 + 16 - 2);
    const majestic = all.find((s) => s.name.includes('Majestic'));
    expect(majestic.lines.sort()).toEqual(['green', 'purple']);
  });
});

describe('station search', () => {
  test('resolves colloquial names', () => {
    expect(findStations('majestic')[0].name).toContain('Majestic');
    expect(findStations('mg road')[0].name).toBe('Mahatma Gandhi Road');
    expect(findStations('rv road')[0].name).toBe('Rashtreeya Vidyalaya Road');
  });

  test('matches by prefix before substring', () => {
    const results = findStations('jaya');
    expect(results[0].name.toLowerCase().startsWith('jaya')).toBe(true);
  });

  test('empty query returns nothing', () => {
    expect(findStations('  ')).toEqual([]);
  });
});

describe('fare slabs', () => {
  test('minimum and maximum fares', () => {
    expect(calculateFare(1).token).toBe(10);
    expect(calculateFare(40).token).toBe(90);
  });

  test('smart card is cheaper than a token', () => {
    const fare = calculateFare(12);
    expect(fare.token).toBe(60);
    expect(fare.smartCard).toBeLessThan(fare.token);
    expect(fare.offPeak).toBeLessThan(fare.smartCard);
  });
});

describe('journey planning', () => {
  test('same-line journey has a single leg', () => {
    const journey = planJourney('Indiranagar', 'Mahatma Gandhi Road');
    expect(journey.legs).toHaveLength(1);
    expect(journey.legs[0].line).toBe('purple');
    expect(journey.totalStops).toBe(3);
    expect(journey.interchanges).toEqual([]);
  });

  test('reports the direction to travel in', () => {
    expect(planJourney('Indiranagar', 'Mahatma Gandhi Road').legs[0].towards).toBe('Challaghatta');
    expect(planJourney('Mahatma Gandhi Road', 'Indiranagar').legs[0].towards).toBe('Whitefield (Kadugodi)');
  });

  test('purple to green changes at Majestic', () => {
    const journey = planJourney('Indiranagar', 'Jayanagara');
    expect(journey.legs).toHaveLength(2);
    expect(journey.interchanges).toEqual(['Nadaprabhu Kempegowda Station, Majestic']);
    expect(journey.legs[0].line).toBe('purple');
    expect(journey.legs[1].line).toBe('green');
  });

  test('purple to yellow needs both interchanges', () => {
    const journey = planJourney('Mahatma Gandhi Road', 'Electronic City');
    expect(journey.legs.map((leg) => leg.line)).toEqual(['purple', 'green', 'yellow']);
    expect(journey.interchanges).toEqual([
      'Nadaprabhu Kempegowda Station, Majestic',
      'Rashtreeya Vidyalaya Road',
    ]);
    expect(journey.durationMinutes).toBeGreaterThan(journey.totalStops * 2);
  });

  test('starting at an interchange does not count as a change', () => {
    const journey = planJourney('Rashtreeya Vidyalaya Road', 'Bommasandra');
    expect(journey.legs).toHaveLength(1);
    expect(journey.legs[0].line).toBe('yellow');
    expect(journey.interchanges).toEqual([]);
  });

  test('legs chain end to end from origin to destination', () => {
    const journey = planJourney('Whitefield (Kadugodi)', 'Silk Institute');
    expect(journey.legs[0].from).toBe('Whitefield (Kadugodi)');
    expect(journey.legs[journey.legs.length - 1].to).toBe('Silk Institute');
    journey.legs.forEach((leg, i) => {
      if (i > 0) expect(leg.from).toBe(journey.legs[i - 1].to);
      expect(leg.stations).toHaveLength(leg.stops + 1);
    });
  });

  test('is symmetric in cost', () => {
    const out = planJourney('Baiyappanahalli', 'Banashankari');
    const back = planJourney('Banashankari', 'Baiyappanahalli');
    expect(back.totalStops).toBe(out.totalStops);
    expect(back.fare.token).toBe(out.fare.token);
  });

  test('long cross-city trips hit the fare ceiling', () => {
    const journey = planJourney('Whitefield (Kadugodi)', 'Challaghatta');
    expect(journey.fare.token).toBe(90);
    expect(journey.totalKm).toBeGreaterThan(40);
  });

  test('rejects missing or identical stations', () => {
    expect(planJourney('Indiranagar', 'Indiranagar')).toBeNull();
    expect(planJourney('Indiranagar', 'Nowhere')).toBeNull();
    expect(planJourney('', 'Indiranagar')).toBeNull();
  });

  test('every popular route is plannable', () => {
    POPULAR_ROUTES.forEach((route) => {
      expect(planJourney(route.from, route.to)).not.toBeNull();
    });
  });

  test('every station pair is reachable', () => {
    const names = getAllStations().map((s) => s.name);
    names.forEach((from) => {
      const journey = planJourney(from, 'Bommasandra');
      if (from !== 'Bommasandra') expect(journey).not.toBeNull();
    });
  });
});

describe('station timetables', () => {
  test('a terminal is only served in one direction', () => {
    const rows = getStationTimetable('Whitefield (Kadugodi)');
    expect(rows).toHaveLength(1);
    expect(rows[0].towards).toBe('Challaghatta');
    expect(rows[0].first).toBe('05:00');
  });

  test('trains reach a mid-line station later than the terminal', () => {
    const [towardsChallaghatta] = getStationTimetable('Indiranagar');
    expect(towardsChallaghatta.first > '05:00').toBe(true);
    expect(towardsChallaghatta.firstSunday > '07:00').toBe(true);
    expect(towardsChallaghatta.last > '23:00').toBe(true);
  });

  test('an interchange is served in four directions', () => {
    expect(getStationTimetable(INTERCHANGE_MAJESTIC)).toHaveLength(4);
  });

  test('times are well-formed clock values', () => {
    getAllStations().forEach((station) => {
      getStationTimetable(station.name).forEach((row) => {
        expect(row.first).toMatch(/^[0-2]\d:[0-5]\d$/);
        expect(row.last).toMatch(/^[0-2]\d:[0-5]\d$/);
      });
    });
  });
});

describe('station detail', () => {
  test('reports position and neighbours on each line', () => {
    const detail = getStationDetail('Indiranagar');
    expect(detail.lines).toHaveLength(1);
    expect(detail.isInterchange).toBe(false);
    expect(detail.lines[0].previous).toBe('Swami Vivekananda Road');
    expect(detail.lines[0].next).toBe('Halasuru');
  });

  test('terminals have no neighbour on one side', () => {
    const detail = getStationDetail('Bommasandra');
    expect(detail.lines[0].next).toBeNull();
    expect(detail.lines[0].previous).toBe('Hebbagodi');
  });

  test('an interchange reports both lines', () => {
    const detail = getStationDetail(INTERCHANGE_MAJESTIC);
    expect(detail.isInterchange).toBe(true);
    expect(detail.lines.map((l) => l.line)).toEqual(['purple', 'green']);
  });

  test('unknown stations return null', () => {
    expect(getStationDetail('Nowhere')).toBeNull();
  });
});

describe('map layout', () => {
  test('every station is placed', () => {
    LINE_IDS.forEach((id) => {
      expect(MAP_LAYOUT.lines[id]).toHaveLength(LINES[id].stations.length);
    });
  });

  test('interchanges sit at exactly one point on both their lines', () => {
    expect(getMapPoint('purple', INTERCHANGE_MAJESTIC)).toEqual(
      getMapPoint('green', INTERCHANGE_MAJESTIC)
    );
    expect(getMapPoint('green', INTERCHANGE_RV_ROAD)).toEqual(
      getMapPoint('yellow', INTERCHANGE_RV_ROAD)
    );
  });

  test('all points fall inside the viewBox', () => {
    const [minX, minY, width, height] = MAP_LAYOUT.viewBox.split(' ').map(Number);
    LINE_IDS.forEach((id) => {
      MAP_LAYOUT.lines[id].forEach((p) => {
        expect(p.x).toBeGreaterThanOrEqual(minX);
        expect(p.y).toBeGreaterThanOrEqual(minY);
        expect(p.x).toBeLessThanOrEqual(minX + width);
        expect(p.y).toBeLessThanOrEqual(minY + height);
      });
    });
  });

  test('route geometry traces one polyline per leg', () => {
    const journey = planJourney('Mahatma Gandhi Road', 'Electronic City');
    const geometry = getRouteGeometry(journey);
    expect(geometry).toHaveLength(3);
    geometry.forEach((leg, i) => {
      expect(leg.points).toHaveLength(journey.legs[i].stations.length);
    });
  });

  test('no route means nothing to draw', () => {
    expect(getRouteGeometry(null)).toEqual([]);
  });
});

describe('shortName', () => {
  test('trims the long official names', () => {
    expect(shortName(INTERCHANGE_MAJESTIC)).toBe('Majestic');
    expect(shortName(INTERCHANGE_RV_ROAD)).toBe('RV Road');
    expect(shortName('Indiranagar')).toBe('Indiranagar');
  });
});
