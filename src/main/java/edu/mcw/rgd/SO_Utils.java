package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.ontologyx.Term;

import java.util.HashMap;
import java.util.Map;

public class SO_Utils {

    static Map<String, Integer> unexpectedSoAccIds = new HashMap<>();

    static Map<String, String> soAccIdToSoName = new HashMap<>();

    public static String getSoName( int rgdId, DAO dao ) throws Exception {
        Gene gene = dao.getGene(rgdId);
        String soAccId = gene.getSoAccId();
        String soName = getSoName(soAccId, dao);
        return soName;
    }

    public static synchronized String getSoName( String soAcc, DAO dao ) throws Exception {

        // according to GAF 2.2 specification for DB Object Type (column 12):
        // protein_complex; protein; transcript; ncRNA; rRNA; tRNA; snRNA; snoRNA; any subtype of ncRNA in So ontology;
        // if precise product type is unknown, 'gene_product' should be used

        String soName = soAccIdToSoName.get(soAcc);
        if( soName==null ) {
            Term t = dao.getTerm(soAcc);
            if( t.getTerm().equals("protein_coding_gene") ) {
                soName = "protein";
            } else {
                if( t.getTerm().endsWith("_gene") ) {
                    soName = t.getTerm().substring(0, t.getTerm().length()-5);
                }
            }
            if( soName==null ) {
                soName = "gene_product";
            }

            soAccIdToSoName.put( soAcc, soName );
        }

        return soName;
    }

    public static String getCompatibleSoType(Gene g, DAO dao ) throws Exception {

        String soAccId = g.getSoAccId();
        if( soAccId==null ) {
            return "SO:0000704"; //gene
        }

        switch( soAccId ) {
            case "SO:0001217": //protein-coding gene
                return soAccId;

            case "SO:0000336": //pseudogene
            case "SO:0000043": //processed-pseudogene
            case "SO:0001760": //unprocessed-pseudogene
            case "SO:0002107": //transcribed_unprocessed_pseudogene
            case "SO:0002109": //transcribed_processed_pseudogene
                return "SO:0000336"; //pseudogene

            case "SO:0001263": //ncRNA-coding gene or any SO child term
                return soAccId;

            case "SO:0000655": //ncRNA or any SO child term
                return soAccId;

            case "SO:0000704": //gene
            case "SO:0002134": // [TR_C_Gene]
            case "SO:0002126": // [IG_V_gene]
            case "SO:0002137": // [TR_V_Gene]
            case "SO:0002136": // [TR_J_Gene]
                return "SO:0000704"; //gene

            default:
                if( dao.isDescendantOf(soAccId, "SO:0001263") ) {
                    return soAccId;
                }
                if( dao.isDescendantOf(soAccId, "SO:0000655") ) {
                    return soAccId;
                }

                Integer count = unexpectedSoAccIds.get(soAccId);
                if( count == null ) {
                    count = 1;
                } else {
                    count++;
                }
                unexpectedSoAccIds.put(soAccId, count);
                return null;
        }
    }

    public static void dumpUnexpectedSoAccIds( DAO dao ) throws Exception {

        for( Map.Entry<String,Integer> entry: unexpectedSoAccIds.entrySet() ) {
            Term soTerm = dao.getTerm(entry.getKey());
            System.out.println("  unexpected SO term: "+ soTerm.getAccId()+" ["+soTerm.getTerm()+"]   {"+entry.getValue()+"}");
        }

        /*
        System.out.println("SO ACC:\n=====");
        for( Map.Entry<String,String> entry: soAccIdToSoName.entrySet() ) {
            System.out.println("  "+ entry.getKey()+" ["+entry.getValue()+"]");
        }
        */
    }
}
