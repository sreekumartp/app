import { render, screen, fireEvent, within } from '@testing-library/react';
import MetroPlanner from './MetroPlanner';

const selectRoute = (from, to) => {
  fireEvent.change(screen.getByLabelText('From'), { target: { value: from } });
  fireEvent.change(screen.getByLabelText('To'), { target: { value: to } });
};

describe('MetroPlanner', () => {
  beforeEach(() => localStorage.clear());

  test('shows popular journeys before a route is chosen', () => {
    render(<MetroPlanner />);
    expect(screen.getByText('Popular journeys')).toBeInTheDocument();
    expect(screen.getByText('MG Road → Electronic City')).toBeInTheDocument();
  });

  test('lists every line in the network overview', () => {
    render(<MetroPlanner />);
    expect(screen.getByText('37 stations')).toBeInTheDocument();
    expect(screen.getByText('32 stations')).toBeInTheDocument();
    expect(screen.getByText('16 stations')).toBeInTheDocument();
  });

  test('plans a direct journey with fare and time', () => {
    render(<MetroPlanner />);
    selectRoute('Indiranagar', 'Mahatma Gandhi Road');

    // 3 stops on the Purple Line ≈ 3.6 km, which lands in the ₹20 slab.
    expect(screen.getByText('₹20')).toBeInTheDocument();
    expect(screen.getByText('₹19 on smart card')).toBeInTheDocument();
    expect(screen.getByText('6 min')).toBeInTheDocument();
    expect(screen.getByText('Direct train')).toBeInTheDocument();
    expect(screen.getByText(/towards Challaghatta/)).toBeInTheDocument();
  });

  test('spells out where to change lines', () => {
    render(<MetroPlanner />);
    selectRoute('Indiranagar', 'Jayanagara');

    const change = screen.getByText(/Change at/);
    expect(within(change).getByText('Nadaprabhu Kempegowda Station, Majestic')).toBeInTheDocument();
    expect(change).toHaveTextContent('Green Line platform');
  });

  test('expands a leg to reveal intermediate stations', () => {
    const { container } = render(<MetroPlanner />);
    selectRoute('Indiranagar', 'Mahatma Gandhi Road');

    expect(container.querySelector('.metro-stop-list')).toBeNull();
    fireEvent.click(screen.getByText(/3 stops/));

    const stops = within(container.querySelector('.metro-stop-list'));
    expect(stops.getByText('Halasuru')).toBeInTheDocument();
    expect(stops.getByText('Trinity')).toBeInTheDocument();
  });

  test('a popular-journey chip fills in the route', () => {
    render(<MetroPlanner />);
    fireEvent.click(screen.getByText('Indiranagar → Jayanagar'));
    expect(screen.getByLabelText('From')).toHaveValue('Indiranagar');
    expect(screen.getByLabelText('To')).toHaveValue('Jayanagara');
  });

  test('swapping reverses the journey', () => {
    render(<MetroPlanner />);
    selectRoute('Indiranagar', 'Mahatma Gandhi Road');
    fireEvent.click(screen.getByTitle('Swap stations'));

    expect(screen.getByLabelText('From')).toHaveValue('Mahatma Gandhi Road');
    expect(screen.getByLabelText('To')).toHaveValue('Indiranagar');
    expect(screen.getByText(/towards Whitefield/)).toBeInTheDocument();
  });

  test('remembers recent journeys', () => {
    const { unmount } = render(<MetroPlanner />);
    selectRoute('Indiranagar', 'Jayanagara');
    unmount();

    render(<MetroPlanner />);
    expect(screen.getByText('Recent')).toBeInTheDocument();
    expect(screen.getByText('Indiranagar → Jayanagara')).toBeInTheDocument();
  });
});
