package anbapp.executor

import anbapp.Arguments
import anbapp.builderApp.BuilderApp
import anbapp.logger.logger
import anbapp.parsers.IParser
import anbapp.parsers.ParserAnb

class Executor {
    lateinit var p: IParser

    init {
        when (BuilderApp.arg) {
            Arguments.ANB -> run { p = ParserAnb(); executeParser(p) { parser() } }
        }
    }

    private fun executeParser(d: IParser, fn: IParser.() -> Unit) {
        try {
            d.fn()
        } catch (e: Exception) {
            logger("error in executor fun", e.stackTrace, e)
        }

    }
}