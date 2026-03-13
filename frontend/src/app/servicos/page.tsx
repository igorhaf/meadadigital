'use client';

import Link from 'next/link';
import Navbar from '../components/Navbar';
import Footer from '../components/Footer';
import {
  CodeIcon,
  CloudIcon,
  CpuIcon,
  SmartphoneIcon,
  LayersIcon,
  BarChartIcon,
  CheckCircleIcon,
} from '../components/icons';

const services = [
  {
    icon: CodeIcon,
    href: '/servicos/desenvolvimento',
    color: '#3b82f6',
    gradientFrom: 'rgba(59,130,246,0.15)',
    gradientTo: 'rgba(59,130,246,0.05)',
    border: 'rgba(59,130,246,0.25)',
    title: 'Desenvolvimento sob Medida',
    description:
      'Criamos aplicações web, mobile e APIs altamente performáticas, alinhadas às necessidades específicas do seu negócio. Cada linha de código é escrita com propósito, qualidade e escalabilidade em mente.',
    bullets: ['Arquitetura escalável', 'APIs RESTful & GraphQL', 'Testes automatizados'],
  },
  {
    icon: CloudIcon,
    href: '/servicos/nuvem',
    color: '#a855f7',
    gradientFrom: 'rgba(168,85,247,0.15)',
    gradientTo: 'rgba(168,85,247,0.05)',
    border: 'rgba(168,85,247,0.25)',
    title: 'Cloud & DevOps',
    description:
      'Infraestrutura robusta e escalável em AWS, GCP e Azure. Automatizamos pipelines, garantimos alta disponibilidade e reduzimos custos operacionais com arquiteturas modernas na nuvem.',
    bullets: ['Kubernetes & Docker', 'CI/CD automatizado', 'Monitoramento 24/7'],
  },
  {
    icon: CpuIcon,
    href: '/servicos/ia-automacao',
    color: '#ec4899',
    gradientFrom: 'rgba(236,72,153,0.15)',
    gradientTo: 'rgba(236,72,153,0.05)',
    border: 'rgba(236,72,153,0.25)',
    title: 'Inteligência Artificial',
    description:
      'Integramos machine learning e IA generativa nos seus produtos para criar experiências inteligentes, automatizar processos e extrair insights valiosos de grandes volumes de dados.',
    bullets: ['Modelos de linguagem (LLM)', 'Visão computacional', 'Análise preditiva'],
  },
  {
    icon: SmartphoneIcon,
    href: '/servicos/mobile',
    color: '#06b6d4',
    gradientFrom: 'rgba(6,182,212,0.15)',
    gradientTo: 'rgba(6,182,212,0.05)',
    border: 'rgba(6,182,212,0.25)',
    title: 'Apps Mobile',
    description:
      'Desenvolvemos aplicativos nativos e cross-platform para iOS e Android com React Native, entregando experiências fluidas e de alto desempenho que encantam os usuários.',
    bullets: ['UX/UI nativo', 'Offline first', 'Push notifications'],
  },
  {
    icon: LayersIcon,
    href: '/servicos/design-ux',
    color: '#34d399',
    gradientFrom: 'rgba(52,211,153,0.15)',
    gradientTo: 'rgba(52,211,153,0.05)',
    border: 'rgba(52,211,153,0.25)',
    title: 'Design & UX',
    description:
      'Criamos interfaces que as pessoas adoram usar. Do discovery à entrega, cada tela é desenhada com base em pesquisa real, testes com usuários e padrões modernos de acessibilidade.',
    bullets: ['Design System completo', 'Testes de usabilidade', 'Prototipagem rápida'],
  },
  {
    icon: BarChartIcon,
    href: '/servicos/apis-integracoes',
    color: '#f97316',
    gradientFrom: 'rgba(249,115,22,0.15)',
    gradientTo: 'rgba(249,115,22,0.05)',
    border: 'rgba(249,115,22,0.25)',
    title: 'APIs & Integrações',
    description:
      'Conectamos sistemas legados, plataformas externas e microsserviços em uma arquitetura coesa. APIs bem documentadas, performáticas e fáceis de manter.',
    bullets: ['REST, GraphQL & gRPC', 'Webhooks & eventos', 'Documentação OpenAPI'],
  },
];

