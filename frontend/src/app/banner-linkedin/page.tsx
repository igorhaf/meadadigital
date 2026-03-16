export default function BannerLinkedInPage() {
  const tags = [
    { label: 'Desenvolvimento Web', color: '#60a5fa' },
    { label: 'IA & Automação',      color: '#a855f7' },
    { label: 'Cloud & DevOps',      color: '#ec4899' },
    { label: 'Design & UX',         color: '#34d399' },
  ];

  return (
    <div style={{
      width: '1584px',
      height: '396px',
      background: '#000812',
      display: 'flex',
      alignItems: 'flex-start',
      justifyContent: 'space-between',
      padding: '44px 80px 0 10px',
      position: 'relative',
      overflow: 'hidden',
      margin: '0 auto',
    }}>

      {/* Background glows */}
      <div style={{
        position: 'absolute', top: '-50%', left: '-8%',
        width: '900px', height: '900px', borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(59,130,246,0.22) 0%, transparent 60%)',
        filter: 'blur(90px)', pointerEvents: 'none',
      }} />
      <div style={{
        position: 'absolute', top: '-35%', right: '20%',
        width: '700px', height: '700px', borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(139,92,246,0.18) 0%, transparent 60%)',
        filter: 'blur(90px)', pointerEvents: 'none',
      }} />
      <div style={{
        position: 'absolute', bottom: '-30%', right: '0%',
        width: '600px', height: '600px', borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(236,72,153,0.15) 0%, transparent 60%)',
        filter: 'blur(80px)', pointerEvents: 'none',
      }} />

      {/* Grid lines */}
      <div style={{
        position: 'absolute', inset: 0, pointerEvents: 'none',
        backgroundImage: `
          linear-gradient(rgba(59,130,246,0.05) 1px, transparent 1px),
          linear-gradient(90deg, rgba(59,130,246,0.05) 1px, transparent 1px)
        `,
        backgroundSize: '60px 60px',
      }} />

      {/* Decorative M watermark */}
      <div style={{
        position: 'absolute', left: 'calc(44% - 70px)', top: '50%',
        transform: 'translate(-50%, -52%)',
        fontSize: '560px', fontWeight: 900,
        color: 'rgba(255,255,255,0.018)',
        fontFamily: 'system-ui, -apple-system, sans-serif',
        lineHeight: 1, pointerEvents: 'none', userSelect: 'none',
      }}>M</div>

      {/* LEFT: Tagline only (top) */}
      <div style={{
        display: 'flex', flexDirection: 'column',
        position: 'relative', zIndex: 1,
        marginLeft: '40px',
      }}>
        <p style={{
          fontSize: '42px', fontWeight: 800, color: '#fff',
          lineHeight: 1.2, letterSpacing: '-0.7px', margin: 0,
          whiteSpace: 'nowrap',
          fontFamily: 'system-ui, -apple-system, sans-serif',
        }}>
          Sites e Sistemas com o{' '}
          <span style={{
            backgroundImage: 'linear-gradient(125deg, #60a5fa 0%, #a855f7 50%, #ec4899 100%)',
            backgroundClip: 'text',
            WebkitBackgroundClip: 'text',
            color: 'transparent',
          }}>
            Diferencial da IA
          </span>
        </p>
      </div>

      {/* BADGES — ordered longest→shortest, curving around profile photo right edge */}
      {[
        { label: 'Desenvolvimento Web', color: '#60a5fa', left: 240, top: 130 },
        { label: 'Cloud & DevOps',      color: '#ec4899', left: 590, top: 195 },
        { label: 'IA & Automação',      color: '#a855f7', left: 290, top: 280 },
        { label: 'Design & UX',         color: '#34d399', left: 610, top: 300 },
      ].map(({ label, color, left, top }) => (
        <span key={label} style={{
          position: 'absolute',
          left: `${left}px`,
          top: `${top}px`,
          padding: '14px 32px',
          borderRadius: '100px',
          border: `1px solid ${color}55`,
          background: `${color}0d`,
          backdropFilter: 'blur(4px)',
          color: color,
          fontSize: '24px',
          fontWeight: 600,
          letterSpacing: '0.02em',
          opacity: 0.3,
          whiteSpace: 'nowrap',
          fontFamily: 'system-ui, -apple-system, sans-serif',
          zIndex: 2,
        }}>
          {label}
        </span>
      ))}


      {/* RIGHT: Logo + Name */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: '28px',
        position: 'relative', zIndex: 1,
        flexShrink: 0, alignSelf: 'center', marginRight: '40px',
      }}>
        <svg width="160" height="160" viewBox="0 0 260 260" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="bannerBg" x1="0" y1="0" x2="260" y2="260" gradientUnits="userSpaceOnUse">
              <stop stopColor="#1d4ed8"/>
              <stop offset="0.45" stopColor="#6d28d9"/>
              <stop offset="1" stopColor="#4c1d95"/>
            </linearGradient>
            <radialGradient id="bannerGlow" cx="30%" cy="22%" r="65%">
              <stop offset="0%" stopColor="white" stopOpacity="0.25"/>
              <stop offset="100%" stopColor="white" stopOpacity="0"/>
            </radialGradient>
            <linearGradient id="bannerShine" x1="0" y1="0" x2="0" y2="260" gradientUnits="userSpaceOnUse">
              <stop offset="0%" stopColor="white" stopOpacity="0.08"/>
              <stop offset="100%" stopColor="white" stopOpacity="0"/>
            </linearGradient>
          </defs>
          <rect width="260" height="260" rx="68" fill="url(#bannerBg)"/>
          <rect width="260" height="260" rx="68" fill="url(#bannerGlow)"/>
          <rect width="260" height="260" rx="68" fill="url(#bannerShine)"/>
          <rect x="1" y="1" width="258" height="258" rx="67" stroke="white" strokeOpacity="0.12" strokeWidth="2" fill="none"/>
          <path
            d="M58 192 L65 82 L130 148 L195 82 L202 192"
            stroke="white" strokeWidth="19"
            strokeLinecap="round" strokeLinejoin="round" fill="none"
          />
          <circle cx="130" cy="148" r="13" fill="white" fillOpacity="0.5"/>
        </svg>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <span style={{
            fontSize: '92px', fontWeight: 900, color: '#fff',
            letterSpacing: '-3px', lineHeight: 1,
            fontFamily: 'system-ui, -apple-system, sans-serif',
          }}>
            Meada
          </span>
          <span style={{
            fontSize: '26px', fontWeight: 700,
            color: 'rgba(148,163,184,0.95)',
            letterSpacing: '0.28em', textTransform: 'uppercase',
            fontFamily: 'system-ui, -apple-system, sans-serif',
          }}>
            Digital
          </span>
          <span style={{
            fontSize: '22px', fontWeight: 500,
            color: 'rgba(96,165,250,0.95)',
            letterSpacing: '0.02em', marginTop: '8px',
            fontFamily: 'system-ui, -apple-system, sans-serif',
          }}>
            meadadigital.com
          </span>
          <span style={{
            fontSize: '20px', fontWeight: 500,
            color: 'rgba(168,139,250,0.9)',
            letterSpacing: '0.02em',
            fontFamily: 'system-ui, -apple-system, sans-serif',
          }}>
            @meadadigital
          </span>
        </div>
      </div>

    </div>
  );
}
