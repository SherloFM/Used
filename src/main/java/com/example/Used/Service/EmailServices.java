package com.example.Used.Service;

import org.apache.logging.log4j.message.SimpleMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServices {

    private JavaMailSender javaMailSender;

    @Autowired
    public EmailServices(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }


    public void sendVerificationEmail(String email, String code) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(email);
        message.setSubject("Used - Email Verification");
        message.setText(
                "Welcome to Used!\n\n" +
                        "Your email verification code is:\n\n" +
                        code + "\n\n" +
                        "This code will expire in 10 minutes."
        );

        javaMailSender.send(message);
    }

    public void sendPasswordResetEmail(String email, String code) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(email);
        message.setSubject("Password Reset Code");

        message.setText(
                "Your password reset code is: " + code +
                        "\n\nThis code will expire in 10 minutes."
        );

        javaMailSender.send(message);
    }
    public void sendListingDeletedEmail(
            String sellerEmail,
            String listingTitle
    ) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(sellerEmail);
        message.setSubject("Used - Your Listing Was Removed");

        message.setText(
                "Your listing has been removed by an administrator.\n\n" +
                        "Listing: " + listingTitle + "\n\n" +
                        "The listing was removed because it was determined to be " +
                        "invalid or inappropriate."
        );

        javaMailSender.send(message);
    }

    public void sendListingSoldEmail(
            String sellerEmail,
            String listingTitle
    ) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(sellerEmail);
        message.setSubject("Used - Your Listing Has Been Sold");

        message.setText(
                "Your listing has been sold!\n\n" +
                        "Listing: " + listingTitle + "\n\n" +
                        "Thank you for using Used."
        );

        javaMailSender.send(message);
    }

    public void sendAccountBannedEmail(String email) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(email);
        message.setSubject("Used - Your Account Has Been Suspended");

        message.setText(
                "Hello,\n\n" +
                        "Your Used account has been suspended by an administrator.\n\n" +
                        "While suspended, you will not be able to log in, create listings, " +
                        "or make purchases.\n\n" +
                        "If you believe this was a mistake, please contact our support team.\n\n" +
                        "Thank you."
        );

        javaMailSender.send(message);
    }

    public void sendAccountUnbannedEmail(String email) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(email);
        message.setSubject("Used - Your Account Has Been Reinstated");

        message.setText(
                "Hello,\n\n" +
                        "Good news — your Used account has been reinstated.\n\n" +
                        "You can now log in and use the marketplace as before.\n\n" +
                        "Thank you for your patience."
        );

        javaMailSender.send(message);
    }

    public void sendCodRequestEmail(
            String sellerEmail,
            String listingTitle,
            String buyerName,
            java.math.BigDecimal amount
    ) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(sellerEmail);
        message.setSubject("Used - Cash on Delivery Request");

        message.setText(
                "A buyer wants to pay cash on delivery for your listing.\n\n" +
                        "Listing: " + listingTitle + "\n" +
                        "Buyer: " + buyerName + "\n" +
                        "Amount: " + amount.toPlainString() + "\n\n" +
                        "Open Used and check the request notification to accept or decline."
        );

        javaMailSender.send(message);
    }

    public void sendCodAcceptedEmail(String buyerEmail, String listingTitle) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(buyerEmail);
        message.setSubject("Used - Cash on Delivery Request Accepted");

        message.setText(
                "Good news! The seller accepted your cash-on-delivery request.\n\n" +
                        "Listing: " + listingTitle + "\n\n" +
                        "Please arrange the payment and pickup with the seller."
        );

        javaMailSender.send(message);
    }

    public void sendCodDeclinedEmail(String buyerEmail, String listingTitle) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(buyerEmail);
        message.setSubject("Used - Cash on Delivery Request Declined");

        message.setText(
                "The seller declined your cash-on-delivery request.\n\n" +
                        "Listing: " + listingTitle + "\n\n" +
                        "The item may still be available. You can try another payment method."
        );

        javaMailSender.send(message);
    }

    public void sendAccountDeletedEmail(String email) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setTo(email);
        message.setSubject("Used - Your Account Has Been Deleted");

        message.setText(
                "Hello,\n\n" +
                        "Your Used account has been permanently deleted by an administrator.\n\n" +
                        "All of your data, including your profile and listings, has been " +
                        "removed and cannot be recovered.\n\n" +
                        "If this was a mistake, you may register a new account.\n\n" +
                        "Thank you."
        );

        javaMailSender.send(message);
    }

    public void sendListingUnavailableEmail(String email, String listingTitle, String reason) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Used - A Listing You Saved Is No Longer Available");

        String status = reason.equalsIgnoreCase("sold")
                ? "has been sold"
                : "is no longer available";

        message.setText(
                "A listing you saved is no longer available.\n\n" +
                        "Listing: " + listingTitle + "\n" +
                        "Status: " + status + "\n\n" +
                        "The listing has been removed from your favorites.\n\n" +
                        "Thank you for using Used."
        );
        javaMailSender.send(message);
    }
}