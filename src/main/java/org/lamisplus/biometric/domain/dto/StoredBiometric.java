
package org.lamisplus.biometric.domain.dto;


public interface StoredBiometric {

     String getPersonUuid();
     byte[] getRightMiddleFinger();
     byte[] getRightThumb();
     byte[] getRightIndexFinger();
     byte[] getRightRingFinger();
     byte[] getRightLittleFinger();
     byte[] getLeftIndexFinger();
     byte[] getLeftMiddleFinger();
     byte[] getLeftThumb();
     byte[] getLeftRingFinger();
     byte[] getLeftLittleFinger();
     int getRecapture();
}
