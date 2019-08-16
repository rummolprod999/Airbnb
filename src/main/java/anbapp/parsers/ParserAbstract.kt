package anbapp.parsers

import anbapp.builderApp.BuilderApp
import anbapp.documents.AbstractDocument
import anbapp.documents.IDocument
import anbapp.logger.logger
import com.google.gson.annotations.SerializedName
import java.sql.Connection
import java.sql.DriverManager
import java.util.*


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

    class DayAnb {
        var date: String? = null
        var available: Boolean? = null
        @SerializedName("min_nights")
        var minNights: Int? = null
        @SerializedName("available_for_checkin")
        var availableForCheckin: Boolean? = null
        var bookable: Boolean? = null
    }

    class CalendarMonths {
        @SerializedName("calendar_months")
        var calendarMonths: ArrayList<Month>? = null
    }

    class Month {
        var month: Int? = null
        var year: Int? = null
        var days: ArrayList<DayAnb>? = null
    }

    data class Day(val date: Date, val available: Boolean, val minNights: Int, val availableForCheckin: Boolean?, val bookable: Boolean?) {
    }
}