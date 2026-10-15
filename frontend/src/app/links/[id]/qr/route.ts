import type { NextRequest } from "next/server";

import { apiFile } from "@/lib/api";
import { isLinkId } from "@/lib/links";

const FORMATS = new Set(["png", "svg"]);

/** Only the file name Spring produces for a QR download, nothing else. */
const DISPOSITION = /^attachment; filename="vurli-[A-Za-z0-9-]{1,64}\.(png|svg)"$/;

/**
 * The QR code of a link, relayed from Spring with the visitor's session. The same URL
 * serves the preview image and the download.
 */
export async function GET(request: NextRequest, ctx: RouteContext<"/links/[id]/qr">) {
  const { id } = await ctx.params;
  const format = request.nextUrl.searchParams.get("format") ?? "png";
  if (!isLinkId(id) || !FORMATS.has(format)) {
    return new Response(null, { status: 404 });
  }

  const result = await apiFile(`/api/v1/links/${id}/qr?format=${format}`);
  if (!result.ok) {
    return new Response(null, { status: result.status === 401 ? 401 : 404 });
  }

  const headers = new Headers({
    "Content-Type": format === "png" ? "image/png" : "image/svg+xml",
    "Cache-Control": "private, no-store",
  });
  if (result.data.disposition && DISPOSITION.test(result.data.disposition)) {
    headers.set("Content-Disposition", result.data.disposition);
  }
  return new Response(result.data.content, { headers });
}
