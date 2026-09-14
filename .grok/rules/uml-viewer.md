# UML viewer

The diagram is already on screen. After you change Clojure source:

- Update `examples/uml-viewer.edn` if packages, classes, or edges changed.
  Keep that file topology-only (no authored CRAP/coverage/killed numbers).
- Do not start the viewer; it reloads when the EDN mtime changes.

If the user asked to recompute CRAP or mutation metrics, run `clj -M:crap`
and `clj -M:mutate` on the affected files under `src/`.

Do not commit or push unless asked. Esc interrupts a turn in this terminal;
do not kill the process on interrupt.
