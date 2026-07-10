import Link from "next/link";

interface PaginationProps {
  currentPage: number;
  lastPage: number;
  total: number;
  from: number | null;
  to: number | null;
  basePath: string;
  params: Record<string, string | undefined>;
}

function pageHref(
  basePath: string,
  params: Record<string, string | undefined>,
  page: number,
): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value) search.set(key, value);
  }
  if (page > 1) {
    search.set("page", String(page));
  } else {
    search.delete("page");
  }
  const query = search.toString();
  return query ? `${basePath}?${query}` : basePath;
}

export default function Pagination({
  currentPage,
  lastPage,
  total,
  from,
  to,
  basePath,
  params,
}: PaginationProps) {
  if (lastPage <= 1) {
    return (
      <p className="mt-4 text-sm text-gray-600">
        {total === 0
          ? "Nenhum registro encontrado."
          : `Exibindo ${from ?? 0}–${to ?? 0} de ${total} registro(s).`}
      </p>
    );
  }

  const linkBase =
    "rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm shadow-sm hover:bg-gray-50";
  const disabled =
    "cursor-not-allowed rounded-md border border-gray-200 bg-gray-100 px-3 py-1.5 text-sm text-gray-400";

  return (
    <div className="mt-4 flex items-center justify-between">
      <p className="text-sm text-gray-600">
        Exibindo {from ?? 0}–{to ?? 0} de {total} registro(s)
      </p>
      <div className="flex items-center gap-2">
        {currentPage > 1 ? (
          <Link href={pageHref(basePath, params, currentPage - 1)} className={linkBase}>
            Anterior
          </Link>
        ) : (
          <span className={disabled}>Anterior</span>
        )}
        <span className="text-sm text-gray-600">
          Página {currentPage} de {lastPage}
        </span>
        {currentPage < lastPage ? (
          <Link href={pageHref(basePath, params, currentPage + 1)} className={linkBase}>
            Próxima
          </Link>
        ) : (
          <span className={disabled}>Próxima</span>
        )}
      </div>
    </div>
  );
}
