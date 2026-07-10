import Link from "next/link";
import { notFound } from "next/navigation";
import DeleteUsuarioButton from "@/components/DeleteUsuarioButton";
import { ArrowLeftIcon, PencilIcon, PersonIcon } from "@/components/icons";
import {
  ApiError,
  fetchUsuario,
  formatarData,
  ROLE_LABELS,
  type Usuario,
} from "@/lib/api";

export const metadata = { title: "Detalhe do Usuário — Conexão Município" };

export default async function UsuarioDetalhePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  let usuario: Usuario;
  try {
    usuario = await fetchUsuario(id);
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
            href="/usuarios"
            aria-label="Voltar para usuários"
            className="flex h-9 w-9 items-center justify-center rounded-md border border-gray-300 bg-white shadow-sm hover:bg-gray-50"
          >
            <ArrowLeftIcon width={18} height={18} />
          </Link>
          <h2 className="text-[28px] font-bold">Usuário #{usuario.id}</h2>
          <span
            className={`inline-block rounded-md px-2.5 py-1 text-xs font-semibold ${
              usuario.role === "admin"
                ? "bg-[#14613c] text-white"
                : "bg-gray-200 text-[#212529]"
            }`}
          >
            {ROLE_LABELS[usuario.role] ?? usuario.role}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <Link
            href={`/usuarios/${usuario.id}/editar`}
            className="flex items-center gap-2 rounded-md bg-[#ffc107] px-3.5 py-1.5 text-sm font-semibold text-[#212529] shadow-sm hover:bg-[#e0a800]"
          >
            <PencilIcon width={16} height={16} />
            Editar
          </Link>
          <DeleteUsuarioButton
            id={usuario.id}
            nome={usuario.name}
            redirectTo="/usuarios"
          />
        </div>
      </header>

      <div className="mt-6 max-w-2xl rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <div className="flex items-center gap-4">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-[#14613c] text-white">
            <PersonIcon width={24} height={24} />
          </span>
          <div>
            <h3 className="text-xl font-bold">{usuario.name}</h3>
            <p className="text-sm text-gray-600">{usuario.email}</p>
          </div>
        </div>

        <dl className="mt-6 grid grid-cols-1 gap-x-8 gap-y-4 sm:grid-cols-2">
          <div>
            <dt className="text-sm font-semibold text-gray-600">Perfil</dt>
            <dd className="mt-0.5 text-[15px]">
              {ROLE_LABELS[usuario.role] ?? usuario.role}
            </dd>
          </div>
          <div>
            <dt className="text-sm font-semibold text-gray-600">Criado em</dt>
            <dd className="mt-0.5 text-[15px]">{formatarData(usuario.created_at)}</dd>
          </div>
        </dl>
      </div>
    </>
  );
}
