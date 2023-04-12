package org.lamisplus.biometric.domain.dto;

import lombok.Data;

import java.util.Objects;

@Data
public class MatchedPair {
    String id1;
    String id2;
    Integer score;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MatchedPair)) return false;
        MatchedPair pair = (MatchedPair) o;
        return getId1().equals(pair.getId1()) && getId2().equals(pair.getId2()) ||
            getId1().equals(pair.getId2()) && getId2().equals(pair.getId1());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId1(), getId2());
    }
}
