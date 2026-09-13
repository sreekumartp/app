import { render, screen, fireEvent, within } from '@testing-library/react';
import App from './App';

const plan = (from, to) => {
  fireEvent.change(screen.getByLabelText('From'), { target: { value: from } });
  fireEvent.change(screen.getByLabelText('To'), { target: { value: to } });
};

const goTo = (tab) =>
  fireEvent.click(
    within(screen.getByRole('navigation', { name: 'Sections' })).getByText(tab)
  );

describe('Namma Metro app', () => {
  beforeEach(() => localStorage.clear());

  test('opens on the journey planner', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: 'Namma Metro' })).toBeInTheDocument();
    expect(screen.getByLabelText('From')).toBeInTheDocument();
    expect(screen.getByText('Popular journeys')).toBeInTheDocument();
  });

  test('plans a direct journey', () => {
    render(<App />);
    plan('Indiranagar', 'Mahatma Gandhi Road');

    expect(screen.getByText('₹20')).toBeInTheDocument();
    expect(screen.getByText('6 min')).toBeInTheDocument();
    expect(screen.getByText('direct train')).toBeInTheDocument();
    expect(screen.getByText(/towards Challaghatta/)).toBeInTheDocument();
  });

  test('spells out the interchange on a two-line journey', () => {
    render(<App />);
    plan('Indiranagar', 'Jayanagara');

    const change = screen.getByText(/Change at/);
    expect(within(change).getByText('Nadaprabhu Kempegowda Station, Majestic')).toBeInTheDocument();
    expect(change).toHaveTextContent('Green Line platform');
  });

  test('expands a leg to list intermediate stations', () => {
    const { container } = render(<App />);
    plan('Indiranagar', 'Mahatma Gandhi Road');

    expect(container.querySelector('.leg-stations')).toBeNull();
    fireEvent.click(screen.getByText(/3 stops/));
    const stops = within(container.querySelector('.leg-stations'));
    expect(stops.getByText('Halasuru')).toBeInTheDocument();
    expect(stops.getByText('Trinity')).toBeInTheDocument();
  });

  test('swapping reverses the journey', () => {
    render(<App />);
    plan('Indiranagar', 'Mahatma Gandhi Road');
    fireEvent.click(screen.getByTitle('Swap stations'));

    expect(screen.getByLabelText('From')).toHaveValue('Mahatma Gandhi Road');
    expect(screen.getByLabelText('To')).toHaveValue('Indiranagar');
    expect(screen.getByText(/towards Whitefield/)).toBeInTheDocument();
  });

  test('saved home and work give a one-tap commute in both directions', () => {
    render(<App />);
    fireEvent.change(screen.getByLabelText('Home'), { target: { value: 'Indiranagar' } });
    fireEvent.change(screen.getByLabelText('Work'), { target: { value: 'Electronic City' } });

    fireEvent.click(screen.getByText('Indiranagar → Electronic City'));
    expect(screen.getByLabelText('From')).toHaveValue('Indiranagar');
    expect(screen.getByLabelText('To')).toHaveValue('Electronic City');

    fireEvent.click(screen.getByText('Electronic City → Indiranagar'));
    expect(screen.getByLabelText('From')).toHaveValue('Electronic City');
  });

  test('remembers recent journeys across reloads', () => {
    const { unmount } = render(<App />);
    plan('Indiranagar', 'Jayanagara');
    unmount();

    render(<App />);
    expect(screen.getByText('Recent')).toBeInTheDocument();
    expect(screen.getByText('Indiranagar → Jayanagara')).toBeInTheDocument();
  });

  test('the map draws every line and highlights a planned route', () => {
    const { container } = render(<App />);
    goTo('Map');
    expect(container.querySelectorAll('.network polyline')).toHaveLength(3);
    expect(container.querySelector('.network.dimmed')).toBeNull();

    goTo('Plan');
    plan('Indiranagar', 'Jayanagara');
    goTo('Map');
    expect(container.querySelector('.network.dimmed')).toBeInTheDocument();
    expect(screen.getByText(/Showing Indiranagar → Jayanagara/)).toBeInTheDocument();
  });

  test('tapping a station on the map offers to plan from it', () => {
    const { container } = render(<App />);
    goTo('Map');
    fireEvent.click(container.querySelector('[aria-label="Indiranagar"]'));

    fireEvent.click(screen.getByText('Travel from here'));
    expect(screen.getByLabelText('From')).toHaveValue('Indiranagar');
  });

  test('station search resolves a landmark name', () => {
    render(<App />);
    goTo('Stations');
    fireEvent.change(screen.getByLabelText('Find a station'), { target: { value: 'mg road' } });

    expect(screen.getByText('1 matching')).toBeInTheDocument();
    expect(screen.getByText('Mahatma Gandhi Road')).toBeInTheDocument();
  });

  test('a station page shows its lines, neighbours and timetable', () => {
    render(<App />);
    goTo('Stations');
    fireEvent.change(screen.getByLabelText('Find a station'), { target: { value: 'majestic' } });
    fireEvent.click(screen.getByText('Nadaprabhu Kempegowda Station, Majestic'));

    expect(screen.getByRole('heading', { name: /Majestic/ })).toBeInTheDocument();
    expect(screen.getByText('On the Purple Line')).toBeInTheDocument();
    expect(screen.getByText('On the Green Line')).toBeInTheDocument();
    expect(screen.getByText('First & last train')).toBeInTheDocument();
    // Majestic is served in four directions: two per line.
    expect(screen.getAllByRole('row')).toHaveLength(5); // header + 4
  });

  test('saving a station surfaces it under Saved', () => {
    render(<App />);
    goTo('Stations');
    fireEvent.change(screen.getByLabelText('Find a station'), { target: { value: 'lalbagh' } });
    fireEvent.click(screen.getByText('Lalbagh'));
    fireEvent.click(screen.getByText('☆ Save'));
    expect(screen.getByText('★ Saved')).toBeInTheDocument();

    // Going back keeps the search, and Saved is hidden while searching.
    fireEvent.click(screen.getByText('← All stations'));
    fireEvent.change(screen.getByLabelText('Find a station'), { target: { value: '' } });

    const saved = screen.getByText('Saved').closest('section');
    expect(within(saved).getByText('Lalbagh')).toBeInTheDocument();
  });

  test('theme toggle flips the document theme', () => {
    render(<App />);
    const before = document.documentElement.dataset.theme;
    fireEvent.click(screen.getByLabelText(/Switch to (light|dark) theme/));
    expect(document.documentElement.dataset.theme).not.toBe(before);
  });
});
