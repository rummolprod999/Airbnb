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
                logger("Error in getCalendar function", e.stackTrace, e, it.Url)
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
        val needMonth = calendar.calendarMonths?.fold(mutableListOf<DayAnb>()) { total, month ->
            total.addAll(month.days ?: throw Exception("month.days is null")); total
        }?.map {
            Day(it.date?.getDateFromString(formatter)
                    ?: throw Exception("it.date?.getDateFromString(formatter) is null $calUrl"), it.available
                    ?: throw Exception("it.available is null $calUrl"), it.minNights
                    ?: throw Exception("it.minNights is null $calUrl"), it.availableForCheckin, it.bookable, (it.price
                    ?: throw Exception("it.price is null $calUrl")).localPriceFormatted
                    ?: throw Exception("it.price.localPriceFormatted is null $calUrl"))
        }?.sortedBy { it.date }
                ?: throw Exception("needMonth is empty, url - $calUrl")
        val firstAv = needMonth.firstOrNull { it.available && it.availableForCheckin ?: false && it.bookable ?: false }
        val price = firstAv?.run { getPrice(firstAv, roomId) }
                ?: Price("недоступно для заказа", "недоступно для заказа", "недоступно для заказа", "недоступно для заказа", "недоступно для заказа", "недоступно для заказа", "недоступно для заказа", "недоступно для заказа", "недоступно для заказа", "недоступно для заказа", "недоступно для заказа", "недоступно для заказа")
        var owner = ""
        var apartName = ""
        if (!existNameAndOwner(room.Id)) {
            /*val pagetext = downloadFromUrl(room.Url)
            if (pagetext == "") {
                logger("pagetext is empty ${room.Url}")
            } else {
                apartName = pagetext.getDataFromRegexp("""<title>(.+)</title>""")
                owner = pagetext.getDataFromRegexp(""""host_name":"(.+?)"""")
            }*/
            val urlListing = "https://www.airbnb.com/api/v1/listings/$roomId?key=d306zoyjsyarp7ifhu67rjxn52tv0t20"
            val jsonListing = downloadFromUrl(urlListing)
            if (jsonListing == "") {
                logger("jsonCal is empty $urlListing")
            }
            val gsonListing = Gson()
            val listing = gsonListing.fromJson(jsonListing, ListingAnb::class.java)
            owner = listing?.listing?.user?.user?.firstName ?: ""
            apartName = listing?.listing?.name ?: ""
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
            throw Exception("jsonPrice is empty")
        }
        val gson = Gson()
        val price = gson.fromJson(jsonPrice, PdpListingBookingDetails::class.java)
        val checkIn = price.pdpListingBookingDetails?.first()?.checkIn ?: ""
        val checkOut = price.pdpListingBookingDetails?.first()?.checkOut ?: ""
        val priceUsd = price.pdpListingBookingDetails?.first()?.price?.priceItems?.first()?.total?.amountFormatted ?: ""

        var checkIn15 = ""
        var checkOut15 = ""
        var priceUsd15 = ""

        var checkIn16 = ""
        var checkOut16 = ""
        var priceUsd16 = ""

        var checkIn30 = ""
        var checkOut30 = ""
        var priceUsd30 = ""

        (0..7).forEach {
            val cDate = if (it == 0) {
                dateNow
            } else {
                dateNow.plusMonths(it.toLong())
            }
            val dayMonth1 = cDate.withDayOfMonth(1)
            val dayMonth15 = cDate.withDayOfMonth(15)
            val priceUrl15 = """https://www.airbnb.com/api/v2/pdp_listing_booking_details?_format=for_web_with_date&_intents=p3_book_it&_interaction_type=pageload&check_in=${dayMonth1.format(customFormatter)}&check_out=${dayMonth15.format(customFormatter)}&currency=USA&force_boost_unc_priority_message_type=&guests=1&key=d306zoyjsyarp7ifhu67rjxn52tv0t20&listing_id=$roomId&locale=ru&number_of_adults=1&number_of_children=0&number_of_infants=0&show_smart_promotion=0"""
            val jsonPrice15 = downloadFromUrl(priceUrl15)
            if (jsonPrice15 == "") {
                logger("jsonPrice15 is empty $priceUrl15")
                throw Exception("jsonPrice15 is empty")
            }
            val gson15 = Gson()
            val price15 = gson15.fromJson(jsonPrice15, PdpListingBookingDetails::class.java)
            checkIn15 += (price15.pdpListingBookingDetails?.first()?.checkIn ?: "") + ","
            checkOut15 += (price15.pdpListingBookingDetails?.first()?.checkOut ?: "") + ","
            priceUsd15 += (price15.pdpListingBookingDetails?.first()?.price?.priceItems?.first()?.total?.amountFormatted
                    ?: "") + ","

            val dayMonth16 = cDate.withDayOfMonth(16)
            val dayMonthLast = if (cDate.month == java.time.Month.FEBRUARY) {
                cDate.withDayOfMonth(cDate.lengthOfMonth())
            } else {
                cDate.withDayOfMonth(30)
            }
            val priceUrl16 = """https://www.airbnb.com/api/v2/pdp_listing_booking_details?_format=for_web_with_date&_intents=p3_book_it&_interaction_type=pageload&check_in=${dayMonth16.format(customFormatter)}&check_out=${dayMonthLast.format(customFormatter)}&currency=USA&force_boost_unc_priority_message_type=&guests=1&key=d306zoyjsyarp7ifhu67rjxn52tv0t20&listing_id=$roomId&locale=ru&number_of_adults=1&number_of_children=0&number_of_infants=0&show_smart_promotion=0"""
            val jsonPrice16 = downloadFromUrl(priceUrl16)
            if (jsonPrice16 == "") {
                logger("jsonPrice16 is empty $priceUrl16")
                throw Exception("jsonPrice16 is empty")
            }
            val gson16 = Gson()
            val price16 = gson16.fromJson(jsonPrice16, PdpListingBookingDetails::class.java)
            checkIn16 += (price16.pdpListingBookingDetails?.first()?.checkIn ?: "") + ","
            checkOut16 += (price16.pdpListingBookingDetails?.first()?.checkOut ?: "") + ","
            priceUsd16 += (price16.pdpListingBookingDetails?.first()?.price?.priceItems?.first()?.total?.amountFormatted
                    ?: "") + ","

            val priceUrl30 = """https://www.airbnb.com/api/v2/pdp_listing_booking_details?_format=for_web_with_date&_intents=p3_book_it&_interaction_type=pageload&check_in=${dayMonth1.format(customFormatter)}&check_out=${dayMonthLast.format(customFormatter)}&currency=USA&force_boost_unc_priority_message_type=&guests=1&key=d306zoyjsyarp7ifhu67rjxn52tv0t20&listing_id=$roomId&locale=ru&number_of_adults=1&number_of_children=0&number_of_infants=0&show_smart_promotion=0"""
            val jsonPrice30 = downloadFromUrl(priceUrl30)
            if (jsonPrice30 == "") {
                logger("jsonPrice30 is empty $priceUrl30")
                throw Exception("jsonPrice30 is empty")
            }
            val gson30 = Gson()
            val price30 = gson30.fromJson(jsonPrice30, PdpListingBookingDetails::class.java)
            checkIn30 += (price30.pdpListingBookingDetails?.first()?.checkIn ?: "") + ","
            checkOut30 += (price30.pdpListingBookingDetails?.first()?.checkOut ?: "") + ","
            priceUsd30 += (price30.pdpListingBookingDetails?.first()?.price?.priceItems?.first()?.total?.amountFormatted
                    ?: "") + ","
        }


        return Price(checkIn, checkOut, priceUsd, checkIn15, checkOut15, priceUsd15, checkIn16, checkOut16, priceUsd16, checkIn30, checkOut30, priceUsd30)
    }
}