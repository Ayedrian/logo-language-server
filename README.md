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
