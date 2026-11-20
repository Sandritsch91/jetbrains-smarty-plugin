package com.smartyplugin.lang

/** Knowledge about built-in Smarty tags, their attributes and modifiers. */
object SmartyTags {

    enum class Kind { INLINE, BLOCK, MIDDLE }

    data class Tag(
        val name: String,
        val kind: Kind,
        val description: String,
        val attributes: List<String>,
        val takesArguments: Boolean,
        val source: String,
    )

    data class Modifier(val name: String, val signature: String, val source: String)

    private val TAGS = LinkedHashMap<String, Tag>()
    private val MIDDLES = HashMap<String, Set<String>>()
    private val MODIFIERS = LinkedHashMap<String, Modifier>()

    @JvmField
    val SMARTY_VARIABLE_PROPERTIES: List<String> = listOf(
        "now", "const", "config", "capture", "foreach", "section", "template", "template_object",
        "current_dir", "version", "ldelim", "rdelim", "get", "post", "cookies", "server", "env",
        "session", "request", "block",
    )

    @JvmField
    val LOOP_PROPERTIES: List<String> = listOf("index", "iteration", "first", "last", "show", "total", "key")

    init {
        // ---- block tags
        block("if", "Conditional block", true)
        block("foreach", "Loop over an array", true, "from", "item", "key", "name")
        block("section", "Loop over a numerically indexed array", true, "name", "loop", "start", "step", "max", "show")
        block("for", "Simple loop: {for \$i=1 to 10}", true)
        block("while", "Loop while a condition is true", true)
        block("block", "Template inheritance block", true, "name", "append", "prepend", "hide", "nocache")
        block("capture", "Capture output into a variable", true, "name", "assign", "append")
        block("function", "Define a reusable template function", true, "name")
        block("strip", "Remove whitespace and line breaks", false)
        block("literal", "Output content without parsing it", false)
        block("nocache", "Disable caching for the enclosed content", false)
        block("setfilter", "Apply a modifier chain to all output in the block", true)
        block(
            "textformat", "Format a block of text", true,
            "style", "indent", "indent_first", "indent_char", "wrap", "wrap_char", "wrap_cut", "assign",
        )
        block("php", "Embedded PHP (SmartyBC only)", false)

        middle("else", "Else branch", false, "if")
        middle("elseif", "Else-if branch", true, "if")
        middle("foreachelse", "Executed when the array is empty", false, "foreach")
        middle("sectionelse", "Executed when the loop is empty", false, "section")
        middle("forelse", "Executed when the loop does not run", false, "for")

        // ---- inline tags
        inline(
            "include", "Include another template", true,
            "file", "assign", "cache_lifetime", "compile_id", "cache_id", "nocache", "caching", "inline", "scope",
        )
        inline("extends", "Inherit from a parent template", true, "file")
        inline("assign", "Assign a template variable", true, "var", "value", "scope", "nocache")
        inline("append", "Append a value to an array variable", true, "var", "value", "index", "scope", "nocache")
        inline("call", "Call a template function", true, "name", "assign", "nocache")
        inline("config_load", "Load config variables", true, "file", "section", "scope")
        inline("counter", "Print a counter", true, "name", "start", "skip", "direction", "print", "assign")
        inline(
            "cycle", "Cycle through a set of values", true,
            "name", "values", "print", "advance", "delimiter", "assign", "reset",
        )
        inline("debug", "Show the debug console", true, "output")
        inline("eval", "Evaluate a variable as a template", true, "var", "assign")
        inline("fetch", "Fetch a file or URL", true, "file", "assign")
        inline(
            "html_checkboxes", "Generate checkboxes", true,
            "name", "values", "output", "selected", "options", "separator", "assign", "labels",
            "label_ids", "escape",
        )
        inline(
            "html_image", "Generate an <img> tag", true,
            "file", "height", "width", "basedir", "alt", "href", "path_prefix",
        )
        inline(
            "html_options", "Generate <option> tags", true,
            "values", "output", "selected", "options", "name", "strict",
        )
        inline(
            "html_radios", "Generate radio buttons", true,
            "name", "values", "output", "selected", "options", "separator", "assign", "labels",
            "label_ids", "escape",
        )
        inline(
            "html_select_date", "Generate date dropdowns", true,
            "prefix", "time", "start_year", "end_year", "display_days", "display_months",
            "display_years", "month_format", "day_format", "year_as_text", "reverse_years",
            "field_array", "field_order", "field_separator",
        )
        inline(
            "html_select_time", "Generate time dropdowns", true,
            "prefix", "time", "display_hours", "display_minutes", "display_seconds",
            "display_meridian", "use_24_hours", "minute_interval", "second_interval",
        )
        inline(
            "html_table", "Render an array as a table", true,
            "loop", "cols", "rows", "inner", "caption", "table_attr", "th_attr", "tr_attr", "td_attr",
            "trailpad", "hdir", "vdir",
        )
        inline("insert", "Insert non-cached content", true, "name", "assign", "script")
        inline("ldelim", "Output a literal '{'", false)
        inline("rdelim", "Output a literal '}'", false)
        inline(
            "mailto", "Generate a mailto: link", true,
            "address", "text", "encode", "cc", "bcc", "subject", "newsgroups", "followupto", "extra",
        )
        inline("math", "Evaluate a math expression", true, "equation", "format", "assign")
        inline("break", "Leave the current loop", false)
        inline("continue", "Skip to the next loop iteration", false)

        // ---- Yii2 smarty extension (yiisoft/yii2-smarty, yii\smarty\Extension)
        yii("url", "Absolute URL for a route; other attributes become URL parameters (Yii2)", "route")
        yii("path", "Relative URL for a route; other attributes become URL parameters (Yii2)", "route")
        yii("use", "Import a class: static calls, or a widget as function/block (Yii2)", "class", "as", "type")
        yii("set", "Set the view title, theme or the controller layout (Yii2)", "title", "theme", "layout")
        yii(
            "meta", "Register a meta tag; all attributes become tag attributes (Yii2)",
            "name", "content", "keywords", "description", "http-equiv", "charset",
        )
        yii("js", "Assign a yii\\web\\JsExpression to a variable (Yii2)", "expression", "assign")
        yii("registerJsFile", "Register a JavaScript file (Yii2)", "url", "key", "depends", "position")
        yii("registerCssFile", "Register a CSS file (Yii2)", "url", "key", "depends", "media")
        yiiBlock("title", "Set the page title to the block content (Yii2)", false)
        yiiBlock("description", "Register the block content as meta description (Yii2)", false)
        yiiBlock("registerJs", "Register the block content as JavaScript (Yii2)", true, "key", "position")
        yiiBlock("registerCss", "Register the block content as CSS (Yii2)", true, "key")

        // ---- modifiers
        mod("capitalize", "[uc_digits:bool [, lc_rest:bool]]")
        mod("cat", "value")
        mod("count_characters", "[include_whitespace:bool]")
        mod("count_paragraphs", "")
        mod("count_sentences", "")
        mod("count_words", "")
        mod("date_format", "[format [, default_date]]")
        mod("default", "value")
        mod("escape", "[type:'html'|'htmlall'|'url'|'quotes'|'hex'|'javascript'|'mail' [, charset]]")
        mod("from_charset", "[charset]")
        mod("indent", "[count [, char]]")
        mod("lower", "")
        mod("nl2br", "")
        mod("regex_replace", "pattern:replacement")
        mod("replace", "search:replace")
        mod("spacify", "[separator]")
        mod("string_format", "format")
        mod("strip", "[replacement]")
        mod("strip_tags", "[replace_with_space:bool]")
        mod("to_charset", "[charset]")
        mod("truncate", "[length [, etc [, break_words [, middle]]]]")
        mod("unescape", "[type [, charset]]")
        mod("upper", "")
        mod("wordwrap", "[width [, break [, cut]]]")
        mod("json_encode", "[flags]")
        mod("number_format", "[decimals [, dec_point [, thousands_sep]]]")
        mod("count", "", "PHP")
        mod("implode", "separator", "PHP")
        mod("explode", "separator", "PHP")
        mod("trim", "", "PHP")
        mod("ucfirst", "", "PHP")
        mod("ucwords", "", "PHP")
        mod("lcfirst", "", "PHP")
        mod("strtolower", "", "PHP")
        mod("strtoupper", "", "PHP")
        mod("htmlspecialchars", "", "PHP")
        mod("nl2br", "", "PHP")
        mod("intval", "", "PHP")
        mod("floatval", "", "PHP")
        mod("in_array", "array", "PHP")
        mod("is_array", "", "PHP")
        mod("var_dump", "", "PHP")
        mod("print_r", "", "PHP")
        mod("debug_print_var", "")
        mod("void", "", "Yii2") // yii\smarty\Extension: discard the output, e.g. {$this->beginPage()|void}
    }

