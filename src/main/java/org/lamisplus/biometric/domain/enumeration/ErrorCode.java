
package org.lamisplus.biometric.domain.enumeration;

import SecuGen.FDxSDKPro.jni.SGFDxErrorCode;
import lombok.Getter;

/**
 * @author
 */
public enum ErrorCode {

    SGFDX_ERROR_NONE(SGFDxErrorCode.SGFDX_ERROR_NONE, "SGFDX_ERROR_NONE", "Success", Type.SUCCESS),
    SGFDX_ERROR_CREATION_FAILED(Long.valueOf(SGFDxErrorCode.SGFDX_ERROR_CREATION_FAILED), "SGFDX_ERROR_CREATION_FAILED", "JSGFPLib object creation failed", Type.ERROR),
    SGFDX_ERROR_FUNCTION_FAILED(SGFDxErrorCode.SGFDX_ERROR_FUNCTION_FAILED, "SGFDX_ERROR_FUNCTION_FAILED", "Function call failed", Type.ERROR),
    SGFDX_ERROR_INVALID_PARAM(SGFDxErrorCode.SGFDX_ERROR_INVALID_PARAM, "SGFDX_ERROR_INVALID_PARAM", "Invalid parameter used", Type.ERROR),
    SGFDX_ERROR_NOT_USED(SGFDxErrorCode.SGFDX_ERROR_NOT_USED, "SGFDX_ERROR_NOT_USED", "Not used function", Type.ERROR),
    SGFDX_ERROR_DLLLOAD_FAILED(SGFDxErrorCode.SGFDX_ERROR_DLLLOAD_FAILED, "SGFDX_ERROR_DLLLOAD_FAILED", "DLL loading failed", Type.ERROR),
    SGFDX_ERROR_DLLLOAD_FAILED_DRV(SGFDxErrorCode.SGFDX_ERROR_DLLLOAD_FAILED_DRV, "USB UPx driver", "260*300", Type.ERROR),
    SGFDX_ERROR_DLLLOAD_FAILED_ALG(SGFDxErrorCode.SGFDX_ERROR_DLLLOAD_FAILED_ALG, "SGFDX_ERROR_DLLLOAD_FAILED_ALG", "Algorithm DLL loading failed", Type.ERROR),
    SGFDX_ERROR_SYSLOAD_FAILED(SGFDxErrorCode.SGFDX_ERROR_SYSLOAD_FAILED, "SGFDX_ERROR_SYSLOAD_FAILED", "Cannot find driver sys file", Type.ERROR),
    SGFDX_ERROR_INITIALIZE_FAILED(SGFDxErrorCode.SGFDX_ERROR_INITIALIZE_FAILED, "SGFDX_ERROR_INITIALIZE_FAILED", "Chip initialization failed", Type.ERROR),
    SGFDX_ERROR_LINE_DROPPED(SGFDxErrorCode.SGFDX_ERROR_LINE_DROPPED, "SGFDX_ERROR_LINE_DROPPED", " Image data lost", Type.ERROR),
    SGFDX_ERROR_TIME_OUT(SGFDxErrorCode.SGFDX_ERROR_TIME_OUT, "SGFDX_ERROR_TIME_OUT", "GetImageEx() timeout", Type.ERROR),
    SGFDX_ERROR_DEVICE_NOT_FOUND(SGFDxErrorCode.SGFDX_ERROR_DEVICE_NOT_FOUND, "SGFDX_ERROR_DEVICE_NOT_FOUND", "Device not found", Type.ERROR),
    SGFDX_ERROR_DRVLOAD_FAILED(SGFDxErrorCode.SGFDX_ERROR_DRVLOAD_FAILED, "SGFDX_ERROR_DRVLOAD_FAILED", "Driver file load failed", Type.ERROR),
    SGFDX_ERROR_WRONG_IMAGE(SGFDxErrorCode.SGFDX_ERROR_WRONG_IMAGE, "SGFDX_ERROR_WRONG_IMAGE", "Wrong image", Type.ERROR),
    SGFDX_ERROR_LACK_OF_BANDWIDTH(SGFDxErrorCode.SGFDX_ERROR_LACK_OF_BANDWIDTH, "SGFDX_ERROR_LACK_OF_BANDWIDTH", "Lack of USB bandwidth", Type.ERROR),
    SGFDX_ERROR_DEV_ALREADY_OPEN(SGFDxErrorCode.SGFDX_ERROR_DEV_ALREADY_OPEN, "SGFDX_ERROR_DEV_ALREADY_OPEN", "Device is already opened", Type.ERROR),
    SGFDX_ERROR_GETSN_FAILED(SGFDxErrorCode.SGFDX_ERROR_GETSN_FAILED, "SGFDX_ERROR_GETSN_FAILED", "Serial number does not exist", Type.ERROR),
    SGFDX_ERROR_UNSUPPORTED_DEV(SGFDxErrorCode.SGFDX_ERROR_UNSUPPORTED_DEV, "SGFDX_ERROR_UNSUPPORTED_DEV", "Unsupported device", Type.ERROR),
    SGFDX_ERROR_FEAT_NUMBER(SGFDxErrorCode.SGFDX_ERROR_FEAT_NUMBER, "SGFDX_ERROR_FEAT_NUMBER", "Inadequate number of minutiae", Type.ERROR),
    SGFDX_ERROR_INVALID_TEMPLATE_TYPE(SGFDxErrorCode.SGFDX_ERROR_INVALID_TEMPLATE_TYPE, "SGFDX_ERROR_INVALID_TEMPLATE_TYPE", "Wrong template type", Type.ERROR),
    SGFDX_ERROR_INVALID_TEMPLATE1(SGFDxErrorCode.SGFDX_ERROR_INVALID_TEMPLATE1, "SGFDX_ERROR_INVALID_TEMPLATE1", "Error in decoding template 1", Type.ERROR),
    SGFDX_ERROR_INVALID_TEMPLATE2(SGFDxErrorCode.SGFDX_ERROR_INVALID_TEMPLATE2, "SGFDX_ERROR_INVALID_TEMPLATE2", "Error in decoding template 2", Type.ERROR),
    SGFDX_ERROR_EXTRACT_FAIL(SGFDxErrorCode.SGFDX_ERROR_EXTRACT_FAIL, "SGFDX_ERROR_EXTRACT_FAIL", "Extraction failed", Type.ERROR),
    SGFDX_ERROR_MATCH_FAIL(SGFDxErrorCode.SGFDX_ERROR_MATCH_FAIL, "SGFDX_ERROR_MATCH_FAIL", "Matching failed", Type.ERROR),
    SGFDX_ERROR_JNI_DLLLOAD_FAILED(SGFDxErrorCode.SGFDX_ERROR_JNI_DLLLOAD_FAILED, "SGFDX_ERROR_JNI_DLLLOAD_FAILED", "An error occurred while loading JSGFPLIB.DLL JNI Wrapper", Type.ERROR),
    SGFDX_ERROR_NOT_AVAILABLE(987654321L, "SGFDX_ERROR_NOT_AVAILABLE", " Device not reachable", Type.ERROR);
    @Getter
    private final Long errorID;
    @Getter
    private final String errorName;
    @Getter
    private final String errorMessage;
    public enum Type {ERROR, SUCCESS}
    @Getter
    private final Type type;
    ErrorCode(Long errorID, String errorName, String errorMessage, Type type) {
        this.errorID = errorID;
        this.errorName = errorName;
        this.errorMessage = errorMessage;
        this.type = type;
    }
    public static ErrorCode getErrorCode(Long errorID){
        for(ErrorCode errorCode : ErrorCode.values()){
            if (errorCode.getErrorID() == errorID){
                return errorCode;
            }
        }
        return ErrorCode.getErrorCode(987654321L);
    }
}
