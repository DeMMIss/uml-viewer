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
clj -M:run --restart                 # new JVM, same Grok session
clj -M:run --restart examples/uml-viewer.edn
```

A **tmux** session `uml-viewer-grok` starts interactive Grok in the
**examined project's directory** (`--yolo --trust --rules …` plus a launch
prompt). On start it updates that project's hierarchical policy and
regenerates the IR. Type there; Esc is the real TUI interrupt. Closing the
diagram kills that tmux session (and the Terminal attach). That instance —
not every Grok in this repo — also runs `clj -M:crap`, `clj -M:mutate`, and
IR generate after later changes. Project-wide rules live in
`.grok/rules/uml-viewer.md`.

`--restart` opens a new JVM on the current EDN and does **not** start a
new Grok session. Use it after source changes so the window loads the new
code while the companion keeps running. Closing the window still kills
Grok. To recycle the window without killing Grok, write
`:quit-for-restart` to `.uml-viewer/to-viewer.edn`, wait for the JVM to
exit, then `clj -M:run --restart examples/uml-viewer.edn`. Do not SIGKILL.

The window stays blank until the companion sends `:display`, with
**Waiting for agent to create diagram.** on the canvas. `--restart` and
`R` load the current EDN immediately and do not wait for the agent. A
missing or unreadable file prints `UML viewer: file not found: …` in the
inspector instead of throwing.

- The first view is the **namespace layers** (first segment after the
  prefix). Dependencies between layers collapse to one arrow. Each layer
  lists its nested namespaces; double-click a layer to open the next
  level. Esc or the ← label goes back up. Double-click a leaf module for
  its **class card**.
- The class card names the **module** (`:ns`). Click it to open that source
  file at the top. Hover a member to highlight it; click it to open the
  same file positioned at the defn. See [Source extractors](#source-extractors).
- Methods on the class card are marked `+` public and `-` private. Private
  functions (`defn-`) are not drawn on the class box.
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
clj -M:mutate src/uml_viewer/engine/layout.clj   # writes .metrics/mutate/uml_viewer/engine/layout.edn
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
`deftype` of a protocol as `:implements`. External `:require`s and `:import`s
become **foreign** classes (the full lib name). Members are not authored —
overlay fills them from `.metrics/`.

### Do not invent layers

The tree **is** the namespaces. After `:prefix`, every `.` is a nesting
level. `uml-viewer.engine.layout` is a child of `engine`. `uml-viewer.clojure-language.source-clojure`
is a child of `clojure-language`. The policy does **not** assign nses to invented
packages. If you want Domain / Engine / Adapters boxes, those segments must
exist as namespaces.

To write a policy for a project:

1. Set `:src` and `:prefix` to the project's source root and ns prefix
   (`src` and `foo` for `foo.bar.baz`).
2. Set `:hierarchical true` (or omit `:packages` and `:diagrams`).
3. List top-level **segments** in `:order` — the first dotted part after
   the prefix, in the order you want the boxes. Do not invent names.
4. List real libraries in `:foreign` if they should appear as ovals.
5. Optionally override a require with `:edge-kinds {[:from :to] :association}`
   using the **leaf** ids (`clojure-language.source-clojure`, not `clojure-language`).
6. Run `clj -M:ir`.

If `foo.bar` and `foo.bar.baz` both exist, the `bar` box lists `bar` (the
module) and `baz` (the child). Double-click `bar` the layer to open that
level; double-click the `bar` module line for its class card.

Wrong (invented partitions):

```edn
:packages [{:id :domain :nses [ir geom source]}
           {:id :engine :nses [layout route]}]
```

Right (the ns tree):

```edn
{:title "UML viewer"
 :src "src"
 :prefix "uml-viewer"
 :lang :clojure
 :out "examples/uml-viewer.edn"
 :hierarchical true
 :foreign [quil]
 :order [main adapters application engine source graph clojure-language domain]
 :edge-kinds {[:engine.compose :engine.layout] :association}}
