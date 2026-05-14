# LOGO Language Server

An LSP (Language Server Protocol) server for the LOGO programming language, written in Kotlin.

Supports:
- Syntax highlighting via semantic tokens
- Go-to-declaration for procedures and variable references

## Requirements

- JDK 21

## Building

Produce a runnable launch script (speaks LSP over stdio):

```bash
./gradlew installDist
```

The script lands at `build/install/logo-language-server/bin/logo-language-server`.

## Trying it in IntelliJ (via LSP4IJ)

1. Install the **LSP4IJ** plugin from the Marketplace and restart the IDE.
2. Open the **Language Servers** tool window (View → Tool Windows → Language Servers).
3. Click `+` → **New Language Server** and fill in:
   - **Name**: `LOGO`
   - **Command**: absolute path to `build/install/logo-language-server/bin/logo-language-server`
   - **Mappings** tab: add a row with file name pattern `*.logo` and language id `logo`
4. Open any `.logo` file. The server boots on first open.

If you make changes to the code: Rebuild with `./gradlew installDist` and right-click the server entry → **Restart**.

## Scope and Limitations

This is a single-file LSP for a (near) total subset of UCBLogo.
The list below documents what the server intentionally does NOT analyze.
Most of these are deliberate trade-offs that keep the static analysis honest
about what dynamic scoping and first-class instruction lists make difficult to handle.

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
