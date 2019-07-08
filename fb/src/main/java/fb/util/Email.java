package fb.util;

import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import fb.DB;

/**
 * For sending emails
 */
public class Email {
	
	public static void main(String[] args) {
		System.out.println("Sending emails");
		sendEmail("ixeyp@outlook.net", "This is a third test 1", "This is a third test to a single address.");
		sendMassEmail(new String[] {"ixeyp@outlook.net","fbphoenix@writing.com","carolinafeeniks@gmail.com","fictionbranches@outlook.net"}, "This is a third test 2", "This is a third test to many addresses.");
		System.out.println("Emails sent");
		DB.closeSessionFactory();
		System.out.println("Bye");
		System.exit(0);
		System.out.println("Bye2");
	}
	
	/**
	 * BCC an email to many addresses
	 * @param toAddress
	 * @param subject
	 * @param body
	 * @return whether it sent successfully or not
	 */
	public static boolean sendMassEmail(String[] addresses, String subject, String body) {
		return sendEmailImpl(null, addresses, subject, body);
	}
	
	/**
	 * Send an email
	 * @param toAddress
	 * @param subject
	 * @param body
	 * @return whether it sent successfully or not
	 */
	public static boolean sendEmail(String toAddress, String subject, String body) {
		return sendEmailImpl(toAddress, null, subject, body);
	}
	
	/**
	 * Sends a single email to toAddress if toAddress is not null, else a mass-BCC email to addresses
	 * @param toAddress
	 * @param addresses
	 * @param subject
	 * @param body
	 * @return
	 */
	private static boolean sendEmailImpl(String toAddress, String[] addresses, String subject, String body) {
		Properties props = new Properties();
		props.put("mail.smtp.host", Strings.getSMTP_SERVER());
		props.put("mail.smtp.port", "587");
		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		Authenticator auth = new Authenticator() {
			@Override
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(Strings.getSMTP_EMAIL(), Strings.getSMTP_PASSWORD());
			}
		};
		Session session = Session.getInstance(props, auth);
		try {
			MimeMessage msg = new MimeMessage(session);
			msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
			msg.addHeader("format", "flowed");
			msg.addHeader("Content-Transfer-Encoding", "8bit");
			msg.setFrom(new InternetAddress(Strings.getSMTP_EMAIL(), "Fiction Branches"));
			msg.setReplyTo(InternetAddress.parse(Strings.getSMTP_EMAIL(), false));
			msg.setSubject(subject, "UTF-8");
			msg.setText(body, "UTF-8", "html");
			msg.setSentDate(new Date());
			
			if (toAddress != null) {
				msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress, false));
			} else {
				msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(Strings.getSMTP_EMAIL(), false));
				String addressesString = Arrays.stream(addresses).map(a->a.trim()).collect(Collectors.joining(" "));
				System.out.println("BCCing " + addressesString);
				msg.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(addressesString, false));
			}
			
			Transport.send(msg);
		} catch (Exception e) {
			return false;
		}
		return true;
	}
}
