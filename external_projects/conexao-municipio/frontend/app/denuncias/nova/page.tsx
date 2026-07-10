import Link from "next/link";
import DenunciaForm from "@/components/DenunciaForm";
import { ArrowLeftIcon } from "@/components/icons";

export const metadata = { title: "Nova Denúncia — Conexão Município" };

export default function NovaDenunciaPage() {
  return (
    <>
      <header className="flex items-center gap-4">
        <Link
          href="/denuncias"
          aria-label="Voltar para denúncias"
          className="flex h-9 w-9 items-center justify-center rounded-md border border-gray-300 bg-white shadow-sm hover:bg-gray-50"
        >
          <ArrowLeftIcon width={18} height={18} />
        </Link>
        <h2 className="text-[28px] font-bold">Nova Denúncia</h2>
      </header>

      <DenunciaForm />
    </>
  );
}
