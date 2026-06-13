package com.example.ui.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color

class CodeSyntaxVisualTransformation(
    private val language: String,
    private val isDarkTheme: Boolean = true,
    private val isAmoled: Boolean = false
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val annotatedString = highlightCode(text.text, language, isDarkTheme, isAmoled)
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }

    private fun highlightCode(
        code: String,
        lang: String,
        isDark: Boolean,
        isAmoled: Boolean
    ): AnnotatedString {
        val builder = AnnotatedString.Builder(code)
        
        // Define clean theme token colors
        val colorQuote = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D) // Lime green/Dark green
        val colorKeyword = if (isDark) Color(0xFFC084FC) else Color(0xFF7E22CE) // Purple/Violet
        val colorComment = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8) // Muted slate/Grey
        val colorNumber = if (isDark) Color(0xFFFB923C) else Color(0xFFC2410C) // Amber/Rust
        val colorTag = if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1) // Soft neon blue/Navy
        val colorAttr = if (isDark) Color(0xFFFFB86C) else Color(0xFFD97706) // Yellow orange

        val normalizedLang = lang.lowercase()

        // 1. Highlight standard strings and quotes across all files
        val quoteRegex = Regex("(\"[^\"]*\")|('[^']*')")
        for (match in quoteRegex.findAll(code)) {
            builder.addStyle(SpanStyle(color = colorQuote), match.range.first, match.range.last + 1)
        }

        // 2. Comments regex
        val commentRegex = if (normalizedLang == "html" || normalizedLang == "xml" || normalizedLang == "markdown") {
            Regex("<!--[\\s\\S]*?-->")
        } else if (normalizedLang == "python") {
            Regex("#.*")
        } else {
            Regex("(//.*)|(/\\*[\\s\\S]*?\\*/)")
        }
        for (match in commentRegex.findAll(code)) {
            builder.addStyle(SpanStyle(color = colorComment), match.range.first, match.range.last + 1)
        }

        // Apply language-specific matching rules
        when (normalizedLang) {
            "html", "xml" -> {
                // Highlight Tags like <html>, </p>, <button id="btn">
                val tagRegex = Regex("<[^>]+>")
                for (match in tagRegex.findAll(code)) {
                    val start = match.range.first
                    val end = match.range.last + 1
                    
                    // Style brackets and tag name
                    builder.addStyle(SpanStyle(color = colorTag, fontWeight = FontWeight.Bold), start, end)
                    
                    // Inside tags, highlight attributes with peach/orange coloring
                    val attrRegex = Regex("\\s[a-zA-Z-0-9]+=")
                    for (attrMatch in attrRegex.findAll(match.value)) {
                        builder.addStyle(
                            SpanStyle(color = colorAttr),
                            start + attrMatch.range.first,
                            start + attrMatch.range.last
                        )
                    }
                }
            }
            "css" -> {
                // Style CSS classes, properties and values
                val propertyRegex = Regex("[a-zA-Z-]+(?=\\s*:)")
                for (match in propertyRegex.findAll(code)) {
                    builder.addStyle(SpanStyle(color = colorTag), match.range.first, match.range.last + 1)
                }
                
                val selectorRegex = Regex("[.#][a-zA-Z0-9_-]+")
                for (match in selectorRegex.findAll(code)) {
                    builder.addStyle(SpanStyle(color = colorKeyword, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                }
                
                val numberRegex = Regex("\\b\\d+(px|em|rem|%|vh|vw|s|ms)?\\b")
                for (match in numberRegex.findAll(code)) {
                    builder.addStyle(SpanStyle(color = colorNumber), match.range.first, match.range.last + 1)
                }
            }
            "javascript", "js", "json" -> {
                val keywords = listOf(
                    "const", "let", "var", "function", "return", "if", "else", "for", "while",
                    "import", "export", "from", "class", "extends", "new", "this", "true", "false", "null", "async", "await"
                )
                val keywordRegex = Regex("\\b(${keywords.joinToString("|")})\\b")
                for (match in keywordRegex.findAll(code)) {
                    builder.addStyle(SpanStyle(color = colorKeyword, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                }
                
                val numberRegex = Regex("\\b\\d+\\b")
                for (match in numberRegex.findAll(code)) {
                    builder.addStyle(SpanStyle(color = colorNumber), match.range.first, match.range.last + 1)
                }
            }
            "python" -> {
                val keywords = listOf(
                    "def", "class", "return", "if", "elif", "else", "for", "while", "import", "from",
                    "as", "try", "except", "with", "print", "and", "or", "not", "in", "is", "True", "False", "None"
                )
                val keywordRegex = Regex("\\b(${keywords.joinToString("|")})\\b")
                for (match in keywordRegex.findAll(code)) {
                    builder.addStyle(SpanStyle(color = colorKeyword, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                }

                val numberRegex = Regex("\\b\\d+\\b")
                for (match in numberRegex.findAll(code)) {
                    builder.addStyle(SpanStyle(color = colorNumber), match.range.first, match.range.last + 1)
                }
            }
            "java", "cpp", "c++" -> {
                val keywords = listOf(
                    "public", "private", "protected", "class", "void", "static", "final", "int", "float", "double",
                    "char", "boolean", "if", "else", "for", "while", "return", "import", "package", "new", "this", "super",
                    "include", "using", "namespace", "std", "cout", "endl"
                )
                val keywordRegex = Regex("\\b(${keywords.joinToString("|")})\\b")
                for (match in keywordRegex.findAll(code)) {
                    builder.addStyle(SpanStyle(color = colorKeyword, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                }

                val numberRegex = Regex("\\b\\d+\\b")
                for (match in numberRegex.findAll(code)) {
                    builder.addStyle(SpanStyle(color = colorNumber), match.range.first, match.range.last + 1)
                }
            }
            "markdown", "md" -> {
                // Style markdown headings #, ##, bold **, list items
                val headerRegex = Regex("^#{1,6}\\s+.*", RegexOption.MULTILINE)
                for (match in headerRegex.findAll(code)) {
                    builder.addStyle(SpanStyle(color = colorTag, fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                }

                val boldRegex = Regex("\\*\\*.*?\\*\\*")
                for (match in boldRegex.findAll(code)) {
                    builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), match.range.first, match.range.last + 1)
                }

                val codeBlockRegex = Regex("`.*?`")
                for (match in codeBlockRegex.findAll(code)) {
                    builder.addStyle(SpanStyle(color = colorQuote), match.range.first, match.range.last + 1)
                }
            }
        }
        
        return builder.toAnnotatedString()
    }
}
