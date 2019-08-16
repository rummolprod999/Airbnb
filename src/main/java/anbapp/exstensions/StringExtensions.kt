package anbapp.exstensions

import java.text.Format
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

fun String.getDataFromRegexp(reg: String): String {
    var st = ""
    val pattern: Pattern = Pattern.compile(reg)
    val matcher: Matcher = pattern.matcher(this)
    if (matcher.find()) {
        st = matcher.group(1)
    }
    return st.trim { it <= ' ' }
}

fun String.getDateFromString(format: Format): Date {
    var d = Date(0L)
    if (this == "") return d
    try {
        d = format.parseObject(this) as Date
    } catch (e: Exception) {
    }

    return d
}