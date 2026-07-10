import Link from "next/link";
import { notFound } from "next/navigation";
import UsuarioForm from "@/components/UsuarioForm";
import { ArrowLeftIcon } from "@/components/icons";
import { ApiError, fetchUsuario, type Usuario } from "@/lib/api";

export const metadata = { title: "Editar Usuário — Conexão Município" };

export default async function EditarUsuarioPage({
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
      <header className="flex items-center gap-4">
        <Link
          href={`/usuarios/${usuario.id}`}
          aria-label="Voltar para o detalhe"
          className="flex h-9 w-9 items-center justify-center rounded-md border border-gray-300 bg-white shadow-sm hover:bg-gray-50"
        >
          <ArrowLeftIcon width={18} height={18} />
        </Link>
        <h2 className="text-[28px] font-bold">Editar Usuário</h2>
      </header>

      <UsuarioForm usuario={usuario} />
    </>
  );
}