```

| Key | Role |
|-----|------|
| `:prefix` | Strip this from each ns; remaining dots are the tree |
| `:hierarchical` | Namespace tree (default when `:packages` is omitted) |
| `:order` | Order of **existing** top-level ns segments, not new layer names |
| `:edge-kinds` | Override parser kind for `[from to]` (usually `:association`) |
| `:omit-edges` | Drop `[from to]` |
| `:lang` | Which `LanguageGraph` to use (default `:clojure`) |
| `:foreign` | External libs as ovals. A listed prefix collapses `quil.core` to `quil`. |

**Viewer Grok loop** (passed with `--rules` to the companion session only)

After **every** source or policy change: `clj -M:crap`, `clj -M:mutate` on
the changed `src/` files, then `clj -M:ir`. Do not wait to be asked.
Uncovered mutants remaining are coverage gaps; keep the snapshot and do
not re-run the file or force a full mutation because mutate exited
non-zero.

- Add/rename/delete a namespace: the tree updates on `clj -M:ir`. Put a
  new top-level **segment** in `:order` if you care about box order.
- Nested nses (`clojure-language.source-clojure`) appear as contents of the parent layer.
- “This require is really an association”: one `:edge-kinds` entry.
- Show a library like quil as an oval: add it to `:foreign`.
- Do not add `:packages` to fake Clean Architecture layers.

Hand-written sample IRs (e.g. `examples/library.edn`) are still valid;
they are not generated.

The viewer reloads when the generated EDN mtime changes.

## Companion mailbox

The viewer and the companion Grok talk through `.uml-viewer/` in the examined
project (gitignored). The file is the mail; tmux is only a doorbell.

| File | Direction |
|------|-----------|
| `.uml-viewer/to-viewer.edn` | Grok → viewer |
| `.uml-viewer/to-agent.edn` | viewer → Grok |

Commands are `{:id n :op …}` with a rising `:id`. Writes are tmp-then-rename.

- `:display` plus `:path` — viewer loads that EDN
- `:regen` — Grok rewrites hierarchical policy, regenerates IR, then `:display`
- `:quit-for-restart` — viewer exits the JVM without killing Grok; then
  `clj -M:run --restart <edn>`

**Regen** in the inspector queues `:regen` and wakes Grok with text, a short
pause, then Enter (`C-m` then `C-j`), same timing as SwarmForge. The wake-up
does not contain the command. If Grok is busy, it finishes first, then reads
the mailbox.

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

**Clojure** (`uml-viewer.clojure-language.graph-clojure`) is the only implementation today: it
reads `ns` forms (including prefix lists), `defprotocol`, `defrecord`, and
`deftype`. Java or C need a different parser; do not special-case languages
in `policy` or `ir-generator`. Main constructs the implementation and
passes it in.

## IR

A hierarchical policy writes one EDN document of all classes and edges
(`:hierarchical true`). The viewer builds each screen from the namespace
tree at the current drill level. A hand-written IR with `:packages` (or
`:diagrams`) is still a static diagram, e.g. `examples/library.edn`.

Metrics on the class card do not have to be authored. If `.metrics/` is present,
the overlay fills CC, coverage, CRAP, killed/survived, and any functions found
in the snapshots (including privates). Authored `:crap` / `:coverage` / `:ops`
are the fallback when no snapshot exists.

Overlay keys snapshots by class `:ns` (the real source namespace). The
generator writes `:ns` from the scanned ns. Hand-written IR must set `:ns`
the same way; there is no project-specific fallback.

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

Clicking a member asks `uml-viewer.source` for the **whole file** and a
**start line**. The IR (and the class card) only supply an **identity
map**; a language-specific extractor turns that into
`{:title :file :body :line}`. The source window opens on that file and
scrolls to the member (highlighted).

```clojure
(source/member-source {:lang :clojure
                       :ns "uml-viewer.engine.layout"
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

**Clojure** (`uml-viewer.clojure-language.source-clojure`) is the only implementation today:
it maps `:ns` to `src/...clj` and finds the top-level `(defn name …)` /
`(defn- name …)` so the window can jump to that line. That locate/line
step is not enough for Java or C — those need a parser or language
server, and a richer identity (`:class`, `:signature`, `:file`). The
protocol is the seam; do not special-case languages in the class card.
Main constructs the extractor and passes it to Core.

The engine (`ir`, `layout`, `route`, `hit`, `events`, `overlay`, `source`,
`graph`, `policy`) does not depend on Quil. Only `draw` and `sketch` talk
to Processing.
