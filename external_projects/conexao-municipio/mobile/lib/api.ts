import Constants from "expo-constants";

import type { CategoriaDenuncia, StatusDenuncia } from "./theme";

export interface Denuncia {
  id: number;
  titulo: string;
  descricao: string | null;
  endereco: string;
  categoria: CategoriaDenuncia | null;
  origem: "web" | "mobile";
  status: StatusDenuncia;
  data: string;
}

export interface Noticia {
  id: number;
  titulo: string;
  resumo: string;
  data: string;
}

export interface HorarioDisponibilidade {
  horario: string;
  disponivel: boolean;
}

export interface Agendamento {
  id: number;
  servico: string;
  data: string;
  horario: string;
}

interface Paginated<T> {
  data: T[];
}

/**
 * Base da API: em desenvolvimento deduz o IP da máquina que roda o Metro
 * (mesma máquina do docker-compose), então o celular físico alcança a API
 * sem configuração. Sobrescreva com EXPO_PUBLIC_API_URL se necessário.
 */
function resolverBaseUrl(): string {
  const explicita = process.env.EXPO_PUBLIC_API_URL;
  if (explicita) {
    return explicita;
  }

  const hostUri = Constants.expoConfig?.hostUri;
  if (hostUri) {
    const host = hostUri.split(":")[0];
    return `http://${host}:8015`;
  }

  return "http://localhost:8015";
}

export const API_URL = resolverBaseUrl();

export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_URL}/api${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });

  if (!res.ok) {
    let mensagem = `Erro na API (HTTP ${res.status})`;
    try {
      const body = await res.json();
      if (typeof body?.message === "string" && body.message) {
        mensagem = body.message;
      }
    } catch {
      // resposta sem corpo JSON: mantém a mensagem genérica
    }
    throw new ApiError(mensagem, res.status);
  }

  return res.json();
}

export async function buscarNoticias(): Promise<Noticia[]> {
  return apiFetch<Noticia[]>("/noticias?limit=10");
}

export async function buscarMinhasDenuncias(): Promise<Denuncia[]> {
  const page = await apiFetch<Paginated<Denuncia>>(
    "/denuncias?origem=mobile&per_page=50",
  );
  return page.data;
}

export interface NovaDenunciaPayload {
  titulo: string;
  descricao: string | null;
  endereco: string;
  categoria: CategoriaDenuncia;
  data: string;
}

export async function criarDenuncia(
  payload: NovaDenunciaPayload,
): Promise<Denuncia> {
  return apiFetch<Denuncia>("/denuncias", {
    method: "POST",
    body: JSON.stringify({ ...payload, origem: "mobile" }),
  });
}

export async function buscarHorarios(
  servico: string,
  data: string,
): Promise<HorarioDisponibilidade[]> {
  const res = await apiFetch<{ horarios: HorarioDisponibilidade[] }>(
    `/agendamentos/horarios?servico=${encodeURIComponent(servico)}&data=${data}`,
  );
  return res.horarios;
}

export async function criarAgendamento(
  servico: string,
  data: string,
  horario: string,
): Promise<Agendamento> {
  return apiFetch<Agendamento>("/agendamentos", {
    method: "POST",
    body: JSON.stringify({ servico, data, horario }),
  });
}
