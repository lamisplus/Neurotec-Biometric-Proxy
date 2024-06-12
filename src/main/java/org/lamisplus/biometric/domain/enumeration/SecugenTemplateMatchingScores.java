
package org.lamisplus.biometric.domain.enumeration;
/**
 * @author 
 */
public enum SecugenTemplateMatchingScores {
    SL_NONE(0, 0),
    SL_LOWEST(1, 30),
    SL_LOWER(2, 50),
    SL_LOW(3, 60),
    SL_BELOW_NORMAL(4, 70),
    SL_NORMAL(5, 80),
    SL_ABOVE_NORMAL(6, 90),
    SL_HIGH(7, 100),
    SL_HIGHER(8, 120),
    SL_HIGHEST(9, 140);
    
    private final int value;
    private final int correspondingMatching;
    
    SecugenTemplateMatchingScores(int value, int correspondingMatching) {
        this.value = value;
        this.correspondingMatching = correspondingMatching;
    }

    public int getValue() {
        return value;
    }

    public int getCorrespondingMatching() {
        return correspondingMatching;
    }
    
    public static SecugenTemplateMatchingScores getSecugenTemplateMatchingScores(int score){
        for(SecugenTemplateMatchingScores ms : SecugenTemplateMatchingScores.values()){
            if (ms.getCorrespondingMatching() == score){
                return ms;
            }
        }
        return null;
    }
    
}
