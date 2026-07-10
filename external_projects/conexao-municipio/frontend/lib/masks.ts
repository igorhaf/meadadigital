/** Aplica progressivamente a máscara (00) 0000-0000 / (00) 00000-0000. */
export function mascaraTelefone(valor: string): string {
  const digitos = valor.replace(/\D/g, "").slice(0, 11);

  if (digitos.length === 0) return "";
  if (digitos.length <= 2) return `(${digitos}`;
  if (digitos.length <= 6) return `(${digitos.slice(0, 2)}) ${digitos.slice(2)}`;
  if (digitos.length <= 10) {
    return `(${digitos.slice(0, 2)}) ${digitos.slice(2, 6)}-${digitos.slice(6)}`;
  }
  return `(${digitos.slice(0, 2)}) ${digitos.slice(2, 7)}-${digitos.slice(7)}`;
}

/** Padrão HTML para telefone fixo ou celular: (00) 0000-0000 ou (00) 00000-0000. */
export const TELEFONE_PATTERN = "^\\(\\d{2}\\) \\d{4,5}-\\d{4}$";

export const TELEFONE_TITLE =
  "Informe no formato (00) 0000-0000 ou (00) 00000-0000";
