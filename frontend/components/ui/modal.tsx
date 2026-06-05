'use client';

import { useEffect } from 'react';

export function Modal({
  open,
  onClose,
  title,
  children,
  size = 'md',
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  size?: 'sm' | 'md' | 'lg';
}) {
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  const widths = { sm: 'max-w-sm', md: 'max-w-md', lg: 'max-w-lg' };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
      <div
        className={`bg-white rounded-xl border border-border shadow-xl w-full ${widths[size]} p-6 relative max-h-[90vh] overflow-y-auto`}
      >
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-muted-foreground hover:text-foreground text-xl leading-none"
          aria-label="Fechar"
        >
          ×
        </button>
        <h2 className="text-base font-semibold text-foreground mb-4">{title}</h2>
        {children}
      </div>
    </div>
  );
}
