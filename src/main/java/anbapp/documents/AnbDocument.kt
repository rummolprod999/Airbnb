package anbapp.documents

import anbapp.parsers.ParserAbstract

class AnbDocument(val d: ParserAbstract.RoomAnb) : IDocument, AbstractDocument() {
    override fun parsing() {
        println(d)
    }
}