package logo.server

import logo.analysis.AnalysisResult
import logo.analysis.analyse
import logo.features.SemanticTokenKind
import logo.features.collectSemanticTokens
import logo.features.encodeSemanticTokens
import logo.features.findDeclaration
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture
import logo.diagnostics.Diagnostic as LogoDiagnostic
import logo.diagnostics.DiagnosticSeverity as LogoDiagnosticSeverity
import org.eclipse.lsp4j.Diagnostic as LspDiagnostic
import org.eclipse.lsp4j.DiagnosticSeverity as LspDiagnosticSeverity

class LogoLanguageServer : LanguageServer, LanguageClientAware {

    private var client: LanguageClient? = null
    private val cache = mutableMapOf<String, AnalysisResult>()

    private val textDocumentService = object : TextDocumentService {

        override fun didOpen(params: DidOpenTextDocumentParams) {
            val doc = params.textDocument
            val result = analyse(doc.text)
            cache[doc.uri] = result
            publish(doc.uri, result)
        }

        override fun didChange(params: DidChangeTextDocumentParams) {
            val uri = params.textDocument.uri
            val result = analyse(params.contentChanges.last().text)
            cache[uri] = result
            publish(uri, result)
        }

        override fun didClose(params: DidCloseTextDocumentParams) {
            val uri = params.textDocument.uri
            cache.remove(uri)
            // Clear any stale squiggles left in the editor for this file
            client?.publishDiagnostics(PublishDiagnosticsParams(uri, emptyList()))
        }

        override fun didSave(params: DidSaveTextDocumentParams) = Unit

        override fun semanticTokensFull(
            params: SemanticTokensParams,
        ): CompletableFuture<SemanticTokens> {
            val result = cache[params.textDocument.uri]
            val data = if (result != null) encodeSemanticTokens(collectSemanticTokens(result.ast))
                       else emptyList()
            return CompletableFuture.completedFuture(SemanticTokens(data))
        }

        override fun declaration(
            params: DeclarationParams,
        ): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
            val uri = params.textDocument.uri
            val result = cache[uri]
            val pos = params.position
            val target = result?.let { findDeclaration(it.ast, it.symbolTable, pos.line, pos.character) }
            val locations: MutableList<out Location> = if (target != null) {
                val r = target.range
                mutableListOf(Location(uri, Range(Position(r.line, r.startChar), Position(r.line, r.endChar))))
            } else mutableListOf()
            return CompletableFuture.completedFuture(Either.forLeft(locations))
        }
    }

    /**
     * Workspace configuration is currently not supported
     */
    private val workspaceService = object : WorkspaceService {
        override fun didChangeConfiguration(params: DidChangeConfigurationParams) = Unit
        override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) = Unit
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        val legend = SemanticTokensLegend(
            SemanticTokenKind.entries.sortedBy { it.index }.map { it.name.lowercase() },
            emptyList(),
        )
        val capabilities = ServerCapabilities().apply {
            semanticTokensProvider = SemanticTokensWithRegistrationOptions(legend, SemanticTokensServerFull(false))
            declarationProvider = Either.forLeft(true)
            textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
        }
        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun initialized(params: InitializedParams) = Unit

    override fun shutdown(): CompletableFuture<Any> = CompletableFuture.completedFuture(null)

    override fun exit() = Unit

    override fun getTextDocumentService(): TextDocumentService = textDocumentService
    override fun getWorkspaceService(): WorkspaceService = workspaceService
    override fun connect(client: LanguageClient) { this.client = client }

    private fun publish(uri: String, result: AnalysisResult) {
        val lspDiagnostics = result.diagnostics.map { it.toLsp() }
        client?.publishDiagnostics(PublishDiagnosticsParams(uri, lspDiagnostics))
    }

    private fun LogoDiagnostic.toLsp(): LspDiagnostic {
        val start = Position(line, char)
        val end = Position(line, char + length)
        val lspSeverity = when (severity) {
            LogoDiagnosticSeverity.ERROR -> LspDiagnosticSeverity.Error
            LogoDiagnosticSeverity.WARNING -> LspDiagnosticSeverity.Warning
        }
        return LspDiagnostic(Range(start, end), message, lspSeverity, "logo")
    }
}
