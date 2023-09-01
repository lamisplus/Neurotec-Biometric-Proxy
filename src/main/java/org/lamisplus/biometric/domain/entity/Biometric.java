package org.lamisplus.biometric.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import lombok.*;
import org.hibernate.annotations.*;
import org.springframework.data.domain.Persistable;

import javax.persistence.*;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDate;


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
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class Biometric implements Serializable, Persistable<String> {

    @Id
    @GeneratedValue( generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Basic(optional = false)
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

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    private JsonNode extra;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "facility_id")
    private Long facilityId;

    private String reason;

    @Column(name = "version_iso_20")
    private Boolean versionIso20;

    @Column(name = "image_quality")
    private Integer imageQuality=0;

    @Column(name = "recapture")
    private Integer recapture;

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

}
