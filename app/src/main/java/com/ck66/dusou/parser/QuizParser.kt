package com.ck66.dusou.parser

import com.ck66.dusou.parser.model.ParsedQuestion

interface QuizParser {
    fun parse(content: String): List<ParsedQuestion>
    fun supportedFormats(): List<String>
}
