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
}