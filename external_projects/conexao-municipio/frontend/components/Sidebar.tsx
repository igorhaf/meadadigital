"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  AlertTriangleIcon,
  GearIcon,
  HomeIcon,
  PersonIcon,
  UsersIcon,
} from "@/components/icons";

const navItems = [
  { label: "Dashboard", icon: HomeIcon, href: "/" },
  { label: "Denúncias", icon: AlertTriangleIcon, href: "/denuncias" },
  { label: "Usuários", icon: UsersIcon, href: "/usuarios" },
  { label: "Configurações", icon: GearIcon, href: "/configuracoes" },
];

export default function Sidebar() {
  const pathname = usePathname();

  const isActive = (href: string) =>
    href === "/" ? pathname === "/" : pathname.startsWith(href);

  return (
    <aside className="flex w-[270px] shrink-0 flex-col bg-[#14613c] text-white">
      <div className="px-6 pb-5 pt-7">
        <h1 className="text-xl font-bold">Conexão Município</h1>
        <p className="mt-1 text-sm text-white/90">Painel Administrativo</p>
      </div>

      <nav className="mt-2 flex flex-col gap-1 px-3">
        {navItems.map(({ label, icon: Icon, href }) => (
          <Link
            key={href}
            href={href}
            className={`flex items-center gap-3 rounded-md px-4 py-2.5 text-[15px] transition-colors ${
              isActive(href) ? "bg-[#1e7a4d]" : "hover:bg-white/10"
            }`}
          >
            <Icon width={18} height={18} />
            {label}
          </Link>
        ))}
      </nav>

      <hr className="mx-3 mt-5 border-white/25" />

      <div className="flex items-center gap-3 px-6 py-5">
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-white text-[#2563eb]">
          <PersonIcon width={16} height={16} />
        </span>
        <div className="text-sm leading-tight">
          <p>Administrador</p>
          <p className="mt-0.5 text-[13px] text-white/90">
            admin@prefeitura.gov.br
          </p>
        </div>
      </div>
    </aside>
  );
}
