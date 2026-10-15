import Link from "next/link";

import { getDictionary } from "@/lib/i18n";

export default async function AuthLayout({ children }: LayoutProps<"/">) {
  const { t } = await getDictionary();
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 p-6">
      <Link href="/" className="text-2xl font-semibold tracking-tight">
        {t.common.brand}
      </Link>
      <div className="w-full max-w-sm">{children}</div>
    </main>
  );
}
