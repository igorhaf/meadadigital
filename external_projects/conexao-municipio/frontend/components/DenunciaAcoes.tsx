"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ArchiveIcon, CheckCircleIcon, PencilIcon } from "@/components/icons";
import { atualizarDenuncia, type DenunciaStatus } from "@/lib/api";

export default function DenunciaAcoes({
  id,
  status,
}: {
  id: number;
  status: DenunciaStatus;
}) {
  const router = useRouter();
  const [alterando, setAlterando] = useState(false);

  async function mudarStatus(novo: DenunciaStatus, confirmacao: string) {
    if (!window.confirm(confirmacao)) return;

    setAlterando(true);
    try {
      await atualizarDenuncia(id, { status: novo });
      router.refresh();
    } catch (err) {
      window.alert(err instanceof Error ? err.message : "Erro ao alterar status.");
    } finally {
      setAlterando(false);
    }
  }

  return (
    <div className="flex items-center gap-2">
      <Link
        href={`/denuncias/${id}/editar`}
        className="flex items-center gap-2 rounded-md bg-[#ffc107] px-3.5 py-1.5 text-sm font-semibold text-[#212529] shadow-sm hover:bg-[#e0a800]"
      >
        <PencilIcon width={16} height={16} />
        Editar
      </Link>

      {status !== "arquivado" && (
        <button
          type="button"
          disabled={alterando}
          onClick={() =>
            mudarStatus("arquivado", `Arquivar a denúncia #${id}?`)
          }
          className="flex items-center gap-2 rounded-md bg-[#6c757d] px-3.5 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-[#5c636a] disabled:opacity-60"
        >
          <ArchiveIcon width={16} height={16} />
          Arquivar
        </button>
      )}

      {status !== "resolvido" && (
        <button
          type="button"
          disabled={alterando}
          onClick={() =>
            mudarStatus("resolvido", `Concluir a denúncia #${id}?`)
          }
          className="flex items-center gap-2 rounded-md bg-[#198754] px-3.5 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-[#157347] disabled:opacity-60"
        >
          <CheckCircleIcon width={16} height={16} />
          Concluir
        </button>
      )}
    </div>
  );
}
