package logo.analysis

import logo.lexer.Lexer
import logo.parser.FirstPassScanner
import logo.parser.LogoParser

/**
 * Built-in procedure arities, ported from the procedures map in UCBLogo.g4
 * (which itself is taken from the UCB Logo user manual). Names are lowercase
 * to match the lexer, which lowercases all identifiers.
 *
 * Two grammar entries are intentionally omitted (see README "Scope and
 * Limitations"):
 *  - ":quoted.varname" — starts with ':' which the lexer reserves for
 *    variable refs; would require a hard-coded lexeme rewrite.
 *  - (none other; backtick is included below.)
 */
val BUILTIN_ARITIES: Map<String, Int> = mapOf(
    // ---- constructors ----
    "word" to 2, "list" to 2, "sentence" to 2, "se" to 2,
    "fput" to 2, "lput" to 2,
    "array" to 1, "mdarray" to 1, "listtoarray" to 1, "arraytolist" to 1,
    "combine" to 2, "reverse" to 1, "gensym" to 0,

    // ---- data selectors ----
    "first" to 1, "firsts" to 1, "last" to 1,
    "butfirst" to 1, "bf" to 1, "butfirsts" to 1, "bfs" to 1,
    "butlast" to 1, "bl" to 1,
    "item" to 2, "mditem" to 2,
    "pick" to 1, "remove" to 2, "remdup" to 1, "quoted" to 1,

    // ---- mutators ----
    "setitem" to 3, "mdsetitem" to 3,
    ".setfirst" to 2, ".setbf" to 2, ".setitem" to 3,
    "push" to 2, "pop" to 1, "queue" to 2, "dequeue" to 1,

    // ---- predicates ----
    "wordp" to 1, "word?" to 1,
    "listp" to 1, "list?" to 1,
    "arrayp" to 1, "array?" to 1,
    "emptyp" to 1, "empty?" to 1,
    "equalp" to 2, "equal?" to 2,
    "notequalp" to 2, "notequal?" to 2,
    "beforep" to 2, "before?" to 2,
    ".eq" to 2,
    "memberp" to 2, "member?" to 2,
    "substringp" to 2, "substring?" to 2,
    "numberp" to 1, "number?" to 1,
    "vbarredp" to 1, "vbarred?" to 1,
    "backslashedp" to 1, "backslashed?" to 1,

    // ---- queries ----
    "count" to 1, "ascii" to 1, "rawascii" to 1, "char" to 1,
    "member" to 2, "lowercase" to 1, "uppercase" to 1, "standout" to 1,
    "parse" to 1, "runparse" to 1,

    // ---- I/O ----
    "print" to 1, "pr" to 1, "type" to 1, "show" to 1,
    "readlist" to 0, "rl" to 0,
    "readword" to 0, "rw" to 0,
    "readrawline" to 0,
    "readchar" to 0, "rc" to 0,
    "readchars" to 1, "rcs" to 1,
    "shell" to 1,

    // ---- file system ----
    "setprefix" to 1, "prefix" to 0,
    "openread" to 1, "openwrite" to 1, "openappend" to 1, "openupdate" to 1,
    "close" to 1, "allopen" to 0, "closeall" to 0,
    "erasefile" to 1, "erf" to 1,
    "dribble" to 1, "nodribble" to 0,
    "setread" to 1, "setwrite" to 1, "reader" to 0, "writer" to 0,
    "setreadpos" to 1, "setwritepos" to 1, "readpos" to 0, "writepos" to 0,
    "eofp" to 0, "eof?" to 0, "filep" to 1, "file?" to 1,
    "keyp" to 0, "key?" to 0,

    // ---- terminal / display ----
    "cleartext" to 0, "ct" to 0,
    "setcursor" to 1, "cursor" to 0,
    "setmargins" to 1,
    "settextcolor" to 2, "settc" to 2,
    "increasefont" to 0, "decreasefont" to 0,
    "settextsize" to 1, "textsize" to 0,
    "setfont" to 1, "font" to 0,

    // ---- arithmetic ----
    "sum" to 2, "difference" to 2, "minus" to 1,
    "product" to 2, "quotient" to 2, "remainder" to 2, "modulo" to 2,
    "int" to 1, "round" to 1, "sqrt" to 1, "power" to 2,
    "exp" to 1, "log10" to 1, "ln" to 1,
    "sin" to 1, "radsin" to 1, "cos" to 1, "radcos" to 1,
    "arctan" to 1, "radarctan" to 1,
    "iseq" to 2, "rseq" to 3,

    // ---- numeric predicates ----
    "lessp" to 2, "less?" to 2,
    "greaterp" to 2, "greater?" to 2,
    "lessequalp" to 2, "lessequal?" to 2,
    "greaterequalp" to 2, "greaterequal?" to 2,

    // ---- random / bit ----
    "random" to 1, "rerandom" to 0, "form" to 3,
    "bitand" to 2, "bitor" to 2, "bitxor" to 2, "bitnot" to 1,
    "ashift" to 2, "lshift" to 2,

    // ---- logical ----
    "and" to 2, "or" to 2, "not" to 1,

    // ---- turtle motion ----
    "forward" to 1, "fd" to 1,
    "back" to 1, "bk" to 1,
    "left" to 1, "lt" to 1,
    "right" to 1, "rt" to 1,
    "setpos" to 1, "setxy" to 2,
    "setx" to 1, "sety" to 1,
    "setheading" to 1, "seth" to 1,
    "home" to 0, "arc" to 2,
    "pos" to 0, "xcor" to 0, "ycor" to 0, "heading" to 0,
    "towards" to 1, "scrunch" to 0,

    // ---- turtle / window state ----
    "showturtle" to 0, "st" to 0,
    "hideturtle" to 0, "ht" to 0,
    "clean" to 0, "clearscreen" to 0, "cs" to 0,
    "wrap" to 0, "window" to 0, "fence" to 0,
    "fill" to 0, "filled" to 2,
    "label" to 1, "setlabelheight" to 1,
    "textscreen" to 0, "ts" to 0,
    "fullscreen" to 0, "fs" to 0,
    "splitscreen" to 0, "ss" to 0,
    "setscrunch" to 2,
    "refresh" to 0, "norefresh" to 0,
    "shownp" to 0, "shown?" to 0,
    "screenmode" to 0, "turtlemode" to 0, "labelsize" to 0,

    // ---- pen ----
    "pendown" to 0, "pd" to 0,
    "penup" to 0, "pu" to 0,
    "penpaint" to 0, "ppt" to 0,
    "penerase" to 0, "pe" to 0,
    "penreverse" to 0, "px" to 0,
    "setpencolor" to 1, "setpc" to 1,
    "setpalette" to 2,
    "setpensize" to 1, "setpenpattern" to 1, "setpen" to 1,
    "setbackground" to 1, "setbg" to 1,
    "pendownp" to 0, "pendown?" to 0,
    "penmode" to 0,
    "pencolor" to 0, "pc" to 0,
    "palette" to 1, "pensize" to 0, "penpattern" to 0, "pen" to 0,
    "background" to 0, "bg" to 0,
    "savepict" to 1, "loadpict" to 1, "epspict" to 1,

    // ---- mouse ----
    "mousepos" to 0, "clickpos" to 0,
    "buttonp" to 0, "button?" to 0, "button" to 0,

    // ---- procedure manipulation ----
    "define" to 2, "text" to 1, "fulltext" to 1, "copydef" to 2,
    "make" to 2, "name" to 2,
    "local" to 1, "localmake" to 2,
    "thing" to 1, "global" to 1,

    // ---- properties ----
    "pprop" to 3, "gprop" to 2, "remprop" to 2, "plist" to 1,

    // ---- workspace queries ----
    "procedurep" to 1, "procedure?" to 1,
    "primitivep" to 1, "primitive?" to 1,
    "definedp" to 1, "defined?" to 1,
    "namep" to 1, "name?" to 1,
    "plistp" to 1, "plist?" to 1,
    "contents" to 0, "buried" to 0, "traced" to 0, "stepped" to 0,
    "procedures" to 0, "primitives" to 0, "names" to 0, "plists" to 0,
    "namelist" to 1, "pllist" to 1,
    "arity" to 1, "nodes" to 0,

    // ---- editor / display ----
    "printout" to 1, "po" to 1, "poall" to 0,
    "pops" to 0, "pons" to 0, "popls" to 0,
    "pon" to 1, "popl" to 1, "pot" to 1, "pots" to 0,
    "erase" to 1, "er" to 1, "erall" to 0,
    "erps" to 0, "erns" to 0, "erpls" to 0,
    "ern" to 1, "erpl" to 1,
    "bury" to 1, "buryall" to 0, "buryname" to 1,
    "unbury" to 1, "unburyall" to 0, "unburyname" to 1,
    "buriedp" to 1, "buried?" to 1,
    "trace" to 1, "untrace" to 1,
    "tracedp" to 1, "traced?" to 1,
    "step" to 1, "unstep" to 1,
    "steppedp" to 1, "stepped?" to 1,
    "edit" to 1, "ed" to 1, "editfile" to 1,
    "edall" to 0, "edps" to 0, "edns" to 0, "edpls" to 0,
    "edn" to 1, "edpl" to 1,

    // ---- session / files ----
    "save" to 1, "savel" to 2, "load" to 1, "cslsload" to 1,
    "help" to 1,
    "seteditor" to 1, "setlibloc" to 1, "sethelploc" to 1,
    "setcslsloc" to 1, "settemploc" to 1,
    "gc" to 0, ".setsegmentsize" to 1,

    // ---- control ----
    "run" to 1, "runresult" to 1,
    "repeat" to 2, "forever" to 1, "repcount" to 0,
    "if" to 2, "ifelse" to 3,
    "test" to 1, "iftrue" to 1, "ift" to 1, "iffalse" to 1, "iff" to 1,
    "stop" to 0, "output" to 1, "op" to 1,
    "catch" to 2, "throw" to 1, "error" to 0,
    "pause" to 0, "continue" to 1, "co" to 1,
    "wait" to 1, "bye" to 0,
    ".maybeoutput" to 1,
    "goto" to 1, "tag" to 1, "ignore" to 1,
    "`" to 1, // backquote macro reader

    // ---- loops ----
    "for" to 2, "do.while" to 2, "while" to 2,
    "do.until" to 2, "until" to 2,
    "case" to 2, "cond" to 1,

    // ---- templating ----
    "apply" to 2, "invoke" to 2,
    "foreach" to 2, "map" to 2, "map.se" to 2,
    "filter" to 2, "find" to 2, "reduce" to 2,
    "crossmap" to 2, "cascade" to 3, "cascade.2" to 5,
    "transfer" to 3,

    // ---- macros ----
    ".defmacro" to 2,
    "macrop" to 1, "macro?" to 1, "macroexpand" to 1,
)

fun analyse(source: String): AnalysisResult {
    val tokens = Lexer(source).tokenise()

    val scanner = FirstPassScanner(tokens)
    scanner.scan()

    val arities = BUILTIN_ARITIES + scanner.procedures
    val parser = LogoParser(tokens, arities)
    val ast = parser.parse()

    val symbolResult = SymbolTableBuilder(ast).build()

    return AnalysisResult(ast, symbolResult.table, parser.diagnostics + symbolResult.diagnostics)
}
