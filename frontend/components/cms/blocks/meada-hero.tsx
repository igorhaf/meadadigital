'use client'

import { useEffect, useState } from 'react'

import type { MeadaHeroProps } from '@/lib/cms/cms-block-type'

/**
 * Bloco HERO da marca Meada (preset meada-dark) — réplica FIEL do hero do meada-page
 * (src/app/_no-ai.tsx + _ai.tsx). Identidade própria (cores/gradiente cravados, não tematizável).
 * Estilo inline px-a-px como no original. `showcase`: 'terminal' (animação projeto.sh, do _no-ai) ou
 * 'chat' (widget assistente, do _ai). Client component (a animação do terminal usa estado).
 */
export function MeadaHero({ props }: { props: MeadaHeroProps }) {
  return (
    <section
      style={{
        position: 'relative',
        minHeight: '100vh',
        paddingTop: '148px',
        paddingBottom: '100px',
        overflow: 'hidden',
      }}
    >
      {/* keyframes embutidos: imunes ao tree-shaking do Tailwind 4 (não dependem do globals.css) */}
      <style>{`
        @keyframes meada-float { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-20px); } }
        @keyframes meada-lineIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
        @keyframes meada-caretBlink { 0%,50% { opacity: 1; } 51%,100% { opacity: 0; } }
        .meada-term-scroll::-webkit-scrollbar { display: none; }
        .meada-term-scroll { scrollbar-width: none; }
      `}</style>
      {/* blobs radiais animados */}
      <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
        <div
          style={{
            position: 'absolute',
            top: '-15%',
            left: '-8%',
            width: '700px',
            height: '700px',
            borderRadius: '50%',
            background: 'radial-gradient(circle, rgba(59,130,246,0.22) 0%, transparent 68%)',
            filter: 'blur(60px)',
            animation: 'meada-float 8s ease-in-out infinite',
          }}
        />
        <div
          style={{
            position: 'absolute',
            top: '5%',
            right: '-12%',
            width: '800px',
            height: '800px',
            borderRadius: '50%',
            background: 'radial-gradient(circle, rgba(139,92,246,0.18) 0%, transparent 68%)',
            filter: 'blur(60px)',
            animation: 'meada-float 11s ease-in-out infinite',
            animationDelay: '2s',
          }}
        />
        <div
          style={{
            position: 'absolute',
            bottom: '-20%',
            left: '25%',
            width: '600px',
            height: '600px',
            borderRadius: '50%',
            background: 'radial-gradient(circle, rgba(236,72,153,0.12) 0%, transparent 68%)',
            filter: 'blur(60px)',
            animation: 'meada-float 13s ease-in-out infinite',
            animationDelay: '4s',
          }}
        />
      </div>

      <div
        style={{
          position: 'relative',
          zIndex: 1,
          maxWidth: '1360px',
          margin: '0 auto',
          paddingLeft: '4rem',
          paddingRight: '4rem',
        }}
      >
        <div className="grid grid-cols-1 items-center md:grid-cols-2" style={{ gap: '7rem' }}>
          {/* esquerda */}
          <div>
            <h1
              style={{
                fontSize: 'clamp(2.8rem, 5vw, 4.4rem)',
                fontWeight: 900,
                lineHeight: 1.12,
                letterSpacing: '-0.03em',
                marginBottom: '2rem',
              }}
            >
              {props.titlePrefix}{' '}
              <span
                style={{
                  backgroundImage: 'linear-gradient(125deg, #60a5fa 0%, #a855f7 50%, #ec4899 100%)',
                  backgroundClip: 'text',
                  WebkitBackgroundClip: 'text',
                  color: 'transparent',
                }}
              >
                {props.gradientText}
              </span>{' '}
              {props.titleSuffix}
            </h1>

            <p
              style={{
                fontSize: '18px',
                color: 'rgb(203,213,225)',
                lineHeight: 1.85,
                marginBottom: '2.5rem',
                maxWidth: '460px',
              }}
            >
              {props.subtitle}
            </p>

            <div style={{ display: 'flex', gap: '1rem', marginBottom: '4.5rem', flexWrap: 'wrap' }}>
              {props.primaryLabel && (
                <a
                  href={props.primaryHref || '#'}
                  style={{
                    padding: '15px 34px',
                    background: 'linear-gradient(135deg, #3b82f6, #6366f1)',
                    borderRadius: '10px',
                    color: 'white',
                    fontWeight: 600,
                    fontSize: '15px',
                    textDecoration: 'none',
                    display: 'inline-block',
                    boxShadow: '0 14px 32px rgba(59,130,246,0.38)',
                  }}
                >
                  {props.primaryLabel}
                </a>
              )}
              {props.secondaryLabel && (
                <a
                  href={props.secondaryHref || '#'}
                  style={{
                    padding: '15px 34px',
                    background: 'transparent',
                    border: '1px solid rgba(59,130,246,0.28)',
                    borderRadius: '10px',
                    color: 'rgb(96,165,250)',
                    fontWeight: 600,
                    fontSize: '15px',
                    textDecoration: 'none',
                    display: 'inline-block',
                  }}
                >
                  {props.secondaryLabel}
                </a>
              )}
            </div>

            {(props.stats ?? []).length > 0 && (
              <div
                className="grid grid-cols-3"
                style={{
                  gap: '2rem',
                  paddingTop: '3rem',
                  borderTop: '1px solid rgba(71,85,105,0.25)',
                }}
              >
                {(props.stats ?? []).map((s, i) => {
                  const grads = [
                    'linear-gradient(90deg, #60a5fa, #a855f7)',
                    'linear-gradient(90deg, #a855f7, #ec4899)',
                    'linear-gradient(90deg, #ec4899, #60a5fa)',
                  ]
                  return (
                    <div key={i}>
                      <p
                        style={{
                          fontSize: '2.1rem',
                          fontWeight: 900,
                          marginBottom: '0.35rem',
                          backgroundImage: grads[i % 3],
                          backgroundClip: 'text',
                          WebkitBackgroundClip: 'text',
                          color: 'transparent',
                        }}
                      >
                        {s.value}
                      </p>
                      <p
                        style={{
                          fontSize: '11px',
                          color: 'rgb(148,163,184)',
                          textTransform: 'uppercase',
                          letterSpacing: '0.07em',
                          fontWeight: 600,
                        }}
                      >
                        {s.label}
                      </p>
                    </div>
                  )
                })}
              </div>
            )}
          </div>

          {/* direita — showcase */}
          <div style={{ position: 'relative', height: '520px' }}>
            {props.showcase === 'chat' ? (
              <ChatShowcase props={props} />
            ) : props.showcase === 'plan' ? (
              <PlanShowcase props={props} />
            ) : (
              <TerminalShowcase props={props} />
            )}
          </div>
        </div>
      </div>
    </section>
  )
}

