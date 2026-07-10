'use client'

import {
  Baby,
  BedDouble,
  Camera,
  Car,
  ChefHat,
  Croissant,
  Dumbbell,
  Fish,
  Flower2,
  Gem,
  Glasses,
  GraduationCap,
  Heart,
  Package,
  PartyPopper,
  PawPrint,
  PenTool,
  Pill,
  Pizza,
  Plane,
  Ruler,
  Salad,
  Scale,
  School,
  Scissors,
  Shirt,
  Smile,
  Sparkles,
  Stethoscope,
  Store,
  Utensils,
  WashingMachine,
  Wine,
  Wrench,
  type LucideIcon,
} from 'lucide-react'
import { useEffect, useState } from 'react'

import type { NichesGridProps } from '@/lib/cms/cms-block-type'
import nichesFallback from '@/lib/cms/niches-fallback.json'
import { PALETTES } from '@/lib/themes/palettes'

/** Card vindo do backend (/public/niches). A cor é resolvida aqui pelo paletteId. */
type NicheCard = { profileId: string; productName: string; subdomain: string; paletteId: string }

/** Ícone (lucide) por nicho — a "thumb" de cada card. Nicho sem entrada cai no Store. */
const NICHE_ICON: Record<string, LucideIcon> = {
  adega: Wine,
  atelie: Ruler,
  casamento: Heart,
  concessionaria: Car,
  cursos: GraduationCap,
  dermatologia: Stethoscope,
  escola: School,
  floricultura: Flower2,
  fotografia: Camera,
  lavanderia: WashingMachine,
  lingerie: Shirt,
  las: Package,
  moda_infantil: Baby,
  padaria: Croissant,
  papelaria: PenTool,
  pizzaria: Pizza,
  suplementos: Pill,
  sushi: Fish,
  viagens: Plane,
  otica: Glasses,
  comida: Utensils,
  dental: Smile,
  salon: Sparkles,
  barbearia: Scissors,
  estetica: Gem,
  legal: Scale,
  restaurant: ChefHat,
  pousada: BedDouble,
  academia: Dumbbell,
  pet: PawPrint,
  oficina: Wrench,
  nutri: Salad,
  eventos: PartyPopper,
}

function iconFor(profileId: string): LucideIcon {
  return NICHE_ICON[profileId] ?? Store
}

function colorFor(paletteId: string): string {
  return PALETTES.find((p) => p.id === paletteId)?.primary ?? '#3b82f6'
}

/** Base pública do backend (mesma env do client). */
function apiBase(): string {
  return process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8095'
}

/** Lista ESTÁTICA (fallback) — aparece de cara e permanece se o backend não responder (ex.: prod). */
function fallbackCards(mode: NichesGridProps['mode']): NicheCard[] {
  const list = mode === 'featured' ? nichesFallback.featured : nichesFallback.all
  return list as NicheCard[]
}

/**
 * Grade de nichos (produtos do Meada) — bloco AUTO-POPULADO. Busca os nichos no backend
 * (/public/niches?featured=...) e monta os cards. mode 'featured' = home (destaques);
 * 'all' = página /produtos (todos, na ordem). Card = nome do nicho + cor da paleta + link
 * pra PÁGINA INSTITUCIONAL do nicho (meadadigital.com/{profileId} — a vitrine do produto, com
 * hero/features/CTA editável no CMS). Client component (faz fetch); SSR renderiza o cabeçalho.
 */
export function NichesGrid({ props }: { props: NichesGridProps }) {
  // Começa com a lista ESTÁTICA (aparece de cara, com as thumbs) e SUBSTITUI pela do backend se
  // ele responder. Em prod (backend morto) o fetch expira (2,5s) e a estática permanece.
  const [cards, setCards] = useState<NicheCard[]>(() => fallbackCards(props.mode))
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    const featured = props.mode === 'featured'
    fetch(`${apiBase()}/public/niches?featured=${featured}`, {
      cache: 'no-store',
      signal: AbortSignal.timeout(2500),
    })
      .then((r) => (r.ok ? (r.json() as Promise<NicheCard[]>) : null))
      .then((data) => {
        if (Array.isArray(data) && data.length > 0) setCards(data)
      })
      .catch(() => {
        /* mantém o fallback estático */
      })
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
              const Icon = iconFor(c.profileId)
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
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      marginBottom: '1.25rem',
                    }}
                  >
                    <Icon size={24} color="#fff" strokeWidth={2} aria-hidden />
                  </div>
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
