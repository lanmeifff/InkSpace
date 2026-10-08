'use client';

import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { cn } from '@/lib/cn';

type ToastItem = { id: number; message: string; kind: 'info' | 'error' | 'success' };
type ToastContextValue = { push: (message: string, kind?: ToastItem['kind']) => void };

const ToastContext = createContext<ToastContextValue>({ push: () => {} });

export function useToast(): ToastContextValue {
  return useContext(ToastContext);
}

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);

  const push = useCallback((message: string, kind: ToastItem['kind'] = 'info') => {
    const id = Date.now() + Math.random();
    setItems((prev) => [...prev, { id, message, kind }]);
    window.setTimeout(() => {
      setItems((prev) => prev.filter((item) => item.id !== id));
    }, 3200);
  }, []);

  const value = useMemo(() => ({ push }), [push]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed bottom-6 right-6 z-50 flex w-[320px] flex-col gap-2">
        {items.map((item) => (
          <div
            key={item.id}
            className={cn(
              'pointer-events-auto rounded-[2px] border px-4 py-3 text-[13px] ',
              item.kind === 'error'
                ? 'border-danger/30 bg-white text-danger'
                : item.kind === 'success'
                  ? 'border-olive/30 bg-white text-olive'
                  : 'border-line bg-white text-ink',
            )}
          >
            {item.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
