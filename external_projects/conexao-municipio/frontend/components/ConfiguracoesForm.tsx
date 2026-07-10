"use client";

import { useState, type FormEvent } from "react";
import { ApiError, salvarConfiguracoes, type Configuracoes } from "@/lib/api";
import { mascaraTelefone, TELEFONE_PATTERN, TELEFONE_TITLE } from "@/lib/masks";

const inputClass =
  "w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-[15px] shadow-sm focus:border-[#14613c] focus:outline-none focus:ring-1 focus:ring-[#14613c]";

export default function ConfiguracoesForm({
  configuracoes,
}: {
  configuracoes: Configuracoes;
}) {
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [erros, setErros] = useState<Record<string, string[]>>({});
  const [sucesso, setSucesso] = useState(false);
  const [telefone, setTelefone] = useState(
    mascaraTelefone(configuracoes.telefone_contato ?? ""),
  );

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSalvando(true);
    setErro(null);
    setErros({});
    setSucesso(false);

    const form = new FormData(event.currentTarget);
    const payload: Configuracoes = {
      nome_municipio: String(form.get("nome_municipio") ?? ""),
      email_contato: String(form.get("email_contato") ?? ""),
      telefone_contato: String(form.get("telefone_contato") ?? ""),
      notificacoes_email: form.get("notificacoes_email") ? "1" : "0",
      denuncias_por_pagina: String(form.get("denuncias_por_pagina") ?? "15"),
    };

    try {
      await salvarConfiguracoes(payload);
      setSucesso(true);
    } catch (err) {
      if (err instanceof ApiError) {
        setErro(err.message);
        setErros(err.errors ?? {});
      } else {
        setErro(err instanceof Error ? err.message : "Erro ao salvar.");
      }
    } finally {
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
      {sucesso && (
        <p className="mb-4 rounded-md border border-[#badbcc] bg-[#d1e7dd] px-4 py-3 text-sm text-[#0f5132]">
          Configurações salvas com sucesso.
        </p>
      )}

      <div className="flex flex-col gap-4">
        <label className="block">
          <span className="mb-1 block text-sm font-semibold">Nome do município *</span>
          <input
            name="nome_municipio"
            required
            maxLength={255}
            defaultValue={configuracoes.nome_municipio ?? ""}
            className={inputClass}
          />
          {erros.nome_municipio && (
            <span className="mt-1 block text-xs text-[#dc3545]">
              {erros.nome_municipio[0]}
            </span>
          )}
        </label>

        <label className="block">
          <span className="mb-1 block text-sm font-semibold">E-mail de contato *</span>
          <input
            type="email"
            name="email_contato"
            required
            maxLength={255}
            defaultValue={configuracoes.email_contato ?? ""}
            className={inputClass}
          />
          {erros.email_contato && (
            <span className="mt-1 block text-xs text-[#dc3545]">
              {erros.email_contato[0]}
            </span>
          )}
        </label>

        <label className="block">
          <span className="mb-1 block text-sm font-semibold">Telefone de contato</span>
          <input
            type="tel"
            name="telefone_contato"
            maxLength={15}
            value={telefone}
            onChange={(e) => setTelefone(mascaraTelefone(e.target.value))}
            pattern={TELEFONE_PATTERN}
            title={TELEFONE_TITLE}
            className={inputClass}
            placeholder="(00) 0000-0000"
          />
          {erros.telefone_contato && (
            <span className="mt-1 block text-xs text-[#dc3545]">
              {erros.telefone_contato[0]}
            </span>
          )}
        </label>

        <label className="block">
          <span className="mb-1 block text-sm font-semibold">
            Denúncias por página *
          </span>
          <input
            type="number"
            name="denuncias_por_pagina"
            required
            min={5}
            max={100}
            defaultValue={configuracoes.denuncias_por_pagina ?? "15"}
            className={inputClass}
          />
          {erros.denuncias_por_pagina && (
            <span className="mt-1 block text-xs text-[#dc3545]">
              {erros.denuncias_por_pagina[0]}
            </span>
          )}
        </label>

        <label className="flex items-center gap-3">
          <input
            type="checkbox"
            name="notificacoes_email"
            defaultChecked={configuracoes.notificacoes_email === "1"}
            className="h-4 w-4 rounded border-gray-300 accent-[#14613c]"
          />
          <span className="text-sm font-semibold">
            Receber notificações por e-mail
          </span>
        </label>
      </div>

      <div className="mt-6">
        <button
          type="submit"
          disabled={salvando}
          className="rounded-md bg-[#14613c] px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-[#1e7a4d] disabled:opacity-60"
        >
          {salvando ? "Salvando…" : "Salvar configurações"}
        </button>
      </div>
    </form>
  );
}
