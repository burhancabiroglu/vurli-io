import { Alert, AlertDescription } from "@/components/ui/alert";

/** A form message announced to screen readers as it appears. */
export function FormAlert({ message, tone = "error" }: { message: string; tone?: "error" | "info" }) {
  return (
    <Alert variant={tone === "error" ? "destructive" : "default"} aria-live="polite">
      <AlertDescription>{message}</AlertDescription>
    </Alert>
  );
}
