/*
 * Copyright (c) 2019 - 2022 Memorial Sloan Kettering Cancer Center.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF MERCHANTABILITY OR FITNESS
 * FOR A PARTICULAR PURPOSE. The software and documentation provided hereunder
 * is on an "as is" basis, and Memorial Sloan Kettering Cancer Center has no
 * obligations to provide maintenance, support, updates, enhancements or
 * modifications. In no event shall Memorial Sloan Kettering Cancer Center be
 * liable to any party for direct, indirect, special, incidental or
 * consequential damages, including lost profits, arising out of the use of this
 * software and its documentation, even if Memorial Sloan Kettering Cancer
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

package org.mskcc.cbio.portal.util;

import org.mskcc.cbio.maf.TabDelimitedFileUtil;
import org.mskcc.cbio.portal.model.CnaEvent;
import org.mskcc.cbio.portal.model.GeneticProfile;

import java.util.HashMap;

import static org.mskcc.cbio.portal.scripts.ImportTabDelimData.*;

/**
 * @author ochoaa
 */
public class CnaDiscreteLongUtil {
    private HashMap<String, Integer> columnIndexMap;
    // TODO: Hugo_Symbol	Entrez_Gene_Id	Sample_Id	Value	cbp_driver	cbp_driver_annotation	cbp_driver_tiers	cbp_driver_tiers_annotation
    // Column names structural variant file
    public static final String HUGO_SYMBOL = "Hugo_Symbol";
    public static final String ENTREZ_GENE_ID = "Entrez_Gene_Id";
    public static final String SAMPLE_ID = "Sample_Id";
    public static final String VALUE = "Value";
    public static final String CBP_DRIVER = "cbp_driver";
    public static final String CBP_DRIVER_ANNOTATION = "cbp_driver_annotation";
    public static final String CBP_DRIVER_TIERS = "cbp_driver_tiers";
    public static final String CBP_DRIVER_TIERS_ANNOTATION = "cbp_driver_tiers_annotation";

    public CnaDiscreteLongUtil(){}

    public CnaDiscreteLongUtil(String headerRow) {
        this.columnIndexMap = new HashMap<String, Integer>();
        String[] headerParts = headerRow.trim().split("\t");
        
        // Find header indices
        for (int i=0; i<headerParts.length; i++) {
            // Put the index in the map
            this.columnIndexMap.put(headerParts[i].toLowerCase(), i);
        }

        if (getColumnIndex(HUGO_SYMBOL) == -1 && getColumnIndex(ENTREZ_GENE_ID) == -1) {
            throw new RuntimeException("Error: at least one of the following columns should be present: Hugo_Symbol or Entrez_Gene_Id");
        }
    }

    public CnaEvent createCnaEvent(GeneticProfile geneticProfile, int sampleId, String[] parts) {

//        String sampleId = TabDelimitedFileUtil.getPartString(getColumnIndex(SAMPLE_ID), parts);
        int cnaProfileId = geneticProfile.getGeneticProfileId();
        long entrezGeneId = getEntrezSymbol(parts);
        short alteration = getAlteration(parts);
        CnaEvent cna = new CnaEvent(sampleId, cnaProfileId, entrezGeneId, alteration);
        cna.setDriverFilter(TabDelimitedFileUtil.getPartString(getColumnIndex(CnaDiscreteLongUtil.CBP_DRIVER), parts));
        cna.setDriverFilterAnnotation(TabDelimitedFileUtil.getPartString(getColumnIndex(CnaDiscreteLongUtil.CBP_DRIVER_ANNOTATION), parts));
        cna.setDriverTiersFilter(TabDelimitedFileUtil.getPartString(getColumnIndex(CnaDiscreteLongUtil.CBP_DRIVER_TIERS), parts));
        cna.setDriverTiersFilterAnnotation(TabDelimitedFileUtil.getPartString(getColumnIndex(CnaDiscreteLongUtil.CBP_DRIVER_TIERS_ANNOTATION), parts));
        return cna;
    }

    public long getEntrezSymbol(String[] parts) {
        String entrezAsString = TabDelimitedFileUtil.getPartString(getColumnIndex(CnaDiscreteLongUtil.ENTREZ_GENE_ID), parts);
        if (entrezAsString.isEmpty()) {
            return 0;
        } else if (!entrezAsString.matches("[0-9]+")) {
            ProgressMonitor.logWarning("Ignoring line with invalid Entrez_Id " + entrezAsString);
            return 0;
        }
        return TabDelimitedFileUtil.getPartLong(getColumnIndex(CnaDiscreteLongUtil.ENTREZ_GENE_ID), parts);
    }

    public String getHugoSymbol(String[] parts) {
        return TabDelimitedFileUtil.getPartString(getColumnIndex(CnaDiscreteLongUtil.HUGO_SYMBOL), parts);
    }
    
    private short getAlteration(String[] parts) {
        String value = TabDelimitedFileUtil.getPartString(getColumnIndex(CnaDiscreteLongUtil.VALUE), parts);
        String result = value;
        // temporary solution -- change partial deletion back to full deletion.
        if (value.equals(CNA_VALUE_PARTIAL_DELETION)) {
            result = CNA_VALUE_HOMOZYGOUS_DELETION;
        }
        return Integer.valueOf(result).shortValue();
    }

    public int getColumnIndex(String colName) {
        Integer index = this.columnIndexMap.get(colName.toLowerCase());
        if (index == null) {
            index = -1;
        }
        return index;
    }

    public String getSampleIdStr(String[] parts) {
        return TabDelimitedFileUtil.getPartString(getColumnIndex(CnaDiscreteLongUtil.SAMPLE_ID), parts);
    }
}
