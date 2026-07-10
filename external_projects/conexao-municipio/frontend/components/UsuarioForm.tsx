"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import {
  ApiError,
  atualizarUsuario,
  criarUsuario,
  ROLE_LABELS,
  type Usuario,
  type UsuarioRole,
} from "@/lib/api";

const inputClass =
  "w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-[15px] shadow-sm focus:border-[#14613c] focus:outline-none focus:ring-1 focus:ring-[#14613c]";

export default function UsuarioForm({ usuario }: { usuario?: Usuario }) {
  const router = useRouter();
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [erros, setErros] = useState<Record<string, string[]>>({});

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSalvando(true);
    setErro(null);
    setErros({});

    const form = new FormData(event.currentTarget);
    const password = String(form.get("password") ?? "");
    const payload = {
      name: String(form.get("name") ?? ""),
      email: String(form.get("email") ?? ""),
      role: String(form.get("role") ?? "operador") as UsuarioRole,
      // na edição, senha em branco significa "manter a atual"
      ...(password ? { password } : {}),
    };

    try {
      if (usuario) {
        await atualizarUsuario(usuario.id, payload);
      } else {
        await criarUsuario(payload as Required<typeof payload>);
      }
      router.push("/usuarios");
      router.refresh();
    } catch (err) {
      if (err instanceof ApiError) {
        setErro(err.message);
        setErros(err.errors ?? {});
      } else {
        setErro(err instanceof Error ? err.message : "Erro ao salvar.");
      }
      setSalvando(false);
    }
  }

  return (
    <form
      onSubmit={onSubmit}
      className="mt-6 max-w-xl rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
    >
      {erro && (
        <p className="mb-4 rounded-md border border-[#f5c2c7] bg-[#f8d7da] px-4 py-3 text-sm text-[#842029]">
          {erro}
        </p>
      )}

      <div className="flex flex-col gap-4">
        <label className="block">
          <span className="mb-1 block text-sm font-semibold">Nome *</span>
          <input
            name="name"
            required
            minLength={3}
            maxLength={255}
            defaultValue={usuario?.name}
            className={inputClass}
            placeholder="Nome completo"
            title="Informe o nome com pelo menos 3 caracteres"
          />
          {erros.name && (
            <span className="mt-1 block text-xs text-[#dc3545]">{erros.name[0]}</span>
          )}
        </label>

        <label className="block">
          <span className="mb-1 block text-sm font-semibold">E-mail *</span>
          <input
            type="email"
            name="email"
            required
            maxLength={255}
            defaultValue={usuario?.email}
            className={inputClass}
            placeholder="usuario@prefeitura.gov.br"
            title="Informe um e-mail válido"
          />
          {erros.email && (
            <span className="mt-1 block text-xs text-[#dc3545]">{erros.email[0]}</span>
          )}
        </label>

        <label className="block">
          <span className="mb-1 block text-sm font-semibold">Perfil *</span>
          <select
            name="role"
            defaultValue={usuario?.role ?? "operador"}
            className={inputClass}
          >
            {Object.entries(ROLE_LABELS).map(([valor, label]) => (
              <option key={valor} value={valor}>
                {label}
              </option>
            ))}
          </select>
        </label>

        <label className="block">
          <span className="mb-1 block text-sm font-semibold">
            {usuario ? "Nova senha" : "Senha *"}
          </span>
          <input
            type="password"
            name="password"
            required={!usuario}
            minLength={8}
            maxLength={72}
            title="A senha deve ter pelo menos 8 caracteres"
            className={inputClass}
            placeholder={
              usuario ? "Deixe em branco para manter a senha atual" : "Mínimo de 8 caracteres"
            }
          />
          {erros.password && (
            <span className="mt-1 block text-xs text-[#dc3545]">{erros.password[0]}</span>
          )}
        </label>
      </div>

      <div className="mt-6 flex items-center gap-3">
        <button
          type="submit"
          disabled={salvando}
          className="rounded-md bg-[#14613c] px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-[#1e7a4d] disabled:opacity-60"
        >
          {salvando ? "Salvando…" : usuario ? "Salvar alterações" : "Cadastrar usuário"}
        </button>
        <button
          type="button"
          onClick={() => router.back()}
          className="rounded-md border border-gray-300 bg-white px-4 py-2 text-sm shadow-sm hover:bg-gray-50"
        >
          Cancelar
        </button>
      </div>
    </form>
  );
}
