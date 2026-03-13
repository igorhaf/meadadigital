'use client';

import { useState, useEffect, useRef, KeyboardEvent } from 'react';
import Link from 'next/link';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import {
  CodeIcon, CloudIcon, CpuIcon, SmartphoneIcon, LayersIcon, BarChartIcon,
} from './components/icons';

const CLAUDIO_URL = 'https://claudio.meadadigital.com';

function IconBox({ icon, color }: { icon: React.ReactNode; color: string }) {
  return (
    <div style={{
      width: '52px', height: '52px', borderRadius: '14px', marginBottom: '1.5rem',
      background: `linear-gradient(135deg, ${color}22, ${color}08)`,
      border: `1px solid ${color}30`,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      flexShrink: 0,
    }}>
      {icon}
    </div>
  );
}

type ChatMsg = { id: number; from: 'client' | 'ia'; text: string; time: string; streaming?: boolean };

const demoMessages: ChatMsg[] = [
  { id: 0, from: 'client', text: 'Vocês fazem sites com IA integrada?', time: '14:31' },
  { id: 1, from: 'ia', text: 'Sim! Desenvolvemos sites e sistemas com IA embarcada — chatbots, automações e análise de dados. Qual é o seu negócio? 😊', time: '14:31' },
  { id: 2, from: 'client', text: 'Tenho uma clínica. Quero automatizar o agendamento.', time: '14:32' },
  { id: 3, from: 'ia', text: 'Perfeito para uma clínica! Podemos criar um assistente que agenda consultas 24h, envia lembretes automáticos e responde dúvidas dos pacientes — tudo sem precisar de atendente. 🗓️', time: '14:32' },
  { id: 4, from: 'client', text: 'Que tipo de resultado vocês já entregaram?', time: '14:33' },
  { id: 5, from: 'ia', text: 'Um dos nossos clientes reduziu 80% do trabalho manual na operação com IA. Outro aumentou retenção em +340% após reformular o sistema. Posso te mostrar mais cases?', time: '14:34' },
  { id: 6, from: 'client', text: 'Quero! Como a gente começa?', time: '14:34' },
  { id: 7, from: 'ia', text: '✅ Ótimo! É só agendar uma consultoria gratuita com nossa equipe. Vamos entender seu negócio e montar uma proposta personalizada. Sem compromisso! 🚀', time: '14:35' },
];

const msgDelays = [800, 2000, 1400, 2200, 1200, 2400, 1000, 2000];

