import {
  BarChart3,
  Cloud,
  Code,
  Cpu,
  Globe,
  Heart,
  Layers,
  Rocket,
  Smartphone,
  Sparkles,
  Star,
  Target,
  type LucideIcon,
} from 'lucide-react'

import type {
  MeadaCtaProps,
  MeadaPortfolioProps,
  MeadaServicesProps,
} from '@/lib/cms/cms-block-type'
import { safeUrl } from '@/lib/cms/safe-url'
import { slotOutlineStyle } from '@/lib/cms/slot-highlight'

/**
 * Seções da marca Meada (preset meada-dark) — réplicas FIÉIS das seções do meada-page
 * (src/app/_no-ai.tsx): SERVICES (grid 6 cards), PORTFOLIO (cards curados) e CTA (glow + gradiente).
 * Identidade própria cravada (cores/gradiente), estilo inline px-a-px. Grids via classes Tailwind
 * nativas (lição do Tailwind 4 — classes custom são tree-shaken). Server-safe (sem estado).
 */

const ICONS: Record<string, LucideIcon> = {
  Code,
  Cloud,
  Heart,
  Smartphone,
  Layers,
  BarChart3,
  Cpu,
  Globe,
  Rocket,
  Sparkles,
  Star,
  Target,
}

// ---- SERVICES ----
export function MeadaServices({ props }: { props: MeadaServicesProps }) {
  return (
    <section
      style={{
        padding: '9rem 4rem',
        borderTop: '1px solid rgba(59,130,246,0.1)',
        borderBottom: '1px solid rgba(59,130,246,0.1)',
        background:
          'linear-gradient(180deg, transparent 0%, rgba(59,130,246,0.025) 50%, transparent 100%)',
      }}
    >
      <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
        <div style={{ marginBottom: '5rem' }}>
          {props.eyebrow && (
            <span
              style={{
                fontSize: '11px',
                color: 'rgb(96,165,250)',
                textTransform: 'uppercase',
                letterSpacing: '0.1em',
                fontWeight: 700,
              }}
            >
              {props.eyebrow}
            </span>
          )}
          {props.title && (
            <h2
              style={{
                fontSize: 'clamp(2.2rem, 4vw, 3.2rem)',
                fontWeight: 900,
                lineHeight: 1.2,
                marginTop: '1rem',
              }}
            >
              {props.title}
            </h2>
          )}
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3" style={{ gap: '2rem' }}>
          {(props.items ?? []).map((s, i) => {
            const Icon = ICONS[s.icon] ?? Code
            const color = s.color || '#60a5fa'
            return (
              <div
                key={i}
                style={{
                  padding: '2.5rem',
                  borderRadius: '16px',
                  border: '1px solid rgba(59,130,246,0.1)',
                  background: 'rgba(15,23,42,0.4)',
                  backdropFilter: 'blur(12px)',
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <div
                  style={{
                    width: '52px',
                    height: '52px',
                    borderRadius: '14px',
                    marginBottom: '1.5rem',
                    background: `linear-gradient(135deg, ${color}22, ${color}08)`,
                    border: `1px solid ${color}30`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }}
                >
                  <Icon size={24} color={color} strokeWidth={1.5} />
                </div>
                <h3
                  style={{
                    fontSize: '18px',
                    fontWeight: 700,
                    marginBottom: '0.75rem',
                    lineHeight: 1.3,
                  }}
                >
                  {s.title}
                </h3>
                <p
                  style={{
                    color: 'rgb(148,163,184)',
                    fontSize: '14px',
                    lineHeight: 1.7,
                    marginBottom: '1.75rem',
                    flex: 1,
                  }}
                >
                  {s.description}
                </p>
                {s.linkLabel && (
                  <a
                    href={safeUrl(s.linkHref) || '#'}
                    style={{ color, fontWeight: 600, fontSize: '14px', textDecoration: 'none' }}
                  >
                    {s.linkLabel}
                  </a>
                )}
              </div>
            )
          })}
        </div>
      </div>
    </section>
  )
}

// ---- PORTFOLIO ----
function parseTags(tags: string): string[] {
  return (tags ?? '')
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)
    .slice(0, 3)
}
export function MeadaPortfolio({ props }: { props: MeadaPortfolioProps }) {
  return (
    <section style={{ padding: '9rem 4rem', borderTop: '1px solid rgba(59,130,246,0.1)' }}>
      <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-end',
            marginBottom: '4rem',
            flexWrap: 'wrap',
            gap: '2rem',
          }}
        >
          <div>
            {props.eyebrow && (
              <span
                style={{
                  fontSize: '11px',
                  color: 'rgb(96,165,250)',
                  textTransform: 'uppercase',
                  letterSpacing: '0.1em',
                  fontWeight: 700,
                }}
              >
                {props.eyebrow}
              </span>
            )}
            {props.title && (
              <h2
                style={{
                  fontSize: 'clamp(2.2rem, 4vw, 3.2rem)',
                  fontWeight: 900,
                  lineHeight: 1.2,
                  marginTop: '1rem',
                }}
              >
                {props.title}
              </h2>
            )}
          </div>
          {props.linkLabel && (
            <a
              href={safeUrl(props.linkHref) || '#'}
              style={{
                padding: '12px 28px',
                borderRadius: '10px',
                border: '1px solid rgba(59,130,246,0.28)',
                background: 'transparent',
                color: 'rgb(96,165,250)',
                fontWeight: 600,
                fontSize: '14px',
                textDecoration: 'none',
                whiteSpace: 'nowrap',
              }}
            >
              {props.linkLabel}
            </a>
          )}
        </div>

        {(props.items ?? []).length === 0 ? (
          <p style={{ color: 'rgb(148,163,184)', fontSize: '14px' }}>
            Nenhum projeto cadastrado ainda.
          </p>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4" style={{ gap: '1.5rem' }}>
            {(props.items ?? []).map((p, i) => {
              const accent = p.accentColor || '#3b82f6'
              const tags = parseTags(p.tags)
              return (
                <a
                  key={i}
                  href={safeUrl(p.href) || '#'}
                  style={{
                    borderRadius: '16px',
                    overflow: 'hidden',
                    border: '1px solid rgba(59,130,246,0.1)',
                    background: 'rgba(15,23,42,0.4)',
                    display: 'flex',
                    flexDirection: 'column',
                    textDecoration: 'none',
                    color: 'inherit',
                  }}
                >
                  <div
                    style={{
                      height: '140px',
                      position: 'relative',
                      overflow: 'hidden',
                      background: '#0f172a',
                    }}
                  >
                    {safeUrl(p.imageUrl) ? (
                      /* eslint-disable-next-line @next/next/no-img-element -- URL externa colada pelo tenant */
                      <img
                        src={safeUrl(p.imageUrl)}
                        alt={p.name}
                        style={{
                          width: '100%',
                          height: '100%',
                          objectFit: 'cover',
                          objectPosition: 'top',
                          display: 'block',
                        }}
                      />
                    ) : (
                      <div
                        style={{
                          width: '100%',
                          height: '100%',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          color: 'rgba(255,255,255,0.15)',
                          fontSize: '32px',
                          fontWeight: 900,
                        }}
                      >
                        {(p.name || '?')[0]}
                      </div>
                    )}
                    {p.category && (
                      <div
                        style={{
                          position: 'absolute',
                          top: '0.75rem',
                          left: '0.85rem',
                          padding: '3px 9px',
                          borderRadius: '24px',
                          background: 'rgba(0,0,0,0.6)',
                          backdropFilter: 'blur(8px)',
                          border: `1px solid ${accent}40`,
                          fontSize: '10px',
                          fontWeight: 700,
                          color: accent,
                          letterSpacing: '0.07em',
                          textTransform: 'uppercase',
                        }}
                      >
                        {p.category}
                      </div>
                    )}
                  </div>
                  <div
                    style={{
                      padding: '1.25rem',
                      flex: 1,
                      display: 'flex',
                      flexDirection: 'column',
                    }}
                  >
                    <p
                      style={{
                        fontSize: '16px',
                        fontWeight: 800,
                        color: '#e2e8f0',
                        marginBottom: '0.4rem',
                        letterSpacing: '-0.01em',
                      }}
                    >
                      {p.name}
                    </p>
                    {p.description && (
                      <p
                        style={{
                          fontSize: '12px',
                          color: 'rgb(148,163,184)',
                          lineHeight: 1.6,
                          marginBottom: '1rem',
                          flex: 1,
                        }}
                      >
                        {p.description}
                      </p>
                    )}
                    {tags.length > 0 && (
                      <div
                        style={{
                          display: 'flex',
                          flexWrap: 'wrap',
                          gap: '0.35rem',
                          marginBottom: '1rem',
                        }}
                      >
                        {tags.map((t) => (
                          <span
                            key={t}
                            style={{
                              padding: '2px 8px',
                              borderRadius: '5px',
                              background: 'rgba(59,130,246,0.08)',
                              border: '1px solid rgba(59,130,246,0.15)',
                              fontSize: '10px',
                              fontWeight: 600,
                              color: 'rgb(148,163,184)',
                            }}
                          >
                            {t}
                          </span>
                        ))}
                      </div>
                    )}
                    <span style={{ color: accent, fontWeight: 600, fontSize: '13px' }}>
                      Ver detalhes →
                    </span>
                  </div>
                </a>
              )
            })}
          </div>
        )}
      </div>
    </section>
  )
}

