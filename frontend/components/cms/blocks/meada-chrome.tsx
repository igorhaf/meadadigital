'use client'

import Link from 'next/link'
import { useEffect, useState } from 'react'

import type { MeadaFooterProps, MeadaNavbarProps } from '@/lib/cms/cms-block-type'
import { safeUrl } from '@/lib/cms/safe-url'
import { slotOutlineStyle } from '@/lib/cms/slot-highlight'

/**
 * Chrome da marca Meada (preset meada-dark) — navbar + rodapé, réplicas FIÉIS de
 * meada-page/src/app/components/Navbar.tsx e Footer.tsx. Blocos: o root adiciona o navbar no topo e
 * o footer no fim de cada página (links editáveis nos props). Logo SVG idêntico (gradiente "M").
 * Estilo inline px-a-px. A navbar é client (estado de scroll); o footer é puro mas mora aqui junto.
 */

/** Logo "M" da Meada — SVG com gradiente azul→roxo (idêntico ao meada-page). */
function MeadaLogo({ size = 38 }: { size?: number }) {
  const uid = `mlogo-${size}`
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 38 38"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden
    >
      <defs>
        <linearGradient
          id={`${uid}-bg`}
          x1="0"
          y1="0"
          x2="38"
          y2="38"
          gradientUnits="userSpaceOnUse"
        >
          <stop stopColor="#1d4ed8" />
          <stop offset="0.55" stopColor="#6d28d9" />
          <stop offset="1" stopColor="#4c1d95" />
        </linearGradient>
        <radialGradient id={`${uid}-glow`} cx="25%" cy="20%" r="65%">
          <stop offset="0%" stopColor="white" stopOpacity="0.18" />
          <stop offset="100%" stopColor="white" stopOpacity="0" />
        </radialGradient>
      </defs>
      <rect width="38" height="38" rx="11" fill={`url(#${uid}-bg)`} />
      <rect width="38" height="38" rx="11" fill={`url(#${uid}-glow)`} />
      <path
        d="M8.5 27.5 L9.5 12 L19 21.5 L28.5 12 L29.5 27.5"
        stroke="white"
        strokeWidth="2.75"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill="none"
      />
      <circle cx="19" cy="21.5" r="2" fill="white" fillOpacity="0.55" />
    </svg>
  )
}

function Brand({
  name,
  suffix,
  size = 38,
  nameSize = 20,
}: {
  name: string
  suffix: string
  size?: number
  nameSize?: number
}) {
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: '11px' }}>
      <MeadaLogo size={size} />
      <span style={{ display: 'flex', flexDirection: 'column', gap: 0 }}>
        <span
          style={{
            fontSize: `${nameSize}px`,
            fontWeight: 800,
            color: '#fff',
            letterSpacing: '-0.5px',
            lineHeight: 1,
          }}
        >
          {name}
        </span>
        <span
          style={{
            fontSize: '9.5px',
            fontWeight: 600,
            color: 'rgba(148,163,184,0.7)',
            letterSpacing: '0.12em',
            textTransform: 'uppercase',
            lineHeight: 1.4,
          }}
        >
          {suffix}
        </span>
      </span>
    </span>
  )
}

