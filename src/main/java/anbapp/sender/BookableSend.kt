package anbapp.sender

import java.util.*

data class BookableSend(val dateCal: Date, val dateParsing: Date, val price: String)
data class BookableOwner(val bookable: MutableList<BookableSend>, val owner: String, val appName: String, val url: String, val id: Int)

val listBookableForSend = mutableListOf<BookableOwner>()