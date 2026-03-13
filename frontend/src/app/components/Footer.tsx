import Link from 'next/link';

const year = new Date().getFullYear();

export default function Footer() {
  return (
    <footer
      style={{
        background: '#000812',
        borderTop: '1px solid rgba(255,255,255,0.05)',
        padding: '5rem 2rem 2.5rem',
      }}
    >
      <div style={{ maxWidth: '1200px', margin: '0 auto' }}>
        {/* Top Row */}
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: '2fr 1fr 1fr 1fr',
            gap: '3rem',
            marginBottom: '4rem',
          }}
        >
          {/* Brand */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '11px', marginBottom: '1.25rem' }}>
              <svg width="36" height="36" viewBox="0 0 38 38" fill="none" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <linearGradient id="footerBg" x1="0" y1="0" x2="38" y2="38" gradientUnits="userSpaceOnUse">
                    <stop stopColor="#1d4ed8"/>
                    <stop offset="0.55" stopColor="#6d28d9"/>
                    <stop offset="1" stopColor="#4c1d95"/>
                  </linearGradient>
                  <radialGradient id="footerGlow" cx="25%" cy="20%" r="65%">
                    <stop offset="0%" stopColor="white" stopOpacity="0.18"/>
                    <stop offset="100%" stopColor="white" stopOpacity="0"/>
                  </radialGradient>
                </defs>
                <rect width="38" height="38" rx="11" fill="url(#footerBg)"/>
                <rect width="38" height="38" rx="11" fill="url(#footerGlow)"/>
                <path d="M8.5 27.5 L9.5 12 L19 21.5 L28.5 12 L29.5 27.5"
                  stroke="white" strokeWidth="2.75" strokeLinecap="round" strokeLinejoin="round" fill="none"/>
                <circle cx="19" cy="21.5" r="2" fill="white" fillOpacity="0.55"/>
              </svg>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0px' }}>
                <span style={{ fontSize: '18px', fontWeight: 800, color: '#fff', letterSpacing: '-0.5px', lineHeight: 1 }}>Meada</span>
                <span style={{ fontSize: '9px', fontWeight: 600, color: 'rgba(148,163,184,0.5)', letterSpacing: '0.14em', textTransform: 'uppercase', lineHeight: 1.4 }}>Digital</span>
              </div>
            </div>
            <p style={{ color: 'rgba(255,255,255,0.38)', fontSize: '14px', lineHeight: 1.75, maxWidth: '260px', marginBottom: '1.5rem' }}>
              Agência digital especializada em sites, sistemas e IA para pequenos e médios negócios.
            </p>
            {/* Social icons */}
            <div style={{ display: 'flex', gap: '10px' }}>
              {[
                {
                  href: 'https://instagram.com/meadadigital',
                  label: 'Instagram',
                  path: 'M12 2.163c3.204 0 3.584.012 4.85.07 3.252.148 4.771 1.691 4.919 4.919.058 1.265.069 1.645.069 4.849 0 3.205-.012 3.584-.069 4.849-.149 3.225-1.664 4.771-4.919 4.919-1.266.058-1.644.07-4.85.07-3.204 0-3.584-.012-4.849-.07-3.26-.149-4.771-1.699-4.919-4.92-.058-1.265-.07-1.644-.07-4.849 0-3.204.013-3.583.07-4.849.149-3.227 1.664-4.771 4.919-4.919 1.266-.057 1.645-.069 4.849-.069zm0-2.163c-3.259 0-3.667.014-4.947.072-4.358.2-6.78 2.618-6.98 6.98-.059 1.281-.073 1.689-.073 4.948 0 3.259.014 3.668.072 4.948.2 4.358 2.618 6.78 6.98 6.98 1.281.058 1.689.072 4.948.072 3.259 0 3.668-.014 4.948-.072 4.354-.2 6.782-2.618 6.979-6.98.059-1.28.073-1.689.073-4.948 0-3.259-.014-3.667-.072-4.947-.196-4.354-2.617-6.78-6.979-6.98-1.281-.059-1.69-.073-4.949-.073zm0 5.838c-3.403 0-6.162 2.759-6.162 6.162s2.759 6.163 6.162 6.163 6.162-2.759 6.162-6.163c0-3.403-2.759-6.162-6.162-6.162zm0 10.162c-2.209 0-4-1.79-4-4 0-2.209 1.791-4 4-4s4 1.791 4 4c0 2.21-1.791 4-4 4zm6.406-11.845c-.796 0-1.441.645-1.441 1.44s.645 1.44 1.441 1.44c.795 0 1.439-.645 1.439-1.44s-.644-1.44-1.439-1.44z',
                },
                {
                  href: 'https://wa.me/5581992612292',
                  label: 'WhatsApp',
                  path: 'M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.940 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347m-5.421 7.403h-.004a9.87 9.87 0 01-5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374a9.86 9.86 0 01-1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898a9.825 9.825 0 012.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884m8.413-18.297A11.815 11.815 0 0012.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654a11.882 11.882 0 005.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893a11.821 11.821 0 00-3.48-8.413Z',
                },
              ].map((s) => (
                <a key={s.label} href={s.href} target="_blank" rel="noopener noreferrer" aria-label={s.label}
                  style={{ width: '34px', height: '34px', borderRadius: '8px', background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'rgba(255,255,255,0.4)', transition: 'all 0.2s' }}
                  onMouseEnter={e => { e.currentTarget.style.borderColor = 'rgba(99,102,241,0.4)'; e.currentTarget.style.color = '#a5b4fc'; }}
                  onMouseLeave={e => { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.08)'; e.currentTarget.style.color = 'rgba(255,255,255,0.4)'; }}>
                  <svg width="15" height="15" fill="currentColor" viewBox="0 0 24 24">
                    <path d={s.path} />
                  </svg>
                </a>
              ))}
            </div>
          </div>

          {/* Serviços */}
          <div>
            <h4 style={{ color: 'rgba(255,255,255,0.5)', fontSize: '11px', fontWeight: 600, letterSpacing: '0.12em', textTransform: 'uppercase', marginBottom: '1.25rem' }}>
              Serviços
            </h4>
            <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '0.7rem' }}>
              {[
                { label: 'Sites Profissionais', href: '/servicos' },
                { label: 'IA para Negócios', href: '/servicos' },
                { label: 'Sistemas sob Medida', href: '/servicos' },
              ].map((item) => (
                <li key={item.label}>
                  <Link href={item.href}
                    style={{ color: 'rgba(255,255,255,0.4)', textDecoration: 'none', fontSize: '14px', transition: 'color 0.2s ease' }}
                    onMouseEnter={(e) => ((e.target as HTMLElement).style.color = 'rgba(255,255,255,0.85)')}
                    onMouseLeave={(e) => ((e.target as HTMLElement).style.color = 'rgba(255,255,255,0.4)')}>
                    {item.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          {/* Empresa */}
          <div>
            <h4 style={{ color: 'rgba(255,255,255,0.5)', fontSize: '11px', fontWeight: 600, letterSpacing: '0.12em', textTransform: 'uppercase', marginBottom: '1.25rem' }}>
              Empresa
            </h4>
            <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '0.7rem' }}>
              {[
                { label: 'Sobre Nós', href: '/sobre' },
                { label: 'Produtos', href: '/produtos' },
                { label: 'Serviços', href: '/servicos' },
              ].map((item) => (
                <li key={item.label}>
                  <Link href={item.href}
                    style={{ color: 'rgba(255,255,255,0.4)', textDecoration: 'none', fontSize: '14px', transition: 'color 0.2s ease' }}
                    onMouseEnter={(e) => ((e.target as HTMLElement).style.color = 'rgba(255,255,255,0.85)')}
                    onMouseLeave={(e) => ((e.target as HTMLElement).style.color = 'rgba(255,255,255,0.4)')}>
                    {item.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          {/* Contato */}
          <div>
            <h4 style={{ color: 'rgba(255,255,255,0.5)', fontSize: '11px', fontWeight: 600, letterSpacing: '0.12em', textTransform: 'uppercase', marginBottom: '1.25rem' }}>
              Contato
            </h4>
            <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: '0.7rem' }}>
              <li>
                <a href="mailto:oi@meadadigital.com"
                  style={{ color: 'rgba(255,255,255,0.4)', textDecoration: 'none', fontSize: '14px', transition: 'color 0.2s ease' }}
                  onMouseEnter={(e) => ((e.target as HTMLElement).style.color = 'rgba(255,255,255,0.85)')}
                  onMouseLeave={(e) => ((e.target as HTMLElement).style.color = 'rgba(255,255,255,0.4)')}>
                  oi@meadadigital.com
                </a>
              </li>
              <li>
                <a href="https://wa.me/5581992612292" target="_blank" rel="noopener noreferrer"
                  style={{ color: 'rgba(255,255,255,0.4)', textDecoration: 'none', fontSize: '14px', transition: 'color 0.2s ease' }}
                  onMouseEnter={(e) => ((e.target as HTMLElement).style.color = '#4ade80')}
                  onMouseLeave={(e) => ((e.target as HTMLElement).style.color = 'rgba(255,255,255,0.4)')}>
                  (81) 99261-2292
                </a>
              </li>
              <li>
                <a href="https://instagram.com/meadadigital" target="_blank" rel="noopener noreferrer"
                  style={{ color: 'rgba(255,255,255,0.4)', textDecoration: 'none', fontSize: '14px', transition: 'color 0.2s ease' }}
                  onMouseEnter={(e) => ((e.target as HTMLElement).style.color = '#f472b6')}
                  onMouseLeave={(e) => ((e.target as HTMLElement).style.color = 'rgba(255,255,255,0.4)')}>
                  @meadadigital
                </a>
              </li>
              <li style={{ paddingTop: '0.25rem' }}>
                <Link href="/contato"
                  style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', padding: '8px 18px', background: 'linear-gradient(135deg,#3b82f6,#6366f1)', borderRadius: '8px', color: '#fff', fontSize: '13px', fontWeight: 600, textDecoration: 'none', transition: 'opacity 0.2s' }}
                  onMouseEnter={(e) => ((e.currentTarget as HTMLElement).style.opacity = '0.88')}
                  onMouseLeave={(e) => ((e.currentTarget as HTMLElement).style.opacity = '1')}>
                  Pedir orçamento
                  <svg width="12" height="12" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" d="M17 8l4 4m0 0l-4 4m4-4H3"/></svg>
                </Link>
              </li>
            </ul>
          </div>
        </div>

        {/* Bottom Row */}
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
            © {year} Meada Agência Digital. Todos os direitos reservados.
          </p>
          <div style={{ display: 'flex', gap: '1.5rem' }}>
            {['Privacidade', 'Termos de Uso'].map((item) => (
              <span key={item} style={{ color: 'rgba(255,255,255,0.25)', fontSize: '13px' }}>{item}</span>
            ))}
          </div>
        </div>
      </div>
    </footer>
  );
}