// ---- CTA ----
export function MeadaCta({ props, activeSlot }: { props: MeadaCtaProps; activeSlot?: string }) {
  return (
    <section style={{ padding: '11rem 4rem', position: 'relative', overflow: 'hidden' }}>
      <div
        style={{
          position: 'absolute',
          top: '-25%',
          right: '-5%',
          width: '600px',
          height: '600px',
          background: 'radial-gradient(circle, rgba(59,130,246,0.22) 0%, transparent 65%)',
          borderRadius: '50%',
          filter: 'blur(70px)',
        }}
      />
      <div
        style={{
          position: 'absolute',
          bottom: '-25%',
          left: '5%',
          width: '600px',
          height: '600px',
          background: 'radial-gradient(circle, rgba(139,92,246,0.18) 0%, transparent 65%)',
          borderRadius: '50%',
          filter: 'blur(70px)',
        }}
      />
      <div
        style={{
          maxWidth: '780px',
          margin: '0 auto',
          textAlign: 'center',
          position: 'relative',
          zIndex: 1,
        }}
      >
        <div data-slot="content" style={slotOutlineStyle(activeSlot, 'content')}>
          <h2
            style={{
              fontSize: 'clamp(2.2rem, 5vw, 3.8rem)',
              fontWeight: 900,
              lineHeight: 1.15,
              marginBottom: '2rem',
            }}
          >
            {props.titlePrefix}{' '}
            <span
              style={{
                backgroundImage: 'linear-gradient(125deg, #60a5fa, #a855f7, #ec4899)',
                backgroundClip: 'text',
                WebkitBackgroundClip: 'text',
                color: 'transparent',
              }}
            >
              {props.gradientText}
            </span>
          </h2>
          {props.subtitle && (
            <p
              style={{
                fontSize: '18px',
                color: 'rgb(203,213,225)',
                lineHeight: 1.85,
                maxWidth: '560px',
                margin: '0 auto 3.5rem',
              }}
            >
              {props.subtitle}
            </p>
          )}
        </div>
        <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap' }}>
          {props.primaryLabel && (
            <a
              href={safeUrl(props.primaryHref) || '#'}
              data-slot="buttonPrimary"
              style={{
                padding: '13px 32px',
                background: 'linear-gradient(135deg, #3b82f6, #6366f1)',
                borderRadius: '12px',
                color: 'white',
                fontWeight: 600,
                fontSize: '15px',
                textDecoration: 'none',
                display: 'inline-block',
                boxShadow: '0 8px 24px rgba(59,130,246,0.3)',
                ...slotOutlineStyle(activeSlot, 'buttonPrimary'),
              }}
            >
              {props.primaryLabel}
            </a>
          )}
          {props.secondaryLabel && (
            <a
              href={safeUrl(props.secondaryHref) || '#'}
              data-slot="buttonSecondary"
              style={{
                padding: '13px 32px',
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.12)',
                borderRadius: '12px',
                color: 'rgba(255,255,255,0.75)',
                fontWeight: 600,
                fontSize: '15px',
                textDecoration: 'none',
                display: 'inline-block',
                ...slotOutlineStyle(activeSlot, 'buttonSecondary'),
              }}
            >
              {props.secondaryLabel}
            </a>
          )}
        </div>
      </div>
    </section>
  )
}
