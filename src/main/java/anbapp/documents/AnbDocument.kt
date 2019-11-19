package anbapp.documents

import anbapp.builderApp.BuilderApp
import anbapp.exstensions.getDataFromRegexp
import anbapp.logger.logger
import anbapp.parsers.ParserAbstract
import anbapp.parsers.ParserAnbNew
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class AnbDocument(private val d: ParserAbstract.RoomAnb) : IDocument, AbstractDocument() {
    override fun parsing() {
        DriverManager.getConnection(BuilderApp.UrlConnect, BuilderApp.UserDb, BuilderApp.PassDb).use(fun(con: Connection) {
            if (d.owner != "" || d.appName != "") {
                con.prepareStatement("UPDATE anb_url SET owner = ?, apartment_name = ? WHERE id = ?").apply {
                    setString(1, d.owner)
                    setString(2, d.appName)
                    setInt(3, d.Id)
                    executeUpdate()
                    close()
                }
            }
            var changePrice = ""
            var lastNumPars = 0
            val stmtmp = con.prepareStatement("SELECT num_parsing FROM anb_url WHERE id = ?").apply {
                setInt(1, d.Id)
            }
            val pmp = stmtmp.executeQuery()
            if (pmp.next()) {
                lastNumPars = pmp.getInt(1)
            }
            val cD = ParserAnbNew.currDate
            val listPrice = mutableListOf<PriceChange>()
            d.calendars.filter { it.date.after(cD) || (it.date.date == cD.date && it.date.month == cD.month && it.date.year == cD.year) }.forEach {
                if (lastNumPars <= 0) {
                    return@forEach
                }
                val stmt0 = con.prepareStatement("SELECT price_day FROM days d JOIN checkup c on d.id_checkup = c.id JOIN anb_url au on c.iid_anb = au.id WHERE au.id = ? AND d.date = ?").apply {
                    setInt(1, d.Id)
                    setTimestamp(2, Timestamp(it.date.time))
                }
                val p0 = stmt0.executeQuery()
                if (p0.next()) {
                    val res = p0.getString(1)
                    if (it.price != res) {
                        listPrice.add(PriceChange(it.price ?: "", res, it.date, cD))
                    }
                    p0.close()
                    stmt0.close()
                } else {
                    p0.close()
                    stmt0.close()
                }

            }
            for (lp in listPrice) {
                con.prepareStatement("INSERT INTO price_changes(id_url, price, date_cal, date_parsing, num_parsing, price_was) VALUES (?, ?, ?, ?, ?, ?)").apply {
                    setInt(1, d.Id)
                    setString(2, lp.price)
                    setTimestamp(3, Timestamp(lp.dateCal.time))
                    setTimestamp(4, Timestamp(cD.time))
                    setInt(5, lastNumPars + 1)
                    setString(6, lp.priceWas)
                    executeUpdate()
                    close()
                }
            }
            val listBookable = mutableListOf<BookingChange>()
            d.calendars.filter { it.date.after(cD) }.forEach {
                if (lastNumPars <= 0) {
                    return@forEach
                }
                val stmt0 = con.prepareStatement("SELECT IFNULL(available, 0), IFNULL(bookable, 0) FROM anb_url an LEFT JOIN  checkup c on an.id = c.iid_anb LEFT JOIN days d on c.id = d.id_checkup WHERE an.id = ? AND d.date = ?").apply {
                    setInt(1, d.Id)
                    setTimestamp(2, Timestamp(it.date.time))
                }
                val p0 = stmt0.executeQuery()
                if (p0.next()) {
                    val av = p0.getInt(1)
                    val bok = p0.getInt(2)
                    val itAv = if (it.available) {
                        1
                    } else {
                        0
                    }
                    val itBok = if (it.bookable == true) {
                        1
                    } else {
                        0
                    }
                    if (av == 1 && itAv == 0) {
                        listBookable.add(BookingChange(1, it.date, cD, it.price ?: "0"))
                    }
                    p0.close()
                    stmt0.close()
                } else {
                    p0.close()
                    stmt0.close()
                }

            }

            for (lp in listBookable) {
                con.prepareStatement("INSERT INTO bookable_changes(id_url, booking, date_cal, date_parsing, num_parsing, price) VALUES (?, ?, ?, ?, ?, ?)").apply {
                    setInt(1, d.Id)
                    setInt(2, lp.booking)
                    setTimestamp(3, Timestamp(lp.dateCal.time))
                    setTimestamp(4, Timestamp(cD.time))
                    setInt(5, lastNumPars + 1)
                    setString(6, lp.price)
                    executeUpdate()
                    close()
                }
            }
            con.prepareStatement("DELETE FROM checkup WHERE iid_anb = ?").apply {
                setInt(1, d.Id)
                executeUpdate()
                close()
            }
            AddDoc++
            var idCheck = 0
            val stmt1 = con.prepareStatement("INSERT INTO checkup SET iid_anb = ?, price = ?, check_in = ?, check_out = ?, price_first_15 = ?, check_in_first_15 = ?, check_out_first_15 = ?, price_second_15 = ?, check_in_second_15 = ?, check_out_second_15 = ?, price_30 = ?, check_in_30 = ?, check_out_30 = ?, date_last = NOW()", Statement.RETURN_GENERATED_KEYS).apply {
                setInt(1, d.Id)
                setString(2, d.price.priceUsd)
                setString(3, d.price.checkIn)
                setString(4, d.price.checkOut)
                setString(5, d.price.priceUsdFirst15)
                setString(6, d.price.checkInFirst15)
                setString(7, d.price.checkOutFirst15)
                setString(8, d.price.priceUsdSecond15)
                setString(9, d.price.checkInSecond15)
                setString(10, d.price.checkOutSecond15)
                setString(11, d.price.priceUsd30)
                setString(12, d.price.checkIn30)
                setString(13, d.price.checkOut30)
                executeUpdate()
            }
            val rsoi = stmt1.generatedKeys
            if (rsoi.next()) {
                idCheck = rsoi.getInt(1)
            }
            stmt1.close()
            rsoi.close()
            d.calendars.map {
                if (it.date.before(Date())) {
                    it.available = false
                }
            }
            d.calendars.forEach {
                con.prepareStatement("INSERT INTO days SET id_checkup = ?, available = ?, min_nights = ?, available_for_checkin = ?, bookable = ?, date = ?, price_day = ?").apply {
                    setInt(1, idCheck)
                    setInt(2, if (it.available) {
                        1
                    } else {
                        0
                    })
                    setInt(3, it.minNights)
                    setInt(4, if (it.availableForCheckin == true) {
                        1
                    } else {
                        0
                    })
                    setInt(5, if (it.bookable == true) {
                        1
                    } else {
                        0
                    })
                    setTimestamp(6, Timestamp(it.date.time))
                    setString(7, it.price)
                    executeUpdate()
                }
            }
            con.prepareStatement("DELETE FROM price_cleaning WHERE id_url = ?").apply {
                setInt(1, d.Id)
                executeUpdate()
                close()
            }
            con.prepareStatement("DELETE FROM discounts WHERE id_url = ?").apply {
                setInt(1, d.Id)
                executeUpdate()
                close()
            }
            con.prepareStatement("INSERT INTO price_cleaning(id_url, price_cleaning) VALUES (?, ?)").apply {
                setInt(1, d.Id)
                setInt(2, d.cl.cleaning)
                executeUpdate()
                close()
            }
            d.cl.discounts.forEach {
                con.prepareStatement("INSERT INTO discounts(id_url, discount, date_parsing) VALUES (?, ?, ?)").apply {
                    setInt(1, d.Id)
                    setString(2, it)
                    setTimestamp(3, Timestamp(cD.time))
                    executeUpdate()
                    close()
                }
            }
            /*if ((d.calendars.firstOrNull { it.date.after(cD) }?.minNights ?: 0) <= 6) {
                analytics(con, 6L)
            }*/
            val (monthDisc, weekDisc) = findDiscounts()
            (6..30).forEach {
                analytics(con, it.toLong(), monthDisc, weekDisc)
            }

            con.prepareStatement("UPDATE anb_url SET num_parsing = num_parsing+1 WHERE id = ?").apply {
                setInt(1, d.Id)
                executeUpdate()
                close()
            }
        })
    }

    private fun analytics(con: Connection, interval: Long, monthDisc: Int, weekDisc: Int) {
        var dateNextDay = LocalDate.MIN
        var dateLastDay = LocalDate.MIN
        val stmt0 = con.prepareStatement("SELECT MAX(d.date), (SELECT DATE_ADD(CURRENT_DATE(), INTERVAL 1 DAY)) FROM anb_url an LEFT JOIN  checkup c on an.id = c.iid_anb LEFT JOIN days d on c.id = d.id_checkup WHERE an.id = ?").apply {
            setInt(1, d.Id)
        }
        val p0 = stmt0.executeQuery()
        if (p0.next()) {
            dateLastDay = p0.getTimestamp(1)?.toLocalDateTime()?.toLocalDate() ?: LocalDate.MIN
            dateNextDay = p0.getTimestamp(2).toLocalDateTime().toLocalDate()
        }
        p0.close()
        stmt0.close()
        if (dateLastDay == LocalDate.MIN || dateNextDay == LocalDate.MIN) {
            return
        }
        while (true) {
            val datePlus = dateNextDay.plusDays(interval)
            if (datePlus.isAfter(dateLastDay)) {
                break
            }
            val minNights = d.calendars.find { d -> d.date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isEqual(dateNextDay) }?.minNights
                    ?: throw Exception("minNights not found - ${d.Url}")
            if (minNights > interval) {
                dateNextDay = dateNextDay.plusDays(1L)
                continue
            }
            val stmt2 = con.prepareStatement("SELECT SUM(d.available) FROM anb_url an LEFT JOIN  checkup c on an.id = c.iid_anb LEFT JOIN days d on c.id = d.id_checkup WHERE an.id = ? AND (d.date BETWEEN ? AND ?)").apply {
                setInt(1, d.Id)
                setTimestamp(2, Timestamp.valueOf(dateNextDay.atStartOfDay()))
                setTimestamp(3, Timestamp.valueOf(datePlus.atStartOfDay()))
            }
            var price = 0
            var avail = 0
            val p1 = stmt2.executeQuery()
            if (p1.next()) {
                avail = p1.getInt(1) ?: 0
            }
            p1.close()
            stmt2.close()
            if (avail != (interval.toInt() + 1)) {
                dateNextDay = dateNextDay.plusDays(1L)
                continue
            }
            val stmt3 = con.prepareStatement("SELECT SUM(d.price_day) FROM anb_url an LEFT JOIN  checkup c on an.id = c.iid_anb LEFT JOIN days d on c.id = d.id_checkup WHERE an.id = ? AND d.date >= ? AND d.date < ?").apply {
                setInt(1, d.Id)
                setTimestamp(2, Timestamp.valueOf(dateNextDay.atStartOfDay()))
                setTimestamp(3, Timestamp.valueOf(datePlus.atStartOfDay()))
            }
            val p2 = stmt3.executeQuery()
            if (p2.next()) {
                price = p2.getInt(1) ?: 0
            }
            p2.close()
            stmt3.close()
            var nPice = price.toFloat()
            if (interval in 6..27 && weekDisc != 0) {
                nPice *= ((100 - weekDisc).toFloat() / 100)
            } else if (interval > 27 && monthDisc != 0) {
                nPice *= ((100 - monthDisc).toFloat() / 100)
            }
            price = nPice.toInt()
            if (price != 0) {
                con.prepareStatement("INSERT INTO analitic SET id_url = ?, start_date = ?, end_date = ?, perid_nights = ?, price = ?, id_user = ?").apply {
                    setInt(1, d.Id)
                    setTimestamp(2, Timestamp.valueOf(dateNextDay.atStartOfDay()))
                    setTimestamp(3, Timestamp.valueOf(datePlus.atStartOfDay()))
                    setInt(4, interval.toInt())
                    setInt(5, price + d.cl.cleaning)
                    setInt(6, BuilderApp.UserId)
                    executeUpdate()
                    close()
                }
            }
            dateNextDay = dateNextDay.plusDays(1L)
        }
    }

    private fun findDiscounts(): Discounts {
        var month = 0
        var week = 0
        d.cl.discounts.forEach {
            try {
                val mText = it.getDataFromRegexp("(\\d{1,2})% monthly")
                if (mText != "") {
                    month = Integer.parseInt(mText)
                }
            } catch (e: Exception) {
                logger(e, e.stackTrace)
            }
            try {
                val mText = it.getDataFromRegexp("(\\d{1,2})% weekly")
                if (mText != "") {
                    week = Integer.parseInt(mText)
                }
            } catch (e: Exception) {
                logger(e, e.stackTrace)
            }
        }
        return Discounts(month, week)

    }
}