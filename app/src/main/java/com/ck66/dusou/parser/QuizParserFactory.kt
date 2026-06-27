package com.ck66.dusou.parser

object QuizParserFactory {

    fun getParser(format: String): QuizParser? {
        return when (format.lowercase().trim()) {
            "txt" -> SmartTxtParser()
            "json" -> JsonQuizParser()
            "csv" -> CsvQuizParser()
            "jsonl", "ndjson" -> NdJsonQuizParser()
            else -> null
        }
    }

    fun getParserForFile(fileName: String): QuizParser? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return getParser(extension)
    }
}
