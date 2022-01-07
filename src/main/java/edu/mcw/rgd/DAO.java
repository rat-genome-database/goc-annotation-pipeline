package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.dao.spring.IntStringMapQuery;
import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    RGDManagementDAO idDao = new RGDManagementDAO();
    XdbIdDAO xdbIdDAO = new XdbIdDAO();

    public String getConnectionInfo() {
        return adao.getConnectionInfo();
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
                Logger log = LogManager.getLogger("core");
                log.warn("WARNING! multiple PMIDs for REF_RGD_ID:"+pair.keyValue+", PMID:"+pmid);
            }
        }
        return pmidMap;
    }

    public List<String> getAllActiveTermDescandantAccIds(String termAcc) throws Exception {
        return odao.getAllActiveTermDescendantAccIds(termAcc);
    }

    public List<String> getObsoleteTermsForGO() throws Exception {
        List<String> result = odao.getObsoleteTerms("BP");
        result.addAll(odao.getObsoleteTerms("MF"));
        result.addAll(odao.getObsoleteTerms("CC"));
        return result;
    }

    public RgdId getId(int rgdId) throws Exception {
        return idDao.getRgdId2(rgdId);
    }

    public List<XdbId> getXdbIds(int rgdId, int xdbKey) throws Exception {
        return xdbIdDAO.getXdbIdsByRgdId(xdbKey, rgdId);
    }
}
