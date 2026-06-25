package com.ck66.dusou.parser

object QuizParserFactory {

    fun getParser(format: String): QuizParser? {
        return when (format.lowercase().trim()) {
            "txt" -> TxtQuizParser()
            "json" -> JsonQuizParser()
            "csv" -> CsvQuizParser()
            else -> null
        }
    }

    fun getParserForFile(fileName: String): QuizParser? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return getParser(extension)
    }
}
