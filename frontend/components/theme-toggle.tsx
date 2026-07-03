'use client'

import { Moon, Sun } from 'lucide-react'
import { useEffect, useState } from 'react'

import { Button } from '@/components/ui/button'
import { useThemeMode } from '@/components/theme-mode-provider'

/**
 * Botão de alternância do modo de tema. Alterna SOMENTE claro ↔ escuro (o modo 'system' foi
 * removido — o produto tem só 2 modos). Ícone: Sun (claro), Moon (escuro).
 *
 * mounted guard: o modo real só é conhecido após o useEffect de hidratação do
 * ThemeModeProvider (no SSR começa em 'light'). Para não piscar errado→certo na hidratação,
 * mostramos um placeholder neutro até montar.
 */
export function ThemeToggle() {
  const { mode, cycle } = useThemeMode()
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- guard de montagem (SSR começa 'light'; evita flash na hidratação)
    setMounted(true)
  }, [])

  const isDark = mode === 'dark'
  const label = isDark ? 'Tema: escuro' : 'Tema: claro'

  return (
    <Button
      variant="outline"
      size="icon"
      onClick={cycle}
      aria-label={`${label} (clique para alternar)`}
      title={label}
    >
      {!mounted ? (
        <Sun className="size-4 opacity-0" />
      ) : isDark ? (
        <Moon className="size-4" />
      ) : (
        <Sun className="size-4" />
      )}
    </Button>
  )
}
