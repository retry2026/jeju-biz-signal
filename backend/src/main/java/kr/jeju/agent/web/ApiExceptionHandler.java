package kr.jeju.agent.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import kr.jeju.agent.web.dto.ApiErrorResponse;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        var field = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField())
                .orElse(null);

        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("INVALID_REQUEST", "요청값이 올바르지 않습니다.", field));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("INVALID_REQUEST", "요청 본문 형식이 올바르지 않습니다.", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        logger.error("API 요청 처리 중 예기치 않은 오류", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("INTERNAL_ERROR", "요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.", null));
    }
}
