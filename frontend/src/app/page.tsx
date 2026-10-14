import { Button } from "@/components/ui/button";

// Placeholder until the panel screens arrive (M2 slice 3).
export default function Home() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 p-8 text-center">
      <h1 className="text-4xl font-semibold tracking-tight">Vurli</h1>
      <p className="max-w-md text-muted-foreground">
        Kısa linkler, senin kontrolünde. Panel yakında burada.
      </p>
      <Button disabled>Giriş yap</Button>
    </main>
  );
}
