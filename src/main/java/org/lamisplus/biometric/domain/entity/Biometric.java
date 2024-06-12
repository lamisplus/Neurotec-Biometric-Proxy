package org.lamisplus.biometric.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import org.hibernate.annotations.ResultCheckStyle;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.Where;
import org.springframework.data.domain.Persistable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;


@Entity
@Table(name = "biometric")
@SQLDelete(sql = "delete from biometric where id = ?", check = ResultCheckStyle.COUNT)
@Where(clause = "archived = 0")
@NoArgsConstructor
@Setter
@Getter
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Builder
public class Biometric extends BiometricAuditEntity  implements Serializable, Persistable<String> {

    @Id
    @Column(name = "ID")
    private String id;

    @Column(name = "person_uuid")
    private String personUuid;

    @NotNull
    private byte[] template;

    @Column(name = "biometric_type")
    @NotNull
    private String biometricType;

    @Column(name = "template_type")
    @NotNull
    private String templateType;

    @Column(name = "enrollment_date")
    @NotNull
    private LocalDate date;

    private Integer archived = 0;

    private Boolean iso = false;

    @Type(type = "jsonb-node")
    @Column(columnDefinition = "jsonb")
    private JsonNode extra;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "reason")
    private String reason;

    @Column(name = "version_iso_20")
    private Boolean versionIso20;

    @Column(name = "image_quality")
    private Integer imageQuality=0;

    @Column(name = "recapture")
    private Integer recapture;

    @Column(name = "replace_date")
    private LocalDate replaceDate;

    @Column(name = "match_type")
    private String matchType;

    @Column(name = "match_biometric_id")
    private String matchBiometricId;

    @Column(name = "match_person_uuid")
    private String matchPersonUuid;

    @Column(name = "recapture_message")
    private String recaptureMessage;

    @Column(name = "hashed")
    private String hashed;

    @Column(name = "count")
    private Integer count;

    @Override
    public boolean isNew() {
        return id == null;
    }

    public void setId(String id) {
        if (id != null && !id.isEmpty()) {
            this.id = id;
        } else {
            this.id = UUID.randomUUID().toString();
        }
    }

}
