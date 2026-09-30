package com.duanttcsn5.library.exception;

import com.duanttcsn5.library.dto.common.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        return ResponseEntity
                .status(exception.getStatus())
                .body(new ErrorResponse(
                        exception.getMessage(),
                        exception.getCode(),
                        OffsetDateTime.now(),
                        exception.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null
                        ? "Dữ liệu đầu vào không hợp lệ"
                        : error.getDefaultMessage())
                .orElse("Dữ liệu đầu vào không hợp lệ");

        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(
                        message,
                        "VALIDATION_ERROR",
                        OffsetDateTime.now(),
                        Map.of()));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(org.springframework.http.converter.HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "Dữ liệu không hợp lệ. Kiểm tra kiểu dữ liệu, ngày theo định dạng YYYY-MM-DD và các giá trị lựa chọn.",
                "INVALID_REQUEST", OffsetDateTime.now(), Map.of()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(org.springframework.security.access.AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(
                "Bạn không có quyền thực hiện thao tác này.", "FORBIDDEN", OffsetDateTime.now(), Map.of()));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse(
                "Tham số yêu cầu không hợp lệ.", "INVALID_PARAMETER", OffsetDateTime.now(), Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "Hệ thống đang gặp lỗi. Vui lòng thử lại sau.",
                        "INTERNAL_SERVER_ERROR",
                        OffsetDateTime.now(),
                        Map.of()));
    }
}
