package anbapp.parsers

import anbapp.documents.AnbDocument
import anbapp.exstensions.getDataFromRegexp
import anbapp.exstensions.getDateFromString
import anbapp.httpTools.downloadFromUrl
import anbapp.logger.logger
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ParserAnbNew : IParser, ParserAbstract() {
    companion object WebCl {
        val dateNow = LocalDate.now()
        val customFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        var formatter = SimpleDateFormat("yyyy-MM-dd")
    }

    override fun parser() = parse {
        parserAnb()
    }

    private fun parserAnb() {
        getUrlArray().forEach {
            try {
                getCalendar(it)
                Thread.sleep(10_000L)
            } catch (e: Exception) {
                logger("Error in getCalendar function", e.stackTrace, e)
            }
        }
    }

    private fun getCalendar(room: RoomAnb) {
        val roomId = room.Url.getDataFromRegexp("""rooms/(\d+)""")
        if (roomId == "") {
            logger("Bad Url, roomId was not found ${room.Url}")
            return
        }
        val calUrl = "https://www.airbnb.com/api/v2/homes_pdp_availability_calendar?currency=USA&key=d306zoyjsyarp7ifhu67rjxn52tv0t20&locale=en&listing_id=$roomId&month=${dateNow.monthValue}&year=${dateNow.year}&count=8"
        val jsonCal = downloadFromUrl(calUrl)
        if (jsonCal == "") {
            logger("jsonCal is empty $calUrl")
            return
        }
        val gson = Gson()
        val calendar = gson.fromJson(jsonCal, CalendarMonths::class.java)
        val needMonth = calendar.calendarMonths?.fold(mutableListOf<DayAnb>()) { total, month -> total.addAll(month.days!!); total }?.map { Day(it.date?.getDateFromString(formatter)!!, it.available!!, it.minNights!!, it.availableForCheckin, it.bookable, it.price!!.localPriceFormatted!!) }?.sortedBy { it.date }
                ?: throw Exception("needMonth is empty, url - $calUrl")
        val firstAv = needMonth.firstOrNull { it.available && it.availableForCheckin ?: false && it.bookable ?: false }
        val price = firstAv?.run { getPrice(firstAv, roomId) }
                ?: Price("недоступно для заказа", "недоступно для заказа", "недоступно для заказа")
        var owner = ""
        var apartName = ""
        if (!existNameAndOwner(room.Id)) {
            val pagetext = downloadFromUrl(room.Url)
            if (pagetext == "") {
                logger("pagetext is empty ${room.Url}")
            } else {
                apartName = pagetext.getDataFromRegexp("""<title>(.+)</title>""")
                owner = pagetext.getDataFromRegexp(""""host_name":"(.+?)"""")
            }
        }
        room.price = price
        room.owner = owner
        room.appName = apartName
        room.calendars = needMonth
        ParserDocument(AnbDocument(room))
    }

    private fun getPrice(d: Day, roomId: String): Price {
        val minDay = d.date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val minNextday = minDay.plusDays(d.minNights.toLong())
        val priceUrl = """https://www.airbnb.com/api/v2/pdp_listing_booking_details?_format=for_web_with_date&_intents=p3_book_it&_interaction_type=pageload&check_in=${minDay.format(customFormatter)}&check_out=${minNextday.format(customFormatter)}&currency=USA&force_boost_unc_priority_message_type=&guests=1&key=d306zoyjsyarp7ifhu67rjxn52tv0t20&listing_id=$roomId&locale=ru&number_of_adults=1&number_of_children=0&number_of_infants=0&show_smart_promotion=0"""
        val jsonPrice = downloadFromUrl(priceUrl)
        if (jsonPrice == "") {
            logger("jsonPrice is empty $priceUrl")
            return Price("ошибка", "ошибка", "ошибка")
        }
        val gson = Gson()
        val price = gson.fromJson(jsonPrice, PdpListingBookingDetails::class.java)
        val checkIn = price.pdpListingBookingDetails?.first()?.checkIn ?: ""
        val checkOut = price.pdpListingBookingDetails?.first()?.checkOut ?: ""
        val priceUsd = price.pdpListingBookingDetails?.first()?.price?.priceItems?.first()?.total?.amountFormatted ?: ""
        return Price(checkIn, checkOut, priceUsd)
    }
}