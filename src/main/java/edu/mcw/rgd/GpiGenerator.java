package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.Gene;
import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.text.SimpleDateFormat;
import java.util.*;

public class GpiGenerator {

    private String outputFile;

    Set<String> canonicalProteinAccessions = null;

    public void run( Logger log, DAO dao ) throws Exception {

        long startTime = System.currentTimeMillis();

        int speciesTypeKey = SpeciesType.RAT;
        String taxonId = "NCBITaxon:"+SpeciesType.getTaxonomicId(speciesTypeKey);
        String proteomeId = "UP000002494"; // proteome for rat

        log.info("START generation of GPI file");
        log.info("   " + dao.getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String today = sdt.format(new Date(startTime));
        log.info("   started at "+today);

        BufferedWriter out = Utils.openWriter(getOutputFile());
        out.write( "!gpi-version: 2.0\n" );
        out.write( "!generated-by: RGD\n" );
        out.write( "!date-generated: "+today.substring(0, 10)+"\n" );

        List<Gene> genes = dao.getActiveGenes(SpeciesType.RAT);
        Collections.shuffle(genes);
        log.info("   loaded active genes: "+genes.size());

        canonicalProteinAccessions = dao.getUniProtAccessionsForCanonicalProteins(speciesTypeKey);
        log.info("   loaded canonical protein accessions: "+canonicalProteinAccessions.size());

        List<String> lines = new ArrayList<>();

        for( Gene g: genes ) {

            String soAccId = SO_Utils.getCompatibleSoType(g, dao);
            if( soAccId==null ) {
                continue;
            }

            String objectId = "RGD:"+g.getRgdId();

            GpiInfo info = new GpiInfo();

            // col 1: DB object id
            info.objectId = objectId;

            // col 2: Object symbol
            info.objectSymbol = Utils.NVL(g.getSymbol(), g.getEnsemblGeneSymbol());

            // col 3: Object name
            info.objectName = Utils.NVL(g.getName(), g.getEnsemblFullName());

            // col 4: Object synonym(s)
            // n/a

            // col 5: Object type
            info.objectType = soAccId;

            // col 6: Object taxon
            info.objectTaxon = taxonId;

            // col 7: Encoded by (for proteins and transcripts only)
            // n/a

            // col 8: Canonical object id
            info.canonicalObjectId = objectId;

            // col 9: Protein-Containing Complex Members
            // n/a

            // col 10: cross references: NCBIGene: UniProtKB: ENSEMBL:  RNAcentral:
            processXrefs( g, info, dao, proteomeId );


            String line = info.toString();
            synchronized (lines) {
                lines.add(line);
            }
        }

        // sort by RGD ID from 1st column
        Collections.sort(lines, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {

                // RGD:10045415    Kantr ...
                int tabPos = o1.indexOf('\t');
                int rgdId1 = Integer.parseInt(o1.substring(4, tabPos));

                tabPos = o2.indexOf('\t');
                int rgdId2 = Integer.parseInt(o2.substring(4, tabPos));

                return rgdId1 - rgdId2;
            }
        });

        for( String line: lines ) {
            out.write(line);
        }

        out.close();

        SO_Utils.dumpUnexpectedSoAccIds(dao);

        log.info("data lines written: " + Utils.formatThousands(lines.size()));
        log.info("END:  time elapsed: " + Utils.formatElapsedTime(startTime, System.currentTimeMillis()));
        log.info("===");
    }