/** Card de plano/preço no lado direito do hero (páginas de nicho) — substitui o console. */
function PlanShowcase({ props }: { props: MeadaHeroProps }) {
  return (
    <div
      style={{
        height: '520px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <div
        style={{
          width: '100%',
          maxWidth: '420px',
          background: '#ffffff',
          color: '#0b1220',
          borderRadius: '20px',
          padding: '2.25rem',
          border: '1px solid rgba(59,130,246,0.55)',
          boxShadow: '0 0 0 4px rgba(59,130,246,0.12), 0 30px 60px -20px rgba(0,0,0,0.65)',
        }}
      >
        {props.planBadge && (
          <div
            style={{
              fontSize: '12px',
              fontWeight: 700,
              letterSpacing: '0.08em',
              textTransform: 'uppercase',
              color: '#3b82f6',
              marginBottom: '0.9rem',
            }}
          >
            {props.planBadge}
          </div>
        )}
        <div style={{ fontSize: '1.4rem', fontWeight: 800, marginBottom: '0.75rem' }}>
          {props.planName}
        </div>
        {props.planDescription && (
          <p
            style={{ fontSize: '15px', lineHeight: 1.55, color: '#475569', marginBottom: '1.5rem' }}
          >
            {props.planDescription}
          </p>
        )}
        <div style={{ fontSize: '2.4rem', fontWeight: 800, marginBottom: '1.5rem' }}>
          {props.planPrice}
        </div>
        {props.planButtonLabel && (
          <a
            href={props.planButtonHref || '#'}
            style={{
              display: 'block',
              textAlign: 'center',
              background: '#3b82f6',
              color: '#fff',
              fontWeight: 700,
              padding: '0.9rem 1rem',
              borderRadius: '12px',
              textDecoration: 'none',
            }}
          >
            {props.planButtonLabel}
          </a>
        )}
      </div>
    </div>
  )
}

/** Terminal animado (do _no-ai.tsx): as linhas aparecem uma a uma, reinicia ao fim. */
function TerminalShowcase({ props }: { props: MeadaHeroProps }) {
  const lines = props.terminalLines ?? []
  const [visible, setVisible] = useState(0)

  useEffect(() => {
    if (visible >= lines.length) {
      const reset = setTimeout(() => setVisible(0), 4000)
      return () => clearTimeout(reset)
    }
    const next = setTimeout(() => setVisible((v) => v + 1), visible === 0 ? 700 : 520)
    return () => clearTimeout(next)
  }, [visible, lines.length])

  const mono = 'ui-monospace, SFMono-Regular, Menlo, monospace'
  return (
    <>
      <div
        style={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%,-50%)',
          width: '420px',
          height: '420px',
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(99,102,241,0.18) 0%, transparent 70%)',
          filter: 'blur(52px)',
          pointerEvents: 'none',
        }}
      />
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: '24px',
          overflow: 'hidden',
          border: '1px solid rgba(99,102,241,0.3)',
          background: 'rgba(10,14,30,0.92)',
          backdropFilter: 'blur(24px)',
          boxShadow: '0 32px 80px rgba(99,102,241,0.2)',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        {/* header */}
        <div
          style={{
            padding: '0.9rem 1.4rem',
            borderBottom: '1px solid rgba(255,255,255,0.06)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
            background: 'rgba(255,255,255,0.03)',
            flexShrink: 0,
          }}
        >
          <div style={{ display: 'flex', gap: '6px' }}>
            <span
              style={{ width: '11px', height: '11px', borderRadius: '50%', background: '#ef4444' }}
            />
            <span
              style={{ width: '11px', height: '11px', borderRadius: '50%', background: '#eab308' }}
            />
            <span
              style={{ width: '11px', height: '11px', borderRadius: '50%', background: '#22c55e' }}
            />
          </div>
          <div
            style={{
              flex: 1,
              textAlign: 'center',
              fontFamily: mono,
              fontSize: '12px',
              color: 'rgba(148,163,184,0.7)',
              letterSpacing: '0.05em',
            }}
          >
            {props.terminalTitle}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '5px' }}>
            <span
              style={{
                width: '7px',
                height: '7px',
                borderRadius: '50%',
                background: '#4ade80',
                boxShadow: '0 0 6px rgba(74,222,128,0.8)',
              }}
            />
            <span style={{ fontSize: '11px', color: '#4ade80', fontWeight: 600 }}>online</span>
          </div>
        </div>
        {/* corpo */}
        <div style={{ position: 'relative', flex: 1, overflow: 'hidden' }}>
          <div
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              height: '40px',
              background: 'linear-gradient(to bottom, rgba(10,14,30,0.98) 0%, transparent 100%)',
              zIndex: 2,
              pointerEvents: 'none',
            }}
          />
          <div
            style={{
              position: 'absolute',
              bottom: 0,
              left: 0,
              right: 0,
              height: '24px',
              background: 'linear-gradient(to top, rgba(10,14,30,0.8) 0%, transparent 100%)',
              zIndex: 2,
              pointerEvents: 'none',
            }}
          />
          <div
            className="meada-term-scroll"
            style={{
              height: '100%',
              overflowY: 'auto',
              padding: '1.25rem 1.4rem',
              display: 'flex',
              flexDirection: 'column',
              gap: '0.55rem',
              background: 'rgba(0,8,18,0.35)',
              fontFamily: mono,
              fontSize: '13px',
              lineHeight: 1.6,
            }}
          >
            {lines.slice(0, visible).map((line, i) => {
              if (line.kind === 'cmd')
                return (
                  <div
                    key={i}
                    style={{
                      display: 'flex',
                      gap: '0.5rem',
                      alignItems: 'baseline',
                      animation: 'meada-lineIn 0.25s ease forwards',
                    }}
                  >
                    <span style={{ color: '#a855f7', fontWeight: 700 }}>$</span>
                    <span style={{ color: '#e2e8f0' }}>{line.text}</span>
                  </div>
                )
              if (line.kind === 'check')
                return (
                  <div
                    key={i}
                    style={{
                      display: 'flex',
                      gap: '0.55rem',
                      alignItems: 'baseline',
                      animation: 'meada-lineIn 0.25s ease forwards',
                    }}
                  >
                    <span style={{ color: '#4ade80', fontWeight: 700 }}>✓</span>
                    <span style={{ color: '#cbd5e1' }}>{line.text}</span>
                  </div>
                )
              if (line.kind === 'info')
                return (
                  <div
                    key={i}
                    style={{
                      display: 'flex',
                      gap: '0.55rem',
                      alignItems: 'baseline',
                      animation: 'meada-lineIn 0.25s ease forwards',
                    }}
                  >
                    <span style={{ color: '#60a5fa', fontWeight: 700 }}>›</span>
                    <span style={{ color: 'rgba(203,213,225,0.7)' }}>{line.text}</span>
                  </div>
                )
              return (
                <div
                  key={i}
                  style={{
                    marginTop: '0.4rem',
                    padding: '0.55rem 0.75rem',
                    borderRadius: '8px',
                    background:
                      'linear-gradient(135deg, rgba(59,130,246,0.18), rgba(99,102,241,0.12))',
                    border: '1px solid rgba(59,130,246,0.25)',
                    animation: 'meada-lineIn 0.25s ease forwards',
                  }}
                >
                  <span style={{ color: '#93c5fd', fontWeight: 600 }}>{line.text}</span>
                </div>
              )
            })}
            {visible < lines.length && (
              <span
                style={{
                  display: 'inline-block',
                  width: '8px',
                  height: '14px',
                  background: '#a855f7',
                  animation: 'meada-caretBlink 1s steps(1) infinite',
                  verticalAlign: 'middle',
                }}
              />
            )}
          </div>
        </div>
        {/* caption */}
        <div
          style={{
            padding: '0.7rem 1rem',
            borderTop: '1px solid rgba(99,102,241,0.15)',
            background: 'rgba(99,102,241,0.04)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexShrink: 0,
            fontFamily: mono,
            fontSize: '11px',
          }}
        >
          <span style={{ color: 'rgba(148,163,184,0.6)' }}>{props.terminalCaptionLeft}</span>
          <span style={{ color: 'rgba(168,85,247,0.7)', fontWeight: 600 }}>
            {props.terminalCaptionRight}
          </span>
        </div>
      </div>
    </>
  )
}

