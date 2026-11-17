package com.example.marketplace.model.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InternalDelistRequest(
        String reason,
        @NotNull(message = "Admin id is required") Long adminId,
        @NotBlank(message = "Admin username is required") String adminUsername
) {
}
