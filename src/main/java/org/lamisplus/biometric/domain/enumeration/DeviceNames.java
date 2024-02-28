
package org.lamisplus.biometric.domain.enumeration;

import SecuGen.FDxSDKPro.jni.SGFDxDeviceName;

/**
 * @author
 */
public enum DeviceNames {
        
    SG_DEV_UNKNOWN(SGFDxDeviceName.SG_DEV_UNKNOWN, "Default", "Based on Attached Device"),
    SG_DEV_FDP02(SGFDxDeviceName.SG_DEV_FDP02, "Parallel device driver", "260*300"),
    SG_DEV_FDU02(SGFDxDeviceName.SG_DEV_FDU02, "USB FDU02 driver", "260*300"),
    SG_DEV_FDU03(SGFDxDeviceName.SG_DEV_FDU03, "USB FDU03 / SDU03 driver", "260*300"),
    SG_DEV_FDU04(SGFDxDeviceName.SG_DEV_FDU04, "USB FDU04 / SDU04 driver", "258*336"),
    SG_DEV_FDU05(SGFDxDeviceName.SG_DEV_FDU05, "USB U20 driver", "300*400"),
    SG_DEV_FDU06(SGFDxDeviceName.SG_DEV_FDU06, "USB UPx driver", "260*300"),
    //SG_DEV_FDU07(SGFDxDeviceName.SG_DEV_FDU07, "USB U10 driver", "252*330"),
    //SG_DEV_FDU07A(SGFDxDeviceName.SG_DEV_FDU07A, "USB U10-AP driver", "252*330"),
    //SG_DEV_FDU08(SGFDxDeviceName.SG_DEV_FDU08, "USB U20-A and U20-AP driver", "252*330"),
    SG_DEV_AUTO(SGFDxDeviceName.SG_DEV_AUTO, "Auto-detect", "Based on Attached Device");
    
    
    private final Long deviceID;
    private final String deviceDriver;
    private final String imageSize;
    
    DeviceNames(Long deviceID, String deviceDriver, String imageSize) {
        this.deviceID = deviceID;
        this.deviceDriver = deviceDriver;
        this.imageSize = imageSize;
    }   

    public Long getDeviceID() {
        return deviceID;
    }

    public String getDeviceDriver() {
        return deviceDriver;
    }

    public String getImageSize() {
        return imageSize;
    }
    
    public static DeviceNames getDeviceNames(int deviceID, int imageWidth, int imageHeight){
        String imageSize = imageWidth+"*"+imageHeight;
        for(DeviceNames dt : DeviceNames.values()){
            if (dt.getDeviceID() == deviceID && dt.getImageSize().equalsIgnoreCase(imageSize)){
                return dt;
            }
        }
        return null;
    }
}