    // ---------------------------------------------------------------- queries

    @JvmStatic
    fun tag(name: String?): Tag? = if (name == null) null else TAGS[name]

    @JvmStatic
    fun allTags(): Collection<Tag> = TAGS.values.toList()

    @JvmStatic
    fun isBlock(name: String?): Boolean = tag(name)?.kind == Kind.BLOCK

    @JvmStatic
    fun isMiddle(name: String?): Boolean = tag(name)?.kind == Kind.MIDDLE

    @JvmStatic
    fun isKnownInline(name: String?): Boolean = tag(name)?.kind == Kind.INLINE

    /** Whether {middle} may appear directly inside {block}...{/block}. */
    @JvmStatic
    fun isMiddleAllowed(block: String?, middle: String?): Boolean {
        if (block == null || middle == null) return false
        return MIDDLES[middle]?.contains(block) == true
    }

    @JvmStatic
    fun allowedParentsOfMiddle(middle: String): Set<String> = MIDDLES[middle] ?: emptySet()

    @JvmStatic
    fun allModifiers(): Collection<Modifier> = MODIFIERS.values.toList()

    // ---------------------------------------------------------------- registration

    private fun block(name: String, description: String, args: Boolean, vararg attrs: String) {
        TAGS[name] = Tag(name, Kind.BLOCK, description, attrs.toList(), args, "Smarty")
    }

    private fun middle(name: String, description: String, args: Boolean, vararg parents: String) {
        TAGS[name] = Tag(name, Kind.MIDDLE, description, emptyList(), args, "Smarty")
        MIDDLES[name] = parents.toSet()
    }

    private fun inline(name: String, description: String, args: Boolean, vararg attrs: String) {
        TAGS[name] = Tag(name, Kind.INLINE, description, attrs.toList(), args, "Smarty")
    }

    private fun yii(name: String, description: String, vararg attrs: String) {
        TAGS[name] = Tag(name, Kind.INLINE, description, attrs.toList(), true, "Yii2")
    }

    private fun yiiBlock(name: String, description: String, args: Boolean, vararg attrs: String) {
        TAGS[name] = Tag(name, Kind.BLOCK, description, attrs.toList(), args, "Yii2")
    }

    private fun mod(name: String, signature: String, source: String = "Smarty") {
        MODIFIERS.putIfAbsent(name, Modifier(name, signature, source))
    }
}