const processSteps = [
  {
    number: '01',
    title: 'Descoberta',
    description: 'Mergulhamos no seu negócio para entender objetivos, desafios e oportunidades. Workshops colaborativos que alinham visão e estratégia.',
  },
  {
    number: '02',
    title: 'Arquitetura',
    description: 'Desenhamos a solução técnica ideal — stack, infraestrutura e integrações. Documentação clara antes de qualquer linha de código.',
  },
  {
    number: '03',
    title: 'Execução',
    description: 'Desenvolvimento ágil em sprints de duas semanas. Demos regulares, feedback contínuo e adaptação rápida às mudanças.',
  },
  {
    number: '04',
    title: 'Lançamento',
    description: 'Deploy seguro, monitoramento proativo e suporte pós-lançamento. Sua solução vai ao ar com confiança e estabilidade.',
  },
];

const techStack = [
  'React', 'Next.js', 'TypeScript', 'Node.js', 'Python', 'Go',
  'AWS', 'GCP', 'Azure', 'Kubernetes', 'Docker', 'PostgreSQL',
  'Redis', 'MongoDB', 'TensorFlow', 'PyTorch',
];

export default function ServicosPage() {
  return (
    <div style={{ background: '#000812', minHeight: '100vh', color: '#fff', fontFamily: 'system-ui, -apple-system, sans-serif' }}>
      <Navbar />

      {/* Hero Section */}
      <section
        style={{
          paddingTop: '160px',
          paddingBottom: '100px',
          paddingLeft: '2rem',
          paddingRight: '2rem',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        {/* Background Glows */}
        <div
          style={{
            position: 'absolute',
            top: '-100px',
            left: '-200px',
            width: '700px',
            height: '700px',
            background: 'radial-gradient(circle, rgba(59,130,246,0.12) 0%, transparent 70%)',
            pointerEvents: 'none',
          }}
        />
        <div
          style={{
            position: 'absolute',
            top: '-50px',
            right: '-100px',
            width: '500px',
            height: '500px',
            background: 'radial-gradient(circle, rgba(139,92,246,0.08) 0%, transparent 70%)',
            pointerEvents: 'none',
          }}
        />

        <div style={{ maxWidth: '1360px', margin: '0 auto', textAlign: 'center', position: 'relative' }}>
          {/* Badge */}
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              padding: '6px 16px',
              borderRadius: '100px',
              background: 'rgba(59,130,246,0.1)',
              border: '1px solid rgba(59,130,246,0.25)',
              marginBottom: '1.5rem',
            }}
          >
            <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#3b82f6' }} />
            <span style={{ color: '#93c5fd', fontSize: '13px', fontWeight: 500, letterSpacing: '0.05em' }}>
              Nossas Soluções
            </span>
          </div>

          <h1
            style={{
              fontSize: 'clamp(2.5rem, 5vw, 4rem)',
              fontWeight: 800,
              lineHeight: 1.1,
              letterSpacing: '-0.03em',
              marginBottom: '1.5rem',
              color: '#fff',
            }}
          >
            Tecnologia que{' '}
            <span
              style={{
                background: 'linear-gradient(135deg, #3b82f6, #8b5cf6, #ec4899)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
                backgroundClip: 'text',
              }}
            >
              Escala
            </span>{' '}
            com seu Negócio
          </h1>

          <p
            style={{
              fontSize: '1.125rem',
              color: 'rgba(255,255,255,0.55)',
              maxWidth: '600px',
              margin: '0 auto',
              lineHeight: 1.7,
            }}
          >
            Da ideia ao produto final, construímos com excelência técnica em cada etapa. Escolha as soluções que impulsionam seu crescimento.
          </p>
        </div>
      </section>

      {/* Services Grid */}
      <section style={{ padding: '0 2rem 6rem', maxWidth: '1360px', margin: '0 auto' }}>
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 1fr)',
            gap: '1.5rem',
          }}
        >
          {services.map((service) => {
            const Icon = service.icon;
            return (
              <div
                key={service.title}
                style={{
                  background: 'rgba(255,255,255,0.03)',
                  border: '1px solid rgba(255,255,255,0.07)',
                  borderRadius: '16px',
                  padding: '2rem',
                  transition: 'border-color 0.3s ease, background 0.3s ease',
                  cursor: 'default',
                }}
                onMouseEnter={(e) => {
                  (e.currentTarget as HTMLElement).style.borderColor = service.border;
                  (e.currentTarget as HTMLElement).style.background = 'rgba(255,255,255,0.05)';
                }}
                onMouseLeave={(e) => {
                  (e.currentTarget as HTMLElement).style.borderColor = 'rgba(255,255,255,0.07)';
                  (e.currentTarget as HTMLElement).style.background = 'rgba(255,255,255,0.03)';
                }}
              >
                {/* Icon */}
                <div
                  style={{
                    width: '48px',
                    height: '48px',
                    borderRadius: '14px',
                    background: `linear-gradient(135deg, ${service.gradientFrom}, ${service.gradientTo})`,
                    border: `1px solid ${service.border}`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    marginBottom: '1.25rem',
                  }}
                >
                  <Icon size={22} color={service.color} />
                </div>

                <h3
                  style={{
                    fontSize: '1.1rem',
                    fontWeight: 700,
                    color: '#fff',
                    marginBottom: '0.75rem',
                    letterSpacing: '-0.01em',
                  }}
                >
                  {service.title}
                </h3>

                <p
                  style={{
                    color: 'rgba(255,255,255,0.5)',
                    fontSize: '14px',
                    lineHeight: 1.7,
                    marginBottom: '1.25rem',
                  }}
                >
                  {service.description}
                </p>

                {/* Bullet points */}
                <ul style={{ listStyle: 'none', padding: 0, margin: '0 0 1.25rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                  {service.bullets.map((bullet) => (
                    <li key={bullet} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                      <CheckCircleIcon size={15} color={service.color} strokeWidth={2} />
                      <span style={{ color: 'rgba(255,255,255,0.6)', fontSize: '13px' }}>{bullet}</span>
                    </li>
                  ))}
                </ul>

                <Link
                  href={service.href}
                  style={{
                    color: service.color,
                    fontSize: '13px',
                    fontWeight: 600,
                    textDecoration: 'none',
                    letterSpacing: '0.02em',
                  }}
                >
                  Saiba mais →
                </Link>
              </div>
            );
          })}
        </div>
      </section>

      {/* Process Section */}
      <section
        style={{
          padding: '5rem 2rem',
          background: 'rgba(255,255,255,0.01)',
          borderTop: '1px solid rgba(59,130,246,0.08)',
          borderBottom: '1px solid rgba(59,130,246,0.08)',
        }}
      >
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
          <div style={{ textAlign: 'center', marginBottom: '3.5rem' }}>
            <h2
              style={{
                fontSize: 'clamp(1.75rem, 3vw, 2.5rem)',
                fontWeight: 800,
                color: '#fff',
                letterSpacing: '-0.03em',
                marginBottom: '0.75rem',
              }}
            >
              Como Trabalhamos
            </h2>
            <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: '1rem' }}>
              Um processo estruturado para resultados previsíveis e de alta qualidade.
            </p>
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(4, 1fr)',
              gap: '1.5rem',
            }}
          >
            {processSteps.map((step) => (
              <div
                key={step.number}
                style={{
                  background: 'rgba(255,255,255,0.03)',
                  border: '1px solid rgba(255,255,255,0.07)',
                  borderRadius: '16px',
                  padding: '2rem',
                  position: 'relative',
                }}
              >
                <div
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    width: '40px',
                    height: '40px',
                    borderRadius: '10px',
                    background: 'rgba(59,130,246,0.1)',
                    border: '1px solid rgba(59,130,246,0.2)',
                    marginBottom: '1rem',
                  }}
                >
                  <span style={{ color: '#3b82f6', fontSize: '14px', fontWeight: 700, fontFamily: 'monospace' }}>
                    {step.number}
                  </span>
                </div>
                <h3
                  style={{
                    fontSize: '1rem',
                    fontWeight: 700,
                    color: '#fff',
                    marginBottom: '0.5rem',
                  }}
                >
                  {step.title}
                </h3>
                <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: '13px', lineHeight: 1.6 }}>
                  {step.description}
                </p>
              </div>
            ))}
          </div>
        </div>
      </section>


      {/* CTA Section */}
      <section style={{ padding: '5rem 2rem 7rem' }}>
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
          <div
            style={{
              borderRadius: '24px',
              background: 'linear-gradient(135deg, rgba(59,130,246,0.12), rgba(139,92,246,0.08))',
              border: '1px solid rgba(59,130,246,0.2)',
              padding: '4rem',
              textAlign: 'center',
              position: 'relative',
              overflow: 'hidden',
            }}
          >
            <div
              style={{
                position: 'absolute',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
                width: '600px',
                height: '300px',
                background: 'radial-gradient(ellipse, rgba(59,130,246,0.08) 0%, transparent 70%)',
                pointerEvents: 'none',
              }}
            />
            <h2
              style={{
                fontSize: 'clamp(1.75rem, 3vw, 2.5rem)',
                fontWeight: 800,
                color: '#fff',
                letterSpacing: '-0.03em',
                marginBottom: '1rem',
                position: 'relative',
              }}
            >
              Qual solução é certa para você?
            </h2>
            <p
              style={{
                color: 'rgba(255,255,255,0.5)',
                fontSize: '1rem',
                maxWidth: '480px',
                margin: '0 auto 2.5rem',
                lineHeight: 1.7,
                position: 'relative',
              }}
            >
              Nossa equipe de especialistas vai analisar seu caso e recomendar a arquitetura ideal para alcançar seus objetivos.
            </p>
            <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', position: 'relative' }}>
              <a
                href="/contato"
                style={{
                  padding: '14px 32px',
                  borderRadius: '10px',
                  background: 'linear-gradient(135deg, #3b82f6, #6366f1)',
                  color: '#fff',
                  fontSize: '15px',
                  fontWeight: 600,
                  textDecoration: 'none',
                  transition: 'opacity 0.2s, transform 0.2s',
                  display: 'inline-block',
                }}
                onMouseEnter={(e) => {
                  (e.currentTarget as HTMLElement).style.opacity = '0.88';
                  (e.currentTarget as HTMLElement).style.transform = 'translateY(-2px)';
                }}
                onMouseLeave={(e) => {
                  (e.currentTarget as HTMLElement).style.opacity = '1';
                  (e.currentTarget as HTMLElement).style.transform = 'translateY(0)';
                }}
              >
                Falar com especialista
              </a>
              <a
                href="#"
                style={{
                  padding: '14px 32px',
                  borderRadius: '10px',
                  background: 'transparent',
                  color: 'rgba(255,255,255,0.7)',
                  fontSize: '15px',
                  fontWeight: 600,
                  textDecoration: 'none',
                  border: '1px solid rgba(255,255,255,0.15)',
                  transition: 'border-color 0.2s, color 0.2s',
                  display: 'inline-block',
                }}
                onMouseEnter={(e) => {
                  (e.currentTarget as HTMLElement).style.borderColor = 'rgba(255,255,255,0.35)';
                  (e.currentTarget as HTMLElement).style.color = '#fff';
                }}
                onMouseLeave={(e) => {
                  (e.currentTarget as HTMLElement).style.borderColor = 'rgba(255,255,255,0.15)';
                  (e.currentTarget as HTMLElement).style.color = 'rgba(255,255,255,0.7)';
                }}
              >
                Ver produtos
              </a>
            </div>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
