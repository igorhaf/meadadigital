"use client";

export default function Error({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-[#f8f9fa] px-6">
      <div className="w-full max-w-md rounded-lg border border-gray-200 bg-white p-8 text-center shadow-sm">
        <h2 className="text-xl font-bold text-[#212529]">
          Não foi possível carregar o dashboard
        </h2>
        <p className="mt-2 text-sm text-gray-600">
          A API pode estar iniciando ou temporariamente indisponível. Tente
          novamente em alguns instantes.
        </p>
        <button
          type="button"
          onClick={() => reset()}
          className="mt-6 rounded-md bg-[#14613c] px-5 py-2 text-sm font-semibold text-white hover:bg-[#1e7a4d]"
        >
          Tentar novamente
        </button>
      </div>
    </div>
  );
}
