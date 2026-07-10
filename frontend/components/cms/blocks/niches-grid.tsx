'use client'

import { useEffect, useState } from 'react'

import type { NichesGridProps } from '@/lib/cms/cms-block-type'
import { PALETTES } from '@/lib/themes/palettes'

/** Card vindo do backend (/public/niches). A cor é resolvida aqui pelo paletteId. */
type NicheCard = { profileId: string; productName: string; subdomain: string; paletteId: string }

function colorFor(paletteId: string): string {
  return PALETTES.find((p) => p.id === paletteId)?.primary ?? '#3b82f6'
}

/** Base pública do backend (mesma env do client). */
function apiBase(): string {
  return process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8095'
}

/**
 * Grade de nichos (produtos do Meada) — bloco AUTO-POPULADO. Busca os nichos no backend
 * (/public/niches?featured=...) e monta os cards. mode 'featured' = home (destaques);
 * 'all' = página /produtos (todos, na ordem). Card = nome do nicho + cor da paleta + link
 * pra PÁGINA INSTITUCIONAL do nicho (meadadigital.com/{profileId} — a vitrine do produto, com
 * hero/features/CTA editável no CMS). Client component (faz fetch); SSR renderiza o cabeçalho.
 */
export function NichesGrid({ props }: { props: NichesGridProps }) {
  const [cards, setCards] = useState<NicheCard[]>([])
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    const featured = props.mode === 'featured'
    fetch(`${apiBase()}/public/niches?featured=${featured}`, { cache: 'no-store' })
      .then((r) => (r.ok ? r.json() : []))
      .then((data: NicheCard[]) => setCards(Array.isArray(data) ? data : []))
      .catch(() => setCards([]))
      .finally(() => setLoaded(true))
  }, [props.mode])

  return (
    <section style={{ padding: '100px 0', position: 'relative' }}>
      <div style={{ maxWidth: '1360px', margin: '0 auto', padding: '0 2rem' }}>
        {props.eyebrow && (
          <div
            style={{
              fontSize: '14px',
              fontWeight: 600,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: '#60a5fa',
              marginBottom: '0.75rem',
            }}
          >
            {props.eyebrow}
          </div>
        )}
        {props.title && (
          <h2
            style={{
              fontSize: 'clamp(1.9rem, 3.5vw, 2.8rem)',
              fontWeight: 800,
              letterSpacing: '-0.02em',
              marginBottom: '3rem',
              color: '#fff',
            }}
          >
            {props.title}
          </h2>
        )}

        {loaded && cards.length === 0 ? (
          <p style={{ color: 'rgba(255,255,255,0.5)' }}>Nenhum nicho em destaque ainda.</p>
        ) : (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
              gap: '1.5rem',
            }}
          >
            {cards.map((c) => {
              const color = colorFor(c.paletteId)
              return (
                <a
                  key={c.profileId}
                  href={`/${c.profileId}`}
                  style={{
                    display: 'block',
                    textDecoration: 'none',
                    position: 'relative',
                    overflow: 'hidden',
                    borderRadius: '18px',
                    padding: '2rem',
                    minHeight: '180px',
                    background: 'rgba(255,255,255,0.03)',
                    border: '1px solid rgba(255,255,255,0.08)',
                    transition: 'transform 0.25s, border-color 0.25s',
                  }}
                >
                  {/* barra de cor da marca do nicho */}
                  <div
                    style={{
                      position: 'absolute',
                      top: 0,
                      left: 0,
                      right: 0,
                      height: '4px',
                      background: color,
                    }}
                  />
                  <div
                    style={{
                      width: '44px',
                      height: '44px',
                      borderRadius: '12px',
                      background: color,
                      opacity: 0.9,
                      marginBottom: '1.25rem',
                    }}
                  />
                  <h3
                    style={{
                      fontSize: '1.35rem',
                      fontWeight: 700,
                      color: '#fff',
                      marginBottom: '0.5rem',
                    }}
                  >
                    {c.productName}
                  </h3>
                  <p style={{ fontSize: '14px', color: 'rgba(255,255,255,0.55)' }}>
                    Atendimento com IA por WhatsApp para {c.productName.toLowerCase()}.
                  </p>
                  <span
                    style={{
                      display: 'inline-block',
                      marginTop: '1.25rem',
                      fontSize: '14px',
                      fontWeight: 600,
                      color,
                    }}
                  >
                    Conhecer →
                  </span>
                </a>
              )
            })}
          </div>
        )}
      </div>
    </section>
  )
}
