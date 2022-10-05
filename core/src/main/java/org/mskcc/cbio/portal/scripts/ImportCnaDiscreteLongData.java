/*
 * Copyright (c) 2018 - 2022 The Hyve B.V.
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.mskcc.cbio.portal.scripts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import org.cbioportal.model.CNA;
import org.mskcc.cbio.portal.dao.*;
import org.mskcc.cbio.portal.model.*;
import org.mskcc.cbio.portal.util.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

public class ImportCnaDiscreteLongData {

    private final File cnaFile;
    private final int geneticProfileId;
    private final GeneticAlterationGeneImporter geneticAlterationGeneImporter;
    private final DaoGeneOptimized daoGene = DaoGeneOptimized.getInstance();
    private CnaDiscreteLongUtil cnaUtil;
    private boolean isDiscretizedCnaProfile;
    private final Map<CnaEvent.Event, CnaEvent.Event> existingCnaEvents = new HashMap<>();
    private final SampleFinder sampleFinder;
    private final SampleProfileImporter sampleProfileImporter;

    public ImportCnaDiscreteLongData(
        File cnaFile,
        int geneticProfileId,
        String genePanel
    ) throws DaoException {
        this.cnaFile = cnaFile;
        this.geneticProfileId = geneticProfileId;
        DaoGeneticAlteration daoGeneticAlteration = DaoGeneticAlteration.getInstance();
        this.geneticAlterationGeneImporter = new GeneticAlterationGeneImporter(geneticProfileId, daoGeneticAlteration);
        this.sampleFinder = new SampleFinder();
        this.sampleProfileImporter = new SampleProfileImporter(geneticProfileId, genePanel);
    }

    public void importData() throws Exception {
        FileReader reader = new FileReader(this.cnaFile);
        BufferedReader buf = new BufferedReader(reader);

        // Pass first line with headers to util:
        String line = buf.readLine();
        int lineIndex = 1;
        this.cnaUtil = new CnaDiscreteLongUtil(line);

        GeneticProfile geneticProfile = DaoGeneticProfile.getGeneticProfileById(geneticProfileId);

        isDiscretizedCnaProfile = geneticProfile != null
            && geneticProfile.getGeneticAlterationType() == GeneticAlterationType.COPY_NUMBER_ALTERATION
            && geneticProfile.showProfileInAnalysisTab();

        if (isDiscretizedCnaProfile) {
            for (CnaEvent.Event event : DaoCnaEvent.getAllCnaEvents()) {
                existingCnaEvents.put(event, event);
            }
            MySQLbulkLoader.bulkLoadOn();
        }

        while ((line = buf.readLine()) != null) {
            lineIndex++;
            ProgressMonitor.incrementCurValue();
            ConsoleUtil.showProgress();
            boolean hasData = !line.startsWith("#") && line.trim().length() > 0;
            if (hasData) {
                String[] parts = line.split("\t", -1);
                this.importCnaRecord(geneticProfile, parts, lineIndex);
            }
        }
        ProgressMonitor.setCurrentMessage(" --> total number of samples skipped (normal samples): "
            + sampleFinder.getSamplesSkipped()
        );
        buf.close();
        MySQLbulkLoader.flushAll();
    }

    /**
     * Per regel:
     * - record aanmaken in sample_cna_event
     * - record aanmaken in cna_event
     */
    public void importCnaRecord(
        GeneticProfile geneticProfile,
        String[] parts,
        int lineIndex
    ) throws Exception {
        System.out.println("parts: " + new ObjectMapper().writeValueAsString(parts));

        CanonicalGene gene = this.getGene(cnaUtil.getEntrezSymbol(parts), parts, cnaUtil);
        if (gene == null) {
            throw new IllegalStateException("No gene could be found for line " + lineIndex);
        }

        int cancerStudyId = geneticProfile.getCancerStudyId();
        String sampleIdStr = cnaUtil.getSampleIdStr(parts);

        Sample sample = sampleFinder.findSample(sampleIdStr, cancerStudyId);
        sampleProfileImporter.createSampleProfile(sample);

        CnaEvent cna = cnaUtil.createCnaEvent(geneticProfile, sample.getInternalId(), parts);
        System.out.println("cna: " + new ObjectMapper().writeValueAsString(cna));

        if (!isDiscretizedCnaProfile) {
            ProgressMonitor.logWarning(String.format("Skip line %s, !isDiscretizedCnaProfile", lineIndex));
            return;
        }

        if (!cna.getAlteration().equals(CNA.AMP) && !cna.getAlteration().equals(CNA.HOMDEL)) {
            return;
        }

        String geneSymbol = Strings.isNullOrEmpty(cna.getGeneSymbol()) ? cna.getGeneSymbol() : "" + cna.getEntrezGeneId();
        
        // In the original wide format, all alterations of a single gene were contained in single row
        // Now every combination of sample and gene has their own row, resulting in an single value per row:
        // TODO: 1. allemaal verzamelen per sample&gen:
        String[] values = new String[]{"" + cna.getAlteration().getCode()};
        
        // TODO: 2. als komma seperated list toevoegen aan  genetic_alteration:
        boolean recordStored = this.geneticAlterationGeneImporter.storeGeneticAlterations(
            values, 
            gene, 
            geneSymbol
        );
        // TODO: 3. alle waardes concatten en toevoegen in genetic_profile_samples

        if (recordStored) {
            if (existingCnaEvents.containsKey(cna.getEvent())) {
                cna.setEventId(existingCnaEvents.get(cna.getEvent()).getEventId());
                DaoCnaEvent.addCaseCnaEvent(cna, false);
            } else {
                DaoCnaEvent.addCaseCnaEvent(cna, true);
                existingCnaEvents.put(cna.getEvent(), cna.getEvent());
            }
        }
    }

    /**
     * @return null when no gene could be found
     */
    private CanonicalGene getGene(
        long entrez,
        String[] parts,
        CnaDiscreteLongUtil util
    ) {

        String hugoSymbol = util.getHugoSymbol(parts);

        if (Strings.isNullOrEmpty(hugoSymbol) && entrez == 0) {
            ProgressMonitor.logWarning("Ignoring line with no Hugo_Symbol or Entrez_Id value");
            return null;
        }
        if (entrez != 0) {
            //try entrez:
            return this.daoGene.getGene(entrez);
        } else if (!Strings.isNullOrEmpty(hugoSymbol)) {
            //try hugo:
            if (hugoSymbol.contains("///") || hugoSymbol.contains("---")) {
                //  Ignore gene IDs separated by ///.  This indicates that
                //  the line contains information regarding multiple genes, and
                //  we cannot currently handle this.
                //  Also, ignore gene IDs that are specified as ---.  This indicates
                //  the line contains information regarding an unknown gene, and
                //  we cannot currently handle this.
                ProgressMonitor.logWarning("Ignoring gene ID:  " + hugoSymbol);
                return null;
            }
            int ix = hugoSymbol.indexOf("|");
            if (ix > 0) {
                hugoSymbol = hugoSymbol.substring(0, ix);
            }
            List<CanonicalGene> genes = daoGene.getGene(hugoSymbol, true);
            if (genes.size() > 1) {
                throw new IllegalStateException("Found multiple genes for Hugo symbol " + hugoSymbol + " while importing cna");
            }
            return genes.get(0);
        } else {
            ProgressMonitor.logWarning("Entrez_Id " + entrez + " not found. Record will be skipped for this gene.");
            return null;
        }
    }

}

