package anbapp.documents

import anbapp.builderApp.BuilderApp
import anbapp.parsers.ParserAbstract
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.sql.Timestamp

class AnbDocument(val d: ParserAbstract.RoomAnb) : IDocument, AbstractDocument() {
    override fun parsing() {
        var changes = ""
        DriverManager.getConnection(BuilderApp.UrlConnect, BuilderApp.UserDb, BuilderApp.PassDb).use(fun(con: Connection) {
            val stmt0 = con.prepareStatement("SELECT an.changes, ch.price FROM anb_url an LEFT JOIN checkup ch ON an.id = ch.iid_anb WHERE an.id = ?").apply {
                setInt(1, d.Id)
            }
            val p0 = stmt0.executeQuery()
            while (p0.next()) {
                val price = p0.getString(2)
                if (price == null) {
                    changes = "Новая"
                    break
                } else {
                    if (d.price.priceUsd != price) {
                        changes = "Было:${d.price.priceUsd} Стало: $price"
                    }
                    break
                }
            }
            p0.close()
            stmt0.close()
            if (d.owner != "" || d.appName != "") {
                val p1 = con.prepareStatement("UPDATE anb_url SET owner = ?, apartment_name = ? WHERE id = ?")
                p1.setString(1, d.owner)
                p1.setString(2, d.appName)
                p1.setInt(3, d.Id)
                p1.executeUpdate()
                p1.close()
            }
            if (changes != "") {
                val p1 = con.prepareStatement("UPDATE anb_url SET changes = ? WHERE id = ?")
                p1.setString(1, changes)
                p1.setInt(2, d.Id)
                p1.executeUpdate()
                p1.close()
            }
            con.prepareStatement("DELETE FROM checkup WHERE iid_anb = ?").apply {
                setInt(1, d.Id)
                executeUpdate()
                close()
            }
            AddDoc++
            var idCheck = 0
            val stmt1 = con.prepareStatement("INSERT INTO checkup SET iid_anb = ?, price = ?, check_in = ?, check_out = ?, date_last = NOW()", Statement.RETURN_GENERATED_KEYS)
            stmt1.setInt(1, d.Id)
            stmt1.setString(2, d.price.priceUsd)
            stmt1.setString(3, d.price.checkIn)
            stmt1.setString(4, d.price.checkOut)
            stmt1.executeUpdate()
            val rsoi = stmt1.generatedKeys
            if (rsoi.next()) {
                idCheck = rsoi.getInt(1)
            }
            stmt1.close()
            rsoi.close()
            d.calendars.forEach {
                val stmt2 = con.prepareStatement("INSERT INTO days SET id_checkup = ?, available = ?, min_nights = ?, available_for_checkin = ?, bookable = ?, date = ?")
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
                stmt2.executeUpdate()
            }

        })
    }
}