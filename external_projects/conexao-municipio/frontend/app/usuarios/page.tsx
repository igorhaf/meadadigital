import Link from "next/link";
import Pagination from "@/components/Pagination";
import { EyeIcon, PlusIcon, SearchIcon } from "@/components/icons";
import { fetchUsuarios, formatarData, ROLE_LABELS } from "@/lib/api";

export const metadata = { title: "Usuários — Conexão Município" };

export default async function UsuariosPage({
  searchParams,
}: {
  searchParams: Promise<{ busca?: string; page?: string }>;
}) {
  const params = await searchParams;
  const busca = params.busca?.trim() || undefined;
  const page = Math.max(1, Number(params.page) || 1);

  const usuarios = await fetchUsuarios({ busca, page });

  return (
    <>
      <header className="flex items-center justify-between">
        <h2 className="text-[28px] font-bold">Usuários</h2>
        <Link
          href="/usuarios/novo"
          className="flex items-center gap-2 rounded-md bg-[#14613c] px-3.5 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-[#1e7a4d]"
        >
          <PlusIcon width={16} height={16} />
          Novo Usuário
        </Link>
      </header>

      <form action="/usuarios" method="get" className="mt-6 flex items-center gap-2">
        <input
          type="search"
          name="busca"
          defaultValue={busca}
          placeholder="Buscar por nome ou e-mail…"
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

      <div className="mt-4 overflow-x-auto rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
        <table className="w-full min-w-[680px] text-left text-[15px]">
          <thead>
            <tr className="border-b border-gray-300 text-[#212529]">
              <th className="px-3 py-3 font-bold">ID</th>
              <th className="px-3 py-3 font-bold">Nome</th>
              <th className="px-3 py-3 font-bold">E-mail</th>
              <th className="px-3 py-3 font-bold">Perfil</th>
              <th className="px-3 py-3 font-bold">Criado em</th>
              <th className="px-3 py-3 font-bold">Ações</th>
            </tr>
          </thead>
          <tbody>
            {usuarios.data.length === 0 && (
              <tr>
                <td colSpan={6} className="px-3 py-8 text-center text-gray-500">
                  Nenhum usuário encontrado.
                </td>
              </tr>
            )}
            {usuarios.data.map((usuario) => (
              <tr key={usuario.id} className="border-b border-gray-200 last:border-0">
                <td className="px-3 py-3.5">#{usuario.id}</td>
                <td className="px-3 py-3.5">{usuario.name}</td>
                <td className="px-3 py-3.5">{usuario.email}</td>
                <td className="px-3 py-3.5">
                  <span
                    className={`inline-block rounded-md px-2.5 py-1 text-xs font-semibold ${
                      usuario.role === "admin"
                        ? "bg-[#14613c] text-white"
                        : "bg-gray-200 text-[#212529]"
                    }`}
                  >
                    {ROLE_LABELS[usuario.role] ?? usuario.role}
                  </span>
                </td>
                <td className="px-3 py-3.5">{formatarData(usuario.created_at)}</td>
                <td className="px-3 py-3.5">
                  <Link
                    href={`/usuarios/${usuario.id}`}
                    aria-label={`Ver usuário ${usuario.name}`}
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
        currentPage={usuarios.current_page}
        lastPage={usuarios.last_page}
        total={usuarios.total}
        from={usuarios.from}
        to={usuarios.to}
        basePath="/usuarios"
        params={{ busca }}
      />
    </>
  );
}
