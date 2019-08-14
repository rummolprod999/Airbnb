package anbapp.exstensions

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