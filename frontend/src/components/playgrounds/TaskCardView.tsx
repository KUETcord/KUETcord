import { forwardRef, type HTMLAttributes } from 'react';
import { CalendarClock } from 'lucide-react';
import { Avatar } from '../collab/Avatar';
import { COLUMN_LABELS, PRIORITY_STYLES } from '../../lib/collab/kanban';
import { cn, formatSmartTimestamp } from '../../lib/format';
import { TASK_STATUSES, type TaskCard, type TaskStatus } from '../../types/collab';

interface TaskCardViewProps extends HTMLAttributes<HTMLDivElement> {
  task: TaskCard;
  canEdit: boolean;
  pending?: boolean;
  dragging?: boolean;
  overlay?: boolean;
  onMove?: (status: TaskStatus) => void;
}

/**
 * Presentational Kanban card. The status dropdown is the keyboard/screen-reader path for the same move a
 * drag performs, so both go through one handler.
 */
export const TaskCardView = forwardRef<HTMLDivElement, TaskCardViewProps>(function TaskCardView(
  { task, canEdit, pending, dragging, overlay, onMove, className, ...rest },
  ref,
) {
  const overdue = task.dueDate !== null && task.status !== 'DONE' && new Date(task.dueDate).getTime() < Date.now();

  return (
    <div
      ref={ref}
      data-testid={overlay ? undefined : `task-${task.id}`}
      className={cn(
        'rounded-lg border border-slate-800 bg-slate-900 p-3 transition-[opacity,box-shadow] duration-150 ease-out',
        canEdit && 'cursor-grab',
        dragging && 'opacity-40',
        // DESIGN_GUIDELINES: the lifted card scales slightly and casts an elevated shadow (transform only).
        overlay && 'scale-[1.03] cursor-grabbing border-emerald-500/40 shadow-xl shadow-black/50',
        pending && 'opacity-70',
        className,
      )}
      {...rest}
    >
      <p className="line-clamp-3 break-words text-sm font-medium text-slate-100" title={task.title}>{task.title}</p>
      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        <span className={cn('rounded border px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide', PRIORITY_STYLES[task.priority])}>
          {task.priority}
        </span>
        {task.dueDate && (
          <span
            className={cn('inline-flex items-center gap-1 text-[11px] tabular-nums', overdue ? 'text-rose-300' : 'text-slate-400')}
            title={new Date(task.dueDate).toLocaleString()}
          >
            <CalendarClock className="h-3 w-3 shrink-0" aria-hidden="true" />
            {overdue ? 'Overdue · ' : ''}{formatSmartTimestamp(task.dueDate)}
          </span>
        )}
        <span className="ml-auto">
          {task.assignee ? (
            <Avatar name={task.assignee.displayName} size="xs" />
          ) : (
            <span className="text-[11px] text-slate-500">Unassigned</span>
          )}
        </span>
      </div>
      {canEdit && onMove && (
        <label className="mt-2 block">
          <span className="sr-only">Move {task.title}</span>
          <select
            aria-label={`Move ${task.title}`}
            value={task.status}
            disabled={pending}
            onPointerDown={(event) => event.stopPropagation()}
            onKeyDown={(event) => event.stopPropagation()}
            onChange={(event) => onMove(event.target.value as TaskStatus)}
            className="w-full rounded-md border border-slate-800 bg-slate-950 px-2 py-1 text-[11px] text-slate-300 focus:border-emerald-500/50 focus:outline-none disabled:cursor-not-allowed disabled:opacity-50"
          >
            {TASK_STATUSES.map((status) => (
              <option key={status} value={status}>{COLUMN_LABELS[status]}</option>
            ))}
          </select>
        </label>
      )}
    </div>
  );
});
