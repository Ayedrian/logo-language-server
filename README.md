# LOGO Language Server

An LSP (Language Server Protocol) server for the LOGO programming language, written in Kotlin.

Supports:
- Syntax highlighting via semantic tokens
- Go-to-declaration for procedures and variable references
- Unused variable warnings
- Simple hover documentation for procedures, parameters and variables

Does NOT support (although I would've loved to work on these and might do so in the future):
- Variable renaming (although partial support exists via NodeAtCursor walker and in SymbolTable)
- Simple code completion
- Many more LSP features of course

## Requirements

- JDK 21

## Building

Produce a runnable launch script (speaks LSP over stdio):

```bash
./gradlew installDist
```

The script lands at `build/install/logo-language-server/bin/logo-language-server`.

## Running tests

```bash
./gradlew test
```

## Trying it in IntelliJ (via LSP4IJ)

1. Install the **LSP4IJ** plugin from the Marketplace and restart the IDE.
2. Open the **Language Servers** tool window (View → Tool Windows → Language Servers).
3. Click `+` → **New Language Server** and fill in:
   - **Name**: `LOGO`
   - **Command**: absolute path to `build/install/logo-language-server/bin/logo-language-server`
   - **Mappings** tab: add a row with file name pattern `*.logo` and language id `logo`
4. Open any `.logo` file. The server boots on first open.

### TextMate grammar

This project has a TextMate grammar at `grammars/logo.tmLanguage.json` that
provides regex-based coloring (comments, numbers, `:variables`, operators,
and the ~300 UCBLogo built-in primitives). This is the base layer the
LSP's semantic tokens build on top of, without it opening a `.logo` file
before the server boots shows plain text.

To install in IntelliJ:
1. Go to **Settings → Editor → TextMate Bundles**.
2. Click `+` and select the repo's `grammars/` directory.
3. Reopen any `.logo` file — basic highlighting kicks in immediately; the LSP
   refines it once the server starts.

If you make changes to the code: Rebuild with `./gradlew installDist` and right-click the server entry → **Restart**.

## Architecture

The server is a six-layer pipeline under `src/main/kotlin/logo/`:

| Layer | Package | Responsibility |
|-------|---------|----------------|
| 1. Lexer | `lexer/` | Source → tokens, position-preserving and case-insensitive |
| 2. Parser | `parser/` | Tokens → AST. Two-pass: a cheap first pass builds the procedure arity table; the second is a recursive-descent parser with statement-level error recovery |
| 3. Symbol table | `analysis/SymbolTable.kt` | Walks the AST, resolves variable refs to bindings (params, `make` / `local` / `localmake`, `for` counters) |
| 4. Pipeline | `analysis/Pipeline.kt` | Wires layers 1-3 into a single `analyse(source)` call; re-runs from scratch on every `didChange` (no incremental parsing) |
| 5. Features | `features/` | LSP-facing handlers: `SemanticTokens.kt`, `Declaration.kt`, `Hover.kt` |
| 6. Server | `server/` | LSP4J wiring: `Main.kt` bootstraps stdio, `LogoLanguageServer.kt` registers capabilities and dispatches requests |

Diagnostics from all pipeline layers flow through `diagnostics/Diagnostic.kt` and are pushed via `textDocument/publishDiagnostics`.

Two decisions worth flagging:

- **Two-pass parser** — LOGO's grammar is context-sensitive: a call's argument count depends on the callee's arity. The first pass builds an arity table so the second pass knows where each expression ends.
- **Error recovery, not error throwing** — the parser never aborts on bad input. It records a diagnostic and skips to the next statement boundary, so the rest of the document still produces semantic tokens and definitions.

## Scope and Limitations

This is a single-file LSP for a (near) total subset of UCBLogo.
The list below documents what the server intentionally does NOT analyze.
Most of these are deliberate trade-offs that keep the static analysis honest
about what dynamic scoping and first-class instruction lists make difficult to handle.

### LSP protocol scope

A handful of LSP protocol features were deliberately placed out of scope:

- **Single-file analysis** — no multi-file LOGO workspace; each file is
  analyzed in isolation.
- **No incremental parsing** — every `textDocument/didChange` re-runs the
  full pipeline (also mentioned in the architecture section)
- **Semantic tokens: `full` only** — the `full/delta` and `range` variants
  are not advertised or implemented
- **No workspace configuration** — `workspace/configuration` and
  `workspace/didChangeConfiguration` are ignored
- **No references / usages** — `textDocument/references` is not implemented;
  only `textDocument/declaration` is supported on the go-to side (which in
  LOGO semantically covers both declaration and definition)

### Lexer

- **`:quoted.varname`** — UCBLogo's special pseudo-procedure (returns a
  variable name as a quoted word) is not recognized. The lexer always treats
  `:name` as a variable reference, and we don't handle this one specific
  lexeme. All other UCBLogo primitives (including the backtick `` ` `` macro reader)
  are recognized.

### Dynamic scoping

LOGO uses dynamic scoping at runtime. We approximate it lexically:

- `:x` resolves against the enclosing procedure's parameters and any preceding
  `make` / `local` / `localmake` binding, plus the `for` counter for refs
  inside a `for` body
- A `:x` ref that fails lexical resolution **but matches a binding anywhere
  in the file** is treated as plausibly dynamically scoped: no warning is
  emitted, but unfortunately **go-to-declaration returns no target** (we cannot statically
  prove which binding the runtime would pick)
- A `:x` that doesn't match any binding in the file gets an "Unbound
  variable" warning.

### First-class instruction lists

`apply`, `invoke`, `run`, `runresult` execute instruction lists as data:

```
run [fd 50 rt 90]
apply "square [5]
```

We can't statically follow the list, so procedure calls reached through these
primitives are invisible to go-to-declaration.

### Programmatic procedure manipulation

`define`, `copydef`, `text`, `fulltext` create or copy procedures at runtime.
Procedures introduced this way are invisible to go-to-declaration — we only
track procedures defined via `to ... end` and `.macro ... end`.

### `for` templatelist

`for [counter start end] [body]` extracts the counter name and seeds it into
the body's scope. The start/end (and optional step) expressions inside the
template are **NOT analyzed**:

```
for [i 1 :n] [print :i]    ; :i resolves to i; :n is silently ignored
```

This is a consequence of how this implementation parses block expressions (statements only,
numbers and variable refs inside `[...]` are not kept as AST nodes). A more
complete fix would require a dedicated `ForLoopNode` AST type with a separate
parse path. The 4-element form `for [counter start end step] [body]` is handled the same
way (counter extracted, rest ignored).

### Built-in vs user-defined

The lexer cannot distinguish user-defined procedures from built-in primitives
— both are just `NAME` tokens. Resolution happens via the merged arity table
built during the first parser pass. Calls to unknown names parse as arity-0
(consume no arguments) and produce no diagnostic.
