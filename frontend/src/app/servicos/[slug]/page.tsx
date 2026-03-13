'use client';

import { use } from 'react';
import Link from 'next/link';
import { notFound } from 'next/navigation';
import Navbar from '../../components/Navbar';
import Footer from '../../components/Footer';
import {
  CodeIcon, CloudIcon, CpuIcon, SmartphoneIcon, LayersIcon, BarChartIcon,
  CheckCircleIcon,
} from '../../components/icons';

type ServiceData = {
  Icon: React.ComponentType<{ size?: number; color?: string }>;
  color: string;
  gradient: string;
  title: string;
  subtitle: string;
  description: string;
  features: { title: string; desc: string }[];
  useCases: { title: string; desc: string }[];
  tech: string[];
};

const SERVICES: Record<string, ServiceData> = {
  desenvolvimento: {
    Icon: CodeIcon,
    color: '#60a5fa',
    gradient: 'linear-gradient(135deg, #1d4ed8, #4f46e5)',
    title: 'Desenvolvimento Personalizado',
    subtitle: 'Sites e sistemas feitos sob medida para o seu negócio',
    description: 'Do site institucional ao sistema mais complexo, entregamos soluções digitais que funcionam de verdade. Código limpo, performance otimizada e foco total no que importa para o seu negócio.',
    features: [
      { title: 'Design Responsivo', desc: 'Layout perfeito em desktop, tablet e mobile sem comprometer a experiência' },
      { title: 'Performance Otimizada', desc: 'Carregamento rápido, Core Web Vitals no verde e SEO técnico from scratch' },
      { title: 'Painel Administrativo', desc: 'Gerencie seu conteúdo, cadastros e configurações sem precisar de dev' },
      { title: 'Código Limpo', desc: 'Manutenível, documentado e fácil de evoluir conforme o negócio cresce' },
      { title: 'Integrações', desc: 'Pagamentos, e-mail marketing, WhatsApp, CRMs e qualquer API necessária' },
      { title: 'Deploy Incluso', desc: 'Do desenvolvimento ao ar em produção, configurado e estável' },
    ],
    useCases: [
      { title: 'Site Institucional', desc: 'Presença digital profissional para sua empresa, com blog, portfólio, página de serviços e formulário de contato.' },
      { title: 'Landing Page', desc: 'Página de alta conversão para captar leads ou vender um produto ou serviço específico com foco total na ação do usuário.' },
      { title: 'Sistema Web', desc: 'Plataforma completa com autenticação, banco de dados, relatórios e toda lógica de negócio personalizada para o seu processo.' },
    ],
    tech: ['Next.js', 'React', 'TypeScript', 'Node.js', 'Laravel', 'PHP', 'PostgreSQL', 'MySQL', 'Tailwind CSS'],
  },
  nuvem: {
    Icon: CloudIcon,
    color: '#a855f7',
    gradient: 'linear-gradient(135deg, #7c3aed, #4f46e5)',
    title: 'Infraestrutura em Nuvem',
    subtitle: 'Seu projeto no ar com segurança, velocidade e escalabilidade',
    description: 'Configuramos, migramos e mantemos servidores e ambientes em nuvem para que sua aplicação rode com estabilidade e performance. Sem dores de cabeça técnicas, sem surpresas.',
    features: [
      { title: 'Deploy Automatizado', desc: 'CI/CD configurado do zero: menos erros humanos, mais agilidade nas entregas' },
      { title: 'SSL & Segurança', desc: 'HTTPS obrigatório, firewalls configurados e boas práticas de segurança aplicadas' },
      { title: 'Monitoramento', desc: 'Alertas automáticos para qualquer instabilidade antes que o usuário perceba' },
      { title: 'Backups Automáticos', desc: 'Seus dados protegidos com rotina de backup diária e retenção configurável' },
      { title: 'Escalabilidade', desc: 'Infra que cresce junto com o seu negócio sem precisar reescrever tudo' },
      { title: 'Domínio e DNS', desc: 'Configuração completa de domínio, subdomínios, redirecionamentos e e-mails' },
    ],
    useCases: [
      { title: 'Deploy de Aplicação', desc: 'Leve seu projeto do localhost para produção com ambiente corretamente configurado, seguro e monitorado.' },
      { title: 'Migração de Servidor', desc: 'Migre de hospedagem compartilhada para VPS ou cloud sem downtime e sem perder dados.' },
      { title: 'Setup de CI/CD', desc: 'Pipeline de deploy automático com build, testes e publicação disparados a cada push para a branch principal.' },
    ],
    tech: ['AWS', 'DigitalOcean', 'Vercel', 'Netlify', 'Hetzner', 'Docker', 'Nginx', 'GitHub Actions', 'Let\'s Encrypt'],
  },
  'ia-automacao': {
    Icon: CpuIcon,
    color: '#ec4899',
    gradient: 'linear-gradient(135deg, #db2777, #7c3aed)',
    title: 'IA & Automação',
    subtitle: 'Inteligência artificial aplicada ao seu negócio, de forma prática',
    description: 'Integramos IA nos seus sistemas ou processos onde faz sentido real: chatbots, automações, análise de documentos e muito mais. Sem hype, com resultado mensurável.',
    features: [
      { title: 'Chatbots Inteligentes', desc: 'Atendimento automático treinado com as informações do seu negócio' },
      { title: 'Automação de Processos', desc: 'Tarefas repetitivas eliminadas com fluxos automáticos e gatilhos inteligentes' },
      { title: 'Análise de Dados', desc: 'Relatórios e insights gerados por IA a partir dos dados que você já tem' },
      { title: 'Geração de Conteúdo', desc: 'Textos, descrições e e-mails gerados em escala com padrão da sua marca' },
      { title: 'Leitura de Documentos', desc: 'Extração e organização automática de dados de PDFs, formulários e contratos' },
      { title: 'Integração com LLMs', desc: 'GPT, Claude e outros modelos conectados diretamente ao seu sistema' },
    ],
    useCases: [
      { title: 'Chatbot de Atendimento', desc: 'Assistente virtual no seu site ou WhatsApp, treinado com FAQ e dados do negócio para responder dúvidas 24h.' },
      { title: 'Análise de Documentos', desc: 'Leitura automática de notas fiscais, contratos ou formulários para extrair e organizar informações sem trabalho manual.' },
      { title: 'Automação de E-mails', desc: 'Respostas automáticas inteligentes, classificação de mensagens e geração de rascunhos baseados no histórico.' },
    ],
    tech: ['OpenAI API', 'Claude API', 'Python', 'LangChain', 'n8n', 'Make', 'Flowise', 'Pinecone', 'FastAPI'],
  },
  mobile: {
    Icon: SmartphoneIcon,
    color: '#22d3ee',
    gradient: 'linear-gradient(135deg, #0891b2, #1d4ed8)',
    title: 'Design Mobile First',
    subtitle: 'Experiências digitais que funcionam perfeitamente em qualquer tela',
    description: 'Desenvolvemos interfaces e aplicações com foco total no mobile: layouts responsivos, apps nativos e PWAs que oferecem uma experiência fluida e rápida em qualquer dispositivo.',
    features: [
      { title: 'Totalmente Responsivo', desc: 'Layout perfeito em qualquer resolução, do celular ao ultrawide' },
      { title: 'Performance Mobile', desc: 'Carregamento rápido mesmo em conexões 3G/4G e dispositivos mais simples' },
      { title: 'Touch-Friendly', desc: 'Gestos, swipes e interações pensadas para toque, não para mouse' },
      { title: 'PWA', desc: 'Funciona offline e pode ser instalado na tela inicial do celular sem app store' },
      { title: 'App Nativo', desc: 'React Native para iOS e Android a partir do mesmo código-base' },
      { title: 'Publicação nas Lojas', desc: 'Submissão e aprovação na Google Play Store e Apple App Store incluídas' },
    ],
    useCases: [
      { title: 'App Empresarial', desc: 'Aplicativo para equipe interna, clientes ou parceiros disponível no Android e iOS com experiência nativa.' },
      { title: 'PWA', desc: 'Progressive Web App que funciona offline e pode ser instalado direto do navegador, sem precisar de loja.' },
      { title: 'Site Mobile First', desc: 'Site ou sistema web com experiência perfeita no celular, sem precisar manter um app separado.' },
    ],
    tech: ['React Native', 'Expo', 'Flutter', 'PWA', 'Next.js', 'TypeScript', 'App Store Connect', 'Google Play'],
  },
  'design-ux': {
    Icon: LayersIcon,
    color: '#34d399',
    gradient: 'linear-gradient(135deg, #059669, #0891b2)',
    title: 'Design & UX',
    subtitle: 'Interfaces bonitas que as pessoas sabem e gostam de usar',
    description: 'Criamos interfaces com foco em usabilidade e estética. Do wireframe ao protótipo navegável, passando por design system e handoff completo para desenvolvimento.',
    features: [
      { title: 'Wireframes', desc: 'Estrutura e hierarquia da interface antes de partir para o visual final' },
      { title: 'Prototipação', desc: 'Protótipos interativos para validar o fluxo com usuários antes de codar' },
      { title: 'Design System', desc: 'Componentes reutilizáveis que garantem consistência visual em todo o produto' },
      { title: 'Pesquisa UX', desc: 'Entender o usuário real antes de tomar decisões de design' },
      { title: 'Testes de Usabilidade', desc: 'Validação com usuários reais para identificar fricções antes do lançamento' },
      { title: 'Microinterações', desc: 'Animações e transições que elevam a percepção de qualidade do produto' },
    ],
    useCases: [
      { title: 'Redesign de Produto', desc: 'Revisão completa da interface de um produto existente com foco em usabilidade, estética e conversão.' },
      { title: 'Novo Produto do Zero', desc: 'Design completo de uma nova aplicação, do mapeamento de usuário ao protótipo aprovado para desenvolvimento.' },
      { title: 'Design System', desc: 'Biblioteca de componentes e guia de estilo para manter consistência em produtos com múltiplas telas e times.' },
    ],
    tech: ['Figma', 'Framer', 'Adobe XD', 'Illustrator', 'Lottie'],
  },
  'apis-integracoes': {
    Icon: BarChartIcon,
    color: '#f97316',
    gradient: 'linear-gradient(135deg, #d97706, #dc2626)',
    title: 'APIs & Integrações',
    subtitle: 'Conecte seus sistemas e ferramentas em uma arquitetura coesa',
    description: 'Criamos APIs robustas e integramos plataformas externas para que seus sistemas conversem entre si sem atrito. Pagamentos, CRM, ERP, marketplaces — tudo conectado e funcionando.',
    features: [
      { title: 'APIs REST & GraphQL', desc: 'Endpoints claros, bem documentados e protegidos por autenticação adequada' },
      { title: 'Webhooks', desc: 'Comunicação em tempo real entre sistemas diferentes por eventos' },
      { title: 'Autenticação', desc: 'OAuth2, JWT e controle de acesso por perfil e permissões' },
      { title: 'Documentação', desc: 'Swagger/OpenAPI gerado automaticamente para facilitar o uso por outros devs' },
      { title: 'Rate Limiting', desc: 'Proteção contra abusos, throttling e uso indevido da API' },
      { title: 'Logs & Monitoramento', desc: 'Rastreamento de erros, tempo de resposta e alertas em tempo real' },
    ],
    useCases: [
      { title: 'Gateway de Pagamento', desc: 'Integração com Stripe, Mercado Pago, PagSeguro ou qualquer outro meio de pagamento disponível.' },
      { title: 'ERP & CRM', desc: 'Sincronização de dados entre seu sistema e ferramentas como Salesforce, HubSpot, TOTVS e similares.' },
      { title: 'Marketplaces', desc: 'Integração de catálogo, pedidos e estoque com Shopify, WooCommerce, Mercado Livre ou VTEX.' },
    ],
    tech: ['Node.js', 'PHP', 'REST', 'GraphQL', 'Stripe', 'Swagger', 'Redis', 'RabbitMQ', 'Zapier'],
  },
};

