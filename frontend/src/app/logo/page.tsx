export default function LogoPage() {
  return (
    <div style={{
      width: '400px',
      height: '400px',
      background: '#0a0a1a',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      margin: '0 auto',
    }}>
      <svg width="260" height="260" viewBox="0 0 260 260" fill="none" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id="logoBg" x1="0" y1="0" x2="260" y2="260" gradientUnits="userSpaceOnUse">
            <stop stopColor="#1d4ed8"/>
            <stop offset="0.45" stopColor="#6d28d9"/>
            <stop offset="1" stopColor="#4c1d95"/>
          </linearGradient>
          <radialGradient id="logoGlow" cx="30%" cy="22%" r="65%">
            <stop offset="0%" stopColor="white" stopOpacity="0.22"/>
            <stop offset="100%" stopColor="white" stopOpacity="0"/>
          </radialGradient>
          <linearGradient id="logoShine" x1="0" y1="0" x2="0" y2="260" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="white" stopOpacity="0.06"/>
            <stop offset="100%" stopColor="white" stopOpacity="0"/>
          </linearGradient>
        </defs>

        {/* Background rounded square */}
        <rect width="260" height="260" rx="68" fill="url(#logoBg)"/>
        <rect width="260" height="260" rx="68" fill="url(#logoGlow)"/>
        <rect width="260" height="260" rx="68" fill="url(#logoShine)"/>

        {/* Subtle inner border */}
        <rect x="1" y="1" width="258" height="258" rx="67" stroke="white" strokeOpacity="0.1" strokeWidth="2" fill="none"/>

        {/* M shape */}
        <path
          d="M58 192 L65 82 L130 148 L195 82 L202 192"
          stroke="white"
          strokeWidth="19"
          strokeLinecap="round"
          strokeLinejoin="round"
          fill="none"
        />

        {/* Center dot */}
        <circle cx="130" cy="148" r="13" fill="white" fillOpacity="0.5"/>
      </svg>
    </div>
  );
}
