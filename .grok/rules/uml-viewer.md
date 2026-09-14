# UML viewer

The diagram is already on screen.

- Do **not** edit `examples/uml-viewer.edn`. It is generated from
  `examples/uml-viewer.policy.edn` via `clj -M:ir`.
- If layering, diagrams, or association-vs-dependency changed, edit the
  policy then run `clj -M:ir`.
- A class under **Unassigned** means add it to a package `:nses` and regenerate.
- Do not start the viewer; it reloads when the EDN mtime changes.

Do not commit or push unless asked. Esc interrupts a turn in this terminal;
do not kill the process on interrupt.
