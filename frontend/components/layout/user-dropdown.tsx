'use client'

import { Menu } from '@base-ui/react/menu'
import { ChevronDown, LogOut } from 'lucide-react'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import type { Me } from '@/lib/api/me'
import { createClient } from '@/lib/supabase/client'
import { cn } from '@/lib/utils'

/** Iniciais para o avatar (1-2 letras do email, antes do @). */
function initials(email: string): string {
  const local = email.split('@')[0] ?? email
  const parts = local.split(/[.\-_]/).filter(Boolean)
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase()
  }
  return local.slice(0, 2).toUpperCase()
}

/** Rótulo pt-BR do papel exibido no header do dropdown. */
function roleLabel(me: Me | undefined): string {
  if (!me) return ''
  if (me.role === 'super_admin') return 'Super Admin'
  switch (me.tenantRole) {
    case 'owner':
      return 'Owner'
    case 'agent':
      return 'Agent'
    default:
      return 'Admin'
  }
}

function Avatar({ email, className }: { email: string; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center justify-center rounded-full bg-primary text-primary-foreground',
        className,
      )}
      aria-hidden="true"
    >
      {initials(email)}
    </span>
  )
}

/**
 * Dropdown de usuário no header (camada UI). Trigger = avatar (iniciais) + chevron.
 * Conteúdo: header com avatar maior + email + badge da role, separador, item "Sair"
 * (destrutivo). Substitui o SignOutButton solto. base-ui Menu cuida de a11y/teclado/foco.
 *
 * O signOut preserva o comportamento do SignOutButton: tenta sair e, com erro ou não,
 * empurra para /login (melhor sair da tela protegida que ficar meio-logado).
 */
export function UserDropdown({ me }: { me: Me | undefined }) {
  const router = useRouter()
  const [signingOut, setSigningOut] = useState(false)
  const email = me?.email ?? '—'

  async function handleSignOut() {
    setSigningOut(true)
    const supabase = createClient()
    const { error } = await supabase.auth.signOut()
    if (error) {
      console.error('signOut failed:', error.message)
    }
    router.push('/login')
  }

  return (
    <Menu.Root>
      <Menu.Trigger
        className="flex items-center gap-2 rounded-md px-1.5 py-1 text-sm outline-none hover:bg-muted/50 focus-visible:ring-3 focus-visible:ring-ring/50"
        aria-label="Menu do usuário"
      >
        <Avatar email={email} className="size-7 text-xs font-medium" />
        <ChevronDown className="size-4 text-muted-foreground" />
      </Menu.Trigger>
      <Menu.Portal>
        <Menu.Positioner sideOffset={8} align="end" className="z-50">
          <Menu.Popup className="min-w-56 rounded-lg border border-border bg-popover p-1 text-popover-foreground shadow-lg outline-none">
            <div className="flex items-center gap-3 px-3 py-2.5">
              <Avatar email={email} className="size-9 text-sm font-medium" />
              <div className="min-w-0">
                <p className="truncate text-sm font-medium text-foreground">{email}</p>
                <p className="text-xs text-muted-foreground">{roleLabel(me)}</p>
              </div>
            </div>
            <Menu.Separator className="my-1 h-px bg-border" />
            <Menu.Item
              onClick={handleSignOut}
              disabled={signingOut}
              className="flex cursor-pointer items-center gap-2 rounded-md px-3 py-2 text-sm text-destructive outline-none disabled:opacity-50 data-[highlighted]:bg-destructive/10"
            >
              <LogOut className="size-4" />
              {signingOut ? 'Saindo…' : 'Sair'}
            </Menu.Item>
          </Menu.Popup>
        </Menu.Positioner>
      </Menu.Portal>
    </Menu.Root>
  )
}
