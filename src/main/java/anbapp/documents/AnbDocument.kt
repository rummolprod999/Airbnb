package anbapp.documents

import anbapp.builderApp.BuilderApp
import anbapp.parsers.ParserAbstract
import anbapp.parsers.ParserAnbNew
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.sql.Timestamp
import java.util.*

class AnbDocument(private val d: ParserAbstract.RoomAnb) : IDocument, AbstractDocument() {
    override fun parsing() {
        DriverManager.getConnection(BuilderApp.UrlConnect, BuilderApp.UserDb, BuilderApp.PassDb).use(fun(con: Connection) {
            if (d.owner != "" || d.appName != "") {
                val p1 = con.prepareStatement("UPDATE anb_url SET owner = ?, apartment_name = ? WHERE id = ?")
                p1.setString(1, d.owner)
                p1.setString(2, d.appName)
                p1.setInt(3, d.Id)
                p1.executeUpdate()
                p1.close()
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
                        listBookable.add(BookingChange(1, it.date, cD))
                    }
                    p0.close()
                    stmt0.close()
                } else {
                    p0.close()
                    stmt0.close()
                }

            }

            for (lp in listBookable) {
                con.prepareStatement("INSERT INTO bookable_changes(id_url, booking, date_cal, date_parsing, num_parsing) VALUES (?, ?, ?, ?, ?)").apply {
                    setInt(1, d.Id)
                    setInt(2, lp.booking)
                    setTimestamp(3, Timestamp(lp.dateCal.time))
                    setTimestamp(4, Timestamp(cD.time))
                    setInt(5, lastNumPars + 1)
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
            val stmt1 = con.prepareStatement("INSERT INTO checkup SET iid_anb = ?, price = ?, check_in = ?, check_out = ?, price_first_15 = ?, check_in_first_15 = ?, check_out_first_15 = ?, price_second_15 = ?, check_in_second_15 = ?, check_out_second_15 = ?, price_30 = ?, check_in_30 = ?, check_out_30 = ?, date_last = NOW()", Statement.RETURN_GENERATED_KEYS)
            stmt1.setInt(1, d.Id)
            stmt1.setString(2, d.price.priceUsd)
            stmt1.setString(3, d.price.checkIn)
            stmt1.setString(4, d.price.checkOut)
            stmt1.setString(5, d.price.priceUsdFirst15)
            stmt1.setString(6, d.price.checkInFirst15)
            stmt1.setString(7, d.price.checkOutFirst15)
            stmt1.setString(8, d.price.priceUsdSecond15)
            stmt1.setString(9, d.price.checkInSecond15)
            stmt1.setString(10, d.price.checkOutSecond15)
            stmt1.setString(11, d.price.priceUsd30)
            stmt1.setString(12, d.price.checkIn30)
            stmt1.setString(13, d.price.checkOut30)
            stmt1.executeUpdate()
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
                val stmt2 = con.prepareStatement("INSERT INTO days SET id_checkup = ?, available = ?, min_nights = ?, available_for_checkin = ?, bookable = ?, date = ?, price_day = ?")
                stmt2.setInt(1, idCheck)
                stmt2.setInt(2, if (it.available) {
                    1
                } else {
                    0
                })
                stmt2.setInt(3, it.minNights)
                stmt2.setInt(4, if (it.availableForCheckin == true) {
                    1
                } else {
                    0
                })
                stmt2.setInt(5, if (it.bookable == true) {
                    1
                } else {
                    0
                })
                stmt2.setTimestamp(6, Timestamp(it.date.time))
                stmt2.setString(7, it.price)
                stmt2.executeUpdate()
            }
            val pend = con.prepareStatement("UPDATE anb_url SET num_parsing = num_parsing+1 WHERE id = ?")
            pend.setInt(1, d.Id)
            pend.executeUpdate()
            pend.close()
        })
    }

    data class PriceChange(val price: String, val priceWas: String, val dateCal: Date, val dateParsing: Date)
    data class BookingChange(val booking: Int, val dateCal: Date, val dateParsing: Date)
}