class SampleIdGeneticProfileId {
    public int sampleId;
    public int geneticProfileId;

    public SampleIdGeneticProfileId(int sampleId, int geneticProfileId) {
        this.sampleId = sampleId;
        this.geneticProfileId = geneticProfileId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        SampleIdGeneticProfileId that = (SampleIdGeneticProfileId) o;
        return sampleId == that.sampleId
            && geneticProfileId == that.geneticProfileId;
    }
}

class SampleFinder {

    private int samplesSkipped = 0;

    /**
     * Find sample and create sample profile when needed
     */
    public Sample findSample(
        String sampleId,
        int cancerStudyId
    ) throws Exception {
        Sample sample = DaoSample.getSampleByCancerStudyAndSampleId(
            cancerStudyId,
            StableIdUtil.getSampleId(sampleId)
        );
        // can be null in case of 'normal' sample, throw exception if not 'normal' and sample not found in db
        if (sample == null) {
            if (StableIdUtil.isNormal(sampleId)) {
                samplesSkipped++;
                return null;
            } else {
                throw new RuntimeException("Unknown sample id '" + StableIdUtil.getSampleId(sampleId));
            }
        }
        return sample;
    }

    public int getSamplesSkipped() {
        return samplesSkipped;
    }
}

class SampleProfileImporter {

    private final ArrayList<SampleIdGeneticProfileId> sampleIdGeneticProfileIds = new ArrayList<>();

    private final int geneticProfileId;
    private final String genePanel;

    public SampleProfileImporter(int geneticProfileId, String genePanel) {
        this.geneticProfileId = geneticProfileId;
        this.genePanel = genePanel;
    }

    /**
     * Find sample and create sample profile when needed
     *
     * @return boolean created or not
     */
    public boolean createSampleProfile(
        Sample sample
    ) throws Exception {
        boolean inDatabase = DaoSampleProfile.sampleExistsInGeneticProfile(sample.getInternalId(), geneticProfileId);
        Integer genePanelID = (genePanel == null) ? null : GeneticProfileUtil.getGenePanelId(genePanel);
        SampleIdGeneticProfileId toCreate = new SampleIdGeneticProfileId(sample.getInternalId(), geneticProfileId);
        boolean isQueued = this.sampleIdGeneticProfileIds.contains(toCreate);
        if (!inDatabase && !isQueued) {
            DaoSampleProfile.addSampleProfile(sample.getInternalId(), geneticProfileId, genePanelID);
            this.sampleIdGeneticProfileIds.add(toCreate);
            return true;
        }
        return false;
    }

}
