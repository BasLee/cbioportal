package org.cbioportal.model;

import java.io.Serializable;
import javax.validation.constraints.NotNull;

public class DiscreteCopyNumberData extends Alteration implements Serializable {
    // TODO: add annotationJSON
    @NotNull
    private Integer alteration;
    
    public Integer getAlteration() {
        return alteration;
    }

    public void setAlteration(Integer alteration) {
        this.alteration = alteration;
    }
}
