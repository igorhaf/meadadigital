import Link from "next/link";
import { notFound } from "next/navigation";
import DenunciaForm from "@/components/DenunciaForm";
import { ArrowLeftIcon } from "@/components/icons";
import { ApiError, fetchDenuncia, type Denuncia } from "@/lib/api";

export const metadata = { title: "Editar Denúncia — Conexão Município" };

export default async function EditarDenunciaPage({
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
      <header className="flex items-center gap-4">
        <Link
          href={`/denuncias/${denuncia.id}`}
          aria-label="Voltar para o detalhe"
          className="flex h-9 w-9 items-center justify-center rounded-md border border-gray-300 bg-white shadow-sm hover:bg-gray-50"
        >
          <ArrowLeftIcon width={18} height={18} />
        </Link>
        <h2 className="text-[28px] font-bold">Editar Denúncia #{denuncia.id}</h2>
      </header>

      <DenunciaForm denuncia={denuncia} />
    </>
  );
}
