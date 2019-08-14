package anbapp.parsers

import java.util.logging.Level

class ParserAnb : IParser, ParserAbstract() {
    init {
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog")
        java.util.logging.Logger.getLogger("org.openqa.selenium").level = Level.OFF
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver")
    }

    override fun parser() = parse { parserAnb() }

    fun parserAnb() {
        println("hello")
    }
}