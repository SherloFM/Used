package com.example.accounts.model.requests;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VerificationRequests{

    private String email;
    private String code;
}