package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.dao.spring.IntStringMapQuery;
import edu.mcw.rgd.datamodel.Ortholog;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * @author hsnalabolu
 * @since Mar 27, 2019
 * <p>
 * wrapper to handle all DAO code
 */
public class DAO {

    AnnotationDAO adao = new AnnotationDAO();
    OntologyXDAO odao = new OntologyXDAO();
    ReferenceDAO rdao = new ReferenceDAO();

    public DAO() {
        System.out.println(adao.getConnectionInfo());
    }


    public List<Annotation> getAnnotationsBySpecies(int speciesType,String aspect) throws Exception {
        return adao.getAnnotationsBySpecies(speciesType,aspect);
    }

    public boolean isForCuration(String termAcc) throws Exception{
        return odao.isForCuration(termAcc);
    }

    public Map<Integer,String> loadPmidMap() throws Exception {
        List<IntStringMapQuery.MapPair> pmidList = rdao.getPubmedIdsAndRefRgdIds();
        Map<Integer,String> pmidMap = new HashMap<>(pmidList.size());
        for (IntStringMapQuery.MapPair pair : pmidList) {
            String pmid = pmidMap.put(pair.keyValue, pair.stringValue);
            if( pmid != null ) {
                System.out.println("multiple PMIDs for REF_RGD_ID:"+pair.keyValue+", PMID:"+pmid);
            }
        }
        return pmidMap;
    }

    public List<TermSynonym> getTermSynonyms(String termAcc) throws Exception {
        return odao.getTermSynonyms(termAcc);
    }

    public List<String> getAllActiveTermDescandantAccIds(String termAcc) throws Exception {
        return odao.getAllActiveTermDescendantAccIds(termAcc);
    }

    public List<String> getObsoleteTermsForGO() throws Exception {
        List<String> result =  odao.getObsoleteTerms("BP");
        result.addAll(odao.getObsoleteTerms("MF"));
        result.addAll(odao.getObsoleteTerms("CC"));
        return result;
    }
}
