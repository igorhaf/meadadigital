import Link from "next/link";
import Pagination from "@/components/Pagination";
import StatusBadge from "@/components/StatusBadge";
import {
  DownloadIcon,
  EyeIcon,
  PlusIcon,
  SearchIcon,
} from "@/components/icons";
import {
  exportDenunciasUrl,
  fetchConfiguracoes,
  fetchDenuncias,
  formatarData,
  STATUS_LABELS,
  type DenunciaStatus,
} from "@/lib/api";

export const metadata = { title: "Denúncias — Conexão Município" };

const STATUS_VALIDOS = Object.keys(STATUS_LABELS) as DenunciaStatus[];

export default async function DenunciasPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string; busca?: string; page?: string }>;
}) {
  const params = await searchParams;
  const status = STATUS_VALIDOS.find((s) => s === params.status);
  const busca = params.busca?.trim() || undefined;
  const page = Math.max(1, Number(params.page) || 1);

  const configuracoes = await fetchConfiguracoes();
  const porPagina =
    Math.min(100, Math.max(5, Number(configuracoes.denuncias_por_pagina))) || 15;

  const denuncias = await fetchDenuncias({
    status,
    busca,
    page,
    per_page: porPagina,
  });

  const filtros: Array<{ label: string; valor?: DenunciaStatus }> = [
    { label: "Todas" },
    ...STATUS_VALIDOS.map((s) => ({ label: STATUS_LABELS[s], valor: s })),
  ];

  const filtroHref = (valor?: DenunciaStatus) => {
    const search = new URLSearchParams();
    if (valor) search.set("status", valor);
    if (busca) search.set("busca", busca);
    const query = search.toString();
    return query ? `/denuncias?${query}` : "/denuncias";
  };

  return (
    <>
      <header className="flex items-center justify-between">
        <h2 className="text-[28px] font-bold">Denúncias</h2>
        <div className="flex items-center gap-2">
          <a
            href={exportDenunciasUrl({ status, busca })}
            className="flex items-center gap-2 rounded-md border border-gray-300 bg-white px-3.5 py-1.5 text-sm text-[#212529] shadow-sm hover:bg-gray-50"
          >
            <DownloadIcon width={16} height={16} />
            Exportar
          </a>
          <Link
            href="/denuncias/nova"
            className="flex items-center gap-2 rounded-md bg-[#14613c] px-3.5 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-[#1e7a4d]"
          >
            <PlusIcon width={16} height={16} />
            Nova Denúncia
          </Link>
        </div>
      </header>

      <div className="mt-6 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-1 rounded-lg border border-gray-200 bg-white p-1 shadow-sm">
          {filtros.map(({ label, valor }) => (
            <Link
              key={label}
              href={filtroHref(valor)}
              className={`rounded-md px-3 py-1.5 text-sm ${
                status === valor
                  ? "bg-[#14613c] font-semibold text-white"
                  : "text-[#212529] hover:bg-gray-100"
              }`}
            >
              {label}
            </Link>
          ))}
        </div>

        <form action="/denuncias" method="get" className="flex items-center gap-2">
          {status && <input type="hidden" name="status" value={status} />}
          <input
            type="search"
            name="busca"
            defaultValue={busca}
            placeholder="Buscar por título ou endereço…"
            className="w-64 rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm shadow-sm focus:border-[#14613c] focus:outline-none focus:ring-1 focus:ring-[#14613c]"
          />
          <button
            type="submit"
            aria-label="Buscar"
            className="flex h-[34px] w-[34px] items-center justify-center rounded-md border border-gray-300 bg-white shadow-sm hover:bg-gray-50"
          >
            <SearchIcon width={16} height={16} />
          </button>
        </form>
      </div>

      <div className="mt-4 overflow-x-auto rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
        <table className="w-full min-w-[760px] text-left text-[15px]">
          <thead>
            <tr className="border-b border-gray-300 text-[#212529]">
              <th className="px-3 py-3 font-bold">ID</th>
              <th className="px-3 py-3 font-bold">Título</th>
              <th className="px-3 py-3 font-bold">Endereço</th>
              <th className="px-3 py-3 font-bold">Data</th>
              <th className="px-3 py-3 font-bold">Status</th>
              <th className="px-3 py-3 font-bold">Ações</th>
            </tr>
          </thead>
          <tbody>
            {denuncias.data.length === 0 && (
              <tr>
                <td colSpan={6} className="px-3 py-8 text-center text-gray-500">
                  Nenhuma denúncia encontrada.
                </td>
              </tr>
            )}
            {denuncias.data.map((denuncia) => (
              <tr key={denuncia.id} className="border-b border-gray-200 last:border-0">
                <td className="px-3 py-3.5">#{denuncia.id}</td>
                <td className="px-3 py-3.5">{denuncia.titulo}</td>
                <td className="px-3 py-3.5">{denuncia.endereco}</td>
                <td className="px-3 py-3.5">{formatarData(denuncia.data)}</td>
                <td className="px-3 py-3.5">
                  <StatusBadge status={denuncia.status} />
                </td>
                <td className="px-3 py-3.5">
                  <Link
                    href={`/denuncias/${denuncia.id}`}
                    aria-label={`Ver denúncia #${denuncia.id}`}
                    className="flex h-8 w-8 items-center justify-center rounded-md bg-[#0d6efd] text-white hover:bg-[#0b5ed7]"
                  >
                    <EyeIcon width={16} height={16} />
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Pagination
        currentPage={denuncias.current_page}
        lastPage={denuncias.last_page}
        total={denuncias.total}
        from={denuncias.from}
        to={denuncias.to}
        basePath="/denuncias"
        params={{ status, busca }}
      />
    </>
  );
}
