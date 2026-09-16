# UML viewer

A live Quil app that lays out and draws UML from an EDN IR. A **policy** plus
a language-specific parser write the topology; this tool displays it, routes
the arrows, colors CRAP, and lets you click.

The IR is **topology**: the namespace tree, classes, and edges. **Metrics**
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
clj -M:run --help
clj -M:run --restart                 # new JVM, same Grok session
clj -M:run --restart examples/uml-viewer.edn
```

A **tmux** session `uml-viewer-grok` starts interactive Grok in the
**examined project's directory** (`--yolo --trust --rules …` plus a launch
prompt). On start it writes a hierarchical policy from that project's
namespaces and regenerates the IR. Type there; Esc is the real TUI interrupt.
Closing the diagram kills that tmux session (and the Terminal attach). That
instance — not every Grok in this repo — also runs `clj -M:crap`,
`clj -M:mutate`, and IR generate after later changes. Project-wide rules live
in `.grok/rules/uml-viewer.md`.

`--restart` opens a new JVM on the current EDN and does **not** start a new
Grok session. Use it after source changes so the window loads the new code
while the companion keeps running. Closing the window still kills Grok. To
recycle the window without killing Grok, write `:quit-for-restart` to
`.uml-viewer/to-viewer.edn`, wait for the JVM to exit, then
`clj -M:run --restart examples/uml-viewer.edn`. Do not SIGKILL.

On a fresh start the canvas stays blank until the companion sends `:display`,
with **Waiting for agent to create diagram.** `--restart` and `R` load the
current EDN immediately and do not wait. A missing or unreadable file prints
`UML viewer: file not found: …` in the inspector instead of throwing.

```bash
clj -M:spec
clj -M:cov
clj -M:ir                            # writes examples/uml-viewer.edn
clj -M:crap                          # writes .metrics/crap.edn
clj -M:mutate src/uml_viewer/engine/layout.clj
```

This project's `:crap` and `:mutate` aliases use `../clojure/crap4clj` and
`../clojure/clj-mutate`. Commit `.metrics/` so a clone has numbers without
re-running those tools.

Rename or move of a function is a new form: overlay does not match old names.

## Navigation

- First view: **namespace layers** (first segment after the prefix).
  Dependencies between layers collapse to one arrow. Each layer lists nested
  namespaces.
- Double-click a layer to open the next level. Esc or the ← label goes up.
- Double-click a leaf module for its **class card**.
- The class card names the **module** (`:ns`). Click it to open that source
  file at the top. Hover a member to highlight it; click it to open the same
  file at the defn. See [Source extractors](#source-extractors).
- Methods on the card are `+` public and `-` private. `defn-` is not drawn on
  the class box.
- Abstract classes show a white **α** in the upper-right; interfaces a white
  **I**. Names of rectangles that are not classes (layers, interfaces,
  enumerations, package banners) are italic. Foreign libraries listed in
  policy are ovals outside the layers.
- Scroll to pan vertically; Shift-scroll (or left/right arrows) for
  horizontal. Pan can follow arrows that bow past the origin.
- **Regen** in the inspector asks the companion to rewrite policy and IR
  (see [Companion mailbox](#companion-mailbox)).
- `R` reloads the current EDN (the watcher also reloads on save). Overlay
  re-reads `.metrics/` on the next load.
- `Esc` on the class card closes it. Closing the main window exits the app.

## Policy

This project's diagram is **generated**. Do not edit `examples/uml-viewer.edn`.
Edit `examples/uml-viewer.policy.edn`, then run `clj -M:ir` (or press Regen).

The **parser** (`LanguageGraph`) reads source and emits facts: one class per
project namespace, `:require` / `:use` of another project ns as
`:dependency`, `defprotocol` as `:stereotype :interface`, `defrecord` /
`deftype` of a protocol as `:implements`. External `:require`s and `:import`s
become **foreign** classes. Members are not authored — overlay fills them from
`.metrics/`.

### Do not invent layers

The tree **is** the namespaces. After `:prefix`, every `.` is a nesting
level. `uml-viewer.engine.layout` is a child of `engine`.
`uml-viewer.clojure-language.source-clojure` is a child of `clojure-language`.
The policy does **not** assign nses to invented packages. If you want Domain /
Engine / Adapters boxes, those segments must exist as namespaces.

To write a policy for a project:

1. Set `:src` and `:prefix` to the project's source root and ns prefix
   (`src` and `foo` for `foo.bar.baz`).
2. Set `:hierarchical true` (or omit `:packages` and `:diagrams`).
3. List top-level **segments** in `:order` — the first dotted part after
   the prefix, in the order you want the boxes. Do not invent names.
4. List real libraries in `:foreign` if they should appear as ovals.
5. Optionally override a require with `:edge-kinds {[:from :to] :association}`
   using the **leaf** ids (`clojure-language.source-clojure`, not
   `clojure-language`).
6. Run `clj -M:ir` (or Regen).

If `foo.bar` and `foo.bar.baz` both exist, the `bar` box lists `bar` (the
module) and `baz` (the child). Double-click the layer to open that level;
double-click the `bar` module line for its class card.

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

On launch: from the examined directory, write or update the hierarchical
policy and regenerate the IR, then wait.

After **every** later source or policy change: `clj -M:crap`, `clj -M:mutate`
on the changed `src/` files, then `clj -M:ir`. Uncovered mutants remaining are
coverage gaps; keep the snapshot and do not re-run the file or force a full
mutation because mutate exited non-zero.

- Add/rename/delete a namespace: the tree updates on `clj -M:ir`. Put a new
  top-level **segment** in `:order` if you care about box order.
- Nested nses appear as contents of the parent layer.
- “This require is really an association”: one `:edge-kinds` entry.
- Show a library like quil as an oval: add it to `:foreign`.
- Do not add `:packages` to fake Clean Architecture layers.

Hand-written sample IRs (e.g. `examples/library.edn`) are still valid; they
are not generated.

## Companion mailbox

The viewer and the companion Grok talk through `.uml-viewer/` in the examined
project (gitignored). The file is the mail; tmux is only a doorbell.

| File | Direction |
|------|-----------|
| `.uml-viewer/to-viewer.edn` | Grok → viewer |
| `.uml-viewer/to-agent.edn` | viewer → Grok |

Commands are `{:id n :op …}` with a rising `:id`. Writes are tmp-then-rename.

| `:op` | Meaning |
|-------|---------|
| `:display` | Viewer loads `:path` (relative to the project root) |
| `:regen` | Grok rewrites hierarchical policy, regenerates IR, then `:display` |
| `:quit-for-restart` | Viewer exits the JVM without killing Grok |

**Regen** in the inspector queues `:regen` and wakes Grok with literal text, a
150ms pause, `C-m`, 50ms, then `C-j` (same timing as SwarmForge). The wake-up
does not contain the command. If Grok is busy, it finishes first, then reads
the mailbox. If tmux is missing, the button still writes the file and the
inspector says the session is not attached.

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

**Clojure** (`uml-viewer.clojure-language.graph-clojure`) is the only
implementation today: it reads `ns` forms (including prefix lists),
`defprotocol`, `defrecord`, and `deftype`. Java or C need a different parser;
do not special-case languages in `policy` or `ir-generator`. Main constructs
the implementation and passes it in.

## IR

A hierarchical policy writes one EDN document of all classes and edges
(`:hierarchical true`). The viewer builds each screen from the namespace tree
at the current drill level. A hand-written IR with `:packages` (or
`:diagrams`) is still a static diagram, e.g. `examples/library.edn`.

Metrics on the class card do not have to be authored. If `.metrics/` is
present, the overlay fills CC, coverage, CRAP, killed/survived, and any
functions found in the snapshots (including privates). Authored `:crap` /
`:coverage` / `:ops` are the fallback when no snapshot exists.

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
- `:hide-members true` — compact box

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
   cubics. All arrows are solid grey. Paths that pass the target and reverse
   are rejected.

## Source extractors

Clicking a member asks `uml-viewer.source` for the **whole file** and a
**start line**. The IR (and the class card) only supply an **identity
map**; a language-specific extractor turns that into
`{:title :file :body :line}`. The source window opens on that file and
scrolls to the member (highlighted). Clicking the module name opens the same
file at the top (`:line` omitted).

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

**Clojure** (`uml-viewer.clojure-language.source-clojure`) is the only
implementation today: it maps `:ns` to `src/...clj` (or `.cljc` / `.cljs`)
and finds the top-level `(defn name …)` / `(defn- name …)` so the window can
jump to that line. That locate/line step is not enough for Java or C — those
need a parser or language server, and a richer identity (`:class`,
`:signature`, `:file`). The protocol is the seam; do not special-case
languages in the class card. Main constructs the extractor and passes it to
Core.

Quil stays in `adapters.draw` and `adapters.sketch`. The rest of the engine
does not depend on Processing.
