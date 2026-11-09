# KUETcord — Design Guidelines & UI Craft Standards

> **Design Philosophy Source:** Emil Kowalski’s Design Engineering skills (`skills-main`)  
> **Target Audience:** All 3 Engineering Team Members (Member A, Member B, Member C)  
> **Mandate:** All frontend components, user interactions, animations, and typography across KUETcord must adhere to these guidelines to ensure visual harmony, responsive feedback, and 60fps performance.

---

## 1. Core Design Philosophy

1. **Taste is trained, not innate:** Study why the best interfaces (Linear, Vercel, Apple) feel responsive and crisp. Inspect interactions, observe subtle states, and avoid generic "AI slop" or template styling.
2. **Invisible details compound:** When an interface feels effortlessly right, it is because dozens of unseen decisions (tight tracking, tabular numbers, `:active` press feedback, `flex-shrink: 0` on avatars) are singing in tune.
3. **Beauty is leverage:** In a university platform where students have high cognitive loads, a beautiful, polished interface reduces stress and elevates productivity.
4. **Consistency over idiosyncrasy:** A student moving from the CT Hub (Member A) to Core Chat (Member B) to the Playground Kanban Board (Member C) must experience uniform typography, consistent button states, shared elevation shadows, and identical modal mechanics.

---

## 2. Typography & Layout Standards

### 2.1 Font Hierarchy & Tracking
- **Font Family:** System font stack or `Inter` (`font-sans` in Tailwind).
- **Headings (H1, H2, H3):** Large headings must use tight letter-spacing to prevent awkward gaps:
  ```css
  /* Headings 24px and above */
  letter-spacing: -0.02em; /* tracking-tight */
  ```
- **Body Text:** Cap long prose, descriptions, and problem statements to avoid eye fatigue:
  ```css
  max-width: 65ch; /* ~65 characters per line */
  line-height: 1.6;
  ```
- **Numeric Data & Timestamps:** Timestamps, unread badge counters, Pomodoro timers, and voting numbers MUST use tabular numbers to eliminate jitter when digits update:
  ```css
  font-variant-numeric: tabular-nums;
  ```

### 2.2 Text Truncation & Defensive Layouts
- **Avatars:** Always enforce `flex-shrink: 0` on user avatars and status icons so they are never squished into ovals inside flex containers:
  ```html
  <img class="w-8 h-8 rounded-full flex-shrink-0 object-cover" ... />
  ```
- **Channel & Conversation Names:** Truncate cleanly with an ellipsis (`truncate`), but always provide a native `title="..."` or tooltip so the full name is discoverable.
- **Pluralization:** Never display clumsy text like `"1 members"` or `"0 CTs"`. Use `Intl.PluralRules` or a helper utility:
  ```ts
  export function pluralize(count: number, singular: string, plural: string): string {
    return `${count} ${count === 1 ? singular : plural}`;
  }
  ```
- **Timestamp Display:**
  - Less than 7 days old: Relative time (`"2 hours ago"`, `"Yesterday at 4:15 PM"`).
  - 7 days or older: Absolute date (`"Nov 12, 2026"`). Never show `"340 days ago"`.

---

## 3. Micro-Interactions & Component States

### 3.1 Button Dynamics
Buttons must feel tactile and responsive. A user clicking a button must receive immediate feedback:
```css
/* Standard Button Press Feedback */
button:active:not(:disabled) {
  transform: scale(0.97);
  transition: transform 100ms ease-out;
}
```
- **Disabled State:** Never leave disabled buttons clickable; apply `cursor-not-allowed opacity-50` and disable `:active` scaling.
- **Loading State:** When a button triggers an async request, replace or append an inline spinner (`lucide-react: Loader2` with `animate-spin`). Keep button width stable so the layout does not jump.

### 3.2 Modals, Popovers & Dropdowns
- **Modals (Dialogs):** Must remain centered on the viewport. Enter animation scales from `scale(0.95)` to `scale(1)` with opacity fade. Never scale from `scale(0)`.
- **Dropdowns & Popovers:** Must scale from their trigger button:
  ```css
  /* Popover scaling origin */
  transform-origin: var(--radix-popover-content-transform-origin, top left);
  ```
- **Easing:** Dropdown menus and popovers must use `ease-out` under 200–250ms. **NEVER use `ease-in` on popovers or menus** (it feels sluggish and delayed).

