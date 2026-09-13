import { useState, useEffect, useCallback } from 'react';

/**
 * State persisted to localStorage, tolerant of browsers that refuse it
 * (private windows, blocked site data). A failed read or write never breaks
 * the app — the value simply stays in memory for the session.
 */
export function useLocalStorage(key, initialValue) {
  const [value, setValue] = useState(() => {
    try {
      const stored = window.localStorage.getItem(key);
      return stored === null ? initialValue : JSON.parse(stored);
    } catch (e) {
      return initialValue;
    }
  });

  useEffect(() => {
    try {
      window.localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {
      /* storage unavailable — keep going with in-memory state */
    }
  }, [key, value]);

  const reset = useCallback(() => setValue(initialValue), [initialValue]);

  return [value, setValue, reset];
}
