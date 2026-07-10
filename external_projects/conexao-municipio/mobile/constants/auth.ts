// Credenciais fixas da aplicação (sem backend de autenticação por enquanto)
export const HARDCODED_USER = "admin";
export const HARDCODED_PASSWORD = "admin";

export function validarCredenciais(usuario: string, senha: string): boolean {
  return (
    usuario.trim().toLowerCase() === HARDCODED_USER &&
    senha === HARDCODED_PASSWORD
  );
}
