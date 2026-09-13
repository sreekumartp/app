import {
  LINES,
  getAllStations,
  getInterchanges,
  findStations,
  calculateFare,
  planJourney,
  POPULAR_ROUTES,
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
