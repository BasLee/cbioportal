package org.mskcc.cbio.portal.scripts;

import org.mskcc.cbio.portal.dao.DaoException;
import org.mskcc.cbio.portal.dao.DaoGeneticAlteration;
import org.mskcc.cbio.portal.model.CanonicalGene;
import org.mskcc.cbio.portal.util.ProgressMonitor;

import java.util.HashSet;

public class GeneticAlterationGeneImporter {
    private final int geneticProfileId;
    private final HashSet<Long> importSetOfGenes = new HashSet<Long>();
    private final DaoGeneticAlteration daoGeneticAlteration;

    public GeneticAlterationGeneImporter(int geneticProfileId, DaoGeneticAlteration daoGeneticAlteration) {
        this.geneticProfileId = geneticProfileId;
        this.daoGeneticAlteration = daoGeneticAlteration;
    }

    public boolean storeGeneticAlterations(
        String[] values,
        CanonicalGene gene,
        String geneSymbol
    ) throws DaoException {
        //  Check that we have not already imported information regarding this gene.
        //  This is an important check, because a GISTIC or RAE file may contain
        //  multiple rows for the same gene, and we only want to import the first row.
        try {
            if (!importSetOfGenes.contains(gene.getEntrezGeneId())) {
                daoGeneticAlteration.addGeneticAlterations(geneticProfileId, gene.getEntrezGeneId(), values);
                importSetOfGenes.add(gene.getEntrezGeneId());
                return true;
            } else {
                //TODO - review this part - maybe it should be an Exception instead of just a warning.
                String geneSymbolMessage = "";
                if (geneSymbol != null && !geneSymbol.equalsIgnoreCase(gene.getHugoGeneSymbolAllCaps()))
                    geneSymbolMessage = " (given as alias in your file as: " + geneSymbol + ")";
                ProgressMonitor.logWarning("Gene " + gene.getHugoGeneSymbolAllCaps() + " (" + gene.getEntrezGeneId() + ")" + geneSymbolMessage + " found to be duplicated in your file. Duplicated row will be ignored!");
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException("Aborted: Error found for row starting with " + geneSymbol + ": " + e.getMessage());
        }
    }


}
