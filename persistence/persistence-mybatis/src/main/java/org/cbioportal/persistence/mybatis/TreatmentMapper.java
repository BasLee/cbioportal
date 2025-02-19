package org.cbioportal.persistence.mybatis;

import org.cbioportal.model.ClinicalEventSample;
import org.cbioportal.model.Treatment;

import java.util.List;
import java.util.Set;

public interface TreatmentMapper {
    List<Treatment> getAllTreatments(List<String> sampleIds, List<String> studyIds, String key);

    List<ClinicalEventSample> getAllSamples(List<String> sampleIds, List<String> studyIds);
    
    List<ClinicalEventSample> getAllShallowSamples(List<String> sampleIds, List<String> studyIds);

    Set<String> getAllUniqueTreatments(List<String> sampleIds, List<String> studyIds, String key);

    Integer getTreatmentCount(List<String> sampleIds, List<String> studyIds, String key);

    Integer getSampleCount(List<String> sampleIds, List<String> studyIds);
}
