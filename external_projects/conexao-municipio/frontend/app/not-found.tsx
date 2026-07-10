import Link from "next/link";

export default function NotFound() {
  return (
    <div className="flex flex-col items-start gap-4 py-10">
      <h2 className="text-[28px] font-bold">Página não encontrada</h2>
      <p className="text-gray-600">
        O recurso que você procura não existe ou foi removido.
      </p>
      <Link
        href="/"
        className="rounded-md bg-[#14613c] px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-[#1e7a4d]"
      >
        Voltar ao Dashboard
      </Link>
    </div>
  );
}
