package anbapp.documents

import java.util.*

abstract class AbstractDocument {

    data class PriceChange(val price: String, val priceWas: String, val dateCal: Date, val dateParsing: Date)
    data class BookingChange(val booking: Int, val dateCal: Date, val dateParsing: Date)
    companion object Counts {
        var AddDoc = 0
    }
}