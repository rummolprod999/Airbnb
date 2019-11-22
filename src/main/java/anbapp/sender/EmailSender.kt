package anbapp.sender

import anbapp.builderApp.BuilderApp
import anbapp.logger.logger
import java.text.SimpleDateFormat
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class EmailSender : ISender {
    private val props: Properties = Properties()
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd")

    init {
        props["mail.smtp.starttls.enable"] = "true"
        props["mail.smtp.host"] = BuilderApp.SmtpServer
        //props["mail.smtp.ssl.enable"] = "true"
        props["mail.smtp.port"] = BuilderApp.SmtpPort
        props["mail.smtp.auth"] = "true"
    }

    override fun send() {
        if (listBookableForSend.size == 0 || BuilderApp.SendUserEmail == "" || !BuilderApp.IsReport) {
            return
        }
        val subject = "New booking from airbnb"
        sendEmail(subject, createText(), BuilderApp.SendUserEmail)
    }

    private fun sendEmail(subject: String, text: String, toEmail: String) {
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(BuilderApp.EmailUser, BuilderApp.EmailPass)
            }
        })

        try {
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(BuilderApp.EmailUser))
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
            message.subject = subject
            message.setContent(text, "text/html; charset=utf-8")
            Transport.send(message)
            logger("email has been sent")
        } catch (e: MessagingException) {
            throw RuntimeException(e)
        }
    }

    private fun createText(): String {
        val st = StringBuilder()
        listBookableForSend.forEach {
            st.append("<b>Apartment name</b>: ${it.appName}\n<br>")
            st.append("<b>Apartment owner</b>: ${it.owner}\n<br>")
            st.append("<b>Apartment url</b>: ${it.url}\n\n<br><br>")
            it.bookable.forEach { x ->
                st.append("<b>Date parsing:</b> ${dateTimeFormat.format(x.dateParsing)} <b>Data booking:</b> ${dateFormat.format(x.dateCal)} <b>Price:</b> $${x.price}<br>")
            }
            st.append("\n\n\n<hr><br><br><br>")
        }
        return st.toString()
    }
}