    static List<Integer> XDB_KEYS = List.of(
            XdbId.XDB_KEY_NCBI_GENE,
            XdbId.XDB_KEY_ENSEMBL_GENES,
            XdbId.XDB_KEY_UNIPROT,
            68 // RNAcentral
            );
    void processXrefs( Gene g, GpiInfo info, DAO dao, String proteomeId ) throws Exception {

        List<XdbId> xdbIds = dao.getXdbIds(XDB_KEYS, g.getRgdId());

        Set<String> xrefs = new TreeSet<>();

        // CASE 1: look for canonical proteins
        for( XdbId id: xdbIds ) {
            if( id.getXdbKey() == XdbId.XDB_KEY_UNIPROT ) {
                if( canonicalProteinAccessions.contains(id.getAccId()) ) {
                    appendXref(xrefs, id);
                }
            }
        }
        if( !xrefs.isEmpty() ) {
            // we have a canonical protein -- add any other xrefs
            appendNonUniProtXrefs(xrefs, xdbIds);

            info.crossReferences = Utils.concatenate(xrefs, "|");
            info.geneProductProperties = "uniprot-proteome="+proteomeId;
            return;
        }


        // CASE 2: look for swiss-prot proteins
        for( XdbId id: xdbIds ) {
            if( id.getXdbKey() == XdbId.XDB_KEY_UNIPROT ) {
                if( id.getSrcPipeline().contains("Swiss-Prot") ) {
                    appendXref(xrefs, id);
                }
            }
        }
        if( !xrefs.isEmpty() ) {
            // we have a Swiss-Protein protein -- add any other xrefs
            appendNonUniProtXrefs(xrefs, xdbIds);

            info.crossReferences = Utils.concatenate(xrefs, "|");
            info.geneProductProperties = "db-subset=Swiss-Prot";
            return;
        }


        // CASE 3: look for trembl proteins
        for( XdbId id: xdbIds ) {
            if( id.getXdbKey() == XdbId.XDB_KEY_UNIPROT ) {
                if( id.getSrcPipeline().contains("TrEMBL") ) {
                    appendXref(xrefs, id);
                }
            }
        }
        if( !xrefs.isEmpty() ) {
            // we have a TrEMBL protein -- add any other xrefs
            appendNonUniProtXrefs(xrefs, xdbIds);

            info.crossReferences = Utils.concatenate(xrefs, "|");
            info.geneProductProperties = "db-subset=TrEMBL";
            return;
        }


        // CASE 4: no uniprot accessions
        appendNonUniProtXrefs(xrefs, xdbIds);
        info.crossReferences = Utils.concatenate(xrefs, "|");
    }

    void appendNonUniProtXrefs( Set<String> xrefs, List<XdbId> ids ) {
        for( XdbId id: ids ) {
            if( id.getXdbKey() != XdbId.XDB_KEY_UNIPROT ) {
                appendXref(xrefs, id);
            }
        }
    }

    void appendXref( Set<String> xrefs, XdbId xref ) {

        String prefix = null;
        switch (xref.getXdbKey()) {
            case XdbId.XDB_KEY_NCBI_GENE -> prefix = "NCBIGene:";
            case XdbId.XDB_KEY_ENSEMBL_GENES -> prefix = "ENSEMBL:";
            case XdbId.XDB_KEY_UNIPROT -> prefix = "UniProtKB:";
            case 68 -> prefix = "RNAcentral:";
        }
        xrefs.add( prefix+xref.getAccId() );
    }

    public String getOutputFile() {
        return outputFile;
    }

    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    class GpiInfo {
        public String objectId;
        public String objectSymbol;
        public String objectName;
        public String objectSynonyms;
        public String objectType;
        public String objectTaxon;
        public String encodedBy;
        public String canonicalObjectId;
        public String proteinComplexMembers;
        public String crossReferences;
        public String geneProductProperties;

        public String toString() {

            StringBuffer buf = new StringBuffer();

            buf.append( Utils.defaultString(objectId) );
            buf.append( "\t" );

            buf.append( Utils.defaultString(objectSymbol) );
            buf.append( "\t" );

            buf.append( Utils.defaultString(objectName) );
            buf.append( "\t" );

            buf.append( Utils.defaultString(objectSynonyms) );
            buf.append( "\t" );

            buf.append( Utils.defaultString(objectType) );
            buf.append( "\t" );

            buf.append( Utils.defaultString(objectTaxon) );
            buf.append( "\t" );

            buf.append( Utils.defaultString(encodedBy) );
            buf.append( "\t" );

            buf.append( Utils.defaultString(canonicalObjectId) );
            buf.append( "\t" );

            buf.append( Utils.defaultString(proteinComplexMembers) );
            buf.append( "\t" );

            buf.append( Utils.defaultString(crossReferences) );
            buf.append( "\t" );

            buf.append( Utils.defaultString(geneProductProperties) );
            buf.append( "\n" );

            return buf.toString();
        }
    }
}
