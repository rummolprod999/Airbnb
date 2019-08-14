package anbapp.parsers

import anbapp.exstensions.getDataFromRegexp
import anbapp.httpTools.downloadFromUrl
import anbapp.logger.logger
import org.openqa.selenium.By
import org.openqa.selenium.Dimension
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.WebDriverWait
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class ParserAnb : IParser, ParserAbstract() {
    init {
        System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog")
        java.util.logging.Logger.getLogger("org.openqa.selenium").level = Level.OFF
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver")
    }

    companion object WebCl {
        const val timeoutB = 60L
        val dateNow = LocalDate.now()
        val customFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }

    lateinit var driver: ChromeDriver
    lateinit var wait: WebDriverWait

    override fun parser() = parse {
        val options = ChromeOptions()
        options.addArguments("headless")
        options.addArguments("disable-gpu")
        options.addArguments("no-sandbox")
        driver = ChromeDriver(options)
        wait = WebDriverWait(driver, timeoutB)
        driver.manage().timeouts().pageLoadTimeout(timeoutB, TimeUnit.SECONDS)
        driver.manage().window().size = Dimension(1280, 1024)
        driver.manage().window().maximize()
        driver.manage().deleteAllCookies()
        try {
            parserAnb()
        } catch (e: Exception) {
            logger("Error in parser function", e.stackTrace, e)
        } finally {
            driver.quit()
        }
    }

    private fun parserAnb() {
        getUrlArray().forEach {
            try {
                getPage(it)
            } catch (e: Exception) {
                logger("Error in getPage function", e.stackTrace, e)
            }
        }
    }

    private fun getPage(room: RoomAnb) {
        driver.get(room.Url)
        wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("(//table[@role=\"presentation\"]/..)[2]")))
        val cal = driver.findElementByXPath("(//table[@role=\"presentation\"]/..)[2]")
        val calInnerHtml = cal.getAttribute("outerHTML")
        room.calendars = listOf(calInnerHtml)
        getPrices(room)
    }

    private fun getPrices(room: RoomAnb) {
        val currUrl = driver.currentUrl
        val sourceImpId = currUrl.getDataFromRegexp("""source_impression_id=(.+)${'$'}""")
        val roomId = currUrl.getDataFromRegexp("""rooms/(\d+)""")
        if (sourceImpId == "" || roomId == "") {
            logger("cannot parse url $currUrl")
            return
        }

        val lastDay = dateNow.withDayOfMonth(dateNow.lengthOfMonth())
        var c = 2L
        for (d in dateNow.dayOfMonth until lastDay.plusMonths(1).dayOfMonth) {
            val priceUrl = """https://www.airbnb.ru/api/v2/pdp_listing_booking_details?_format=for_web_with_date&_intents=p3_book_it&_interaction_type=pageload&_p3_impression_id=$sourceImpId&check_in=${dateNow.plusDays(1L).format(customFormatter)}&check_out=${dateNow.plusDays(c).format(customFormatter)}&currency=RUB&force_boost_unc_priority_message_type=&guests=1&key=d306zoyjsyarp7ifhu67rjxn52tv0t20&listing_id=$roomId&locale=ru&number_of_adults=1&number_of_children=0&number_of_infants=0&show_smart_promotion=0"""
            c++
            downloadJson(priceUrl)
        }
    }

    private fun downloadJson(url: String) {
        val json = downloadFromUrl(url)
        if (json == "") {
            logger("get empty string, url $url")
        }
        if (json.contains("\"available\":true"))
            println(json)
    }


}