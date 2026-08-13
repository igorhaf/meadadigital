import type { Metadata } from 'next'
import { Geist_Mono, Inter, Plus_Jakarta_Sans } from 'next/font/google'

import './globals.css'

import { Providers } from './providers'

const inter = Inter({
  variable: '--font-sans',
  subsets: ['latin'],
  display: 'swap',
})
const jakarta = Plus_Jakarta_Sans({
  variable: '--font-heading',
  subsets: ['latin'],
  weight: ['500', '600', '700', '800'],
  display: 'swap',
})
const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
  display: 'swap',
})

export const metadata: Metadata = {
  title: 'Meada — Painel',
  description: 'Painel administrativo do Meada',
}

// Anti-flash (camada 5.9): aplica a classe `dark` no <html> ANTES do React hidratar,
// lendo localStorage 'meada-theme'. Default é CLARO — sem nada salvo (!m), NÃO segue o
// SO; o app abre claro. Só segue prefers-color-scheme quando o usuário escolheu
// EXPLICITAMENTE 'system' no toggle. Roda síncrono no <head> → sem flash no boot.
const themeInitScript = `
(function () {
  try {
    var m = localStorage.getItem('meada-theme');
    var dark = m === 'dark' ||
      (m === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);
    if (dark) document.documentElement.classList.add('dark');
  } catch (e) {}
})();
`

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="pt-BR"
      className={`${inter.variable} ${jakarta.variable} ${geistMono.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
      </head>
      <body className="min-h-full bg-muted/40">
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
