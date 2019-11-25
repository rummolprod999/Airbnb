package anbapp.httpTools

import anbapp.builderApp.BuilderApp
import anbapp.logger.logger
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Thread.sleep
import java.net.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


private const val timeoutD = 3000L

fun downloadFromUrl(urls: String, i: Int = 10, wt: Long = 3000): String {
    var count = 0
    var waitTime = wt
    while (true) {
        if (count >= i) {
            val retSt = String.format("Did not download the page in %d attempts", count)
            logger(retSt, urls)
            return retSt
        }
        try {
            var s: String
            val executor = Executors.newCachedThreadPool()
            val task = { downloadWaitWithRef(urls) }
            val future = executor.submit(task)
            try {
                s = future.get(60, TimeUnit.SECONDS)
            } catch (ex: TimeoutException) {
                throw ex
            } catch (ex: InterruptedException) {
                throw ex
            } catch (ex: ExecutionException) {
                throw ex
            } catch (ex: Exception) {
                throw ex
            } finally {
                future.cancel(true)
                executor.shutdown()
            }
            return s

        } catch (e: Exception) {
            //logger(e, e.stackTrace)
            logger(e)
            count++
            sleep(waitTime)
            waitTime += 5000
        }

    }
    return ""
}

fun downloadWaitWithRef(urls: String): String {
    val s = StringBuilder()
    val url = URL(urls)
    val uc = if (BuilderApp.ProxyAddress != "" && BuilderApp.ProxyPort != 0 && BuilderApp.ProxyUser != "" && BuilderApp.ProxyPass != "") {
        System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(BuilderApp.ProxyAddress, BuilderApp.ProxyPort))
        val connection = URL(urls).openConnection(proxy) as HttpURLConnection
        val authenticator = object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(BuilderApp.ProxyUser, BuilderApp.ProxyPass.toCharArray())
            }
        }
        Authenticator.setDefault(authenticator)
        connection
    } else {
        url.openConnection()
    }
    uc.connectTimeout = 30_000
    uc.readTimeout = 30_000
    uc.addRequestProperty("User-Agent", RandomUserAgent.randomUserAgent)
    uc.connect()
    val `is`: InputStream = uc.getInputStream()
    val br = BufferedReader(InputStreamReader(`is`))
    var inputLine: String?
    var value = true
    while (value) {
        inputLine = br.readLine()
        if (inputLine == null) {
            value = false
        } else {
            s.append(inputLine)
        }

    }
    br.close()
    `is`.close()
    return s.toString()
}