### 3.3 Toast Notifications
- **Standard Library:** Use [Sonner](https://sonner.emilkowal.ski) (`ask-sonner` skill).
- Mount `<Toaster richColors position="top-right" />` in the root application layout.
- Use toasts for asynchronous operations:
  - Success: `toast.success("CT schedule verified successfully")`
  - Error: `toast.error("Failed to connect to chat server")`
  - Action/Undo: `toast("Task moved to DONE", { action: { label: "Undo", onClick: () => ... } })`

---

## 4. Animation Decision Framework (From `emil-design-eng`)

Before adding motion, every developer must answer these three questions:

### 4.1 Should this animate at all?
| Frequency | Interaction Type | Decision |
|---|---|---|
| **100+ times/day** | Keyboard shortcuts, channel switching, typing | **NO ANIMATION. Instant.** |
| **Tens of times/day** | Hover effects, task dragging, sidebar toggle | Subtle, fast ($\le 150\text{ms}$) |
| **Occasional** | Opening a modal, expanding CT revision topics | Standard smooth transition (200–250ms) |
| **Rare / Milestones** | Team victory celebration, project completion | Rich motion permitted |

> **Rule:** Never animate keyboard-initiated actions. A user pressing `Esc` to close a modal or `⌘K` to open quick search expects instant response.

### 4.2 Which properties are permitted to animate?
To guarantee 60fps across student laptops and low-spec hardware:
- **PERMITTED (GPU Composited):** `transform` (`translate`, `scale`, `rotate`), `opacity`.
- **FORBIDDEN (Triggers CPU Reflow & Frame Drops):** `width`, `height`, `top`, `left`, `margin`, `padding`.
- **NEVER DO:** `transition: all`. Always specify the exact property:
  ```css
  /* WRONG */
  transition: all 300ms ease;

  /* RIGHT */
  transition: transform 150ms ease-out, opacity 150ms ease-out;
  ```

### 4.3 Review Checklist for All PRs
Every UI PR must be checked against this table:

| Before (Avoid) | After (Required) | Rationale |
|---|---|---|
| `transition: all 300ms` | `transition: transform 150ms ease-out` | Avoid animating layout triggers |
| `transform: scale(0)` | `transform: scale(0.95); opacity: 0` | Objects in nature never scale from true zero |
| `ease-in` on dropdown | `ease-out` under 200ms | `ease-out` starts fast and feels snappy |
| Missing `:active` on button | `transform: scale(0.97)` on `:active` | Confirms the click was registered |
| Unbounded lines of text | `max-width: 65ch` | Prevents eye fatigue on wide displays |
| Plain numbers in timer/counter | `font-variant-numeric: tabular-nums` | Stops adjacent elements from vibrating |
| Avatar oval deformation | `flex-shrink: 0` | Keeps circular avatar geometry intact |

---

## 5. Curated UI Library Stack (From `pick-ui-library`)

To prevent library fragmentation across the team, all 3 members must use these agreed packages:

| UI Need | Approved Package | Usage Context |
|---|---|---|
| **UI Primitives / Modals** | Radix UI / `@radix-ui/react-*` | Accessible dialogs, dropdowns, tooltips |
| **Toasts** | `sonner` | System alerts, success confirmations, outbox notifications |
| **Icons** | `lucide-react` | Unified icon style across all three modules |
| **Drag & Drop** | `@dnd-kit/core`, `@dnd-kit/sortable` | Member C’s Kanban board task dragging |
| **List Virtualization** | `react-virtuoso` | Member B’s chat feed & Member A’s course list |
| **Animated Numbers** | `number-flow` | Member C’s Pomodoro timer & Member B’s unread counters |
| **Utility Styling** | `tailwindcss` + `tailwind-merge` + `clsx` | Styling, dark theme variables, responsive design |

---

## 6. Adversarial "Break UI" Standards

Before opening a pull request, each member must test their components against worst-case realistic data (`break-ui` skill):

1. **Long Names & Titles:**
   - Student name: `"Aleksandra Wiśniewska-Kowalczyk"`
   - CT title: `"CSE 3103: Advanced Algorithms, Graph Traversals, and Dynamic Programming Exam 2"`
   - Verify text truncates with ellipsis or wraps cleanly without breaking card borders.
2. **Short Data & Missing Fields:**
   - Name: `"Jo"` (verify avatar placeholder initials render cleanly without orphaned hyphens).
   - CT with no room location: shows `"Location TBA"` badge instead of blank space.
3. **Huge Numbers & Zero States:**
   - Unread notifications count: `99+` badge instead of pushing header elements off-screen.
   - Empty Kanban column: displays a clean dotted placeholder card `"No tasks in review"`.
   - Empty CT list: helpful empty state `"No upcoming Class Tests scheduled. Follow a course to receive alerts."` with a primary action button.

---

## 7. Member-Specific Component Guidelines

### Member A (CT Hub, Auth & App Shell)
- **Course & CT Cards:** Use consistent card border `border-border/60 hover:border-border` and subtle background elevation. Status badges must use fixed semantic colors:
  - `VERIFIED`: Emerald green (`bg-emerald-500/10 text-emerald-500 border-emerald-500/20`)
  - `UNVERIFIED` / `PENDING_REVIEW`: Amber (`bg-amber-500/10 text-amber-500 border-amber-500/20`)
  - `RESCHEDULED`: Purple (`bg-purple-500/10 text-purple-500 border-purple-500/20`)
  - `CANCELLED`: Rose (`bg-rose-500/10 text-rose-500 border-rose-500/20`)
- **Navigation Shell:** Sidebar items highlight with active indicator bar; transition width smoothly on collapse without text wrap glitches.

### Member B (Chat Feed, Group DMs & Notification Center)
- **Message List:** Virtualize messages with `react-virtuoso` so 5,000 chat messages scroll at 60fps without DOM bloat.
- **Optimistic Message States:**
  - `SENDING`: 70% opacity, subtle pulse.
  - `SENT`: Full opacity, receipt checkmark.
  - `FAILED`: Red exclamation icon with inline `"Retry"` button.
- **Notification Dropdown:** Max height capped at `400px` with internal virtual scroll; mark-read action dims row with smooth 150ms opacity transition.

### Member C (Contest Board, Kanban Board & Study Circles)
- **Kanban Task Cards:** Implement with `@dnd-kit`. When dragged, card scales slightly (`scale(1.03)`) and casts an elevated shadow (`shadow-xl`).
- **Conflict Dialog on 409:** When a task update collides with a teammate's write, display a non-modal banner with a primary button `"Load Latest Changes"`.
- **Pomodoro Timer Widget:** Display clean countdown with `font-variant-numeric: tabular-nums` and a circular progress ring.

