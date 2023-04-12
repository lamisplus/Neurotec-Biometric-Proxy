
package org.lamisplus.biometric.domain.dto;


public interface StoredBiometric {

     String getPatientId();
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
}
