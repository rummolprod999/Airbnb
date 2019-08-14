package anbapp.parsers

import anbapp.documents.AbstractDocument
import anbapp.documents.IDocument
import anbapp.logger.logger


abstract class ParserAbstract {
    fun parse(fn: () -> Unit) {
        logger("Начало парсинга")
        fn()
        logger("Добавили документов ${AbstractDocument.AddTender}")
        logger("Обновили документов ${AbstractDocument.UpdateTender}")
        logger("Конец парсинга")
    }

    fun ParserDocument(t: IDocument) {
        try {
            t.parsing()
        } catch (e: Exception) {
            logger("error in ${t::class.simpleName}.ParserDocument()", e.stackTrace, e)
        }
    }
}