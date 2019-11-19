package anbapp

import anbapp.builderApp.Builder
import anbapp.executor.Executor
import anbapp.sender.BookableOwner
import anbapp.sender.BookableSend
import anbapp.sender.EmailSender
import java.util.*


fun main(args: Array<String>) {
    val b = Builder(args)
    //testSend()
    b.deleteBigLog()
    Executor()
}

fun testSend() {
    val listBook: MutableList<BookableOwner> = mutableListOf()
    val booksend1 = BookableSend(Date(), Date(), "50")
    val booksend2 = BookableSend(Date(), Date(), "60")
    val ls1 = mutableListOf<BookableSend>()
    ls1.add(booksend1)
    ls1.add(booksend2)
    val bokkown1 = BookableOwner(ls1, "Dan", "name apartment", "https://www.airbnb.com/rooms/39409560", 47)
    listBook.add(bokkown1)
    val email = EmailSender(listBook)
    email.send()
}