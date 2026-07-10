import type { ReactNode } from "react";

interface StatCardProps {
  label: string;
  value: number;
  className: string;
  icon: ReactNode;
}

export default function StatCard({
  label,
  value,
  className,
  icon,
}: StatCardProps) {
  return (
    <div
      className={`flex items-center justify-between rounded-lg p-5 text-white shadow-sm ${className}`}
    >
      <div>
        <p className="text-[15px] font-semibold">{label}</p>
        <p className="mt-1 text-[32px] font-bold leading-none">{value}</p>
      </div>
      <div className="text-white/75">{icon}</div>
    </div>
  );
}
