package anbapp.parsers

import anbapp.builderApp.BuilderApp
import anbapp.documents.AbstractDocument
import anbapp.documents.IDocument
import anbapp.logger.logger
import java.sql.Connection
import java.sql.DriverManager


abstract class ParserAbstract {
    data class RoomAnb(val Id: Int, val Url: String, var calendars: List<String>)

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

    fun getUrlArray(): MutableList<RoomAnb> {
        val arr = mutableListOf<RoomAnb>()

        DriverManager.getConnection(BuilderApp.UrlConnect, BuilderApp.UserDb, BuilderApp.PassDb).use(fun(con: Connection) {
            val res = con.prepareStatement("SELECT id, url FROM anb_url").executeQuery()
            while (res.next()) {
                val id = res.getInt(1)
                val url = res.getString(2)
                arr.add(RoomAnb(id, url, listOf()))
            }
        })
        return arr
    }
}