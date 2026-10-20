package model;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import java.util.logging.Logger;

public class Mail {
    private static final Logger LOGGER = Logger.getLogger(Mail.class.getName());
    private static final String APP_EMAIL = System.getenv("APP_EMAIL") != null ? System.getenv("APP_EMAIL") : "your_email@gmail.com";
    private static final String APP_PASSWORD = System.getenv("APP_PASSWORD") != null ? System.getenv("APP_PASSWORD") : "your_app_password";

    public static void sendMail(String to, String subject, String htmlContent) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.timeout", "5000"); // 5-second timeout
        props.put("mail.smtp.connectiontimeout", "5000"); // 5-second connection timeout

        Session session = null;
        try {
            session = Session.getInstance(props, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(APP_EMAIL, APP_PASSWORD);
                }
            });
            if (session == null) {
                throw new MessagingException("Session creation failed");
            }
            LOGGER.info("Session created successfully for email: " + APP_EMAIL);
            session.setDebug(true); // Enable debug output

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(APP_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setContent(htmlContent, "text/html; charset=utf-8");

            Transport transport = session.getTransport("smtp");
            transport.connect(APP_EMAIL, APP_PASSWORD);
            transport.sendMessage(message, message.getAllRecipients());
            transport.close();
            LOGGER.info("Email sent successfully to: " + to);
        } catch (MessagingException e) {
            LOGGER.severe("Email sending failed: " + e.getMessage());
            if (session != null && session.getDebug()) {
                LOGGER.severe("JavaMail debug output: Check server logs for details.");
            }
            throw new MessagingException("Failed to send email: " + e.getMessage(), e);
        }
    }
}