/** Widget de assistente (do _ai.tsx): card dark-glass com a 1ª mensagem do assistente. */
function ChatShowcase({ props }: { props: MeadaHeroProps }) {
  return (
    <>
      <div
        style={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%,-50%)',
          width: '420px',
          height: '420px',
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(99,102,241,0.18) 0%, transparent 70%)',
          filter: 'blur(52px)',
          pointerEvents: 'none',
        }}
      />
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: '24px',
          overflow: 'hidden',
          border: '1px solid rgba(99,102,241,0.3)',
          background: 'rgba(10,14,30,0.92)',
          backdropFilter: 'blur(24px)',
          boxShadow: '0 32px 80px rgba(99,102,241,0.2)',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <div
          style={{
            padding: '1rem 1.4rem',
            borderBottom: '1px solid rgba(255,255,255,0.06)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
            background: 'rgba(255,255,255,0.03)',
          }}
        >
          <div
            style={{
              width: '32px',
              height: '32px',
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #6366f1, #a855f7)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontWeight: 700,
              fontSize: '13px',
            }}
          >
            M
          </div>
          <div>
            <div style={{ fontWeight: 700, fontSize: '14px' }}>{props.chatTitle}</div>
            <div
              style={{
                fontSize: '11px',
                color: '#4ade80',
                display: 'flex',
                alignItems: 'center',
                gap: '5px',
              }}
            >
              <span
                style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#4ade80' }}
              />{' '}
              IA ativa · agora
            </div>
          </div>
        </div>
        <div
          style={{
            flex: 1,
            padding: '1.4rem',
            display: 'flex',
            flexDirection: 'column',
            gap: '0.75rem',
            background: 'rgba(0,8,18,0.35)',
          }}
        >
          <div
            style={{
              alignSelf: 'flex-start',
              maxWidth: '85%',
              padding: '0.7rem 0.95rem',
              borderRadius: '14px 14px 14px 4px',
              background: 'linear-gradient(135deg, rgba(59,130,246,0.2), rgba(99,102,241,0.12))',
              border: '1px solid rgba(59,130,246,0.2)',
              fontSize: '13px',
              lineHeight: 1.55,
              color: '#e2e8f0',
            }}
          >
            <div
              style={{
                fontSize: '10px',
                textTransform: 'uppercase',
                letterSpacing: '0.08em',
                color: 'rgba(148,163,184,0.7)',
                marginBottom: '0.35rem',
              }}
            >
              IA Meada
            </div>
            {props.chatMessage}
          </div>
        </div>
        <div
          style={{
            padding: '0.9rem 1rem',
            borderTop: '1px solid rgba(99,102,241,0.15)',
            background: 'rgba(99,102,241,0.04)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.6rem',
          }}
        >
          <div
            style={{
              flex: 1,
              padding: '0.6rem 0.9rem',
              borderRadius: '10px',
              background: 'rgba(255,255,255,0.04)',
              border: '1px solid rgba(255,255,255,0.08)',
              fontSize: '13px',
              color: 'rgba(148,163,184,0.6)',
            }}
          >
            Pergunte sobre nossos serviços…
          </div>
          <div
            style={{
              width: '36px',
              height: '36px',
              borderRadius: '10px',
              background: 'linear-gradient(135deg, #3b82f6, #6366f1)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'white',
            }}
          >
            →
          </div>
        </div>
      </div>
    </>
  )
}
