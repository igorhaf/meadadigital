export default function BannerLinkedInPage() {
  return (
    <div style={{
      width: '1584px',
      height: '396px',
      background: '#000812',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '0 80px',
      position: 'relative',
      overflow: 'hidden',
      margin: '0 auto',
    }}>

      {/* Background glows */}
      <div style={{
        position: 'absolute', top: '-60%', left: '-5%',
        width: '700px', height: '700px', borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(59,130,246,0.18) 0%, transparent 65%)',
        filter: 'blur(60px)', pointerEvents: 'none',
      }} />
      <div style={{
        position: 'absolute', top: '-40%', right: '15%',
        width: '500px', height: '500px', borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(139,92,246,0.14) 0%, transparent 65%)',
        filter: 'blur(60px)', pointerEvents: 'none',
      }} />
      <div style={{
        position: 'absolute', bottom: '-40%', right: '-5%',
        width: '400px', height: '400px', borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(236,72,153,0.1) 0%, transparent 65%)',
        filter: 'blur(60px)', pointerEvents: 'none',
      }} />

      {/* Subtle grid lines */}
      <div style={{
        position: 'absolute', inset: 0, pointerEvents: 'none',
        backgroundImage: `
          linear-gradient(rgba(59,130,246,0.04) 1px, transparent 1px),
          linear-gradient(90deg, rgba(59,130,246,0.04) 1px, transparent 1px)
        `,
        backgroundSize: '60px 60px',
      }} />

      {/* LEFT: Logo + Name */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: '32px',
        position: 'relative', zIndex: 1,
        flexShrink: 0,
      }}>
        {/* Logo icon */}
        <svg width="120" height="120" viewBox="0 0 260 260" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="bannerBg" x1="0" y1="0" x2="260" y2="260" gradientUnits="userSpaceOnUse">
              <stop stopColor="#1d4ed8"/>
              <stop offset="0.45" stopColor="#6d28d9"/>
              <stop offset="1" stopColor="#4c1d95"/>
            </linearGradient>
            <radialGradient id="bannerGlow" cx="30%" cy="22%" r="65%">
              <stop offset="0%" stopColor="white" stopOpacity="0.22"/>
              <stop offset="100%" stopColor="white" stopOpacity="0"/>
            </radialGradient>
            <linearGradient id="bannerShine" x1="0" y1="0" x2="0" y2="260" gradientUnits="userSpaceOnUse">
              <stop offset="0%" stopColor="white" stopOpacity="0.07"/>
              <stop offset="100%" stopColor="white" stopOpacity="0"/>
            </linearGradient>
          </defs>
          <rect width="260" height="260" rx="68" fill="url(#bannerBg)"/>
          <rect width="260" height="260" rx="68" fill="url(#bannerGlow)"/>
          <rect width="260" height="260" rx="68" fill="url(#bannerShine)"/>
          <rect x="1" y="1" width="258" height="258" rx="67" stroke="white" strokeOpacity="0.1" strokeWidth="2" fill="none"/>
          <path
            d="M58 192 L65 82 L130 148 L195 82 L202 192"
            stroke="white" strokeWidth="19"
            strokeLinecap="round" strokeLinejoin="round" fill="none"
          />
          <circle cx="130" cy="148" r="13" fill="white" fillOpacity="0.5"/>
        </svg>

        {/* Brand name */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <span style={{
            fontSize: '72px', fontWeight: 900, color: '#fff',
            letterSpacing: '-2px', lineHeight: 1,
            fontFamily: 'system-ui, -apple-system, sans-serif',
          }}>
            Meada
          </span>
          <span style={{
            fontSize: '18px', fontWeight: 600,
            color: 'rgba(148,163,184,0.6)',
            letterSpacing: '0.22em', textTransform: 'uppercase',
            fontFamily: 'system-ui, -apple-system, sans-serif',
          }}>
            Digital
          </span>
        </div>
      </div>

      {/* CENTER divider */}
      <div style={{
        width: '1px', height: '120px',
        background: 'linear-gradient(to bottom, transparent, rgba(59,130,246,0.3), transparent)',
        flexShrink: 0, position: 'relative', zIndex: 1,
      }} />

      {/* RIGHT: Tagline + pills */}
      <div style={{
        display: 'flex', flexDirection: 'column', gap: '20px',
        position: 'relative', zIndex: 1,
        maxWidth: '700px',
      }}>
        <p style={{
          fontSize: '30px', fontWeight: 700, color: '#fff',
          lineHeight: 1.3, letterSpacing: '-0.5px', margin: 0,
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

        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
          {['Desenvolvimento Web', 'IA & Automação', 'Cloud & DevOps', 'Design & UX'].map((tag, i) => {
            const colors = ['#60a5fa', '#a855f7', '#ec4899', '#34d399'];
            return (
              <span key={tag} style={{
                padding: '6px 16px',
                borderRadius: '100px',
                border: `1px solid ${colors[i]}40`,
                background: `${colors[i]}0f`,
                color: colors[i],
                fontSize: '13px',
                fontWeight: 600,
                letterSpacing: '0.02em',
                fontFamily: 'system-ui, -apple-system, sans-serif',
              }}>
                {tag}
              </span>
            );
          })}
        </div>

        <p style={{
          fontSize: '16px', color: 'rgba(148,163,184,0.55)',
          margin: 0, letterSpacing: '0.01em',
          fontFamily: 'system-ui, -apple-system, sans-serif',
        }}>
          meadadigital.com
        </p>
      </div>

    </div>
  );
}
