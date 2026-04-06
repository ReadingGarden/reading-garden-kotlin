package std.nooook.readinggardenkotlin.modules.auth.integration

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

interface MailSender {
    fun send(
        email: String,
        title: String,
        content: String,
    )
}

@Component
class SmtpMailSender(
    private val javaMailSenderProvider: ObjectProvider<JavaMailSender>,
    @Value("\${app.email.account:}") private val senderAccount: String,
) : MailSender {
    override fun send(
        email: String,
        title: String,
        content: String,
    ) {
        val javaMailSender = javaMailSenderProvider.getIfAvailable()
            ?: throw IllegalStateException("Mail sender not configured")

        val message = SimpleMailMessage().apply {
            setTo(email)
            subject = title
            text = content
            if (senderAccount.isNotBlank()) {
                from = senderAccount
            }
        }

        javaMailSender.send(message)
    }
}
