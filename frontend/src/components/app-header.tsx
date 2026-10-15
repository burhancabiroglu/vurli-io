import Link from "next/link";

import { logoutAction } from "@/app/actions/auth";
import { Button } from "@/components/ui/button";
import { format, getDictionary } from "@/lib/i18n";
import type { CurrentUser } from "@/lib/session";

export async function AppHeader({ user }: { user: CurrentUser }) {
  const { t } = await getDictionary();
  return (
    <header className="flex flex-wrap items-center justify-between gap-4">
      <Link href="/" className="text-xl font-semibold tracking-tight">
        {t.common.brand}
      </Link>
      <div className="flex items-center gap-3 text-sm text-muted-foreground">
        <span className="hidden sm:inline">{format(t.dashboard.signedInAs, { email: user.email })}</span>
        <form action={logoutAction}>
          <Button type="submit" variant="outline" size="sm">
            {t.dashboard.logout}
          </Button>
        </form>
      </div>
    </header>
  );
}
