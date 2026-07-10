import type { ColorValue } from "react-native";

export const cores = {
  primaria: "#0F8A3F",
  primariaEscura: "#0B6E32",
  azul: "#2196F3",
  azulEscuro: "#1976D2",
  amarelo: "#FFC107",
  vermelho: "#E53935",
  vermelhoClaro: "#FDECEA",
  texto: "#212529",
  textoSuave: "#6B7280",
  borda: "#E5E7EB",
  fundo: "#FFFFFF",
  calendarioFundo: "#111111",
};

export type CategoriaDenuncia =
  | "buracos"
  | "iluminacao"
  | "lixo"
  | "esgoto"
  | "transito"
  | "vandalismo"
  | "outros";

export type StatusDenuncia =
  | "pendente"
  | "em_andamento"
  | "resolvido"
  | "arquivado";

export const CATEGORIAS: {
  id: CategoriaDenuncia;
  label: string;
  chip: string;
  chipCor: ColorValue;
  icone: string;
}[] = [
  { id: "buracos", label: "Buracos", chip: "Buraco", chipCor: "#2196F3", icone: "road-variant" },
  { id: "iluminacao", label: "Iluminação", chip: "Lâmpada", chipCor: "#4CAF50", icone: "lightbulb-on" },
  { id: "lixo", label: "Lixo", chip: "Lixo", chipCor: "#FF9800", icone: "trash-can" },
  { id: "esgoto", label: "Esgoto", chip: "Esgoto", chipCor: "#00BCD4", icone: "waves" },
  { id: "transito", label: "Trânsito", chip: "Trânsito", chipCor: "#9C27B0", icone: "traffic-light" },
  { id: "vandalismo", label: "Vandalismo", chip: "Vandal.", chipCor: "#F44336", icone: "spray" },
];

export const CATEGORIA_PADRAO: {
  chip: string;
  chipCor: ColorValue;
} = { chip: "Outros", chipCor: "#607D8B" };

export function categoriaInfo(id: string | null) {
  return CATEGORIAS.find((c) => c.id === id) ?? CATEGORIA_PADRAO;
}

export const STATUS_INFO: Record<StatusDenuncia, { label: string; cor: ColorValue }> = {
  pendente: { label: "EM ANÁLISE", cor: "#FFC107" },
  em_andamento: { label: "EM ANDAMENTO", cor: "#2196F3" },
  resolvido: { label: "RESOLVIDO", cor: "#0F8A3F" },
  arquivado: { label: "ARQUIVADO", cor: "#6C757D" },
};

export const SERVICOS: {
  id: "saude" | "documentos" | "cras" | "licencas";
  label: string;
  icone: string;
  cor: ColorValue;
}[] = [
  { id: "saude", label: "Saúde", icone: "heart-pulse", cor: "#0F8A3F" },
  { id: "documentos", label: "Documentos", icone: "card-account-details", cor: "#1976D2" },
  { id: "cras", label: "CRAS", icone: "handshake", cor: "#FFC107" },
  { id: "licencas", label: "Licenças", icone: "file-document-edit", cor: "#FFC107" },
];

export function formatarData(isoDate: string): string {
  const [ano, mes, dia] = isoDate.slice(0, 10).split("-");
  return `${dia}/${mes}/${ano}`;
}

export function hojeISO(): string {
  const agora = new Date();
  const mes = String(agora.getMonth() + 1).padStart(2, "0");
  const dia = String(agora.getDate()).padStart(2, "0");
  return `${agora.getFullYear()}-${mes}-${dia}`;
}
