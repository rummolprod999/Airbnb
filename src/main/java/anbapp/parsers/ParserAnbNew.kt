package anbapp.parsers

import anbapp.builderApp.BuilderApp
import anbapp.builderApp.endDayInterval
import anbapp.builderApp.startDayInterval
import anbapp.documents.AnbDocument
import anbapp.exstensions.getDataFromRegexp
import anbapp.exstensions.getDateFromString
import anbapp.httpTools.downloadFromUrl
import anbapp.logger.logger
import anbapp.sender.ISender
import com.google.gson.Gson
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class ParserAnbNew(val sender: ISender) : IParser, ParserAbstract() {
    companion object WebCl {
        val dateNow = LocalDate.now()
        val customFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        var formatter = SimpleDateFormat("yyyy-MM-dd")
        var currDate = Date()
    }

    override fun parser() = parse {
        clearanalitic()
        parserAnb()
        checkIfNotFirst()
        sendEmail()
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

    private fun sendEmail() {
        try {
            sender.send()
        } catch (e: Exception) {
            logger(e)
        }
    }

    private fun clearanalitic() {
        DriverManager.getConnection(BuilderApp.UrlConnect, BuilderApp.UserDb, BuilderApp.PassDb).use(fun(con: Connection) {
            con.prepareStatement("DELETE FROM analitic WHERE id_user = ?").apply {
                setInt(1, BuilderApp.UserId)
                executeUpdate()
                close()
            }
        })
    }

    private fun checkIfNotFirst() {
        DriverManager.getConnection(BuilderApp.UrlConnect, BuilderApp.UserDb, BuilderApp.PassDb).use(fun(con: Connection) {
            con.prepareStatement("DELETE FROM date_not_first WHERE id_user = ?").apply {
                setInt(1, BuilderApp.UserId)
                executeUpdate()
                close()
            }
            con.prepareStatement("DELETE FROM date_not_first_count WHERE id_user = ?").apply {
                setInt(1, BuilderApp.UserId)
                executeUpdate()
                close()
            }
            con.prepareStatement("DELETE FROM intervals_count WHERE id_user = ?").apply {
                setInt(1, BuilderApp.UserId)
                executeUpdate()
                close()
            }
            var analitycsList = mutableListOf<Analitycs>()
            (startDayInterval..endDayInterval).forEach {
                val stmt0 = con.prepareStatement("SELECT a.start_date, a.end_date FROM analitic a WHERE a.perid_nights = ? AND a.id_user = ? GROUP BY a.start_date, a.end_date ORDER BY a.start_date").apply {
                    setInt(1, it)
                    setInt(2, BuilderApp.UserId)
                }
                val p0 = stmt0.executeQuery()
                while (p0.next()) {
                    val stDt = p0.getTimestamp(1).toLocalDateTime().toLocalDate()
                    val ndDt = p0.getTimestamp(2).toLocalDateTime().toLocalDate()
                    val stmt1 = con.prepareStatement("SELECT a.price, au.id, au.own  FROM analitic a JOIN anb_url au on a.id_url = au.id WHERE a.start_date = ? AND a.end_date = ? AND a.id_user = ? ORDER BY  a.price").apply {
                        setTimestamp(1, Timestamp.valueOf(stDt.atStartOfDay()))
                        setTimestamp(2, Timestamp.valueOf(ndDt.atStartOfDay()))
                        setInt(3, BuilderApp.UserId)
                    }
                    val analitycs = Analitycs(stDt, ndDt, mutableListOf())
                    val p1 = stmt1.executeQuery()
                    while (p1.next()) {
                        val price = p1.getInt(1)
                        val idUrl = p1.getInt(2)
                        val own = p1.getInt(3)
                        analitycs.prices.add(AnalitycsPrice(price, idUrl, own))
                    }
                    p1.close()
                    stmt1.close()
                    analitycsList.add(analitycs)
                }
                p0.close()
                stmt0.close()
            }
            analitycsList = analitycsList.map { t -> Analitycs(t.startDate, t.endDate, t.prices.sortedBy { x -> x.price }.toMutableList()) }.filter { m -> m.prices.first().own == 0 }.toMutableList()
            fun r(s: LocalDate, n: LocalDate) = (s.dayOfMonth..n.dayOfMonth).toList()
            fun ss(s: LocalDate, n: LocalDate): List<Int> {
                var ss = s
                val c = mutableListOf<Int>()
                c.add(ss.dayOfMonth)
                while (ss.isBefore(n)) {
                    ss = ss.plusDays(1)
                    c.add(ss.dayOfMonth)
                }
                return c
            }

            val inter = analitycsList.map { x -> AnalitycsInterval(x.startDate, x.endDate, ss(x.startDate, x.endDate)) }
            inter.forEach { t ->
                con.prepareStatement("INSERT INTO date_not_first SET start_date = ?, end_date = ?, days = ?, id_user = ?", Statement.RETURN_GENERATED_KEYS).apply {
                    setTimestamp(1, Timestamp.valueOf(t.startDate.atStartOfDay()))
                    setTimestamp(2, Timestamp.valueOf(t.endDate.atStartOfDay()))
                    setString(3, t.days.fold("") { total, next -> "$total ${next}," })
                    setInt(4, BuilderApp.UserId)
                    executeUpdate()
                }
            }
            val cIntervals = mutableListOf<CountInterval>()
            (1..31).forEach { x ->
                val anInt = inter.filter { v -> v.days.contains(x) }
                cIntervals.add(CountInterval(x, anInt.count(), anInt))
            }
            cIntervals.sortByDescending { v -> v.count }
            cIntervals.forEach { w ->
                val stmt3 = con.prepareStatement("INSERT INTO date_not_first_count SET day_month = ?, count_m = ?, id_user = ?", Statement.RETURN_GENERATED_KEYS).apply {
                    setInt(1, w.day)
                    setInt(2, w.count)
                    setInt(3, BuilderApp.UserId)
                    executeUpdate()
                }
                var idInt = 0
                val rsoi = stmt3.generatedKeys
                if (rsoi.next()) {
                    idInt = rsoi.getInt(1)
                }
                stmt3.close()
                rsoi.close()
                w.intervals.forEach { n ->
                    con.prepareStatement("INSERT INTO intervals_count SET date_start = ?, date_end = ?, id_count = ?, id_user = ?").apply {
                        setTimestamp(1, Timestamp.valueOf(n.startDate.atStartOfDay()))
                        setTimestamp(2, Timestamp.valueOf(n.endDate.atStartOfDay()))
                        setInt(3, idInt)
                        setInt(4, BuilderApp.UserId)
                        executeUpdate()
                        close()
                    }
                }
            }
        })
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
                    ?: throw Exception("it.price is null $calUrl")).localPriceFormatted?.replace("\$", "")?.trim()
                    ?: throw Exception("it.price.localPriceFormatted is null $calUrl"))
        }?.sortedBy { it.date }
                ?: throw Exception("needMonth is empty, url - $calUrl")
        val firstAv = needMonth.firstOrNull { it.available && it.availableForCheckin ?: false && it.bookable ?: false }
        val price = firstAv?.run { getPrice(firstAv, roomId) }
                ?: Price("not bookable", "not bookable", "not bookable", "not bookable", "not bookable", "not bookable", "not bookable", "not bookable", "not bookable", "not bookable", "not bookable", "not bookable")
        var owner = ""
        var apartName = ""
        if (!existNameAndOwner(room.Id)) {
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
        val cdisc = getCleaningAndDiscount(roomId)
        room.price = price
        room.owner = owner
        room.appName = apartName
        room.calendars = needMonth
        room.cl = cdisc
        ParserDocument(AnbDocument(room))
    }

    private fun getCleaningAndDiscount(roomId: String): CleanDisc {
        val priceDiscount7 = """https://www.airbnb.com/api/v2/pdp_listing_booking_details?_format=for_web_with_date&_intents=p3_book_it&_interaction_type=pageload&check_in=${dateNow.format(customFormatter)}&check_out=${dateNow.plusDays(7).format(customFormatter)}&currency=USA&force_boost_unc_priority_message_type=&guests=1&key=d306zoyjsyarp7ifhu67rjxn52tv0t20&listing_id=$roomId&locale=en&number_of_adults=1&number_of_children=0&number_of_infants=0&show_smart_promotion=0"""
        val jsonPrice7 = downloadFromUrl(priceDiscount7)
        if (jsonPrice7 == "") {
            logger("jsonPrice7 is empty $priceDiscount7")
            throw Exception("jsonPrice7 is empty")
        }
        val priceDiscount28 = """https://www.airbnb.com/api/v2/pdp_listing_booking_details?_format=for_web_with_date&_intents=p3_book_it&_interaction_type=pageload&check_in=${dateNow.format(customFormatter)}&check_out=${dateNow.plusDays(28).format(customFormatter)}&currency=USA&force_boost_unc_priority_message_type=&guests=1&key=d306zoyjsyarp7ifhu67rjxn52tv0t20&listing_id=$roomId&locale=en&number_of_adults=1&number_of_children=0&number_of_infants=0&show_smart_promotion=0"""
        val jsonPrice28 = downloadFromUrl(priceDiscount28)
        if (jsonPrice28 == "") {
            logger("jsonPrice28 is empty $priceDiscount28")
            throw Exception("jsonPrice28 is empty")
        }
        val gson7 = Gson()
        val price7 = gson7.fromJson(jsonPrice7, PdpListingBookingDetails::class.java)
        val gson28 = Gson()
        val price28 = gson28.fromJson(jsonPrice28, PdpListingBookingDetails::class.java)
        val cleaningAm = price7.pdpListingBookingDetails?.firstOrNull()?.price?.priceItems?.firstOrNull { it.type == "CLEANING_FEE" }?.total?.amount
                ?: 0
        val listDiscount = mutableListOf<String>()
        price7.pdpListingBookingDetails?.firstOrNull()?.price?.priceItems?.filter { it.type == "DISCOUNT" }?.forEach {
            if (it.localizedTitle != null && it.localizedTitle != "") {
                listDiscount.add(it.localizedTitle!!)
            }
        }
        price28.pdpListingBookingDetails?.firstOrNull()?.price?.priceItems?.filter { it.type == "DISCOUNT" }?.forEach {
            if (it.localizedTitle != null && it.localizedTitle != "") {
                listDiscount.add(it.localizedTitle!!)
            }
        }
        return CleanDisc(listDiscount, cleaningAm)
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
        val priceUsd = price.pdpListingBookingDetails?.first()?.price?.priceItems?.first()?.total?.amountFormatted
                ?: ""

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