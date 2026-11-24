import { useState, type FormEvent, type KeyboardEvent } from 'react';
import { SendHorizontal } from 'lucide-react';
import { cn } from '../../lib/format';

export const MAX_MESSAGE_LENGTH = 2_000;
const COUNTER_THRESHOLD = 1_800;

interface Props {
  disabled?: boolean;
  placeholder: string;
  onSend: (content: string) => void;
  onTyping: () => void;
}

/** Enter sends, Shift+Enter adds a line. Mirrors the backend 2,000-character limit. */
export function MessageComposer({ disabled, placeholder, onSend, onTyping }: Props) {
  const [draft, setDraft] = useState('');
  const length = draft.length;
  const tooLong = length > MAX_MESSAGE_LENGTH;
  const canSend = !disabled && draft.trim().length > 0 && !tooLong;

  const submit = (event?: FormEvent) => {
    event?.preventDefault();
    if (!canSend) return;
    onSend(draft);
    setDraft('');
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      submit();
      event.preventDefault();
    }
  };

  return (
    <form onSubmit={submit} className="border-t border-slate-800 bg-slate-900/60 p-3">
      <div className="flex items-end gap-2">
        <label htmlFor="chat-composer" className="sr-only">
          Message
        </label>
        <textarea
          id="chat-composer"
          rows={1}
          value={draft}
          disabled={disabled}
          placeholder={placeholder}
          onChange={(event) => {
            setDraft(event.target.value);
            if (event.target.value.length > 0) onTyping();
          }}
          onKeyDown={onKeyDown}
          className="max-h-40 min-h-[40px] min-w-0 flex-1 resize-none rounded-xl border border-slate-700 bg-slate-950 px-3.5 py-2.5 text-sm text-slate-100 placeholder:text-slate-500 focus:border-emerald-500/60 focus:outline-none focus:ring-2 focus:ring-emerald-500/20 disabled:cursor-not-allowed disabled:opacity-50"
        />
        <button
          type="submit"
          disabled={!canSend}
          aria-label="Send message"
          className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-600 text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          <SendHorizontal className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
      {length >= COUNTER_THRESHOLD && (
        <p className={cn('mt-1.5 text-right text-[11px] tabular-nums', tooLong ? 'text-rose-400' : 'text-slate-500')}>
          {new Intl.NumberFormat('en-US').format(length)} / {new Intl.NumberFormat('en-US').format(MAX_MESSAGE_LENGTH)}
        </p>
      )}
    </form>
  );
}
