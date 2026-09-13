# UML viewer

A live Quil app that lays out and draws UML class diagrams from an EDN IR.
An agent (or a human) writes the IR; this tool displays it, routes the arrows,
colors CRAP, and lets you click.

## Run

Needs Clojure CLI and Java 21+.

```bash
clj -M:run
clj -M:run examples/library.edn
```

- Click a class or package to inspect it.
- Scroll the mouse wheel to pan vertically; Shift-scroll (or left/right arrows) for horizontal.
- `R` reloads the EDN file from disk (the watcher also reloads on save).
- `Esc` clears the selection.

```bash
clj -M:spec
clj -M:cov
clj -M:crap
clj -M:mutate src/uml_viewer/layout.clj
```

## IR

The file is EDN. A document may contain several diagrams (one per layer),
stacked top to bottom. Unique class `:id`s *within* a diagram; packages as
groups; edges by kind. A single diagram (top-level `:packages`) still works.

```edn
{:title "Lending library"
 :direction :tb
 :packages
 [{:id :domain
   :label "Domain"
   :crap {:mu 1.5 :max 4.0 :sigma 0.9}
   :classes
   [{:id :book
     :name "Book"
     :stereotype :class          ;; optional: :interface :enumeration :abstract
     :crap {:mu 1.1 :max 1.0 :sigma 0.0}
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

`:crap` may be a single number (`μ`) or `{:mu :max :sigma}`. Color bands:
≤ 1.5 green, ≤ 2.5 gold, otherwise rust.

Edge `:kind` values:

| kind | line | head |
|------|------|------|
| `:inheritance` | solid | empty triangle |
| `:implements` | dashed | empty triangle |
| `:association` | solid | open arrow |
| `:dependency` | dashed | open arrow |
| `:aggregation` | solid | empty diamond |
| `:composition` | solid | filled diamond |

Layout follows Mermaid's three stages:

1. **Size** each class from its text (padding 12).
2. **Place** with a layered graph (packages in ranks; classes Sugiyama-ranked
   inside a package; ~40px spacing).
3. **Route** in rank channels (one track per edge), then stroke with D3
   `curveBasis` cubics.

The engine (`ir`, `layout`, `route`, `hit`, `events`) does not depend on Quil.
Only `draw` and `sketch` talk to Processing.