export default function ServicePage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = use(params);
  const service = SERVICES[slug];
  if (!service) notFound();

  const { Icon, color, gradient, title, subtitle, description, features, useCases, tech } = service!;

  return (
    <div style={{ background: '#000812', minHeight: '100vh', color: '#fff', fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      <Navbar />

      {/* Hero */}
      <section style={{ paddingTop: '140px', paddingBottom: '80px', position: 'relative', overflow: 'hidden' }}>
        <div style={{ position: 'absolute', top: '-20%', left: '-10%', width: '700px', height: '700px', borderRadius: '50%', background: `radial-gradient(circle, ${color}22 0%, transparent 68%)`, filter: 'blur(80px)', pointerEvents: 'none' }} />
        <div style={{ position: 'absolute', top: '10%', right: '-10%', width: '500px', height: '500px', borderRadius: '50%', background: `radial-gradient(circle, ${color}12 0%, transparent 68%)`, filter: 'blur(60px)', pointerEvents: 'none' }} />

        <div style={{ maxWidth: '1360px', margin: '0 auto', padding: '0 4rem', position: 'relative', zIndex: 1 }}>
          <Link href="/servicos" style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', fontSize: '13px', color: 'rgba(255,255,255,0.45)', textDecoration: 'none', marginBottom: '2.5rem', transition: 'color 0.2s' }}
            onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = 'rgba(255,255,255,0.8)'; }}
            onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'rgba(255,255,255,0.45)'; }}>
            ← Todos os serviços
          </Link>

          <div style={{ display: 'flex', alignItems: 'flex-start', gap: '1.75rem', marginBottom: '2rem' }}>
            <div style={{ width: '72px', height: '72px', borderRadius: '20px', background: gradient, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, boxShadow: `0 16px 40px ${color}30` }}>
              <Icon size={30} color="#fff" />
            </div>
            <div>
              <h1 style={{ fontSize: 'clamp(2.2rem, 4.5vw, 3.6rem)', fontWeight: '900', lineHeight: '1.1', letterSpacing: '-0.03em', marginBottom: '0.75rem' }}>{title}</h1>
              <p style={{ fontSize: '18px', color: color, fontWeight: '600' }}>{subtitle}</p>
            </div>
          </div>

          <p style={{ fontSize: '17px', color: 'rgba(203,213,225,0.8)', lineHeight: '1.85', maxWidth: '660px' }}>{description}</p>
        </div>
      </section>

      {/* Features */}
      <section style={{ padding: '5rem 4rem', borderTop: '1px solid rgba(59,130,246,0.1)', background: 'linear-gradient(180deg, transparent, rgba(59,130,246,0.02), transparent)' }}>
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
          <h2 style={{ fontSize: 'clamp(1.8rem, 3vw, 2.4rem)', fontWeight: '900', marginBottom: '0.75rem' }}>O que está incluso</h2>
          <p style={{ fontSize: '15px', color: 'rgba(148,163,184,0.8)', marginBottom: '3rem' }}>Tudo o que você recebe ao contratar este serviço.</p>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '1.25rem' }}>
            {features.map((f, i) => (
              <div key={i} style={{ display: 'flex', gap: '1rem', padding: '1.5rem', borderRadius: '14px', border: '1px solid rgba(255,255,255,0.06)', background: 'rgba(15,23,42,0.45)', backdropFilter: 'blur(12px)' }}>
                <div style={{ flexShrink: 0, marginTop: '2px' }}><CheckCircleIcon size={17} color={color} /></div>
                <div>
                  <p style={{ fontSize: '14px', fontWeight: '700', marginBottom: '0.3rem', color: '#e2e8f0' }}>{f.title}</p>
                  <p style={{ fontSize: '12px', color: 'rgb(148,163,184)', lineHeight: '1.6' }}>{f.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Use cases */}
      <section style={{ padding: '5rem 4rem' }}>
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
          <h2 style={{ fontSize: 'clamp(1.8rem, 3vw, 2.4rem)', fontWeight: '900', marginBottom: '0.75rem' }}>Quando faz sentido</h2>
          <p style={{ fontSize: '15px', color: 'rgba(148,163,184,0.8)', marginBottom: '3rem' }}>Exemplos reais de quando este serviço se aplica ao seu negócio.</p>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '2rem' }}>
            {useCases.map((u, i) => (
              <div key={i} style={{ padding: '2.25rem', borderRadius: '18px', border: `1px solid ${color}22`, background: 'rgba(15,23,42,0.5)', backdropFilter: 'blur(12px)', transition: 'border-color 0.3s, transform 0.3s' }}
                onMouseEnter={e => { (e.currentTarget as HTMLElement).style.borderColor = `${color}50`; (e.currentTarget as HTMLElement).style.transform = 'translateY(-4px)'; }}
                onMouseLeave={e => { (e.currentTarget as HTMLElement).style.borderColor = `${color}22`; (e.currentTarget as HTMLElement).style.transform = 'translateY(0)'; }}>
                <div style={{ width: '32px', height: '3px', borderRadius: '3px', background: color, marginBottom: '1.25rem' }} />
                <h3 style={{ fontSize: '17px', fontWeight: '800', marginBottom: '0.75rem', color: '#f1f5f9' }}>{u.title}</h3>
                <p style={{ fontSize: '14px', color: 'rgb(148,163,184)', lineHeight: '1.7' }}>{u.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section style={{ padding: '6rem 4rem 8rem' }}>
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
          <div style={{ borderRadius: '24px', padding: '4rem 5rem', background: `linear-gradient(135deg, ${color}10, rgba(15,23,42,0.7))`, border: `1px solid ${color}20`, position: 'relative', overflow: 'hidden' }}>
            <div style={{ position: 'absolute', top: '-20%', right: '-5%', width: '400px', height: '400px', borderRadius: '50%', background: `radial-gradient(circle, ${color}18 0%, transparent 65%)`, filter: 'blur(60px)', pointerEvents: 'none' }} />
            <div style={{ position: 'relative', zIndex: 1, maxWidth: '560px' }}>
              <h2 style={{ fontSize: 'clamp(1.8rem, 3vw, 2.8rem)', fontWeight: '900', lineHeight: '1.2', marginBottom: '1.25rem' }}>Pronto para começar?</h2>
              <p style={{ fontSize: '16px', color: 'rgba(203,213,225,0.75)', lineHeight: '1.8', marginBottom: '2.5rem' }}>
                Me conta sobre o seu projeto e vamos encontrar a melhor solução juntos.
              </p>
              <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
                <Link href="/contato" style={{ padding: '14px 32px', borderRadius: '10px', background: gradient, color: '#fff', fontWeight: '700', fontSize: '15px', textDecoration: 'none', display: 'inline-block', boxShadow: `0 12px 30px ${color}30` }}
                  onMouseEnter={e => { (e.currentTarget as HTMLElement).style.opacity = '0.88'; (e.currentTarget as HTMLElement).style.transform = 'translateY(-2px)'; }}
                  onMouseLeave={e => { (e.currentTarget as HTMLElement).style.opacity = '1'; (e.currentTarget as HTMLElement).style.transform = 'translateY(0)'; }}>
                  Falar sobre meu projeto →
                </Link>
                <Link href="/produtos" style={{ padding: '14px 32px', borderRadius: '10px', border: '1px solid rgba(255,255,255,0.15)', color: 'rgba(255,255,255,0.65)', fontWeight: '600', fontSize: '15px', textDecoration: 'none', display: 'inline-block', transition: 'border-color 0.2s, color 0.2s' }}
                  onMouseEnter={e => { (e.currentTarget as HTMLElement).style.borderColor = 'rgba(255,255,255,0.35)'; (e.currentTarget as HTMLElement).style.color = '#fff'; }}
                  onMouseLeave={e => { (e.currentTarget as HTMLElement).style.borderColor = 'rgba(255,255,255,0.15)'; (e.currentTarget as HTMLElement).style.color = 'rgba(255,255,255,0.65)'; }}>
                  Ver produtos
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
