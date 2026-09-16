# UML viewer

The diagram is already on screen.

- Do **not** edit `examples/uml-viewer.edn`. It is generated from
  `examples/uml-viewer.policy.edn` via `clj -M:ir`.
- Policy follows **namespace nesting**, not invented layers. Dots after
  the prefix are the tree. Do not add Domain/Engine-style packages.
- After source or policy change: `clj -M:crap`, `clj -M:mutate` on changed
  `src/` files (differential), then `clj -M:ir`. Uncovered mutants are
  coverage gaps: keep the snapshot; do not re-run the file or force a
  full mutation because mutate exited non-zero.
- Do not start the viewer on launch; EDN reloads when the file mtime
  changes. To restart it: write `:quit-for-restart` to
  `.uml-viewer/to-viewer.edn`, wait for the JVM to exit, then
  `clj -M:run --restart <edn>`. Do not SIGKILL; closing the window
  still kills Grok.

Do not commit or push unless asked. Esc interrupts a turn in this terminal;
do not kill the process on interrupt.
