// Plain-text emails for the owner — the daily digest (only on days with new / re-translatable sets) and
// the throttled failure notice. Pure, so they're unit-tested alongside the transform.

export interface ReportSet {
  set_id: number;
  set_number: string;
  number_variant: number;
  name: string | null;
  theme: string | null;
  year: number | null;
  notes: string | null;
}

export interface DigestInput {
  runDate: string; // YYYY-MM-DD (UTC)
  inserted: ReportSet[]; // new, named sets (placeholders are only counted)
  revealed: ReportSet[]; // '{?}' placeholders that just got their real name
  notesChanged: ReportSet[];
  placeholders: number; // new '{?}' placeholders — hidden in the app, nothing to do yet
  backlog: number; // sets currently awaiting the local enrichment pass
  upserted: number;
  minifigsFetched: number;
  minifigsPending: number;
  rendersPinned: number;
}

export interface Email {
  subject: string;
  text: string;
}

/** Lines per list before "…and N more" — keeps a big release-wave day readable. */
export const LIST_LIMIT = 50;

const label = (s: ReportSet) => `${s.set_number}-${s.number_variant} ${s.name ?? "(unnamed)"}`;

function list(sets: ReportSet[], describe: (s: ReportSet) => string): string[] {
  const lines = sets.slice(0, LIST_LIMIT).map((s) => `  • ${describe(s)}`);
  if (sets.length > LIST_LIMIT) lines.push(`  …and ${sets.length - LIST_LIMIT} more`);
  return lines;
}

const plural = (n: number, one: string, many = `${one}s`) => `${n} ${n === 1 ? one : many}`;

const describe = (s: ReportSet) => {
  const meta = [s.theme, s.year].filter((x) => x !== null && x !== undefined && x !== "").join(", ");
  return `${label(s)}${meta ? ` (${meta})` : ""}${s.notes ? " — has notes" : ""}`;
};

/** Whether a run leaves the owner anything to do — the digest is only sent when it does. */
export const needsDigest = (d: Pick<DigestInput, "inserted" | "revealed" | "notesChanged">) =>
  d.inserted.length + d.revealed.length + d.notesChanged.length > 0;

export function digestEmail(d: DigestInput): Email {
  const parts: string[] = [];
  if (d.inserted.length) parts.push(plural(d.inserted.length, "new set"));
  if (d.revealed.length) parts.push(`${d.revealed.length} revealed`);
  if (d.notesChanged.length) parts.push(plural(d.notesChanged.length, "changed note"));
  const subject = `BrickWares catalog: ${parts.join(", ")} (backlog ${d.backlog})`;

  const lines: string[] = [`The daily catalog refresh (${d.runDate}) found:`, ""];
  if (d.inserted.length) {
    lines.push(`New sets (${d.inserted.length}):`);
    lines.push(...list(d.inserted, describe));
    lines.push("");
  }
  if (d.revealed.length) {
    lines.push(`Revealed (${d.revealed.length}) — were "{?}" placeholders, now named and visible in the app:`);
    lines.push(...list(d.revealed, describe));
    lines.push("");
  }
  if (d.notesChanged.length) {
    lines.push(`Notes changed (${d.notesChanged.length}) — Vietnamese translation cleared:`);
    lines.push(...list(d.notesChanged, label));
    lines.push("");
  }
  lines.push(
    `Enrichment backlog: ${plural(d.backlog, "set")} awaiting box images / Vietnamese notes.`,
    "Run the local pass from the project folder (PowerShell or bash; it reads the backlog from prod):",
    "  node --env-file=supabase/.env.local scripts/fetch-catalog.mjs --enrich",
    "then run the `Next:` command it prints (it applies the results to prod).",
    "",
    `Also this run: ${plural(d.upserted, "set")} updated, ${plural(d.minifigsFetched, "minifig list")} fetched` +
      (d.minifigsPending ? ` (${d.minifigsPending} still waiting on Rebrickable)` : "") +
      (d.rendersPinned ? `, ${plural(d.rendersPinned, "render")} pinned` : "") +
      (d.placeholders ? `, ${plural(d.placeholders, 'new "{?}" placeholder')} (hidden until named)` : "") + ".",
  );
  return { subject, text: lines.join("\n") };
}

export function errorEmail(e: { when: string; error: string; lastSuccessAt: string | null }): Email {
  return {
    subject: "BrickWares catalog refresh FAILED",
    text: [
      `The catalog refresh at ${e.when} failed:`,
      "",
      `  ${e.error}`,
      "",
      "The cursor was not advanced, so the next run re-covers the same window — a one-off failure (e.g. the",
      "Brickset daily quota, shared with local runs) fixes itself. Last success: " + (e.lastSuccessAt ?? "never") + ".",
      "Run history: select * from public.catalog_refresh_runs order by id desc limit 10;",
      "(At most one of these emails a day.)",
    ].join("\n"),
  };
}
