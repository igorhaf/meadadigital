import Link from "next/link";
import { notFound } from "next/navigation";
import DenunciaAcoes from "@/components/DenunciaAcoes";
import StatusBadge from "@/components/StatusBadge";
import { ArrowLeftIcon } from "@/components/icons";
import { ApiError, fetchDenuncia, formatarData, type Denuncia } from "@/lib/api";

export const metadata = { title: "Detalhe da Denúncia — Conexão Município" };

export default async function DenunciaDetalhePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  let denuncia: Denuncia;
  try {
    denuncia = await fetchDenuncia(id);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      notFound();
    }
    throw err;
  }

  return (
    <>
      <header className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link
            href="/denuncias"
            aria-label="Voltar para denúncias"
            className="flex h-9 w-9 items-center justify-center rounded-md border border-gray-300 bg-white shadow-sm hover:bg-gray-50"
          >
            <ArrowLeftIcon width={18} height={18} />
          </Link>
          <h2 className="text-[28px] font-bold">Denúncia #{denuncia.id}</h2>
          <StatusBadge status={denuncia.status} />
        </div>
        <DenunciaAcoes id={denuncia.id} status={denuncia.status} />
      </header>

      <div className="mt-6 max-w-3xl rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h3 className="text-xl font-bold">{denuncia.titulo}</h3>

        <dl className="mt-5 grid grid-cols-1 gap-x-8 gap-y-4 sm:grid-cols-2">
          <div>
            <dt className="text-sm font-semibold text-gray-600">Endereço</dt>
            <dd className="mt-0.5 text-[15px]">{denuncia.endereco}</dd>
          </div>
          <div>
            <dt className="text-sm font-semibold text-gray-600">Data da ocorrência</dt>
            <dd className="mt-0.5 text-[15px]">{formatarData(denuncia.data)}</dd>
          </div>
          <div className="sm:col-span-2">
            <dt className="text-sm font-semibold text-gray-600">Descrição</dt>
            <dd className="mt-0.5 whitespace-pre-line text-[15px]">
              {denuncia.descricao || "Sem descrição."}
            </dd>
          </div>
        </dl>
      </div>
    </>
  );
}
