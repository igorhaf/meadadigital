export type DenunciaStatus =
  | "pendente"
  | "em_andamento"
  | "resolvido"
  | "arquivado";
export type Periodo = "semana" | "mes" | "todos";
export type UsuarioRole = "admin" | "operador";

export interface Denuncia {
  id: number;
  titulo: string;
  descricao: string | null;
  endereco: string;
  status: DenunciaStatus;
  data: string;
}

export interface DashboardData {
  total: number;
  pendentes: number;
  em_andamento: number;
  resolvidas: number;
  recentes: Denuncia[];
}

export interface Usuario {
  id: number;
  name: string;
  email: string;
  role: UsuarioRole;
  created_at: string;
}

export type Configuracoes = Record<string, string | null>;

export interface Paginated<T> {
  data: T[];
  current_page: number;
  last_page: number;
  per_page: number;
  total: number;
  from: number | null;
  to: number | null;
}

const API_URL =
  process.env.API_URL ??
  process.env.NEXT_PUBLIC_API_URL ??
  "http://localhost:8015";

/** URL da API alcançável a partir do navegador (para hrefs de download etc.). */
export const PUBLIC_API_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8015";

export class ApiError extends Error {
  status: number;
  errors?: Record<string, string[]>;

  constructor(
    message: string,
    status: number,
    errors?: Record<string, string[]>,
  ) {
    super(message);
    this.status = status;
    this.errors = errors;
  }
}

async function handle<T>(res: Response): Promise<T> {
  if (!res.ok) {
    let message = `Erro na API (HTTP ${res.status})`;
    let errors: Record<string, string[]> | undefined;
    try {
      const body = await res.json();
      if (typeof body?.message === "string" && body.message) {
        message = body.message;
      }
      errors = body?.errors;
    } catch {
      // corpo não-JSON: mantém a mensagem genérica
    }
    throw new ApiError(message, res.status, errors);
  }

  if (res.status === 204) {
    return undefined as T;
  }

  return res.json();
}

function qs(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  }
  const encoded = search.toString();
  return encoded ? `?${encoded}` : "";
}

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_URL}/api${path}`, {
    cache: "no-store",
    ...init,
    headers: {
      Accept: "application/json",
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
  });

  return handle<T>(res);
}

// ---------------------------------------------------------------------------
// Dashboard
// ---------------------------------------------------------------------------

export async function fetchDashboard(periodo?: Periodo): Promise<DashboardData> {
  return apiFetch<DashboardData>(`/dashboard${qs({ periodo })}`);
}

// ---------------------------------------------------------------------------
// Denúncias
// ---------------------------------------------------------------------------

export interface DenunciaFilters {
  status?: DenunciaStatus;
  busca?: string;
  periodo?: Periodo;
  page?: number;
  per_page?: number;
}

export interface DenunciaPayload {
  titulo: string;
  descricao: string | null;
  endereco: string;
  status: DenunciaStatus;
  data: string;
}

export async function fetchDenuncias(
  filters: DenunciaFilters = {},
): Promise<Paginated<Denuncia>> {
  return apiFetch<Paginated<Denuncia>>(`/denuncias${qs({ ...filters })}`);
}

export async function fetchDenuncia(id: number | string): Promise<Denuncia> {
  return apiFetch<Denuncia>(`/denuncias/${id}`);
}

export async function criarDenuncia(payload: DenunciaPayload): Promise<Denuncia> {
  return apiFetch<Denuncia>("/denuncias", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function atualizarDenuncia(
  id: number,
  payload: Partial<DenunciaPayload>,
): Promise<Denuncia> {
  return apiFetch<Denuncia>(`/denuncias/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export async function excluirDenuncia(id: number): Promise<void> {
  return apiFetch<void>(`/denuncias/${id}`, { method: "DELETE" });
}

export function exportDenunciasUrl(
  filters: Pick<DenunciaFilters, "status" | "busca" | "periodo"> = {},
): string {
  return `${PUBLIC_API_URL}/api/denuncias/export${qs({ ...filters })}`;
}

// ---------------------------------------------------------------------------
// Usuários
// ---------------------------------------------------------------------------

export interface UsuarioPayload {
  name: string;
  email: string;
  role: UsuarioRole;
  password?: string;
}

export async function fetchUsuarios(
  filters: { busca?: string; page?: number; per_page?: number } = {},
): Promise<Paginated<Usuario>> {
  return apiFetch<Paginated<Usuario>>(`/usuarios${qs({ ...filters })}`);
}

export async function fetchUsuario(id: number | string): Promise<Usuario> {
  return apiFetch<Usuario>(`/usuarios/${id}`);
}

export async function criarUsuario(payload: UsuarioPayload): Promise<Usuario> {
  return apiFetch<Usuario>("/usuarios", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export async function atualizarUsuario(
  id: number,
  payload: Partial<UsuarioPayload>,
): Promise<Usuario> {
  return apiFetch<Usuario>(`/usuarios/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export async function excluirUsuario(id: number): Promise<void> {
  return apiFetch<void>(`/usuarios/${id}`, { method: "DELETE" });
}

// ---------------------------------------------------------------------------
// Configurações
// ---------------------------------------------------------------------------

export async function fetchConfiguracoes(): Promise<Configuracoes> {
  return apiFetch<Configuracoes>("/configuracoes");
}

export async function salvarConfiguracoes(
  payload: Configuracoes,
): Promise<Configuracoes> {
  return apiFetch<Configuracoes>("/configuracoes", {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

// ---------------------------------------------------------------------------
// Helpers de apresentação
// ---------------------------------------------------------------------------

export function formatarData(isoDate: string): string {
  const [ano, mes, dia] = isoDate.slice(0, 10).split("-");
  return `${dia}/${mes}/${ano}`;
}

export const STATUS_LABELS: Record<DenunciaStatus, string> = {
  pendente: "Pendente",
  em_andamento: "Em Andamento",
  resolvido: "Resolvido",
  arquivado: "Arquivado",
};

export const PERIODO_LABELS: Record<Periodo, string> = {
  semana: "Esta semana",
  mes: "Este mês",
  todos: "Todo o período",
};

export const ROLE_LABELS: Record<UsuarioRole, string> = {
  admin: "Administrador",
  operador: "Operador",
};
