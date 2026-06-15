'use client'

import { Laptop, Moon, Sun } from 'lucide-react'
import { useEffect, useState } from 'react'

import { Button } from '@/components/ui/button'
import { useThemeMode } from '@/components/theme-mode-provider'

/**
 * Botão de alternância do modo de tema (camada 5.9). Cicla light → dark → system → light.
 * Ícone reflete o modo atual: Sun (light), Moon (dark), Laptop (system).
 *
 * mounted guard: o modo real só é conhecido após o useEffect de hidratação do
 * ThemeModeProvider (no SSR o estado começa em 'system'). Para não renderizar um ícone
 * que pisca de errado→certo na hidratação, mostramos um placeholder neutro até montar.
 */
export function ThemeToggle() {
  const { mode, cycle } = useThemeMode()
  const [mounted, setMounted] = useState(false)

  useEffect(() => {
    setMounted(true)
  }, [])

  const label =
    mode === 'light' ? 'Tema: claro' : mode === 'dark' ? 'Tema: escuro' : 'Tema: sistema'

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
      ) : mode === 'light' ? (
        <Sun className="size-4" />
      ) : mode === 'dark' ? (
        <Moon className="size-4" />
      ) : (
        <Laptop className="size-4" />
      )}
    </Button>
  )
}
