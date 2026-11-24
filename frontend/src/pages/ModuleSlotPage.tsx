import { Layers } from 'lucide-react';

export function ModuleSlotPage({
  title,
  owner,
  description,
}: {
  title: string;
  owner: string;
  description: string;
}) {
  return (
    <div className="rounded-2xl border border-dashed border-slate-800 bg-slate-900/40 p-10 text-center">
      <Layers className="mx-auto mb-3 h-8 w-8 text-emerald-400" aria-hidden="true" />
      <h2 className="text-xl font-bold tracking-tight text-white">{title}</h2>
      <p className="mx-auto mt-2 max-w-[60ch] text-sm leading-relaxed text-slate-400">
        {description}
      </p>
      <span className="mt-4 inline-block rounded-full border border-slate-700 bg-slate-800 px-3 py-1 font-mono text-xs text-slate-300">
        Mounted in AppShell • Owned by {owner}
      </span>
    </div>
  );
}
