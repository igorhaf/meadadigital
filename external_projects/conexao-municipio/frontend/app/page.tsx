import Link from "next/link";
import PeriodoSelect from "@/components/PeriodoSelect";
import StatCard from "@/components/StatCard";
import StatusBadge from "@/components/StatusBadge";
import {
  AlertTriangleIcon,
  CheckCircleIcon,
  ClockDashedIcon,
  DownloadIcon,
  EyeIcon,
  GearIcon,
} from "@/components/icons";
import {
  exportDenunciasUrl,
  fetchDashboard,
  formatarData,
  type Periodo,
} from "@/lib/api";

export default async function DashboardPage({
  searchParams,
}: {
  searchParams: Promise<{ periodo?: string }>;
}) {
  const params = await searchParams;
  const periodo: Periodo =
    params.periodo === "semana" || params.periodo === "mes"
      ? params.periodo
      : "todos";

  const dashboard = await fetchDashboard(periodo);

  return (
    <>
      <header className="flex items-center justify-between">
        <h2 className="text-[28px] font-bold">Dashboard</h2>
        <div className="flex items-center gap-2">
          <a
            href={exportDenunciasUrl({ periodo })}
            className="flex items-center gap-2 rounded-md border border-gray-300 bg-white px-3.5 py-1.5 text-sm text-[#212529] shadow-sm hover:bg-gray-50"
          >
            <DownloadIcon width={16} height={16} />
            Exportar
          </a>
          <PeriodoSelect value={periodo} />
        </div>
      </header>

      <section className="mt-7 grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-4">
        <Link href="/denuncias" className="transition-transform hover:scale-[1.02]">
          <StatCard
            label="Total de Denúncias"
            value={dashboard.total}
            className="bg-[#0d6efd]"
            icon={<AlertTriangleIcon width={44} height={44} strokeWidth={1.5} />}
          />
        </Link>
        <Link
          href="/denuncias?status=pendente"
          className="transition-transform hover:scale-[1.02]"
        >
          <StatCard
            label="Pendentes"
            value={dashboard.pendentes}
            className="bg-[#ffc107]"
            icon={<ClockDashedIcon width={44} height={44} strokeWidth={1.5} />}
          />
        </Link>
        <Link
          href="/denuncias?status=em_andamento"
          className="transition-transform hover:scale-[1.02]"
        >
          <StatCard
            label="Em Andamento"
            value={dashboard.em_andamento}
            className="bg-[#0dcaf0]"
            icon={<GearIcon width={44} height={44} strokeWidth={1.5} />}
          />
        </Link>
        <Link
          href="/denuncias?status=resolvido"
          className="transition-transform hover:scale-[1.02]"
        >
          <StatCard
            label="Resolvidas"
            value={dashboard.resolvidas}
            className="bg-[#198754]"
            icon={<CheckCircleIcon width={44} height={44} strokeWidth={1.5} />}
          />
        </Link>
      </section>

      <section className="mt-9">
        <div className="flex items-center justify-between">
          <h3 className="text-xl font-bold">Denúncias Recentes</h3>
          <Link
            href="/denuncias"
            className="text-sm font-semibold text-[#0d6efd] hover:underline"
          >
            Ver todas
          </Link>
        </div>

        <div className="mt-4 overflow-x-auto rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
          <table className="w-full min-w-[720px] text-left text-[15px]">
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
              {dashboard.recentes.map((denuncia) => (
                <tr
                  key={denuncia.id}
                  className="border-b border-gray-200 last:border-0"
                >
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
      </section>
    </>
  );
}
