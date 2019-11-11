package anbapp.builderApp

import anbapp.Arguments
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import java.io.File
import java.io.FileReader
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
    var Port: Int = 3306
    lateinit var TempPath: String
    lateinit var LogPath: String
    lateinit var LogFile: String
    lateinit var UrlConnect: String
}

const val arguments = "anb"

class Builder(args: Array<String>) {
    lateinit var arg: Arguments
    var UserId: Int
    lateinit var Database: String
    lateinit var Prefix: String
    lateinit var UserDb: String
    lateinit var PassDb: String
    lateinit var Server: String
    var Port: Int = 3306
    val executePath: String = File(Class.forName("anbapp.AppKt").protectionDomain.codeSource.location.path).parentFile.toString()
    lateinit var TempPath: String
    lateinit var LogPath: String
    lateinit var LogFile: String

    init {
        if (args.size < 2) {
            println("Недостаточно агрументов для запуска, используйте $arguments для запуска")
            exitProcess(0)
        }
        when (args[0]) {
            "anb" -> arg = Arguments.ANB
            else -> run { println("Неверно указаны аргументы, используйте $arguments, выходим из программы"); exitProcess(0) }
        }
        UserId = Integer.parseInt(args[1])
        setSettings()
        createDirs()
        createObj()
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
    var port: Int? = null
}