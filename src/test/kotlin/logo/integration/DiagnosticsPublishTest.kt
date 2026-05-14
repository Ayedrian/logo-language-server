package logo.integration

import logo.server.LogoLanguageServer
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.eclipse.lsp4j.DiagnosticSeverity as LspDiagnosticSeverity

/**
 * Captures publishDiagnostics calls from the server; other LanguageClient methods are no-ops
 */
private class CapturingClient : LanguageClient {
    val published = mutableListOf<PublishDiagnosticsParams>()

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
        published += diagnostics
    }

    override fun telemetryEvent(`object`: Any?) = Unit
    override fun showMessage(params: MessageParams?) = Unit
    override fun logMessage(params: MessageParams?) = Unit
    override fun showMessageRequest(params: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> =
        CompletableFuture.completedFuture(null)
}

class DiagnosticsPublishTest {

    private fun newServer(): Pair<LogoLanguageServer, CapturingClient> {
        val server = LogoLanguageServer()
        val client = CapturingClient()
        server.connect(client)
        return server to client
    }

    @Test
    fun `didOpen publishes warning for unbound variable`() {
        val (server, client) = newServer()
        val uri = "file:///test.logo"
        // line 0: print :foo (the :foo at char 6 is unbound)
        server.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "logo", 1, "print :foo")),
        )

        val params = client.published.single()
        assertEquals(uri, params.uri)
        val d = params.diagnostics.single()
        assertEquals(LspDiagnosticSeverity.Warning, d.severity)
        assertEquals("logo", d.source)
        assertEquals(0, d.range.start.line)
        assertEquals(6, d.range.start.character)
        assertEquals(10, d.range.end.character) // 6 + ":foo".length
    }

    @Test
    fun `didChange publishes updated diagnostics`() {
        val (server, client) = newServer()
        val uri = "file:///test.logo"
        server.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "logo", 1, "fd 1")),
        )
        // Clean source -> empty diagnostics on open
        assertTrue(client.published.last().diagnostics.isEmpty())

        server.textDocumentService.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(uri, 2),
                listOf(TextDocumentContentChangeEvent("print :bad")),
            ),
        )
        // Latest publish now carries the unbound-variable warning
        assertEquals(1, client.published.last().diagnostics.size)
    }

    @Test
    fun `didClose publishes empty diagnostics to clear stale squiggles`() {
        val (server, client) = newServer()
        val uri = "file:///test.logo"
        server.textDocumentService.didOpen(
            DidOpenTextDocumentParams(TextDocumentItem(uri, "logo", 1, "print :foo")),
        )
        client.published.clear()

        server.textDocumentService.didClose(
            DidCloseTextDocumentParams(TextDocumentIdentifier(uri)),
        )

        val params = client.published.single()
        assertEquals(uri, params.uri)
        assertTrue(params.diagnostics.isEmpty())
    }
}
