import { STATUS_LABELS, type DenunciaStatus } from "@/lib/api";

const styles: Record<DenunciaStatus, string> = {
  pendente: "bg-[#ffc107] text-[#212529]",
  em_andamento: "bg-[#0d6efd] text-white",
  resolvido: "bg-[#198754] text-white",
  arquivado: "bg-[#6c757d] text-white",
};

export default function StatusBadge({ status }: { status: DenunciaStatus }) {
  return (
    <span
      className={`inline-block rounded-md px-2.5 py-1 text-xs font-semibold ${styles[status]}`}
    >
      {STATUS_LABELS[status]}
    </span>
  );
}
