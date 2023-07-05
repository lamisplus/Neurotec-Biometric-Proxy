package org.lamisplus.biometric.repository;

import org.lamisplus.biometric.domain.dto.StoredBiometric;
import org.lamisplus.biometric.domain.entity.Biometric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;


public interface BiometricRepository extends JpaRepository<Biometric, String> {

    /*@Query(value="SELECT person_uuid, id, (CASE template_type WHEN 'Right Middle Finger' THEN template END) AS rightMiddleFinger,  \n" +
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
    Set<StoredBiometric> findByFacilityIdWithTemplate(Long facilityId, String template);*/

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
            "            From biometric WHERE facility_id=?1 AND ENCODE(CAST(template AS BYTEA), 'hex') LIKE ?2 AND archived=0" +
            " GROUP BY person_uuid, recapture", nativeQuery = true)
    Set<StoredBiometric> findByFacilityIdWithTemplate(Long facilityId, String template);


}
