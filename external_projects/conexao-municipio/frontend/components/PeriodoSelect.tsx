"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { CalendarIcon, CaretDownIcon } from "@/components/icons";
import { PERIODO_LABELS, type Periodo } from "@/lib/api";

export default function PeriodoSelect({ value }: { value: Periodo }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  function onChange(periodo: string) {
    const params = new URLSearchParams(searchParams);
    if (periodo === "todos") {
      params.delete("periodo");
    } else {
      params.set("periodo", periodo);
    }
    params.delete("page");
    const query = params.toString();
    router.push(query ? `${pathname}?${query}` : pathname);
  }

  return (
    <label className="relative flex cursor-pointer items-center gap-2 rounded-md border border-gray-300 bg-white px-3.5 py-1.5 text-sm text-[#212529] shadow-sm hover:bg-gray-50">
      <CalendarIcon width={16} height={16} />
      {PERIODO_LABELS[value]}
      <CaretDownIcon width={14} height={14} />
      <select
        aria-label="Filtrar por período"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="absolute inset-0 cursor-pointer opacity-0"
      >
        {Object.entries(PERIODO_LABELS).map(([periodo, label]) => (
          <option key={periodo} value={periodo}>
            {label}
          </option>
        ))}
      </select>
    </label>
  );
}
