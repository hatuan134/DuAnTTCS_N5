package com.duanttcsn5.library.dto.reader;

public record DuplicateCheckResponse(
        boolean emailExists,
        boolean memberCodeExists,
        String emailMessage,
        String memberCodeMessage,
        boolean suggestForgotPassword,
        String forgotPasswordUrl
) {
}
