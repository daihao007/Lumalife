package com.lumalife.common;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;

/** Error envelope kept separate from the legacy success envelope for v1 compatibility. */
public record ErrorResponse(int code, String message, @JsonInclude(JsonInclude.Include.ALWAYS) Object data, String requestId,
                            String reason, List<Map<String, Object>> details) {
  public static ErrorResponse of(int code, String message, String requestId) {
    return new ErrorResponse(code, message, null, requestId,
      ReasonCodes.forBusiness(code, message), List.of());
  }

  public static ErrorResponse of(BusinessException error, String requestId) {
    return new ErrorResponse(error.code(), error.getMessage(), null, requestId,
      error.reason(), error.details());
  }
}
