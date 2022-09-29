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

    public ImportCnaDiscreteLongData(
        File cnaFile,
        int geneticProfileId,
        String genePanel
    ) throws DaoException {
        this.cnaFile = cnaFile;
        this.geneticProfileId = geneticProfileId;
        DaoGeneticAlteration daoGeneticAlteration = DaoGeneticAlteration.getInstance();
        this.geneticAlterationGeneImporter = new GeneticAlterationGeneImporter(geneticProfileId, daoGeneticAlteration);
        this.sampleFinder = new SampleFinder(geneticProfileId, genePanel);
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
            if (!line.startsWith("#") && line.trim().length() > 0) {
                String parts[] = line.split("\t", -1);
                this.importCnaRecord(geneticProfile, parts, lineIndex);
            }
        }
        buf.close();
        MySQLbulkLoader.flushAll();
    }

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

        Sample sample = sampleFinder.findSample(
            sampleIdStr,
            cancerStudyId,
            lineIndex
        );
        
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
        String[] values = new String[]{"" + cna.getAlteration().getCode()};

        boolean recordStored = this.geneticAlterationGeneImporter.storeGeneticAlterations(values, gene, geneSymbol);

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

        String geneSymbol = null;

        String hugoSymbol = util.getHugoSymbol(parts);
        if (!Strings.isNullOrEmpty(hugoSymbol)) {
            geneSymbol = hugoSymbol;
        }

        if (geneSymbol == null && entrez == 0) {
            ProgressMonitor.logWarning("Ignoring line with no Hugo_Symbol or Entrez_Id value");
            return null;
        }
        boolean ignoreGeneSymbol = geneSymbol != null && (geneSymbol.contains("///") || geneSymbol.contains("---"));
        if (ignoreGeneSymbol) {
            //  Ignore gene IDs separated by ///.  This indicates that
            //  the line contains information regarding multiple genes, and
            //  we cannot currently handle this.
            //  Also, ignore gene IDs that are specified as ---.  This indicates
            //  the line contains information regarding an unknown gene, and
            //  we cannot currently handle this.
            ProgressMonitor.logWarning("Ignoring gene ID:  " + geneSymbol);
            return null;
        }
        if (entrez != 0) {
            //try entrez:
            return this.daoGene.getGene(entrez);
        } else if (!Strings.isNullOrEmpty(hugoSymbol)) {
            //try hugo:
            return daoGene.getGene(hugoSymbol, true).get(0);
        } else {
            ProgressMonitor.logWarning("Entrez_Id " + entrez + " not found. Record will be skipped for this gene.");
            return null;
        }
    }
}

class SampleFinder {
    
    private final ArrayList <Integer> filteredSampleIndices = new ArrayList<>();
    private int samplesSkipped = 0;
    
    private final int geneticProfileId;
    private final String genePanel;

    public SampleFinder(int geneticProfileId, String genePanel) {
        this.geneticProfileId = geneticProfileId;
        this.genePanel = genePanel;
    }

    public Sample findSample(
        String sampleId,
        int cancerStudyId,
        int lineIndex
    ) throws Exception {
        Sample sample = DaoSample.getSampleByCancerStudyAndSampleId(
            cancerStudyId,
            StableIdUtil.getSampleId(sampleId)
        );
        // can be null in case of 'normal' sample, throw exception if not 'normal' and sample not found in db
        if (sample == null) {
            if (StableIdUtil.isNormal(sampleId)) {
                filteredSampleIndices.add(lineIndex);
                samplesSkipped++;
                return null;
            } else {
                throw new RuntimeException("Unknown sample id '" + StableIdUtil.getSampleId(sampleId));
            }
        }
        if (!DaoSampleProfile.sampleExistsInGeneticProfile(sample.getInternalId(), geneticProfileId)) {
            Integer genePanelID = (genePanel == null) ? null : GeneticProfileUtil.getGenePanelId(genePanel);
            DaoSampleProfile.addSampleProfile(sample.getInternalId(), geneticProfileId, genePanelID);
        }
        return sample;
    }

}
