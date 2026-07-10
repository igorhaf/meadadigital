'use client'

import { useEffect, useRef, useState } from 'react'

import type { ReviewsCarouselProps } from '@/lib/cms/cms-block-type'
import { safeUrl } from '@/lib/cms/safe-url'

/**
 * Carrossel de avaliações estilo Google (bloco reviews_carousel — onda 1 de blocos genéricos,
 * docs/FEATURES_SUGERIDAS_CMS.md). Cards com estrelas, avatar (foto por URL ou inicial colorida)
 * e selo "via Google" quando source='google'. Carrossel por CSS scroll-snap; setas e autoplay
 * client-side (autoplay pausa no hover/toque e respeita prefers-reduced-motion).
 *
 * Fase estática: o tenant cola as avaliações no editor. A fase viva (Places API) é aditiva —
 * o mesmo bloco ganha um modo hidratado sem quebrar sites publicados (backlog #17 do catálogo).
 */

const AVATAR_COLORS = ['#2563eb', '#7c3aed', '#db2777', '#ea580c', '#059669', '#0891b2']
const CARD_GAP_PX = 24 // = gap-6 do track (usado no passo das setas/autoplay)

function Stars({ rating }: { rating: string }) {
  const n = Math.max(1, Math.min(5, parseInt(rating, 10) || 5))
  return (
    <span className="text-sm text-amber-400" aria-label={`${n} de 5 estrelas`}>
      {'★'.repeat(n)}
      {n < 5 && <span className="opacity-30">{'★'.repeat(5 - n)}</span>}
    </span>
  )
}

export function ReviewsCarousel({ props }: { props: ReviewsCarouselProps }) {
  const items = props.items ?? []
  const trackRef = useRef<HTMLDivElement>(null)
  const [paused, setPaused] = useState(false)

  function scrollByCard(dir: 1 | -1, loop = false) {
    const el = trackRef.current
    if (!el) return
    const card = el.firstElementChild as HTMLElement | null
    const step = card ? card.offsetWidth + CARD_GAP_PX : el.clientWidth
    const atEnd = el.scrollLeft + el.clientWidth >= el.scrollWidth - step / 2
    if (loop && dir === 1 && atEnd) {
      el.scrollTo({ left: 0, behavior: 'smooth' })
    } else {
      el.scrollBy({ left: dir * step, behavior: 'smooth' })
    }
  }

  // autoplay: um card por vez, voltando ao início no fim. Não roda com <2 itens, pausado
  // (hover/toque) ou com prefers-reduced-motion.
  useEffect(() => {
    if (props.autoplay !== true || items.length < 2 || paused) return
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
    const t = setInterval(() => scrollByCard(1, true), 4000)
    return () => clearInterval(t)
  }, [props.autoplay, items.length, paused])

  return (
    <section className="px-6 py-12" style={{ background: 'rgba(0,0,0,0.04)' }}>
      <div className="mx-auto max-w-6xl">
        {props.title && <h2 className="mb-8 text-center text-2xl font-bold">{props.title}</h2>}
        <div
          ref={trackRef}
          className="flex snap-x snap-mandatory [scrollbar-width:none] gap-6 overflow-x-auto pb-2 [&::-webkit-scrollbar]:hidden"
          onMouseEnter={() => setPaused(true)}
          onMouseLeave={() => setPaused(false)}
          onTouchStart={() => setPaused(true)}
        >
          {items.map((r, i) => {
            const avatarSrc = safeUrl(r.avatarUrl)
            return (
              <blockquote
                key={i}
                className="w-[85%] shrink-0 snap-start rounded-2xl border border-black/10 bg-white/60 p-6 text-slate-900 sm:w-[46%] lg:w-[31%]"
              >
                <div className="flex items-center gap-3">
                  {avatarSrc ? (
                    /* eslint-disable-next-line @next/next/no-img-element -- URL externa colada pelo tenant */
                    <img
                      src={avatarSrc}
                      alt={r.name || ''}
                      className="h-10 w-10 shrink-0 rounded-full object-cover"
                    />
                  ) : (
                    <span
                      className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-semibold text-white"
                      style={{ background: AVATAR_COLORS[i % AVATAR_COLORS.length] }}
                    >
                      {(r.name || '?').trim().charAt(0).toUpperCase()}
                    </span>
                  )}
                  <div className="min-w-0">
                    <div className="truncate text-sm font-semibold">{r.name}</div>
                    {r.date && <div className="text-xs opacity-60">{r.date}</div>}
                  </div>
                </div>
                <div className="mt-2 flex items-center gap-2">
                  <Stars rating={r.rating} />
                  {props.source === 'google' && (
                    <span className="text-xs opacity-60">via Google</span>
                  )}
                </div>
                {r.text && <p className="mt-2 text-sm leading-relaxed opacity-80">“{r.text}”</p>}
              </blockquote>
            )
          })}
        </div>
        {items.length > 1 && (
          <div className="mt-4 flex justify-center gap-2">
            <button
              type="button"
              aria-label="Avaliação anterior"
              onClick={() => scrollByCard(-1)}
              className="flex h-9 w-9 items-center justify-center rounded-full border border-black/15 bg-white/60 text-slate-900 hover:bg-white"
            >
              ←
            </button>
            <button
              type="button"
              aria-label="Próxima avaliação"
              onClick={() => scrollByCard(1)}
              className="flex h-9 w-9 items-center justify-center rounded-full border border-black/15 bg-white/60 text-slate-900 hover:bg-white"
            >
              →
            </button>
          </div>
        )}
      </div>
    </section>
  )
}
