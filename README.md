# UML viewer

A live Quil app that lays out and draws UML class diagrams from an EDN IR.
An agent (or a human) writes the IR; this tool displays it, routes the arrows,
colors CRAP, and lets you click.

The IR is **topology**: diagrams, packages, classes, fields, ops you listed,
and edges. **Metrics** (CC, coverage, CRAP, killed/survived) come from
`.metrics/` snapshots produced by [crap4clj](https://github.com/unclebob/crap4clj)
and [clj-mutate](https://github.com/unclebob/clj-mutate). The viewer overlays
those files at load, keyed by namespace + function name.

## Run

Needs Clojure CLI and Java 21+.

```bash
clj -M:run
clj -M:run examples/library.edn
clj -M:run examples/uml-viewer.edn
```

A **tmux** session `uml-viewer-grok` starts interactive Grok (`--yolo --trust`)
and a Terminal window attaches to it. Type there; Esc is the real TUI
interrupt. Closing the diagram kills that tmux session (and the Terminal
attach). After Grok changes the EDN the viewer reloads. Ask it to recompute
CRAP or mutation when you want fresh `.metrics/` snapshots. Standing rules
live in `.grok/rules/uml-viewer.md`.

A missing or unreadable file prints `UML viewer: file not found: …` and opens
an empty window with the error in the inspector, instead of throwing.

- Click a class to open (or retarget) a **class card**. That click brings the
  card in front. Click empty space on the diagram to bring the diagram in front.
  The two windows are otherwise independent.
- Hover a member on the class card to highlight it. Click it to open an
  independent source window (syntax-colored HTML, same style as arch-view).
  See [Source extractors](#source-extractors).
- Click a package to inspect it in the sidebar.
- Methods on the class card are marked `+` public and `-` private. Private
  functions (`defn-`) are not drawn on the class box. `:hide-members true`
  hides fields and ops on the box (used on the Layers overview).
- Scroll the mouse wheel to pan vertically; Shift-scroll (or left/right arrows)
  for horizontal.
- `R` reloads the EDN (the watcher also reloads on save). Overlay re-reads
  `.metrics/` on the next load.
- `Esc` on the diagram clears the selection. `Esc` on the card closes it.
  Closing the main window exits the app.

```bash
clj -M:spec
clj -M:cov
clj -M:crap                          # writes .metrics/crap.edn
clj -M:mutate src/uml_viewer/layout.clj   # writes .metrics/mutate/uml_viewer/layout.edn
```

This project's `:crap` and `:mutate` aliases use `../clojure/crap4clj` and
`../clojure/clj-mutate`. Commit `.metrics/` so a clone has numbers without
re-running those tools.

Rename or move of a function is a new form: overlay does not match old names.

## IR

The file is EDN. A document may contain several diagrams (one per layer),
stacked top to bottom. Unique class `:id`s *within* a diagram; packages as
groups; edges by kind. A single diagram (top-level `:packages`) still works.

Metrics on the class card do not have to be authored. If `.metrics/` is present,
the overlay fills CC, coverage, CRAP, killed/survived, and any functions found
in the snapshots (including privates). Authored `:crap` / `:coverage` / `:ops`
are the fallback when no snapshot exists.

Class `:id` is mapped to namespace `uml-viewer.<id>` (or `:ns` if you set it).

```edn
{:title "Lending library"
 :direction :tb
 :packages
 [{:id :domain
   :label "Domain"
   :classes
   [{:id :book
     :name "Book"
     :stereotype :class          ;; optional: :interface :enumeration :abstract
     :fields [{:name "isbn" :type "String"}]
     :ops [{:name "find" :args ["isbn"] :returns "Book"}]}]}
  {:id :app
   :label "Application"
   :classes
   [{:id :repo
     :name "CatalogRepo"
     :stereotype :interface
     :ops [{:name "get" :args ["isbn"] :returns "Book"}]}]}]
 :edges
 [{:from :sql-repo :to :repo :kind :implements}
  {:from :loan :to :book :kind :association :label "borrows"}]}
```

Optional authored metrics, used when snapshots are missing:

- `:crap` — a number (`μ`) or `{:mu :max :sigma}`
- `:coverage` — ratio 0–1 on a class or op
- `:cc`, `:killed`, `:survived`, `:private` on ops
- `:hide-members true` — compact box (Layers overview)

Package and class **color** uses `μ + σ` from the class `:crap` map (after
overlay): green at 0, gold at 12, rust at 24 and above.

On the class card, the class row shows average CRAP with a `μ` suffix and omits
CC. Max on the header line is the worst function in the namespace, not a sum.

Edge `:kind` values:

| kind | line | head |
|------|------|------|
| `:inheritance` | solid grey | empty triangle |
| `:implements` | solid grey | empty triangle |
| `:association` | solid grey | open arrow |
| `:dependency` | solid grey | open arrow |
| `:aggregation` | solid grey | empty diamond |
| `:composition` | solid grey | filled diamond |

Layout follows Mermaid's three stages:

1. **Size** each class from its text (padding 12).
2. **Place** with a layered graph (packages in ranks; classes Sugiyama-ranked
   inside a package; ~40px spacing).
3. **Route** like Mermaid/ELK: ports on facing sides, orthogonal tracks in
   the rank gap, short same-rank connections through the stack gap (local U
   only when a sibling sits in the way), then stroke with D3 `curveBasis`
   cubics. All arrows are solid grey.

## Source extractors

Clicking a member asks `uml-viewer.source` for the **whole file** and a
**start line**. The IR (and the class card) only supply an **identity
map**; a language-specific extractor turns that into
`{:title :file :body :line}`. The source window opens on that file and
scrolls to the member (highlighted).

```clojure
(source/member-source {:lang :clojure
                       :ns "uml-viewer.layout"
                       :name "layout"})
```

`:lang` selects the extractor (default `:clojure`). Register another
implementation with `(source/register! :java my-java-extractor)`. The
extractor must satisfy `LanguageSource`:

| method | role |
|--------|------|
| `locate` | path to the file that should contain the member |
| `extract` | slice that member out of the file text |
| `title` | window title |

**Clojure** (`uml-viewer.source.clojure`) is the only implementation today:
it maps `:ns` to `src/...clj` and finds the top-level `(defn name …)` /
`(defn- name …)` so the window can jump to that line. That locate/line
step is not enough for Java or C — those need a parser or language
server, and a richer identity (`:class`, `:signature`, `:file`). The
protocol is the seam; do not special-case languages in the class card.

The engine (`ir`, `layout`, `route`, `hit`, `events`, `overlay`, `source`)
does not depend on Quil. Only `draw` and `sketch` talk to Processing.
