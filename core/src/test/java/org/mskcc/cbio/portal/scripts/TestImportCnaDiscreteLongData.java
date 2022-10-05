/*
 * Copyright (c) 2018 The Hyve B.V.
 * This code is licensed under the GNU Affero General Public License (AGPL),
 * version 3, or (at your option) any later version.
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

/*
 * @author Sander Tan
*/

package org.mskcc.cbio.portal.scripts;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mskcc.cbio.portal.dao.*;
import org.mskcc.cbio.portal.model.CnaEvent;
import org.mskcc.cbio.portal.model.GeneticProfile;
import org.mskcc.cbio.portal.model.Sample;
import org.mskcc.cbio.portal.util.ProgressMonitor;
import org.mskcc.cbio.portal.util.StableIdUtil;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:/applicationContext-dao.xml" })
@Rollback
@Transactional
public class TestImportCnaDiscreteLongData {
    int studyId;
    GeneticProfile geneticProfile;

    @Before
    public void setUp() throws DaoException
    {
        studyId = DaoCancerStudy
            .getCancerStudyByStableId("study_tcga_pub")
            .getInternalId();
        this.geneticProfile = DaoGeneticProfile
            .getGeneticProfileByStableId("study_tcga_pub_cna_long");
    }

    @Test
    public void testImportCnaDiscreteLongData() throws Exception {
        String sampleId1 = StableIdUtil.getSampleId("TCGA-A1-A0SB-11");
        String sampleId2 = StableIdUtil.getSampleId("TCGA-A2-A04U-11");

        List<CnaEvent.Event> beforeCnaEvents = DaoCnaEvent.getAllCnaEvents();

        ProgressMonitor.setConsoleMode(false);
        File file = new File("src/test/resources/data_cna_discrete_import_test.txt");
        String genePanel = "TESTPANEL_CNA";
        ImportCnaDiscreteLongData importer = new ImportCnaDiscreteLongData(
            file, 
            geneticProfile.getGeneticProfileId(), 
            genePanel
        );
        importer.importData();

        assertSampleAndProfileExists(sampleId1);
        assertSampleAndProfileExists(sampleId2);

        List<CnaEvent.Event> afterCnaEvents = DaoCnaEvent.getAllCnaEvents();
        int expectedNewCnaEvents = 12;
        assertEquals(afterCnaEvents.size(), beforeCnaEvents.size());
//        assertEquals(afterCnaEvents.size(), (beforeCnaEvents.size() + expectedNewCnaEvents));
        MySQLbulkLoader.flushAll();
    }

    private void assertSampleAndProfileExists(String sampleId1) throws DaoException {
        Sample sample = DaoSample.getSampleByCancerStudyAndSampleId(
            geneticProfile.getCancerStudyId(),
            sampleId1
        );
        Assert.assertNotNull(sample);
        // TODO: first find created samples:
//        DaoCnaEvent.getCnaEvents()
        Assert.assertTrue(DaoSampleProfile.sampleExistsInGeneticProfile(
            sample.getInternalId(), 
            geneticProfile.getGeneticProfileId()
        ));
    }
}
