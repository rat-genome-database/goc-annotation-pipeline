package edu.mcw.rgd;

import edu.mcw.rgd.dao.impl.*;
import edu.mcw.rgd.dao.spring.IntStringMapQuery;
import edu.mcw.rgd.dao.spring.StringListQuery;
import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
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
    GeneDAO geneDAO = new GeneDAO();
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

    public synchronized Gene getGene( int rgdId ) throws Exception {
        Gene gene = _geneCache.get(rgdId);
        if( gene==null ) {
            gene = geneDAO.getGene(rgdId);
            _geneCache.put(rgdId, gene);
        }
        return gene;
    }
    static Map<Integer, Gene> _geneCache = new HashMap<>();

    public List<Gene> getActiveGenes(int speciesTypeKey ) throws Exception {
        return geneDAO.getActiveGenes(speciesTypeKey);
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

    public boolean isDescendantOf( String termAcc, String ancestorTermAcc ) throws Exception {
        return odao.isDescendantOf(termAcc, ancestorTermAcc);
    }

    public List<String> getObsoleteTermsForGO() throws Exception {
        List<String> result = odao.getObsoleteTerms("BP");
        result.addAll(odao.getObsoleteTerms("MF"));
        result.addAll(odao.getObsoleteTerms("CC"));
        return result;
    }

    public Term getTerm(String termAcc ) throws Exception {
        return odao.getTerm(termAcc);
    }

    public RgdId getId(int rgdId) throws Exception {
        return idDao.getRgdId2(rgdId);
    }

    public List<XdbId> getXdbIds(int rgdId, int xdbKey) throws Exception {
        return xdbIdDAO.getXdbIdsByRgdId(xdbKey, rgdId);
    }

    public List<XdbId> getXdbIds(List xdbKeys, int rgdId) throws Exception {
        return xdbIdDAO.getXdbIdsByRgdId(xdbKeys, rgdId);
    }

    public Set<String> getUniProtAccessionsForCanonicalProteins( int speciesTypeKey ) throws Exception {

        String sql = """
            SELECT uniprot_id FROM proteins p WHERE is_canonical<>0
              AND EXISTS (SELECT 1 FROM rgd_ids i WHERE i.rgd_id=p.rgd_id AND object_status='ACTIVE' AND species_type_key=?)
            """;

        List<String> list = StringListQuery.execute(odao, sql, speciesTypeKey);
        Set<String> result = new HashSet<>( list );
        return result;
    }
}
