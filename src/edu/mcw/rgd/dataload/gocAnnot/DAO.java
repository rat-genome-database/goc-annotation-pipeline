package edu.mcw.rgd.dataload.gocAnnot;

import edu.mcw.rgd.dao.impl.AnnotationDAO;
import edu.mcw.rgd.dao.impl.XdbIdDAO;
import edu.mcw.rgd.dao.spring.AnnotationQuery;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: Mar 7, 2011
 * Time: 2:48:19 PM
 * To change this template use File | Settings | File Templates.
 */
public class DAO {

    private AnnotationDAO dao = new AnnotationDAO();
    private XdbIdDAO xdbIdDAO = new XdbIdDAO();

    public DAO() {
        System.out.println(dao.getConnectionInfo());
    }

    /**
     * return ND annotations
     * @return map of NDAnnot objects keyed by full annot key of ND annotations
     * @throws Exception on spring framework dao failure
     */
    public Map<Integer, NDAnnot> getNDAnnotations() throws Exception {
        String query = "select distinct fn.ANNOTATED_OBJECT_RGD_ID,fn.full_annot_key,fn.term_acc,fn.term,fn.object_symbol,fn.aspect,fm.evidence from RGD_IDS ri\n" +
                "join FULL_ANNOT fn on fn.ANNOTATED_OBJECT_RGD_ID=ri.RGD_ID\n" +
                "join FULL_ANNOT fm on fm.ANNOTATED_OBJECT_RGD_ID=ri.RGD_ID\n" +
                "where ri.SPECIES_TYPE_KEY=3\n" +
                "and ri.OBJECT_STATUS='ACTIVE'\n" +
                "and fn.VOCAB_KEY=4\n" +
                "and fn.ASPECT=fm.ASPECT\n" +
                "and fn.EVIDENCE='ND'\n" +
                "and fm.EVIDENCE in ('IDA', 'IMP', 'IEP', 'IPI', 'IGI', 'EXP') ";

        JdbcTemplate jt = new JdbcTemplate(dao.getDataSource());
        final Map<Integer, NDAnnot> ndAnnots = new HashMap<Integer, NDAnnot>();
        jt.query(query, new RowMapper() {
            public Object mapRow(ResultSet rs, int i) throws SQLException {

                int fullAnnotKey = rs.getInt(2);
                NDAnnot ndAnnot = ndAnnots.get(fullAnnotKey);
                if( ndAnnot==null ) {
                    // first occurence of non-ND eveidence for given full_annot_key
                    ndAnnot = new NDAnnot();
                    ndAnnot.rgdId = rs.getInt(1);
                    ndAnnot.fullAnnotKey = fullAnnotKey;
                    ndAnnot.termAcc = rs.getString(3);
                    ndAnnot.term = rs.getString(4);
                    ndAnnot.objectSymbol = rs.getString(5);
                    ndAnnot.aspect = rs.getString(6);
                    ndAnnot.evidences = rs.getString(7);
                    ndAnnots.put(fullAnnotKey, ndAnnot);
                }
                else {
                    // another occurence of non-ND evidence for given full annot key
                    ndAnnot.evidences += "."+rs.getString(7);
                }
                return null;
            }
        });
        return ndAnnots;
    }

    /**
     * wrapper to delete annotation given full annot key
     * @param fullAnnotKey full annot key
     * @return nr of rows affected
     * @throws Exception on spring framework dao failure
     */
    public int deleteAnnotation(int fullAnnotKey) throws Exception {
        return dao.deleteAnnotation(fullAnnotKey);
    }

    /**
     * get list of active annotations for given object and species
     * @param objectKey object key -- 1 for genes
     * @param speciesTypeKey species type key - 3 for rat
     * @param ontMask term acc mask; 'GO%' to search all go ontologies
     * @return list of active annotations
     * @throws Exception
     */
    public List<Annotation> getAnnotationsForOntology(int objectKey, int speciesTypeKey, String ontMask) throws Exception {

        String query = "select f.* from FULL_ANNOT f, RGD_IDS r where f.ANNOTATED_OBJECT_RGD_ID = r.RGD_ID"
            + " and r.SPECIES_TYPE_KEY=? and r.OBJECT_STATUS='ACTIVE' and TERM_ACC like ?"
            + " and r.object_key=?";
        
        AnnotationQuery gq = new AnnotationQuery(dao.getDataSource(), query);
        gq.declareParameter(new SqlParameter(Types.INTEGER));
        gq.declareParameter(new SqlParameter(Types.VARCHAR));
        gq.declareParameter(new SqlParameter(Types.INTEGER));
        gq.compile();
        return gq.execute(new Object[]{speciesTypeKey, ontMask, objectKey});
    }

    /**
     * get xdb ids for given rgd id and xdb key
     * @param xdbKey xdb key
     * @param rgdId rgd id
     * @return list of matching xdb ids; could be empty list
     * @throws Exception
     */
    public List<XdbId> getXdbIdsByRgdId(int xdbKey, int rgdId) throws Exception {
        return xdbIdDAO.getXdbIdsByRgdId(xdbKey, rgdId);
    }
    
    /**
     * helper class to return all information when deleting ND annotations
     */
    class NDAnnot {
        public int rgdId; // annotated_object_rgd_id
        public String aspect; // annotation aspect
        public String evidences; // dot separated non-ND evidences: 'IDA', 'IMP', 'IEP', 'IPI', 'IGI', 'EXP'
        public int fullAnnotKey; // full annot key for annotation with ND evidence
        public String termAcc;
        public String term;
        public String objectSymbol;
    }
}
