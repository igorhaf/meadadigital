import ConfiguracoesForm from "@/components/ConfiguracoesForm";
import { fetchConfiguracoes } from "@/lib/api";

export const metadata = { title: "Configurações — Conexão Município" };

export default async function ConfiguracoesPage() {
  const configuracoes = await fetchConfiguracoes();

  return (
    <>
      <header>
        <h2 className="text-[28px] font-bold">Configurações</h2>
        <p className="mt-1 text-sm text-gray-600">
          Parâmetros gerais do painel administrativo.
        </p>
      </header>

      <ConfiguracoesForm configuracoes={configuracoes} />
    </>
  );
}