// ---- NAVBAR ----
export function MeadaNavbar({
  props,
  activeSlot,
}: {
  props: MeadaNavbarProps
  activeSlot?: string
}) {
  const [scrolled, setScrolled] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 20)
    window.addEventListener('scroll', onScroll)
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  const links = props.links ?? []
  return (
    <>
      <nav
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 100,
          height: '72px',
          background: scrolled ? 'rgba(0,8,18,0.85)' : 'rgba(0,8,18,0.5)',
          backdropFilter: 'blur(20px)',
          WebkitBackdropFilter: 'blur(20px)',
          borderBottom: scrolled
            ? '1px solid rgba(59,130,246,0.15)'
            : '1px solid rgba(59,130,246,0.05)',
          transition: 'background 0.3s ease, border-color 0.3s ease',
        }}
      >
        <div
          style={{
            maxWidth: '1360px',
            margin: '0 auto',
            padding: '0 2rem',
            height: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Link
            href="/"
            data-slot="brand"
            style={{ textDecoration: 'none', ...slotOutlineStyle(activeSlot, 'brand') }}
          >
            <Brand name={props.brandName} suffix={props.brandSuffix} />
          </Link>

          {/* links desktop */}
          <div className="hidden items-center md:flex" style={{ gap: '2rem' }}>
            {links.map((l) => (
              <a
                key={l.href + l.label}
                href={safeUrl(l.href) || '#'}
                style={{
                  textDecoration: 'none',
                  color: 'rgba(255,255,255,0.65)',
                  fontSize: '15px',
                  fontWeight: 400,
                  paddingBottom: '4px',
                  borderBottom: '2px solid transparent',
                }}
              >
                {l.label}
              </a>
            ))}
            {props.ctaLabel && (
              <a
                href={safeUrl(props.ctaHref) || '#'}
                data-slot="cta"
                style={{
                  padding: '9px 20px',
                  background: 'linear-gradient(135deg, #3b82f6, #6366f1)',
                  borderRadius: '8px',
                  color: '#fff',
                  fontSize: '14px',
                  fontWeight: 600,
                  textDecoration: 'none',
                  ...slotOutlineStyle(activeSlot, 'cta'),
                }}
              >
                {props.ctaLabel}
              </a>
            )}
          </div>

          {/* hambúrguer mobile */}
          <button
            className="flex md:hidden"
            onClick={() => setMobileOpen((v) => !v)}
            aria-label="Menu"
            style={{
              background: 'none',
              border: 'none',
              cursor: 'pointer',
              padding: '8px',
              flexDirection: 'column',
              gap: '5px',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <span
              style={{
                display: 'block',
                width: '22px',
                height: '2px',
                background: '#fff',
                borderRadius: '2px',
                transition: 'all 0.25s',
                transform: mobileOpen ? 'rotate(45deg) translate(5px, 5px)' : 'none',
              }}
            />
            <span
              style={{
                display: 'block',
                width: '22px',
                height: '2px',
                background: '#fff',
                borderRadius: '2px',
                transition: 'all 0.25s',
                opacity: mobileOpen ? 0 : 1,
              }}
            />
            <span
              style={{
                display: 'block',
                width: '22px',
                height: '2px',
                background: '#fff',
                borderRadius: '2px',
                transition: 'all 0.25s',
                transform: mobileOpen ? 'rotate(-45deg) translate(5px, -5px)' : 'none',
              }}
            />
          </button>
        </div>
      </nav>

      {/* menu mobile */}
      {mobileOpen && (
        <div
          className="md:hidden"
          style={{
            position: 'fixed',
            top: '72px',
            left: 0,
            right: 0,
            zIndex: 99,
            display: 'flex',
            flexDirection: 'column',
            background: 'rgba(0,8,18,0.97)',
            backdropFilter: 'blur(20px)',
            WebkitBackdropFilter: 'blur(20px)',
            padding: '1.25rem 1.5rem 2rem',
            borderBottom: '1px solid rgba(59,130,246,0.15)',
          }}
        >
          {links.map((l) => (
            <a
              key={l.href + l.label}
              href={safeUrl(l.href) || '#'}
              onClick={() => setMobileOpen(false)}
              style={{
                padding: '1rem 0',
                color: 'rgba(255,255,255,0.7)',
                textDecoration: 'none',
                fontSize: '17px',
                borderBottom: '1px solid rgba(255,255,255,0.06)',
              }}
            >
              {l.label}
            </a>
          ))}
          {props.ctaLabel && (
            <a
              href={safeUrl(props.ctaHref) || '#'}
              onClick={() => setMobileOpen(false)}
              style={{
                marginTop: '1rem',
                padding: '13px',
                textAlign: 'center',
                background: 'linear-gradient(135deg, #3b82f6, #6366f1)',
                borderRadius: '10px',
                color: '#fff',
                fontWeight: 600,
                fontSize: '15px',
                textDecoration: 'none',
              }}
            >
              {props.ctaLabel}
            </a>
          )}
        </div>
      )}
    </>
  )
}

// ---- FOOTER ----
const IG_PATH =
  'M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265.069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zm0-2.163c-3.259 0-3.667.014-4.947.072-4.358.2-6.78 2.618-6.98 6.98-.059 1.281-.073 1.689-.073 4.948 0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98 1.281.058 1.689.072 4.948.072 3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28.073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-6.98-1.281-.059-1.69-.073-4.949-.073zm0 5.838c-3.403 0-6.162 2.759-6.162 6.162s2.759 6.163 6.162 6.163 6.162-2.759 6.162-6.163c0-3.403-2.759-6.162-6.162-6.162zm0 10.162c-2.209 0-4-1.79-4-4 0-2.209 1.791-4 4-4s4 1.791 4 4c0 2.21-1.791 4-4 4zm6.406-11.845c-.796 0-1.441.645-1.441 1.44s.645 1.44 1.441 1.44c.795 0 1.439-.645 1.439-1.44s-.644-1.44-1.439-1.44z'
const WA_PATH =
  'M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z'

export function MeadaFooter({
  props,
  activeSlot,
}: {
  props: MeadaFooterProps
  activeSlot?: string
}) {
  const year = new Date().getFullYear()
  const social = [
    props.instagramUrl ? { href: props.instagramUrl, label: 'Instagram', path: IG_PATH } : null,
    props.whatsappUrl ? { href: props.whatsappUrl, label: 'WhatsApp', path: WA_PATH } : null,
  ].filter(Boolean) as { href: string; label: string; path: string }[]

  return (
    <footer
      style={{
        background: '#000812',
        borderTop: '1px solid rgba(255,255,255,0.05)',
        padding: '5rem 2rem 2.5rem',
      }}
    >
      <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
        <div
          className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4"
          style={{ gap: '3rem', marginBottom: '4rem' }}
        >
          {/* marca */}
          <div data-slot="brand" style={slotOutlineStyle(activeSlot, 'brand')}>
            <div style={{ marginBottom: '1.25rem' }}>
              <Brand name={props.brandName} suffix={props.brandSuffix} size={36} nameSize={18} />
            </div>
            <p
              style={{
                color: 'rgba(255,255,255,0.38)',
                fontSize: '14px',
                lineHeight: 1.75,
                maxWidth: '260px',
                marginBottom: '1.5rem',
              }}
            >
              {props.tagline}
            </p>
            <div
              data-slot="social"
              style={{ display: 'flex', gap: '10px', ...slotOutlineStyle(activeSlot, 'social') }}
            >
              {social.map((s) => (
                <a
                  key={s.label}
                  href={safeUrl(s.href) || '#'}
                  target="_blank"
                  rel="noopener noreferrer"
                  aria-label={s.label}
                  style={{
                    width: '34px',
                    height: '34px',
                    borderRadius: '8px',
                    background: 'rgba(255,255,255,0.04)',
                    border: '1px solid rgba(255,255,255,0.08)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'rgba(255,255,255,0.4)',
                  }}
                >
                  <svg width="15" height="15" fill="currentColor" viewBox="0 0 24 24">
                    <path d={s.path} />
                  </svg>
                </a>
              ))}
            </div>
          </div>

          {/* colunas de links */}
          {(props.columns ?? []).map((col, i) => (
            <div key={i}>
              <h4
                style={{
                  color: 'rgba(255,255,255,0.5)',
                  fontSize: '11px',
                  fontWeight: 600,
                  letterSpacing: '0.12em',
                  textTransform: 'uppercase',
                  marginBottom: '1.25rem',
                }}
              >
                {col.heading}
              </h4>
              <ul
                style={{
                  listStyle: 'none',
                  padding: 0,
                  margin: 0,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '0.7rem',
                }}
              >
                {(col.links ?? []).map((l) => (
                  <li key={l.href + l.label}>
                    <a
                      href={safeUrl(l.href) || '#'}
                      style={{
                        color: 'rgba(255,255,255,0.4)',
                        textDecoration: 'none',
                        fontSize: '14px',
                      }}
                    >
                      {l.label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div
          style={{
            borderTop: '1px solid rgba(255,255,255,0.05)',
            paddingTop: '1.75rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: '1rem',
          }}
        >
          <p style={{ color: 'rgba(255,255,255,0.25)', fontSize: '13px' }}>
            © {year} {props.copyright?.replace(/^©\s*/, '')}
          </p>
          <div style={{ display: 'flex', gap: '1.5rem' }}>
            {['Privacidade', 'Termos de Uso'].map((item) => (
              <span key={item} style={{ color: 'rgba(255,255,255,0.25)', fontSize: '13px' }}>
                {item}
              </span>
            ))}
          </div>
        </div>
      </div>
    </footer>
  )
}