function nowTime() {
  return new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

function cleanText(text: string): string {
  return text
    .replace(/\*\*(.*?)\*\*/g, '$1')
    .replace(/\*(.*?)\*/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/^[-•]\s+/gm, '');
}

export default function Home() {
  const [liveMessages, setLiveMessages] = useState<ChatMsg[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const chatContainerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  let msgIdRef = useRef(1);

  useEffect(() => {
    // Set greeting only on client to avoid hydration mismatch
    setLiveMessages([{
      id: 0, from: 'ia',
      text: 'Olá! 👋 Sou a assistente da Meada Digital. Como posso te ajudar hoje?',
      time: nowTime(),
    }]);
  }, []);

  useEffect(() => {
    const el = chatContainerRef.current;
    if (el) el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
  }, [liveMessages]);

  async function sendMessage() {
    const text = input.trim();
    if (!text || sending) return;
    setInput('');
    setSending(true);

    const userMsg: ChatMsg = { id: msgIdRef.current++, from: 'client', text, time: nowTime() };
    const aiId = msgIdRef.current++;
    const aiMsg: ChatMsg = { id: aiId, from: 'ia', text: '', time: nowTime(), streaming: true };
    setLiveMessages(prev => [...prev, userMsg, aiMsg]);

    try {
      const res = await fetch(`${CLAUDIO_URL}/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: text, session_id: sessionId, project: 'meada', use_case: 'homepage' }),
      });
      const reader = res.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let aiText = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';
        for (const line of lines) {
          if (line.startsWith('event: chunk')) continue;
          if (line.startsWith('data: ')) {
            try {
              const data = JSON.parse(line.slice(6));
              if (data.text) {
                aiText += data.text;
                setLiveMessages(prev => prev.map(m => m.id === aiId ? { ...m, text: aiText } : m));
              }
              if (data.session_id) setSessionId(data.session_id);
            } catch {}
          }
        }
      }
    } catch (e) {
      setLiveMessages(prev => prev.map(m => m.id === aiId ? { ...m, text: 'Ocorreu um erro. Tente novamente.', streaming: false } : m));
    }

    setLiveMessages(prev => prev.map(m => m.id === aiId ? { ...m, streaming: false } : m));
    setSending(false);
    setTimeout(() => inputRef.current?.focus(), 50);
  }

  function handleKey(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage(); }
  }

  const services = [
    { Icon: CodeIcon, color: '#60a5fa', href: '/servicos/desenvolvimento', title: 'Desenvolvimento Personalizado', desc: 'Sites e sistemas feitos sob medida, do institucional ao mais complexo.' },
    { Icon: CloudIcon, color: '#a855f7', href: '/servicos/nuvem', title: 'Infraestrutura em Nuvem', desc: 'Deploy, CI/CD, monitoramento e escalabilidade sem dores de cabeça.' },
    { Icon: CpuIcon, color: '#ec4899', href: '/servicos/ia-automacao', title: 'IA & Automação', desc: 'Chatbots, automações e análise de dados aplicados ao seu negócio.' },
    { Icon: SmartphoneIcon, color: '#22d3ee', href: '/servicos/mobile', title: 'Design Mobile First', desc: 'Experiências nativas e fluidas em qualquer dispositivo e tamanho de tela.' },
    { Icon: LayersIcon, color: '#34d399', href: '/servicos/design-ux', title: 'Design & UX', desc: 'Interfaces bonitas e funcionais. Do wireframe ao Design System completo.' },
    { Icon: BarChartIcon, color: '#f97316', href: '/servicos/apis-integracoes', title: 'APIs & Integrações', desc: 'Pagamentos, CRMs, ERPs e qualquer sistema conectado em uma arquitetura coesa.' },
  ];


  return (
    <div style={{ backgroundColor: '#000812', color: '#fff', minHeight: '100vh' }}>
      <Navbar />

      {/* ── HERO ── */}
      <section style={{
        position: 'relative', minHeight: '100vh',
        paddingTop: '148px', paddingBottom: '100px', overflow: 'hidden',
      }}>
        <div style={{ position: 'absolute', inset: 0, pointerEvents: 'none' }}>
          <div style={{ position: 'absolute', top: '-15%', left: '-8%', width: '700px', height: '700px', borderRadius: '50%', background: 'radial-gradient(circle, rgba(59,130,246,0.22) 0%, transparent 68%)', filter: 'blur(60px)', animation: 'float 8s ease-in-out infinite' }} />
          <div style={{ position: 'absolute', top: '5%', right: '-12%', width: '800px', height: '800px', borderRadius: '50%', background: 'radial-gradient(circle, rgba(139,92,246,0.18) 0%, transparent 68%)', filter: 'blur(60px)', animation: 'float 11s ease-in-out infinite', animationDelay: '2s' }} />
          <div style={{ position: 'absolute', bottom: '-20%', left: '25%', width: '600px', height: '600px', borderRadius: '50%', background: 'radial-gradient(circle, rgba(236,72,153,0.12) 0%, transparent 68%)', filter: 'blur(60px)', animation: 'float 13s ease-in-out infinite', animationDelay: '4s' }} />
        </div>

        <div style={{ position: 'relative', zIndex: 1, maxWidth: '1360px', margin: '0 auto', padding: '0 4rem' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '7rem', alignItems: 'center' }}>

            {/* Left */}
            <div>
              <h1 style={{ fontSize: 'clamp(2.8rem, 5vw, 4.4rem)', fontWeight: '900', lineHeight: '1.12', letterSpacing: '-0.03em', marginBottom: '2rem' }}>
                Sites e Sistemas com o{' '}
                <span style={{ backgroundImage: 'linear-gradient(125deg, #60a5fa 0%, #a855f7 50%, #ec4899 100%)', backgroundClip: 'text', WebkitBackgroundClip: 'text', color: 'transparent' }}>
                  Diferencial da IA
                </span>
              </h1>

              <p style={{ fontSize: '18px', color: 'rgb(203,213,225)', lineHeight: '1.85', marginBottom: '2.5rem', maxWidth: '460px' }}>
                Desenvolvimento de sites e sistemas personalizados, com ou sem integração de inteligência artificial — sempre com qualidade e foco no que importa para o seu negócio.
              </p>

              <div style={{ display: 'flex', gap: '1rem', marginBottom: '4.5rem', flexWrap: 'wrap' }}>
                <Link href="/contato" style={{ padding: '15px 34px', background: 'linear-gradient(135deg, #3b82f6, #6366f1)', borderRadius: '10px', color: 'white', fontWeight: '600', fontSize: '15px', textDecoration: 'none', display: 'inline-block', boxShadow: '0 14px 32px rgba(59,130,246,0.38)', transition: 'all 0.3s ease' }}
                  onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-3px)'; e.currentTarget.style.boxShadow = '0 20px 44px rgba(59,130,246,0.48)'; }}
                  onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '0 14px 32px rgba(59,130,246,0.38)'; }}>
                  Comece Agora →
                </Link>
                <button style={{ padding: '15px 34px', background: 'transparent', border: '1px solid rgba(59,130,246,0.28)', borderRadius: '10px', color: 'rgb(96,165,250)', fontWeight: '600', fontSize: '15px', cursor: 'pointer', transition: 'all 0.3s ease' }}
                  onMouseEnter={e => { e.currentTarget.style.background = 'rgba(59,130,246,0.08)'; e.currentTarget.style.borderColor = 'rgba(59,130,246,0.5)'; }}
                  onMouseLeave={e => { e.currentTarget.style.background = 'transparent'; e.currentTarget.style.borderColor = 'rgba(59,130,246,0.28)'; }}>
                  Ver Produtos
                </button>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '2rem', paddingTop: '3rem', borderTop: '1px solid rgba(71,85,105,0.25)' }}>
                {[
                  { n: '50+', l: 'Projetos', g: 'linear-gradient(90deg, #60a5fa, #a855f7)' },
                  { n: '20+', l: 'Tecnologias', g: 'linear-gradient(90deg, #a855f7, #ec4899)' },
                  { n: '5+', l: 'Anos no mercado', g: 'linear-gradient(90deg, #ec4899, #60a5fa)' },
                ].map(s => (
                  <div key={s.l}>
                    <p style={{ fontSize: '2.1rem', fontWeight: '900', marginBottom: '0.35rem', backgroundImage: s.g, backgroundClip: 'text', WebkitBackgroundClip: 'text', color: 'transparent' }}>{s.n}</p>
                    <p style={{ fontSize: '11px', color: 'rgb(148,163,184)', textTransform: 'uppercase', letterSpacing: '0.07em', fontWeight: '600' }}>{s.l}</p>
                  </div>
                ))}
              </div>
            </div>

            {/* Right - Chat IA real */}
            <div style={{ position: 'relative', height: '520px' }}>
              <style>{`
                @keyframes msgIn {
                  from { opacity: 0; transform: translateY(10px); }
                  to   { opacity: 1; transform: translateY(0); }
                }
                @keyframes typingDot {
                  0%, 60%, 100% { opacity: 0.3; transform: scale(1); }
                  30% { opacity: 1; transform: scale(1.25); }
                }
                .chat-scroll::-webkit-scrollbar { display: none; }
                .chat-scroll { scrollbar-width: none; }
                .chat-input::placeholder { color: rgba(148,163,184,0.4); }
                .chat-input:focus { outline: none; }
                .send-btn:hover { background: rgba(99,102,241,0.9) !important; }
              `}</style>

              <div style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)', width: '420px', height: '420px', borderRadius: '50%', background: 'radial-gradient(circle, rgba(99,102,241,0.18) 0%, transparent 70%)', filter: 'blur(52px)', pointerEvents: 'none' }} />

              <div style={{ position: 'absolute', inset: 0, borderRadius: '24px', overflow: 'hidden', border: '1px solid rgba(99,102,241,0.3)', background: 'rgba(10,14,30,0.92)', backdropFilter: 'blur(24px)', boxShadow: '0 32px 80px rgba(99,102,241,0.2)', display: 'flex', flexDirection: 'column' }}>

                {/* Header */}
                <div style={{ padding: '1.1rem 1.4rem', borderBottom: '1px solid rgba(255,255,255,0.06)', display: 'flex', alignItems: 'center', gap: '0.75rem', background: 'rgba(255,255,255,0.03)', flexShrink: 0 }}>
                  <div style={{ width: '38px', height: '38px', borderRadius: '50%', background: 'linear-gradient(135deg, #6366f1, #a855f7)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, boxShadow: '0 4px 12px rgba(99,102,241,0.5)' }}>
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
                    </svg>
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <p style={{ fontSize: '14px', fontWeight: '700', color: '#e2e8f0', margin: 0, lineHeight: '1.3' }}>Assistente Meada</p>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '5px', marginTop: '2px' }}>
                      <span style={{ width: '7px', height: '7px', borderRadius: '50%', background: '#4ade80', flexShrink: 0, boxShadow: '0 0 6px rgba(74,222,128,0.8)' }} />
                      <span style={{ fontSize: '11px', color: '#4ade80', fontWeight: '600' }}>IA ativa · agora</span>
                    </div>
                  </div>
                </div>

                {/* Messages */}
                <div style={{ position: 'relative', flex: 1, overflow: 'hidden' }}>
                  <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: '40px', background: 'linear-gradient(to bottom, rgba(10,14,30,0.98) 0%, transparent 100%)', zIndex: 2, pointerEvents: 'none' }} />
                  <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, height: '24px', background: 'linear-gradient(to top, rgba(10,14,30,0.8) 0%, transparent 100%)', zIndex: 2, pointerEvents: 'none' }} />

                  <div ref={chatContainerRef} className="chat-scroll" style={{ height: '100%', overflowY: 'auto', padding: '1rem 1.2rem', display: 'flex', flexDirection: 'column', gap: '0.6rem', background: 'rgba(0,8,18,0.35)' }}>
                    {liveMessages.map((msg) => (
                      msg.from === 'client' ? (
                        <div key={msg.id} style={{ display: 'flex', justifyContent: 'flex-start', animation: 'msgIn 0.25s ease forwards' }}>
                          <div style={{ maxWidth: '78%', padding: '0.5rem 0.85rem', borderRadius: '16px 16px 16px 4px', background: 'rgba(255,255,255,0.07)', border: '1px solid rgba(255,255,255,0.08)' }}>
                            <p style={{ fontSize: '13px', color: '#cbd5e1', margin: 0, lineHeight: '1.5' }}>{msg.text}</p>
                            <p style={{ fontSize: '10px', color: 'rgba(148,163,184,0.5)', margin: '3px 0 0', textAlign: 'right' }}>{msg.time}</p>
                          </div>
                        </div>
                      ) : (
                        <div key={msg.id} style={{ display: 'flex', justifyContent: 'flex-end', animation: 'msgIn 0.25s ease forwards' }}>
                          <div style={{ maxWidth: '84%', padding: '0.6rem 0.85rem', borderRadius: '16px 16px 4px 16px', background: 'linear-gradient(135deg, rgba(59,130,246,0.22), rgba(99,102,241,0.18))', border: '1px solid rgba(59,130,246,0.25)', boxShadow: '0 4px 16px rgba(59,130,246,0.1)' }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '5px', marginBottom: '4px' }}>
                              <span style={{ fontSize: '9px', fontWeight: '700', color: '#818cf8', textTransform: 'uppercase', letterSpacing: '0.07em' }}>IA Meada</span>
                              <span style={{ fontSize: '9px', color: 'rgba(129,140,248,0.4)' }}>✦</span>
                            </div>
                            {msg.text ? (
                              <p style={{ fontSize: '13px', color: '#e2e8f0', margin: 0, lineHeight: '1.55' }}>
                                {cleanText(msg.text)}{msg.streaming ? <span style={{ display: 'inline-block', width: '2px', height: '13px', background: '#818cf8', marginLeft: '2px', verticalAlign: 'middle', animation: 'typingDot 0.8s ease-in-out infinite' }} /> : null}
                              </p>
                            ) : (
                              <div style={{ display: 'flex', gap: '4px', alignItems: 'center', padding: '2px 0' }}>
                                {[0,1,2].map(i => <span key={i} style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#818cf8', display: 'inline-block', animation: `typingDot 1.1s ease-in-out ${i * 0.18}s infinite` }} />)}
                              </div>
                            )}
                            <p style={{ fontSize: '10px', color: 'rgba(148,163,184,0.5)', margin: '4px 0 0', textAlign: 'right' }}>{msg.time}</p>
                          </div>
                        </div>
                      )
                    ))}
                    <div style={{ height: '8px', flexShrink: 0 }} />
                  </div>
                </div>

                {/* Input */}
                <div style={{ padding: '0.65rem 1rem', borderTop: '1px solid rgba(99,102,241,0.15)', background: 'rgba(99,102,241,0.04)', display: 'flex', alignItems: 'center', gap: '0.6rem', flexShrink: 0 }}>
                  <input
                    ref={inputRef}
                    className="chat-input"
                    value={input}
                    onChange={e => setInput(e.target.value)}
                    onKeyDown={handleKey}
                    placeholder="Pergunte sobre nossos serviços..."
                    disabled={sending}
                    style={{ flex: 1, background: 'transparent', border: 'none', color: '#e2e8f0', fontSize: '13px', lineHeight: '1.4', fontFamily: 'inherit', opacity: sending ? 0.5 : 1 }}
                  />
                  <button
                    className="send-btn"
                    onClick={sendMessage}
                    disabled={!input.trim() || sending}
                    style={{ width: '32px', height: '32px', borderRadius: '50%', border: 'none', background: input.trim() && !sending ? 'linear-gradient(135deg, #6366f1, #a855f7)' : 'rgba(255,255,255,0.08)', cursor: input.trim() && !sending ? 'pointer' : 'default', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, transition: 'all 0.2s' }}
                  >
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                      <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── SERVICES ── */}
      <section id="services" style={{
        padding: '9rem 4rem',
        borderTop: '1px solid rgba(59,130,246,0.1)',
        borderBottom: '1px solid rgba(59,130,246,0.1)',
        background: 'linear-gradient(180deg, transparent 0%, rgba(59,130,246,0.025) 50%, transparent 100%)',
      }}>
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
          <div style={{ marginBottom: '5rem' }}>
            <span style={{ fontSize: '11px', color: 'rgb(96,165,250)', textTransform: 'uppercase', letterSpacing: '0.1em', fontWeight: '700' }}>Capacidades</span>
            <h2 style={{ fontSize: 'clamp(2.2rem, 4vw, 3.2rem)', fontWeight: '900', lineHeight: '1.2', marginTop: '1rem' }}>Tudo o Que Você Precisa para Crescer</h2>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '2rem' }}>
            {services.map((s, i) => (
              <div key={i} style={{ padding: '2.5rem', borderRadius: '16px', border: '1px solid rgba(59,130,246,0.1)', background: 'rgba(15,23,42,0.4)', backdropFilter: 'blur(12px)', cursor: 'pointer', transition: 'all 0.3s ease', display: 'flex', flexDirection: 'column' }}
                onMouseEnter={e => { e.currentTarget.style.border = `1px solid ${s.color}40`; e.currentTarget.style.transform = 'translateY(-6px)'; e.currentTarget.style.boxShadow = `0 20px 44px ${s.color}15`; e.currentTarget.style.background = 'rgba(15,23,42,0.7)'; }}
                onMouseLeave={e => { e.currentTarget.style.border = '1px solid rgba(59,130,246,0.1)'; e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = 'none'; e.currentTarget.style.background = 'rgba(15,23,42,0.4)'; }}>
                <IconBox icon={<s.Icon size={24} color={s.color} />} color={s.color} />
                <h3 style={{ fontSize: '18px', fontWeight: '700', marginBottom: '0.75rem', lineHeight: '1.3' }}>{s.title}</h3>
                <p style={{ color: 'rgb(148,163,184)', fontSize: '14px', lineHeight: '1.7', flex: 1 }}>{s.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>


      {/* ── PRODUTOS ── */}
      <section style={{
        padding: '9rem 4rem',
        borderTop: '1px solid rgba(59,130,246,0.1)',
      }}>
        <div style={{ maxWidth: '1360px', margin: '0 auto' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: '4rem', flexWrap: 'wrap', gap: '2rem' }}>
            <div>
              <span style={{ fontSize: '11px', color: 'rgb(96,165,250)', textTransform: 'uppercase', letterSpacing: '0.1em', fontWeight: '700' }}>Produtos</span>
              <h2 style={{ fontSize: 'clamp(2.2rem, 4vw, 3.2rem)', fontWeight: '900', lineHeight: '1.2', marginTop: '1rem' }}>
                Soluções Prontas para Usar
              </h2>
            </div>
            <a href="/produtos" style={{
              padding: '12px 28px', borderRadius: '10px',
              border: '1px solid rgba(59,130,246,0.28)', background: 'transparent',
              color: 'rgb(96,165,250)', fontWeight: '600', fontSize: '14px',
              textDecoration: 'none', whiteSpace: 'nowrap',
              transition: 'all 0.2s ease',
            }}
            onMouseEnter={e => { (e.currentTarget as HTMLElement).style.background = 'rgba(59,130,246,0.08)'; }}
            onMouseLeave={e => { (e.currentTarget as HTMLElement).style.background = 'transparent'; }}>
              Ver todos os produtos →
            </a>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1.5rem' }}>
            {[
              {
                name: 'AmigoPet',
                category: 'Pet Care',
                desc: 'Plataforma completa para petshops e clínicas veterinárias: agendamentos, prontuários e gestão em um só lugar.',
                tags: ['Agendamentos', 'Prontuário Digital', 'Petshop'],
                accent: '#34d399',
                href: 'https://amigopet.meadadigital.com',
                thumb: '/images/amigopet.jpg',
              },
              {
                name: 'Bangalô',
                category: 'Hotelaria',
                desc: 'Sistema de reservas e gestão para pousadas e hotéis boutique, com painel de controle intuitivo.',
                tags: ['Reservas', 'Hotelaria', 'Painel Admin'],
                accent: '#f59e0b',
                href: 'https://bangalo.meadadigital.com',
                thumb: '/images/bangalo.jpg',
              },
              {
                name: 'Ateliê Rosendo',
                category: 'E-commerce',
                desc: 'Loja virtual artesanal com catálogo de produtos, carrinho e vitrine elegante para artesãos.',
                tags: ['Artesanato', 'Loja Virtual', 'Catálogo'],
                accent: '#f472b6',
                href: 'https://atelie-rosendo.meadadigital.com',
                thumb: '/images/atelie-rosendo.jpg',
              },
              {
                name: 'Aurora Motors',
                category: 'Automotivo',
                desc: 'Plataforma para concessionárias com vitrine de veículos, simulador de financiamento e agendamento.',
                tags: ['Veículos', 'Financiamento', 'Concessionária'],
                accent: '#60a5fa',
                href: 'https://aurora-motors.meadadigital.com',
                thumb: '/images/aurora-motors.jpg',
              },
              {
                name: 'Entre Linhas e Silêncios',
                category: 'Blog & Literatura',
                desc: 'Blog literário com gestão de conteúdo, categorias e experiência de leitura imersiva.',
                tags: ['Blog', 'Literatura', 'Conteúdo'],
                accent: '#a78bfa',
                href: 'https://entre-linhas-e-silencios.meadadigital.com',
                thumb: '/images/entre-linhas-e-silencios.jpg',
              },
              {
                name: 'Impacto Fitness',
                category: 'Fitness',
                desc: 'Site institucional para academia com planos, horários de aulas e área de membros.',
                tags: ['Academia', 'Membros', 'Planos'],
                accent: '#f97316',
                href: 'https://impacto-fitness.meadadigital.com',
                thumb: '/images/impacto-fitness.jpg',
              },
              {
                name: 'Kazen Sushi House',
                category: 'Gastronomia',
                desc: 'Cardápio digital e sistema de pedidos online para restaurante japonês com experiência premium.',
                tags: ['Restaurante', 'Cardápio Digital', 'Pedidos'],
                accent: '#fb7185',
                href: 'https://kazen-sushi-house.meadadigital.com',
                thumb: '/images/kazen-sushi-house.jpg',
              },
              {
                name: 'Leva e Lava',
                category: 'Serviços',
                desc: 'Plataforma de agendamento para lavanderias com coleta, entrega e acompanhamento em tempo real.',
                tags: ['Lavanderia', 'Agendamento', 'Delivery'],
                accent: '#22d3ee',
                href: 'https://levaelava.meadadigital.com',
                thumb: '/images/levaelava.jpg',
              },
              {
                name: 'Nobre Madeira',
                category: 'Marcenaria',
                desc: 'Site institucional para marcenaria de alto padrão com portfólio de móveis e orçamento online.',
                tags: ['Marcenaria', 'Portfólio', 'Móveis'],
                accent: '#d97706',
                href: 'https://nobre-madeira.meadadigital.com',
                thumb: '/images/nobre-madeira.jpg',
              },
              {
                name: 'Reservo',
                category: 'Reservas',
                desc: 'Sistema de reservas multi-segmento para barbearias, consultórios e espaços de eventos.',
                tags: ['Agendamento', 'Multi-segmento', 'Reservas'],
                accent: '#34d399',
                href: 'https://reservo.meadadigital.com',
                thumb: '/images/reservo.jpg',
              },
              {
                name: 'Suinda',
                category: 'Institucional',
                desc: 'Site institucional moderno com apresentação de serviços, equipe e formulário de contato.',
                tags: ['Institucional', 'Serviços', 'Contato'],
                accent: '#818cf8',
                href: 'https://suinda.meadadigital.com',
                thumb: '/images/suinda.jpg',
              },
              {
                name: 'Viva Pronto',
                category: 'Imobiliário',
                desc: 'Plataforma imobiliária com busca avançada de imóveis, filtros e tour virtual integrado.',
                tags: ['Imóveis', 'Busca Avançada', 'Tour Virtual'],
                accent: '#4ade80',
                href: 'https://vivapronto.meadadigital.com',
                thumb: '/images/vivapronto.jpg',
              },
            ].map((p, i) => (
              <a key={i} href={p.href} target="_blank" rel="noopener noreferrer" style={{
                borderRadius: '16px', overflow: 'hidden',
                border: '1px solid rgba(59,130,246,0.1)',
                background: 'rgba(15,23,42,0.4)',
                transition: 'all 0.3s ease', cursor: 'pointer',
                display: 'flex', flexDirection: 'column',
                textDecoration: 'none', color: 'inherit',
              }}
              onMouseEnter={e => {
                e.currentTarget.style.transform = 'translateY(-5px)';
                e.currentTarget.style.border = `1px solid ${p.accent}40`;
                e.currentTarget.style.boxShadow = `0 20px 40px ${p.accent}18`;
                const img = e.currentTarget.querySelector('.thumb-img') as HTMLElement | null;
                if (img) img.style.transform = 'scale(1.07)';
              }}
              onMouseLeave={e => {
                e.currentTarget.style.transform = 'translateY(0)';
                e.currentTarget.style.border = '1px solid rgba(59,130,246,0.1)';
                e.currentTarget.style.boxShadow = 'none';
                const img = e.currentTarget.querySelector('.thumb-img') as HTMLElement | null;
                if (img) img.style.transform = 'scale(1)';
              }}>
                <div style={{ height: '140px', position: 'relative', overflow: 'hidden', background: '#0f172a' }}>
                  <img
                    className="thumb-img"
                    src={p.thumb}
                    alt={p.name}
                    style={{ width: '100%', height: '100%', objectFit: 'cover', objectPosition: 'top', display: 'block', transition: 'transform 0.4s ease' }}
                  />
                  <div style={{ position: 'absolute', top: '0.75rem', left: '0.85rem', padding: '3px 9px', borderRadius: '24px', background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(8px)', border: `1px solid ${p.accent}40`, fontSize: '10px', fontWeight: '700', color: p.accent, letterSpacing: '0.07em', textTransform: 'uppercase' }}>{p.category}</div>
                </div>
                <div style={{ padding: '1.25rem', flex: 1, display: 'flex', flexDirection: 'column' }}>
                  <p style={{ fontSize: '16px', fontWeight: '800', color: '#e2e8f0', marginBottom: '0.4rem', letterSpacing: '-0.01em' }}>{p.name}</p>
                  <p style={{ fontSize: '12px', color: 'rgb(148,163,184)', lineHeight: '1.6', marginBottom: '1rem', flex: 1 }}>{p.desc}</p>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem', marginBottom: '1rem' }}>
                    {p.tags.map(t => (
                      <span key={t} style={{ padding: '2px 8px', borderRadius: '5px', background: 'rgba(59,130,246,0.08)', border: '1px solid rgba(59,130,246,0.15)', fontSize: '10px', fontWeight: '600', color: 'rgb(148,163,184)' }}>{t}</span>
                    ))}
                  </div>
                  <span style={{ color: p.accent, fontWeight: '600', fontSize: '13px' }}>Conhecer →</span>
                </div>
              </a>
            ))}
          </div>
        </div>
      </section>

      {/* ── CTA ── */}
      <section id="contact" style={{ padding: '11rem 4rem', position: 'relative', overflow: 'hidden' }}>
        <div style={{ position: 'absolute', top: '-25%', right: '-5%', width: '600px', height: '600px', background: 'radial-gradient(circle, rgba(59,130,246,0.22) 0%, transparent 65%)', borderRadius: '50%', filter: 'blur(70px)' }} />
        <div style={{ position: 'absolute', bottom: '-25%', left: '5%', width: '600px', height: '600px', background: 'radial-gradient(circle, rgba(139,92,246,0.18) 0%, transparent 65%)', borderRadius: '50%', filter: 'blur(70px)' }} />
        <div style={{ maxWidth: '780px', margin: '0 auto', textAlign: 'center', position: 'relative', zIndex: 1 }}>
          <h2 style={{ fontSize: 'clamp(2.2rem, 5vw, 3.8rem)', fontWeight: '900', lineHeight: '1.15', marginBottom: '2rem' }}>
            Pronto para{' '}
            <span style={{ backgroundImage: 'linear-gradient(125deg, #60a5fa, #a855f7, #ec4899)', backgroundClip: 'text', WebkitBackgroundClip: 'text', color: 'transparent' }}>
              Transformar seu Negócio?
            </span>
          </h2>
          <p style={{ fontSize: '18px', color: 'rgb(203,213,225)', lineHeight: '1.85', marginBottom: '3.5rem', maxWidth: '560px', margin: '0 auto 3.5rem' }}>
            Do site institucional ao sistema completo. Com integração de IA quando faz sentido. Sem enrolação, com resultado.
          </p>
          <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', flexWrap: 'wrap' }}>
            <button style={{ padding: '13px 32px', background: 'linear-gradient(135deg, #3b82f6, #6366f1)', border: 'none', borderRadius: '12px', color: 'white', fontWeight: '600', fontSize: '15px', cursor: 'pointer', boxShadow: '0 8px 24px rgba(59,130,246,0.3)', transition: 'all 0.22s ease', letterSpacing: '0.01em' }}
              onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-2px)'; e.currentTarget.style.boxShadow = '0 16px 36px rgba(59,130,246,0.45)'; }}
              onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0)'; e.currentTarget.style.boxShadow = '0 8px 24px rgba(59,130,246,0.3)'; }}>
              Agendar Consultoria
            </button>
            <button style={{ padding: '13px 32px', background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.12)', borderRadius: '12px', color: 'rgba(255,255,255,0.75)', fontWeight: '600', fontSize: '15px', cursor: 'pointer', transition: 'all 0.22s ease', letterSpacing: '0.01em' }}
              onMouseEnter={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.08)'; e.currentTarget.style.borderColor = 'rgba(99,102,241,0.5)'; e.currentTarget.style.color = 'white'; }}
              onMouseLeave={e => { e.currentTarget.style.background = 'rgba(255,255,255,0.04)'; e.currentTarget.style.borderColor = 'rgba(255,255,255,0.12)'; e.currentTarget.style.color = 'rgba(255,255,255,0.75)'; }}>
              Ver Produtos
            </button>
          </div>
        </div>
      </section>

      <Footer />
    </div>
  );
}
