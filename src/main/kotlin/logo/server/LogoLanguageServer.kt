package logo.server

import logo.analysis.AnalysisResult
import logo.analysis.analyse
import logo.features.SemanticTokenKind
import logo.features.collectSemanticTokens
import logo.features.encodeSemanticTokens
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import java.util.concurrent.CompletableFuture

class LogoLanguageServer : LanguageServer, LanguageClientAware {

    private var client: LanguageClient? = null
    private val cache = mutableMapOf<String, AnalysisResult>()

    private val textDocumentService = object : TextDocumentService {

        override fun didOpen(params: DidOpenTextDocumentParams) {
            val doc = params.textDocument
            cache[doc.uri] = analyse(doc.text)
        }

        override fun didChange(params: DidChangeTextDocumentParams) {
            val uri = params.textDocument.uri
            cache[uri] = analyse(params.contentChanges.last().text)
        }

        override fun didClose(params: DidCloseTextDocumentParams) {
            cache.remove(params.textDocument.uri)
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

        override fun definition(
            params: DefinitionParams,
        ): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
            // TODO: implement go-to-declaration (currently stubbed)
            return CompletableFuture.completedFuture(Either.forLeft(mutableListOf()))
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
            definitionProvider = Either.forLeft(true)
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
}
