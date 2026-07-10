import Link from "next/link";
import UsuarioForm from "@/components/UsuarioForm";
import { ArrowLeftIcon } from "@/components/icons";

export const metadata = { title: "Novo Usuário — Conexão Município" };

export default function NovoUsuarioPage() {
  return (
    <>
      <header className="flex items-center gap-4">
        <Link
          href="/usuarios"
          aria-label="Voltar para usuários"
          className="flex h-9 w-9 items-center justify-center rounded-md border border-gray-300 bg-white shadow-sm hover:bg-gray-50"
        >
          <ArrowLeftIcon width={18} height={18} />
        </Link>
        <h2 className="text-[28px] font-bold">Novo Usuário</h2>
      </header>

      <UsuarioForm />
    </>
  );
}
