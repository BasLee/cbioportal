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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mskcc.cbio.portal.dao.*;
import org.mskcc.cbio.portal.model.CnaEvent;
import org.mskcc.cbio.portal.model.GeneticProfile;
import org.mskcc.cbio.portal.model.Sample;
import org.mskcc.cbio.portal.util.StableIdUtil;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:/applicationContext-dao.xml"})
@Rollback
@Transactional
public class TestImportCnaDiscreteLongData {
    int studyId;
    GeneticProfile geneticProfile;
    String genePanel = "TESTPANEL_CNA";

    @Before
    public void setUp() throws DaoException {
        studyId = DaoCancerStudy
            .getCancerStudyByStableId("study_tcga_pub")
            .getInternalId();
        this.geneticProfile = DaoGeneticProfile
            .getGeneticProfileByStableId("study_tcga_pub_cna_long");
    }

    @After
    public void cleanUp() throws DaoException {
        MySQLbulkLoader.flushAll();
    }
    
    /**
     * Test the import of cna data file in long format consisting of:
     * - 2 samples
     */
    @Test
    public void testImportCnaDiscreteLongDataAddsSamples() throws Exception {
        List<String> expectedSampleIds = newArrayList("TCGA-A1-A0SB-11", "TCGA-A2-A04U-11");
        File file = new File("src/test/resources/data_cna_discrete_import_test.txt");
        new ImportCnaDiscreteLongData(
            file,
            geneticProfile.getGeneticProfileId(),
            genePanel
        ).importData();

        // Test new samples are added:
        for (String id : expectedSampleIds) {
            assertSampleExistsInGeneticProfile(id);
        }
    }

    /**
     * Test the import of cna data file in long format consisting of:
     * - 10 valid cna events (-2 or 2)
     */
    @Test
    public void testImportCnaDiscreteLongDataAddsCnaEvents() throws Exception {
        int expectedCnaEventCount = 10;

        List<CnaEvent.Event> beforeCnaEvents = DaoCnaEvent.getAllCnaEvents();
        assertEquals(beforeCnaEvents.size(), 17);

        File file = new File("src/test/resources/data_cna_discrete_import_test.txt");
        new ImportCnaDiscreteLongData(
            file,
            geneticProfile.getGeneticProfileId(),
            genePanel
        ).importData();

        List<CnaEvent.Event> afterCnaEvents = DaoCnaEvent.getAllCnaEvents();

        // Test all cna events are added:
        int expectedNewCnaEvents = beforeCnaEvents.size() + expectedCnaEventCount;
        assertEquals(
            expectedNewCnaEvents,
            afterCnaEvents.size()
        );
    }

    /**
     * Test the import of cna data file in long format consisting of:
     * - 7 genes
     */
    @Test
    public void testImportCnaDiscreteLongDataAddsGeneticAlterations() throws Exception {
        List<Long> expectedEntrezIds = newArrayList(2115L, 27334L, 57670L, 80070L, 3983L, 56914L, 2261L);

        List<GeneticAlteration> beforeGeneticAlterations = getAllGeneticAlterations();
        assertEquals(beforeGeneticAlterations.size(), 42);

        File file = new File("src/test/resources/data_cna_discrete_import_test.txt");
        new ImportCnaDiscreteLongData(
            file,
            geneticProfile.getGeneticProfileId(),
            genePanel
        ).importData();

        // Test genetic alterations are added for all genes:
        List<GeneticAlteration> afterGeneticAlterations = getAllGeneticAlterations();
        assertEquals(beforeGeneticAlterations.size() + expectedEntrezIds.size(), afterGeneticAlterations.size());

        // Test order of genetic alteration values:
        GeneticAlteration geneticAlteration = getGeneticAlterationBy(2115L);
        assertEquals(geneticProfile.getGeneticProfileId(), geneticAlteration.geneticProfileId);
        assertEquals("2,-2,", geneticAlteration.value);
        
        // ... and the emptiness of a gene without cna's:
        geneticAlteration = getGeneticAlterationBy(56914L);
        assertEquals(geneticProfile.getGeneticProfileId(), geneticAlteration.geneticProfileId);
        assertEquals("", geneticAlteration.value);
    }

    
    /**
     * Test the import of cna data file in long format consisting of:
     * - 2 samples
     */
    @Test
    public void testImportCnaDiscreteLongDataAddsGeneticProfileSamples() throws Exception {
        File file = new File("src/test/resources/data_cna_discrete_import_test.txt");
        new ImportCnaDiscreteLongData(
            file,
            geneticProfile.getGeneticProfileId(),
            genePanel
        ).importData();

        // Test order of samples in genetic profile samples:
        GeneticAlteration geneticAlteration = getGeneticAlterationBy(2115L);
        GeneticProfileSample geneticProfileSample = getAllGeneticProfileSample(geneticProfile.getGeneticProfileId());
        assertEquals(geneticProfile.getGeneticProfileId(), geneticAlteration.geneticProfileId);
        assertEquals("21,20,", geneticProfileSample.orderedSampleList);
    }

