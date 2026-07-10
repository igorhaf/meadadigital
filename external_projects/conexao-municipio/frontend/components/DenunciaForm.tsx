"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import {
  ApiError,
  atualizarDenuncia,
  criarDenuncia,
  STATUS_LABELS,
  type Denuncia,
  type DenunciaStatus,
} from "@/lib/api";

const inputClass =
  "w-full rounded-md border border-gray-300 bg-white px-3 py-2 text-[15px] shadow-sm focus:border-[#14613c] focus:outline-none focus:ring-1 focus:ring-[#14613c]";

function hojeLocal(): string {
  const agora = new Date();
  const mes = String(agora.getMonth() + 1).padStart(2, "0");
  const dia = String(agora.getDate()).padStart(2, "0");
  return `${agora.getFullYear()}-${mes}-${dia}`;
}

export default function DenunciaForm({ denuncia }: { denuncia?: Denuncia }) {
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
    const payload = {
      titulo: String(form.get("titulo") ?? ""),
      descricao: String(form.get("descricao") ?? "") || null,
      endereco: String(form.get("endereco") ?? ""),
      status: String(form.get("status") ?? "pendente") as DenunciaStatus,
      data: String(form.get("data") ?? ""),
    };

    try {
      const salva = denuncia
        ? await atualizarDenuncia(denuncia.id, payload)
        : await criarDenuncia(payload);
      router.push(`/denuncias/${salva.id}`);
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
      className="mt-6 max-w-2xl rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
    >
      {erro && (
        <p className="mb-4 rounded-md border border-[#f5c2c7] bg-[#f8d7da] px-4 py-3 text-sm text-[#842029]">
          {erro}
        </p>
      )}

      <div className="flex flex-col gap-4">
        <label className="block">
          <span className="mb-1 block text-sm font-semibold">Título *</span>
          <input
            name="titulo"
            required
            minLength={3}
            maxLength={255}
            defaultValue={denuncia?.titulo}
            className={inputClass}
            placeholder="Ex.: Buraco na rua"
            title="Informe um título com pelo menos 3 caracteres"
          />
          {erros.titulo && (
            <span className="mt-1 block text-xs text-[#dc3545]">{erros.titulo[0]}</span>
          )}
        </label>

        <label className="block">
          <span className="mb-1 block text-sm font-semibold">Descrição</span>
          <textarea
            name="descricao"
            rows={4}
            defaultValue={denuncia?.descricao ?? ""}
            className={inputClass}
            placeholder="Detalhes da ocorrência…"
          />
        </label>

        <label className="block">
          <span className="mb-1 block text-sm font-semibold">Endereço *</span>
          <input
            name="endereco"
            required
            minLength={3}
            maxLength={255}
            defaultValue={denuncia?.endereco}
            className={inputClass}
            placeholder="Ex.: Rua das Flores, 123 — Centro"
            title="Informe um endereço com pelo menos 3 caracteres"
          />
          {erros.endereco && (
            <span className="mt-1 block text-xs text-[#dc3545]">{erros.endereco[0]}</span>
          )}
        </label>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1 block text-sm font-semibold">Data *</span>
            <input
              type="date"
              name="data"
              required
              max={hojeLocal()}
              defaultValue={denuncia?.data?.slice(0, 10)}
              className={inputClass}
              title="A data da ocorrência não pode ser futura"
            />
            {erros.data && (
              <span className="mt-1 block text-xs text-[#dc3545]">{erros.data[0]}</span>
            )}
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-semibold">Status *</span>
            <select
              name="status"
              defaultValue={denuncia?.status ?? "pendente"}
              className={inputClass}
            >
              {Object.entries(STATUS_LABELS).map(([valor, label]) => (
                <option key={valor} value={valor}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

      <div className="mt-6 flex items-center gap-3">
        <button
          type="submit"
          disabled={salvando}
          className="rounded-md bg-[#14613c] px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-[#1e7a4d] disabled:opacity-60"
        >
          {salvando ? "Salvando…" : denuncia ? "Salvar alterações" : "Cadastrar denúncia"}
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
