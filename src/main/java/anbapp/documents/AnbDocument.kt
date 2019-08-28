package anbapp.documents

import anbapp.builderApp.BuilderApp
import anbapp.parsers.ParserAbstract
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.sql.Timestamp
import java.util.*

class AnbDocument(val d: ParserAbstract.RoomAnb) : IDocument, AbstractDocument() {
    override fun parsing() {
        val currChanges = d.calendars.filter { it.bookable == true && it.availableForCheckin == true }.count()
        var dbChanges = 0
        DriverManager.getConnection(BuilderApp.UrlConnect, BuilderApp.UserDb, BuilderApp.PassDb).use(fun(con: Connection) {
            val stmt0 = con.prepareStatement("SELECT COUNT(an.id) FROM anb_url an LEFT JOIN  checkup c on an.id = c.iid_anb LEFT JOIN days d on c.id = d.id_checkup WHERE an.id = ? AND available_for_checkin = 1 AND bookable = 1").apply {
                setInt(1, d.Id)
            }
            val p0 = stmt0.executeQuery()
            if (p0.next()) {
                dbChanges = p0.getInt(1)
                p0.close()
                stmt0.close()
            }
            val changes = when {
                currChanges < dbChanges && ((dbChanges - currChanges) > 1) -> "Новая бронь(${dbChanges - currChanges})"
                currChanges > dbChanges -> ""
                else -> ""
            }
            val p6 = con.prepareStatement("UPDATE anb_url SET changes = ? WHERE id = ?")
            p6.setString(1, changes)
            p6.setInt(2, d.Id)
            p6.executeUpdate()
            p6.close()
            if (d.owner != "" || d.appName != "") {
                val p1 = con.prepareStatement("UPDATE anb_url SET owner = ?, apartment_name = ? WHERE id = ?")
                p1.setString(1, d.owner)
                p1.setString(2, d.appName)
                p1.setInt(3, d.Id)
                p1.executeUpdate()
                p1.close()
            }
            var changePrice = ""
            val ddd = Date()
            val currDay = d.calendars.fold(mutableListOf<ParserAbstract.Day>()) { total, month -> total.add(month); total }.firstOrNull { it.date.date == ddd.date && it.date.month == ddd.month && it.date.year == ddd.year }
            val currPrice = currDay?.price
                    ?: ""
            if (currDay != null && currDay.date.date != 1) {
                val prevPrice = d.calendars.fold(mutableListOf<ParserAbstract.Day>()) { total, month -> total.add(month); total }.firstOrNull { it.date.date == currDay.date.date - 1 && it.date.month == currDay.date.month && it.date.year == currDay.date.year }?.price
                        ?: ""
                if (currPrice != "" && prevPrice != "" && currPrice != prevPrice) {
                    changePrice = "Было: $prevPrice <br>Стало: $currPrice"
                }

            }
            /*val cD = Date()
            if (changePrice == "") {
                d.calendars.filter { it.date.after(cD) || (it.date.date == cD.date && it.date.month == cD.month && it.date.year == cD.year) }.forEach { tt ->
                    d.calendars.fold(mutableListOf<ParserAbstract.Day>()) { total, month -> total.add(month); total }.filter { it.date.after(cD) || it.date == cD }.firstOrNull { it.date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() == tt.date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1L) }?.run {
                        if (price != tt.price) {
                            changePrice = "${ParserAnbNew.formatter.format(tt.date)} было: ${tt.price} <br>${ParserAnbNew.formatter.format(date)} стало: $price"
                            return@forEach
                        }
                    }
                }
            }*/
            val p7 = con.prepareStatement("UPDATE anb_url SET change_price = ? WHERE id = ?")
            p7.setString(1, changePrice)
            p7.setInt(2, d.Id)
            p7.executeUpdate()
            p7.close()
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

        })
    }
}