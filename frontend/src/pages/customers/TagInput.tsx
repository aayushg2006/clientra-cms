import { useState } from "react";
import { Pill } from "@/components/ui";
import { tagTone } from "@/lib/status";
import type { TagDto } from "@/lib/types";

/** Chip input: existing tags are suggested, new names are created on save. */
export function TagInput({ value, onChange, options }: { value: string[]; onChange: (v: string[]) => void; options: TagDto[] }) {
  const [text, setText] = useState("");
  const [focused, setFocused] = useState(false);
  const colorOf = (name: string) => options.find((o) => o.name.toLowerCase() === name.toLowerCase())?.color;

  const add = (name: string) => {
    const clean = name.trim();
    if (clean && !value.some((v) => v.toLowerCase() === clean.toLowerCase())) onChange([...value, clean]);
    setText("");
  };

  const suggestions = options.filter((o) => !value.includes(o.name) && o.name.toLowerCase().includes(text.toLowerCase())).slice(0, 6);

  return (
    <div className="relative">
      <div className="flex min-h-[44px] flex-wrap items-center gap-1.5 rounded-xl border border-sand-300 bg-white px-3 py-2 focus-within:border-teal-600 focus-within:ring-2 focus-within:ring-teal-600/15">
        {value.map((t) => (
          <Pill key={t} tone={tagTone(colorOf(t))} className="gap-1">
            {t}
            <button type="button" onClick={() => onChange(value.filter((v) => v !== t))} className="ml-0.5 opacity-60 hover:opacity-100" aria-label={`Remove ${t}`}>
              ×
            </button>
          </Pill>
        ))}
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          onFocus={() => setFocused(true)}
          onBlur={() => setTimeout(() => setFocused(false), 150)}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === ",") {
              e.preventDefault();
              add(text);
            } else if (e.key === "Backspace" && !text && value.length) {
              onChange(value.slice(0, -1));
            }
          }}
          placeholder="+ add tag"
          className="min-w-[90px] flex-1 bg-transparent text-sm text-ink-700 placeholder:text-ink-500 focus:outline-none"
        />
      </div>
      {focused && (suggestions.length > 0 || text) && (
        <div className="absolute z-10 mt-1 w-56 overflow-hidden rounded-xl border border-sand-300 bg-white py-1 shadow-pop">
          {suggestions.map((s) => (
            <button type="button" key={s.id} onMouseDown={(e) => e.preventDefault()} onClick={() => add(s.name)} className="flex w-full px-3 py-1.5 text-left text-sm hover:bg-sand-50">
              <Pill tone={tagTone(s.color)}>{s.name}</Pill>
            </button>
          ))}
          {text && !options.some((o) => o.name.toLowerCase() === text.trim().toLowerCase()) && (
            <button type="button" onMouseDown={(e) => e.preventDefault()} onClick={() => add(text)} className="w-full px-3 py-1.5 text-left text-sm text-teal-700 hover:bg-sand-50">
              Create “{text.trim()}”
            </button>
          )}
        </div>
      )}
    </div>
  );
}
