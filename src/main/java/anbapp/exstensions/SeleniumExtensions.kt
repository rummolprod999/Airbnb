package anbapp.exstensions

import org.openqa.selenium.By
import org.openqa.selenium.SearchContext
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver

fun <T> T.findElementWithoutException(by: By): WebElement?
        where T : SearchContext {
    return try {
        this.findElement(by)
    } catch (e: Exception) {
        null
    }
}

fun ChromeDriver.clickerExp(xpath: String) {
    for (i in 1 until 3) {
        this.switchTo().defaultContent()
        try {
            val el = this.findElementByXPath(xpath)
            el.click()
            return
        } catch (e: Exception) {
            Thread.sleep(1000)
        }
    }
}