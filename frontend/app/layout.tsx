import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import './globals.css';
import { Providers } from './providers';

const geistSans = Geist({ variable: '--font-geist-sans', subsets: ['latin'] });
const geistMono = Geist_Mono({ variable: '--font-geist-mono', subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'Meada WhatsApp — Painel',
  description: 'Painel administrativo do Meada WhatsApp',
};

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
`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="pt-BR"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
      suppressHydrationWarning
    >
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
      </head>
      <body className="min-h-full bg-muted/40">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
