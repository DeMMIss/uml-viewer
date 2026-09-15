# UML viewer

The diagram is already on screen.

- Do **not** edit `examples/uml-viewer.edn`. It is generated from
  `examples/uml-viewer.policy.edn` via `clj -M:ir`.
- If layering, diagrams, or association-vs-dependency changed, edit the
  policy then run `clj -M:ir`.
- Policy describes real dependency structure; it does not create
  partitions. Do not move a ns between `:nses` to fake a layer. Fix
  the source, then update the policy to match.
- A class under **Unassigned** means add it to a package `:nses` and regenerate.
- After source or policy change: `clj -M:crap`, `clj -M:mutate` on changed
  `src/` files (differential), then `clj -M:ir`. Uncovered mutants are
  coverage gaps: keep the snapshot; do not re-run the file or force a
  full mutation because mutate exited non-zero.
- Do not start the viewer; it reloads when the EDN mtime changes.

Do not commit or push unless asked. Esc interrupts a turn in this terminal;
do not kill the process on interrupt.
