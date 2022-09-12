/*
 * Copyright (c) 2016 Memorial Sloan-Kettering Cancer Center.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF MERCHANTABILITY OR FITNESS
 * FOR A PARTICULAR PURPOSE. The software and documentation provided hereunder
 * is on an "as is" basis, and Memorial Sloan-Kettering Cancer Center has no
 * obligations to provide maintenance, support, updates, enhancements or
 * modifications. In no event shall Memorial Sloan-Kettering Cancer Center be
 * liable to any party for direct, indirect, special, incidental or
 * consequential damages, including lost profits, arising out of the use of this
 * software and its documentation, even if Memorial Sloan-Kettering Cancer
 * Center has been advised of the possibility of such damage.
 */

/*
 * This file is part of cBioPortal.
 *
 * cBioPortal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.cbioportal.web.config;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.cbioportal.model.*;
import org.cbioportal.session_service.domain.Session;
import org.cbioportal.web.mixin.*;
import org.cbioportal.web.parameter.CustomDataSession;
import org.cbioportal.web.parameter.PageSettings;
import org.cbioportal.web.parameter.PageSettingsData;
import org.cbioportal.web.parameter.StudyPageSettings;
import org.cbioportal.web.parameter.VirtualStudy;
import org.cbioportal.web.parameter.VirtualStudyData;
import org.cbioportal.web.CustomAttributeWithData;

public class CustomObjectMapper extends ObjectMapper {

    public CustomObjectMapper() {

        super.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        super.enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING);
        Map<Class<?>, Class<?>> mixinMap = new HashMap<>();
        mixinMap.put(CancerStudy.class, CancerStudyMixin.class);
        mixinMap.put(ClinicalAttribute.class, ClinicalAttributeMixin.class);
        mixinMap.put(ClinicalAttributeCount.class, ClinicalAttributeCountMixin.class);
        mixinMap.put(ClinicalData.class, ClinicalDataMixin.class);
        mixinMap.put(ClinicalDataCount.class, ClinicalDataCountMixin.class);
        mixinMap.put(ClinicalEvent.class, ClinicalEventMixin.class);
        mixinMap.put(ClinicalEventData.class, ClinicalEventDataMixin.class);
        mixinMap.put(CopyNumberSeg.class, CopyNumberSegMixin.class);
        mixinMap.put(DataAccessToken.class, DataAccessTokenMixin.class);
        mixinMap.put(DiscreteCopyNumberData.class, DiscreteCopyNumberDataMixin.class);
        mixinMap.put(Gene.class, GeneMixin.class);
        mixinMap.put(GenePanel.class, GenePanelMixin.class);
        mixinMap.put(GenePanelToGene.class, GenePanelToGeneMixin.class);
        mixinMap.put(Geneset.class, GenesetMixin.class);
        mixinMap.put(GenesetMolecularData.class, GenesetMolecularDataMixin.class);
        mixinMap.put(GenesetCorrelation.class, GenesetCorrelationMixin.class);
        mixinMap.put(MolecularProfile.class, MolecularProfileMixin.class);
        mixinMap.put(Gistic.class, GisticMixin.class);
        mixinMap.put(GisticToGene.class, GisticToGeneMixin.class);
        mixinMap.put(Mutation.class, MutationMixin.class);
        mixinMap.put(MutationSpectrum.class, MutationSpectrumMixin.class);
        mixinMap.put(MutSig.class, MutSigMixin.class);
        mixinMap.put(PageSettings.class, SessionMixin.class);
        mixinMap.put(PageSettingsData.class, SessionDataMixin.class);
        mixinMap.put(Patient.class, PatientMixin.class);
        mixinMap.put(Sample.class, SampleMixin.class);
        mixinMap.put(SampleList.class, SampleListMixin.class);
        mixinMap.put(Session.class, SessionMixin.class);
        mixinMap.put(StudyPageSettings.class, SessionDataMixin.class);
        mixinMap.put(TypeOfCancer.class, TypeOfCancerMixin.class);
        mixinMap.put(ResourceDefinition.class, ResourceDefinitionMixin.class);
        mixinMap.put(VirtualStudy.class, SessionMixin.class);
        mixinMap.put(VirtualStudyData.class, SessionDataMixin.class);
        mixinMap.put(CustomAttributeWithData.class, SessionDataMixin.class);
        mixinMap.put(CustomDataSession.class, SessionMixin.class);
        super.setMixIns(mixinMap);
    }
}
