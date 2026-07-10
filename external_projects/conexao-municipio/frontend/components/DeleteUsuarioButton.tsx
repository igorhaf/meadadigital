"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { TrashIcon } from "@/components/icons";
import { excluirUsuario } from "@/lib/api";

export default function DeleteUsuarioButton({
  id,
  nome,
  redirectTo,
}: {
  id: number;
  nome: string;
  /** Quando definido, navega para essa rota após excluir (ex.: página de detalhe). */
  redirectTo?: string;
}) {
  const router = useRouter();
  const [excluindo, setExcluindo] = useState(false);

  async function onDelete() {
    if (!window.confirm(`Excluir o usuário "${nome}"?`)) return;

    setExcluindo(true);
    try {
      await excluirUsuario(id);
      if (redirectTo) {
        router.push(redirectTo);
      }
      router.refresh();
    } catch (err) {
      window.alert(err instanceof Error ? err.message : "Erro ao excluir.");
      setExcluindo(false);
    }
  }

  return (
    <button
      type="button"
      onClick={onDelete}
      disabled={excluindo}
      className="flex items-center gap-2 rounded-md bg-[#dc3545] px-3.5 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-[#bb2d3b] disabled:opacity-60"
    >
      <TrashIcon width={16} height={16} />
      {excluindo ? "Excluindo…" : "Excluir"}
    </button>
  );
}
