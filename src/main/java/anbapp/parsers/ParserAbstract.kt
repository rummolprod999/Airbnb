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
    data class RoomAnb(val Id: Int, val Url: String, var calendars: List<Day>, var price: Price, var owner: String, var appName: String, var cl: CleanDisc)
    data class Price(val checkIn: String, val checkOut: String, val priceUsd: String, val checkInFirst15: String, val checkOutFirst15: String, val priceUsdFirst15: String, val checkInSecond15: String, val checkOutSecond15: String, val priceUsdSecond15: String, val checkIn30: String, val checkOut30: String, val priceUsd30: String)
    data class CleanDisc(val discounts: List<String>, val cleaning: Int)

    fun parse(fn: () -> Unit) {
        logger("Start parsing")
        fn()
        logger("Updated documents ${AbstractDocument.AddDoc}")
        logger("End parsing")
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
                arr.add(RoomAnb(id, url, listOf(), Price("", "", "", "", "", "", "", "", "", "", "", ""), "", "", CleanDisc(mutableListOf(), 0)))
            }
        })
        return arr
    }

    fun existNameAndOwner(id: Int): Boolean {
        var b = true
        DriverManager.getConnection(BuilderApp.UrlConnect, BuilderApp.UserDb, BuilderApp.PassDb).use(fun(con: Connection) {
            val res = con.prepareStatement("SELECT owner, apartment_name FROM anb_url WHERE id = ?").apply { setInt(1, id) }.executeQuery()
            while (res.next()) {
                val owner = res.getString(1)
                val appName = res.getString(2)
                if (owner == "" && appName == "") {
                    b = false
                }
            }

        })
        return b
    }

    class DayAnb {
        var date: String? = null
        var available: Boolean? = null
        @SerializedName("min_nights")
        var minNights: Int? = null
        @SerializedName("available_for_checkin")
        var availableForCheckin: Boolean? = null
        var bookable: Boolean? = null
        var price: priceCal? = null
    }

    class priceCal {

        @SerializedName("local_price_formatted")
        var localPriceFormatted: String? = null
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

    data class Day(val date: Date, var available: Boolean, val minNights: Int, val availableForCheckin: Boolean?, val bookable: Boolean?, val price: String?) {
    }

    class PdpListingBookingDetails {
        @SerializedName("pdp_listing_booking_details")
        var pdpListingBookingDetails: ArrayList<Details>? = null
    }

    class Details {
        @SerializedName("check_in")
        var checkIn: String? = null
        @SerializedName("check_out")
        var checkOut: String? = null
        var price: PriceItems? = null
    }

    class PriceItems {
        @SerializedName("price_items")
        var priceItems: ArrayList<PriceItem>? = null
    }

    class PriceItem {

        var type: String? = null
        var total: Total? = null

        @SerializedName("localized_title")
        var localizedTitle: String? = null
    }

    class Total {
        @SerializedName("amount_formatted")
        var amountFormatted: String? = null
        var amount: Int? = null
    }

    class ListingAnb {
        var listing: Listing? = null
    }

    class Listing {
        var name: String? = null
        var user: User1? = null
    }

    class User1 {
        var user: User2? = null
    }

    class User2 {
        @SerializedName("first_name")
        var firstName: String? = null
    }
}