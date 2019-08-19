package anbapp.parsers

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
        val calUrl = "https://www.airbnb.com/api/v2/homes_pdp_availability_calendar?currency=USA&key=d306zoyjsyarp7ifhu67rjxn52tv0t20&locale=en&listing_id=$roomId&month=${dateNow.monthValue}&year=${dateNow.year}&count=3"
        val jsonCal = downloadFromUrl(calUrl)
        if (jsonCal == "") {
            logger("jsonCal is empty $calUrl")
            return
        }
        val gson = Gson()
        val calendar = gson.fromJson(jsonCal, CalendarMonths::class.java)
        val needMonth = calendar.calendarMonths?.filter { it.month == dateNow.monthValue }?.first()?.days?.map { Day(it.date?.getDateFromString(formatter)!!, it.available!!, it.minNights!!, it.availableForCheckin, it.bookable) }?.sortedBy { it.date }
                ?: throw Exception("needMonth is empty, url - $calUrl")
        val firstAv = needMonth.firstOrNull { it.available && it.availableForCheckin ?: false && it.bookable ?: false }
        val price = firstAv?.run { getPrice(firstAv, roomId) } ?: Price("", "", "")

    }

    private fun getPrice(d: Day, roomId: String): Price {
        val minDay = d.date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        val minNextday = minDay.plusDays(d.minNights.toLong())
        val priceUrl = """https://www.airbnb.com/api/v2/pdp_listing_booking_details?_format=for_web_with_date&_intents=p3_book_it&_interaction_type=pageload&check_in=${minDay.format(customFormatter)}&check_out=${minNextday.format(customFormatter)}&currency=USA&force_boost_unc_priority_message_type=&guests=1&key=d306zoyjsyarp7ifhu67rjxn52tv0t20&listing_id=$roomId&locale=ru&number_of_adults=1&number_of_children=0&number_of_infants=0&show_smart_promotion=0"""
        val jsonPrice = downloadFromUrl(priceUrl)
        if (jsonPrice == "") {
            logger("jsonPrice is empty $priceUrl")
            return Price("", "", "")
        }
        val gson = Gson()
        val price = gson.fromJson(jsonPrice, PdpListingBookingDetails::class.java)
        val checkIn = price.pdpListingBookingDetails?.first()?.checkIn ?: ""
        val checkOut = price.pdpListingBookingDetails?.first()?.checkOut ?: ""
        val priceUsd = price.pdpListingBookingDetails?.first()?.price?.priceItems?.first()?.total?.amountFormatted ?: ""
        return Price(checkIn, checkOut, priceUsd)
    }
}