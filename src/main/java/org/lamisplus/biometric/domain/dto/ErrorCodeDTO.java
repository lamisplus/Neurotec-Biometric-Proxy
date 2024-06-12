package org.lamisplus.biometric.domain.dto;

import lombok.Builder;
import lombok.Data;
import org.lamisplus.biometric.domain.enumeration.ErrorCode;

@Data
@Builder
public class ErrorCodeDTO {
    private final Long errorID;
    private final String errorName;
    private final String errorMessage;
    private final ErrorCode.Type errorType;
}
