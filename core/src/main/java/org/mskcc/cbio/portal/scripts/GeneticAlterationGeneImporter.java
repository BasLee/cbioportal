package org.mskcc.cbio.portal.scripts;

import org.mskcc.cbio.portal.dao.DaoException;
import org.mskcc.cbio.portal.dao.DaoGeneticAlteration;
import org.mskcc.cbio.portal.model.CanonicalGene;
import org.mskcc.cbio.portal.util.ProgressMonitor;

import java.util.HashSet;

import static java.lang.String.format;

public class GeneticAlterationGeneImporter {

    public static class ProfileAndGeneKey {

        private final long geneSymbol;
        private final int profileId;

        public ProfileAndGeneKey(int profileId, long geneSymbol) {
            this.geneSymbol = geneSymbol;
            this.profileId = profileId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) 
                return true;
            if (o == null || getClass() != o.getClass()) 
                return false;
            ProfileAndGeneKey form = (ProfileAndGeneKey) o;
            return geneSymbol == form.geneSymbol && profileId == form.profileId;
        }

    }
    
    private final int geneticProfileId;
    private final HashSet<ProfileAndGeneKey> importSetOfGenes = new HashSet<ProfileAndGeneKey>();
    private final DaoGeneticAlteration daoGeneticAlteration;

    public GeneticAlterationGeneImporter(int geneticProfileId, DaoGeneticAlteration daoGeneticAlteration) {
        this.geneticProfileId = geneticProfileId;
        this.daoGeneticAlteration = daoGeneticAlteration;
    }
    
    /**
     * Check that we have not already imported information regarding this gene.
     * This is an important check, because a GISTIC or RAE file may contain
     * multiple rows for the same gene, and we only want to import the first row.
     */
    public boolean storeGeneticAlterations(
        String[] values,
        CanonicalGene gene,
        String geneSymbol
    ) throws DaoException {
        ProfileAndGeneKey toImport = new ProfileAndGeneKey(geneticProfileId, gene.getEntrezGeneId());
        try {
            if (!importSetOfGenes.contains(toImport)) {
                daoGeneticAlteration.addGeneticAlterations(geneticProfileId, gene.getEntrezGeneId(), values);
                importSetOfGenes.add(toImport);
                return true;
            } else {
                String geneSymbolMessage = "";
                if (geneSymbol != null && !geneSymbol.equalsIgnoreCase(gene.getHugoGeneSymbolAllCaps())) {
                    geneSymbolMessage = " (given as alias in your file as: " + geneSymbol + ")";
                }
                ProgressMonitor.logWarning(format(
                    "Gene %s (%d)%s found to be duplicated in your file. Duplicated row will be ignored!", 
                    gene.getHugoGeneSymbolAllCaps(), 
                    gene.getEntrezGeneId(), 
                    geneSymbolMessage)
                );
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException("Aborted: Error found for row starting with " + geneSymbol + ": " + e.getMessage());
        }
    }


}
