# UML viewer

A live Quil app that lays out and draws UML class diagrams from an EDN IR.
A **policy** plus a language-specific parser write the topology; this tool
displays it, routes the arrows, colors CRAP, and lets you click.

The IR is **topology**: diagrams, packages, classes, and edges. **Metrics**
(CC, coverage, CRAP, killed/survived) come from `.metrics/` snapshots produced
by [crap4clj](https://github.com/unclebob/crap4clj) and
[clj-mutate](https://github.com/unclebob/clj-mutate). The viewer overlays those
files at load, keyed by namespace + function name. Agents edit the policy, not
the IR. See [Policy](#policy).

## Run

Needs Clojure CLI and Java 21+.

```bash
clj -M:ir                            # policy → examples/uml-viewer.edn
clj -M:run
clj -M:run examples/library.edn
clj -M:run examples/uml-viewer.edn
```

A **tmux** session `uml-viewer-grok` starts interactive Grok
(`--yolo --trust --rules …`) and a Terminal window attaches to it. Type
there; Esc is the real TUI interrupt. Closing the diagram kills that tmux
session (and the Terminal attach). That instance — not every Grok in this
repo — is told to run `clj -M:crap`, `clj -M:mutate` on files it changed,
then `clj -M:ir` after every change. The viewer reloads the generated EDN
(and overlay). Project-wide rules live in `.grok/rules/uml-viewer.md`.

A missing or unreadable file prints `UML viewer: file not found: …` and opens
an empty window with the error in the inspector, instead of throwing.

- Double-click a class to open (or retarget) a **class card**. That click
  brings the card in front. Click empty space on the diagram to bring the
  diagram in front. The two windows are otherwise independent.
- Hover a member on the class card to highlight it. Double-click it to open
  an independent source window (syntax-colored HTML, same style as arch-view).
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
clj -M:ir                            # writes examples/uml-viewer.edn
clj -M:crap                          # writes .metrics/crap.edn
clj -M:mutate src/uml_viewer/layout.clj   # writes .metrics/mutate/uml_viewer/layout.edn
```

This project's `:crap` and `:mutate` aliases use `../clojure/crap4clj` and
`../clojure/clj-mutate`. Commit `.metrics/` so a clone has numbers without
re-running those tools.

Rename or move of a function is a new form: overlay does not match old names.

## Policy

This project's diagram is **generated**. Do not edit `examples/uml-viewer.edn`.
Edit `examples/uml-viewer.policy.edn`, then run `clj -M:ir`.

The **parser** (`LanguageGraph`) reads source and emits facts: one class per
project namespace, `:require` / `:use` of another project ns as
`:dependency`, `defprotocol` as `:stereotype :interface`, `defrecord` /
`deftype` of a protocol as `:implements`. External libs (`clojure.*`, `quil`,
Java) are dropped. Members are not authored — overlay fills them from
`.metrics/`.

The **policy** is the judgment the parser cannot make: which ns belongs to
which layer, which diagrams to draw, and the few require-vs-association
overrides.

```edn
{:title "UML viewer"
 :src "src"
 :prefix "uml-viewer"
 :lang :clojure
 :out "examples/uml-viewer.edn"
 :packages
 [{:id :adapters :label "Adapters" :nses [draw sketch core]}
  {:id :app :label "Application" :nses [events detail]}
  {:id :engine :label "Engine" :nses [layout route]}
  {:id :domain :label "Domain" :nses [ir geom source source.clojure]}]
 :diagrams
 [{:title "Layers" :view :overview :hide-members true :direction :tb}
  {:title "Adapters" :package :adapters}
  {:title "Engine" :package :engine
   :edge-kinds {[:compose :layout] :association}}]}
```

List `:packages` (and matching `:diagrams`) in dependency order: nothing
incoming at the top, nothing outgoing at the bottom. Layout stacks them in
that sequence, so arrows on the Layers overview point down.

| Key | Role |
|-----|------|
| `:packages` / `:nses` | Layer membership, class order on the box, and stack order |
| `:view :overview` | Every package, every listed class, no stubs |
| `:package` | One layer: home classes plus one-hop project deps as stubs (`:hide-members`) |
| `:edge-kinds` | Override parser kind for `[from to]` (usually `:association`) |
| `:omit-edges` | Drop `[from to]` from that diagram |
| `:hide-members` | Compact boxes (Layers overview, and all stubs) |
| `:direction` | `:tb` (overview default) or `:lr` (layer default) |
| `:lang` | Which `LanguageGraph` to use (default `:clojure`) |

**Viewer Grok loop** (passed with `--rules` to the companion session only)

After **every** source or policy change: `clj -M:crap`, `clj -M:mutate` on
the changed `src/` files, then `clj -M:ir`. Do not wait to be asked.

- Add/rename/delete a namespace, or change a `:require` / protocol: no
  policy edit unless layering changed; still regenerate.
- New ns not listed in any `:nses` appears under **Unassigned**. Put it in
  a package and regenerate.
- Move a ns to another layer: edit `:nses`, then the usual crap/mutate/ir.
- New layer or diagram: add a package or diagram entry, then crap/mutate/ir.
- “This require is really an association”: one `:edge-kinds` entry.
- Hand-written sample IRs (e.g. `examples/library.edn`) are still valid;
  they are not generated.

`clj -M:ir` prints unassigned namespaces on stderr. The viewer reloads when the generated EDN mtime changes.

## Language graphs

Generating the IR asks `uml-viewer.graph` to scan a source tree. `:lang`
selects the scanner (default `:clojure`). Register another implementation
with `(graph/register! :java my-java-scanner)`. The scanner must satisfy
`LanguageGraph`:

| method | role |
|--------|------|
| `scan` | from a root directory and `{:prefix …}`, return `{:classes :edges}` |

Classes are `{:id :name :ns :stereotype}`. Edges are `{:from :to :kind}`
(`:dependency` or `:implements`). The policy layer is language-neutral.

**Clojure** (`uml-viewer.graph.clojure`) is the only implementation today: it
reads `ns` forms (including prefix lists), `defprotocol`, `defrecord`, and
`deftype`. Java or C need a different parser; do not special-case languages
in `policy` or `ir-generator`. Main constructs the implementation and
passes it in.

## IR

The generated file is EDN. A document may contain several diagrams (one per
layer), stacked top to bottom. Unique class `:id`s *within* a diagram;
packages as groups; edges by kind. A single diagram (top-level `:packages`)
still works as a hand-written IR.

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
2. **Place** packages in document order; classes Sugiyama-ranked inside a
   package; ~40px spacing.
3. **Route** like Mermaid/ELK: ports on facing sides, orthogonal tracks in
   the rank gap, short same-rank connections through the stack gap (local U
   only when a sibling sits in the way), then stroke with D3 `curveBasis`
   cubics. All arrows are solid grey.

## Source extractors

Double-clicking a member asks `uml-viewer.source` for the **whole file** and a
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
Main constructs the extractor and passes it to Core.

The engine (`ir`, `layout`, `route`, `hit`, `events`, `overlay`, `source`,
`graph`, `policy`) does not depend on Quil. Only `draw` and `sketch` talk
to Processing.
