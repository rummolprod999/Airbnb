package anbapp.builderApp

import anbapp.Arguments
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileReader
import java.sql.Connection
import java.sql.DriverManager
import java.text.SimpleDateFormat
import kotlin.system.exitProcess


object BuilderApp {
    lateinit var arg: Arguments
    var UserId: Int = 0
    lateinit var Database: String
    lateinit var Prefix: String
    lateinit var UserDb: String
    lateinit var PassDb: String
    lateinit var Server: String
    lateinit var EmailUser: String
    lateinit var EmailPass: String
    lateinit var SendUserEmail: String
    var Port: Int = 3306
    lateinit var TempPath: String
    lateinit var LogPath: String
    lateinit var LogFile: String
    lateinit var UrlConnect: String
    lateinit var ProxyAddress: String
    var ProxyPort: Int = 0
    lateinit var ProxyUser: String
    lateinit var ProxyPass: String
}

const val arguments = "anb"

class Builder(args: Array<String>) {
    var arg: Arguments
    var UserId: Int
    lateinit var Database: String
    lateinit var Prefix: String
    lateinit var UserDb: String
    lateinit var PassDb: String
    lateinit var Server: String
    lateinit var EmailUser: String
    lateinit var EmailPass: String
    lateinit var SendUserEmail: String
    var Port: Int = 3306
    val executePath: String = File(Class.forName("anbapp.AppKt").protectionDomain.codeSource.location.path).parentFile.toString()
    lateinit var TempPath: String
    lateinit var LogPath: String
    lateinit var LogFile: String
    var ProxyAddress: String = ""
    var ProxyPort: Int = 0
    var ProxyUser: String = ""
    var ProxyPass: String = ""

    init {
        if (args.size < 2) {
            println("Few arguments for running, use $arguments")
            exitProcess(0)
        }
        when (args[0]) {
            "anb" -> arg = Arguments.ANB
            else -> run { println("Bad arguments, use $arguments, exit"); exitProcess(0) }
        }
        UserId = Integer.parseInt(args[1])
        setSettings()
        createDirs()
        createObj()
        getProxySettings()
        getEmailUser()
    }

    private fun getProxySettings() {
        DriverManager.getConnection(BuilderApp.UrlConnect, BuilderApp.UserDb, BuilderApp.PassDb).use(fun(con: Connection) {
            val stmt0 = con.prepareStatement("SELECT proxy_address, proxy_port, proxy_user, proxy_pass FROM proxy WHERE id_user = ?").apply {
                setInt(1, BuilderApp.UserId)
            }
            val res = stmt0.executeQuery()
            if (res.next()) {
                ProxyAddress = res.getString(1)
            }
        })
        BuilderApp.ProxyAddress = ProxyAddress
        BuilderApp.ProxyPass = ProxyPass
        BuilderApp.ProxyUser = ProxyUser
        BuilderApp.ProxyPort = ProxyPort
    }

    private fun getEmailUser() {
        DriverManager.getConnection(BuilderApp.UrlConnect, BuilderApp.UserDb, BuilderApp.PassDb).use(fun(con: Connection) {
            val stmt0 = con.prepareStatement("SELECT users.user_email FROM users WHERE id = ?").apply {
                setInt(1, BuilderApp.UserId)
            }
            val res = stmt0.executeQuery()
            if (res.next()) {
                SendUserEmail = res.getString(1)
            }
        })
        BuilderApp.SendUserEmail = SendUserEmail
    }

    private fun setSettings() {
        val filename = executePath + File.separator + "settings.json"
        val gson = Gson()
        val reader = JsonReader(FileReader(filename))
        val doc = gson.fromJson<Settings>(reader, Settings::class.java)
        Database = doc.database ?: throw IllegalArgumentException("bad database")
        Prefix = doc.prefix ?: ""
        UserDb = doc.userdb ?: throw IllegalArgumentException("bad userdb")
        PassDb = doc.passdb ?: throw IllegalArgumentException("bad passdb")
        Server = doc.server ?: throw IllegalArgumentException("bad server")
        EmailUser = doc.user_email ?: throw IllegalArgumentException("bad user email")
        EmailPass = doc.pass_email ?: throw IllegalArgumentException("bad user pass")
        Port = doc.port ?: 3306
        TempPath = "$executePath${File.separator}tempdir_${arg.name.toLowerCase()}_${UserId}"
        LogPath = "$executePath${File.separator}logdir_${arg.name.toLowerCase()}_${UserId}"
    }

    private fun createDirs() {
        val tmp = File(TempPath)
        if (tmp.exists()) {
            tmp.delete()
            tmp.mkdir()
        } else {
            tmp.mkdir()
        }
        val log = File(LogPath)
        if (!log.exists()) {
            log.mkdir()
        }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")
        LogFile = "$LogPath${File.separator}log_parsing_${arg}.log"
    }

    public fun deleteBigLog() {
        val log = File(LogFile)
        if (log.exists() && log.length() > 1_000_000L) {
            try {
                log.delete()
            } catch (e: Exception) {
                println(e)
            }
        }
    }

    private fun createObj() {
        BuilderApp.arg = arg
        BuilderApp.UserId = UserId
        BuilderApp.Database = Database
        BuilderApp.PassDb = PassDb
        BuilderApp.UserDb = UserDb
        BuilderApp.Port = Port
        BuilderApp.Prefix = Prefix
        BuilderApp.Server = Server
        BuilderApp.EmailPass = EmailPass
        BuilderApp.EmailUser = EmailUser
        BuilderApp.LogPath = LogPath
        BuilderApp.TempPath = TempPath
        BuilderApp.LogFile = LogFile
        BuilderApp.UrlConnect = "jdbc:mysql://$Server:$Port/$Database?jdbcCompliantTruncation=false&useUnicode=true&characterEncoding=utf-8&useLegacyDatetimeCode=false&serverTimezone=Europe/Moscow&connectTimeout=30000&socketTimeout=30000&useSSL=false"
    }
}

class Settings {
    var database: String? = null
    var prefix: String? = null
    var userdb: String? = null
    var passdb: String? = null
    var server: String? = null
    var user_email: String? = null
    var pass_email: String? = null
    var port: Int? = null
}