    private void assertSampleExistsInGeneticProfile(String sampleId) throws DaoException, JsonProcessingException {
        String sampleStableId = StableIdUtil.getSampleId(sampleId);

        Sample sample = DaoSample.getSampleByCancerStudyAndSampleId(
            geneticProfile.getCancerStudyId(),
            sampleStableId
        );
        Assert.assertNotNull(sample);
        Assert.assertTrue(DaoSampleProfile.sampleExistsInGeneticProfile(
            sample.getInternalId(),
            geneticProfile.getGeneticProfileId()
        ));
    }

    private GeneticAlteration getGeneticAlterationBy(long entrezId) throws DaoException {
        return query("SELECT ga.GENETIC_PROFILE_ID, ga.GENETIC_ENTITY_ID, ga.VALUES, g.ENTREZ_GENE_ID " +
                "FROM genetic_alteration AS ga " +
                "RIGHT JOIN gene AS g " +
                "ON g.GENETIC_ENTITY_ID = ga.GENETIC_ENTITY_ID " +
                "WHERE g.ENTREZ_GENE_ID=" + entrezId,
            (ResultSet rs) -> {
                GeneticAlteration line = new GeneticAlteration();
                line.geneticProfileId = rs.getInt("GENETIC_PROFILE_ID");
                line.geneticEntityId = rs.getInt("GENETIC_ENTITY_ID");
                line.value = rs.getString("VALUES");
                line.entrezId = rs.getLong("ENTREZ_GENE_ID");
                return line;
            }).get(0);
    }

    private List<GeneticAlteration> getAllGeneticAlterations() throws DaoException {
        return query("SELECT * FROM genetic_alteration", (ResultSet rs) -> {
            GeneticAlteration line = new GeneticAlteration();
            line.geneticProfileId = rs.getInt("GENETIC_PROFILE_ID");
            line.geneticEntityId = rs.getInt("GENETIC_ENTITY_ID");
            line.value = rs.getString("VALUES");
            return line;
        });
    }

    private GeneticProfileSample getAllGeneticProfileSample(long profileId) throws DaoException {
        return query(
            "SELECT * FROM genetic_profile_samples WHERE GENETIC_PROFILE_ID=" + profileId,
            (ResultSet rs) -> {
                GeneticProfileSample line = new GeneticProfileSample();
                line.geneticProfileId = rs.getInt("GENETIC_PROFILE_ID");
                line.orderedSampleList = rs.getString("ORDERED_SAMPLE_LIST");
                return line;
            }).get(0);
    }

    private <T> List<T> query(String query, FunctionThrowsSql<ResultSet, T> handler) throws DaoException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        List<T> result = new ArrayList<>();
        try {
            con = JdbcUtil.getDbConnection(DaoGeneticAlteration.class);
            pstmt = con.prepareStatement(query);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                result.add(handler.apply(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new DaoException(e);
        } finally {
            JdbcUtil.closeAll(DaoGeneticAlteration.class, con, pstmt, rs);
        }
    }

}

class GeneticAlteration {
    public int geneticProfileId;
    public int geneticEntityId;
    public String value;
    public long entrezId;
}

class GeneticProfileSample {
    public int geneticProfileId;
    public String orderedSampleList;
}

@FunctionalInterface
interface FunctionThrowsSql<T, R> {
    R apply(T t) throws SQLException;
}