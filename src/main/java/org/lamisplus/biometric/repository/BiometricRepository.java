package org.lamisplus.biometric.repository;

import org.lamisplus.biometric.domain.ClientIdentificationProject;
import org.lamisplus.biometric.domain.dto.StoredBiometric;
import org.lamisplus.biometric.domain.entity.Biometric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;


public interface BiometricRepository extends JpaRepository<Biometric, String> {

    @Query(value="SELECT person_uuid AS personUuid, recapture, string_agg((CASE template_type WHEN 'Right Middle Finger' THEN template END), '') AS rightMiddleFinger,   \n" +
            "                string_agg((CASE template_type WHEN 'Right Thumb' THEN template END), '') AS rightThumb,  \n" +
            "            string_agg((CASE template_type WHEN 'Right Index Finger' THEN template END), '') AS rightIndexFinger,  \n" +
            "            string_agg((CASE template_type WHEN 'Right Ring Finger' THEN template END), '') AS rightRingFinger, \n" +
            "            string_agg((CASE template_type WHEN 'Right Little Finger' THEN template END), '') AS rightLittleFinger, \n" +
            "            string_agg((CASE template_type WHEN 'Left Index Finger' THEN template END), '') AS leftIndexFinger,   \n" +
            "            string_agg((CASE template_type WHEN 'Left Middle Finger' THEN template END), '') AS leftMiddleFinger,  \n" +
            "            string_agg((CASE template_type WHEN 'Left Thumb' THEN template END), '') AS leftThumb, \n" +
            "            string_agg((CASE template_type WHEN 'Left Ring Finger' THEN template END), '') AS leftRingFinger, \n" +
            "            string_agg((CASE template_type WHEN 'Left Little Finger' THEN template END), '') AS leftLittleFinger \n" +
            "            From biometric WHERE version_iso_20 is not null AND version_iso_20 is true " +
            "ENCODE(CAST(template AS BYTEA), 'hex') LIKE ?1 AND archived=0" +
            " GROUP BY person_uuid, recapture", nativeQuery = true)
    List<StoredBiometric> findByFacilityIdWithTemplate(String template);

    @Query(value="SELECT person_uuid AS personUuid, recapture, string_agg((CASE template_type WHEN 'Right Middle Finger' THEN template END), '') AS rightMiddleFinger,   \n" +
            "                string_agg((CASE template_type WHEN 'Right Thumb' THEN template END), '') AS rightThumb,  \n" +
            "            string_agg((CASE template_type WHEN 'Right Index Finger' THEN template END), '') AS rightIndexFinger,  \n" +
            "            string_agg((CASE template_type WHEN 'Right Ring Finger' THEN template END), '') AS rightRingFinger, \n" +
            "            string_agg((CASE template_type WHEN 'Right Little Finger' THEN template END), '') AS rightLittleFinger, \n" +
            "            string_agg((CASE template_type WHEN 'Left Index Finger' THEN template END), '') AS leftIndexFinger,   \n" +
            "            string_agg((CASE template_type WHEN 'Left Middle Finger' THEN template END), '') AS leftMiddleFinger,  \n" +
            "            string_agg((CASE template_type WHEN 'Left Thumb' THEN template END), '') AS leftThumb, \n" +
            "            string_agg((CASE template_type WHEN 'Left Ring Finger' THEN template END), '') AS leftRingFinger, \n" +
            "            string_agg((CASE template_type WHEN 'Left Little Finger' THEN template END), '') AS leftLittleFinger \n" +
            "            FROM biometric WHERE version_iso_20 is not null AND version_iso_20 is true AND archived=0" +
            " GROUP BY person_uuid, recapture", nativeQuery = true)
    List<StoredBiometric> findByAllPrints();

    @Query(value="SELECT person_uuid AS personUuid, recapture, string_agg((CASE template_type WHEN 'Right Middle Finger' THEN template END), '') AS rightMiddleFinger,   \n" +
            "                string_agg((CASE template_type WHEN 'Right Thumb' THEN template END), '') AS rightThumb,  \n" +
            "            string_agg((CASE template_type WHEN 'Right Index Finger' THEN template END), '') AS rightIndexFinger,  \n" +
            "            string_agg((CASE template_type WHEN 'Right Ring Finger' THEN template END), '') AS rightRingFinger, \n" +
            "            string_agg((CASE template_type WHEN 'Right Little Finger' THEN template END), '') AS rightLittleFinger, \n" +
            "            string_agg((CASE template_type WHEN 'Left Index Finger' THEN template END), '') AS leftIndexFinger,   \n" +
            "            string_agg((CASE template_type WHEN 'Left Middle Finger' THEN template END), '') AS leftMiddleFinger,  \n" +
            "            string_agg((CASE template_type WHEN 'Left Thumb' THEN template END), '') AS leftThumb, \n" +
            "            string_agg((CASE template_type WHEN 'Left Ring Finger' THEN template END), '') AS leftRingFinger, \n" +
            "            string_agg((CASE template_type WHEN 'Left Little Finger' THEN template END), '') AS leftLittleFinger \n" +
            "            From biometric WHERE version_iso_20 is not null " +
            "AND version_iso_20 is true person_uuid=?1 AND recapture=?2 " +
            "AND ENCODE(CAST(template AS BYTEA), 'hex') LIKE ?3 and archived=0" +
            " GROUP BY person_uuid, recapture", nativeQuery = true)
    List<StoredBiometric> findByFacilityIdWithTemplateAndPersonUuid(String personUuid, Integer recapture, String template);


    @Query(value="SELECT uuid FROM patient_person WHERE id=?1", nativeQuery = true)
    Optional<String> getPersonUuid(Long patientId);

    @Query(value = "select * from biometric where archived = 0 " +
            "and version_iso_20 = true and template is not null ",
    nativeQuery = true)
    List<Biometric> getAllFingerPrintsByFacility();

    @Query(value = "select * from biometric where archived = 0 " +
            "and version_iso_20 = true and template is not null and recapture = 0",
            nativeQuery = true)
    List<Biometric> getAllBaselineFingerPrintsByFacility();

    @Query(value="SELECT id, first_name AS firstName, surname AS surName, hospital_number AS hospitalNumber, sex " +
            "FROM patient_person WHERE uuid=?1", nativeQuery = true)
    Optional<ClientIdentificationProject> getBiometricPersonData(String personUuid);


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

    @Query(value = "select * from biometric " +
            "where archived = 0 and version_iso_20 = true " +
            "and recapture = 0 " +
            "and person_uuid = ?1", nativeQuery = true)
    List<Biometric> getPatientBaselineFingerprints1(String patientID);

    @Query(value = "SELECT * FROM biometric WHERE template=?1 LIMIT 1", nativeQuery = true)
    Optional<Biometric> getPatientMatchedPrint(byte[] template);

}
