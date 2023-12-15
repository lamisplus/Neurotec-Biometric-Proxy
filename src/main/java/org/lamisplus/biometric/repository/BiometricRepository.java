package org.lamisplus.biometric.repository;

import org.lamisplus.biometric.domain.dto.StoredBiometric;
import org.lamisplus.biometric.domain.entity.Biometric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;


public interface BiometricRepository extends JpaRepository<Biometric, String> {

    @Query(value="SELECT person_uuid, id, (CASE template_type WHEN 'Right Middle Finger' THEN template END) AS rightMiddleFinger,  \n" +
            "    (CASE template_type WHEN 'Right Thumb' THEN template END) AS rightThumb, \n" +
            "\t(CASE template_type WHEN 'Right Index Finger' THEN template END) AS rightIndexFinger, \n" +
            "\t(CASE template_type WHEN 'Right Ring Finger' THEN template END) AS rightRingFinger,\n" +
            "\t(CASE template_type WHEN 'Right Little Finger' THEN template END) AS rightLittleFinger,\n" +
            "\t(CASE template_type WHEN 'Left Index Finger' THEN template END) AS leftIndexFinger,  \n" +
            "    (CASE template_type WHEN 'Left Middle Finger' THEN template END) AS leftMiddleFinger, \n" +
            "\t(CASE template_type WHEN 'Left Thumb' THEN template END) AS leftThumb,\n" +
            "\t(CASE template_type WHEN 'Left Ring Finger' THEN template END) AS leftRingFinger,\n" +
            "\t(CASE template_type WHEN 'Left Little Finger' THEN template END) AS leftLittleFinger\t\n" +
            "\tFrom biometric WHERE facility_id=?1 AND ENCODE(CAST(template AS BYTEA), 'hex') LIKE ?2 Group By person_uuid, id", nativeQuery = true)
    Set<StoredBiometric> findByFacilityIdWithTemplate(Long facilityId, String template);

    @Query(value = "select * from biometric where archived = 0 " +
            "and version_iso_20 = true and template is not null ",
    nativeQuery = true)
    List<Biometric> getAllFingerPrintsByFacility();

    @Query(value = "select * from biometric where archived = 0 " +
            "and version_iso_20 = true and template is not null and recapture = 0",
            nativeQuery = true)
    List<Biometric> getAllBaselineFingerPrintsByFacility();


    @Query(value = "select * from biometric b where b.person_uuid in ( " +
            "select distinct person_uuid from biometric where archived = 0 " +
            "and recapture > 0 )" ,
            nativeQuery = true)
    List<Biometric> getPrintForRecapturedDeduplication();


    @Query(value = "select * from biometric " +
            "where archived = 0 and version_iso_20 = true " +
            "and recapture = 0 " +
            "and person_uuid = (select uuid from patient_person where archived = 0 and id = ?1)", nativeQuery = true)
    List<Biometric> getPatientBaselineFingerprints(Long patientID);
}
