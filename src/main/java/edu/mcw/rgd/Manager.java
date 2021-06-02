package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Aspect;
import edu.mcw.rgd.process.CounterPool;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map;

/**
 * @author hsnalabolu
 * @since Mar 21, 2019
 * create annotation file in gaf format, based on rgd annotations and go annotations
 */
public class Manager {

    private DAO dao = new DAO();
    private String version;
    private String pipelineName;
    private String outputFileRGD;
    private String outputFileProtein;
    private String goaFile;
    private Set<Integer> refRgdIdsForGoPipelines;
    private int fileSizeChangeThresholdInPercent;
    private Set<String> allowedQualifiersForMF;
    private Set<String> allowedQualifiersForBP;
    private Set<String> allowedQualifiersForCC;

    private List<String> catalyticTerms;
    private List<String> obsoleteTerms;
    static private Map<Integer, String> pmidMap;

    final String HEADER_COMMON_LINES =
        "!gaf-version: 2.2\n" +
        "!generated-by: RGD\n" +
        "!date-generated: ##DATE## \n" +
        "!\n" +
        "!{ The gene_association.rgd file is available at the GO Consortium website (http://geneontology.org/docs/download-go-annotations/) "+
            "and on RGD's FTP site (https://download.rgd.mcw.edu/data_release/). The file and its contents follow the specifications laid out by the Consortium, "+
            "currently GO Annotation File (GAF) Format 2.2 located at http://geneontology.org/docs/go-annotation-file-gaf-format-2.2/. "+
            "This requires that some details available for certain annotations on the RGD website and/or in other annotations files found on the RGD FTP site "+
            "must be excluded from this file in order to conform to the GOC guidelines and to correspond to GAF files from other groups. }\n" +
        "!{ As of march 2021, the gene_association.rgd file is provided in gaf 2.2 format. }\n" +
        "!{ As of December 2016, the gene_association.rgd file only contains 'RGD' in column 1 and RGD gene identifiers in column 2. }\n" +
        "!{ As of March 2018, the gene_association.rgd file no longer includes identifiers for the original references (see below) for ISO annotations in column 6. "+
            "For ISO annotations, entries in column 6 will be limited to RGD:1624291, RGD's internal reference which explains the assignment of GO ISO annotations "+
            "to rat genes. }\n" +
        "!{ The gene_protein_association.rgd file (available on the RGD ftp site at https://download.rgd.mcw.edu/data_release/) contains both RGD gene "+
            "and UniProt protein IDs in columns 1/2. The gene_protein_association.rgd file also includes original reference IDs for rat ISO annotations, "+
            "as well as the ID for RGD's internal reference which explains the assignment of GO ISO annotations to rat genes.  \"Original reference\" refers to "+
            "the identifier(s), such as PMIDs and/or other database IDs for the references used to assign GO annotations to genes or proteins in other species "+
            "which are then inferred to rat genes by orthology. }\n" +
        "!{ Additional annotation files can be found on RGD's ftp site in the https://download.rgd.mcw.edu/data_release/annotated_rgd_objects_by_ontology/ directory "+
            "and its \"with_terms\" subdirectory (ftp://ftp.rgd.mcw.edu/pub/data_release/annotated_rgd_objects_by_ontology/with_terms/). "+
            "The annotated_rgd_objects_by_ontology directory contains GAF-formatted files for all of RGD's ontology annotations, that is, annotations for all of "+
            "the ontologies that RGD uses for all annotated objects from all of the species in RGD.  Files in the \"with_terms\" subdirectory contain the same data "+
            "with the addition of ontology terms for human-readability as well as additional information in the form of curator notes. }\n" +
        "!{ For additional information about the file formats for files in the annotated_rgd_objects_by_ontology/ directory and it's \"with_terms\" subdirectory "+
            "see the README files at https://download.rgd.mcw.edu/data_release/annotated_rgd_objects_by_ontology/README and "+
            "https://download.rgd.mcw.edu/data_release/annotated_rgd_objects_by_ontology/with_terms/WITHTERMS_README. }\n";

    SimpleDateFormat sdt = new SimpleDateFormat("yyyyMMdd");
    SimpleDateFormat headerDate = new SimpleDateFormat("yyyy-MM-dd");
    String date11MonthAgo;

    private int notForCuration = 0;
    private int notGene = 0;
    private int ibaAnnot = 0;
    private int obsolete = 0;
    private int ipiAnnot = 0;
    private int ipiInCatalytic = 0;
    private int iepHep = 0;
    private int ndAnnotations = 0;
    private int icIpiIda = 0;
    private int badQualifier = 0;
    private int noQualifier = 0;
    private int uniProtKbReplacements = 0;
    private int geneProductFormIdCleared = 0;
    private int rgdIdsInWithInfoReplaced = 0;
    private int rgdIdsInWithInfoUnexpectedSpecies = 0;
    private int rgdIdsInWithInfoMultipleSwissProt = 0;
    private int ieaDateAdjusted = 0;
    private int ieaDateAsIs = 0;

    Logger log = Logger.getLogger("core");
    Logger logWarnings = Logger.getLogger("warnings");

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        Manager manager = (Manager) (bf.getBean("manager"));

        try {
            manager.run(SpeciesType.RAT);
        } catch (Exception e) {
            Utils.printStackTrace(e, manager.log);
            throw e;
        }
    }

    public void run(int speciesTypeKey) throws Exception {

        long startTime = System.currentTimeMillis();

        String species = SpeciesType.getCommonName(speciesTypeKey);
        log.info("START: " + getVersion());
        log.info("   " + dao.getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("   started at "+sdt.format(new Date(startTime)));


        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DATE, -11*30); // date roughly 11 months ago
        date11MonthAgo = formatDate(calendar.getTime());


        pmidMap = dao.loadPmidMap();
        handleGO(species, speciesTypeKey);

        dumpWarnings();

        log.info("END:  time elapsed: " + Utils.formatElapsedTime(startTime, System.currentTimeMillis()));
        log.info("===");
    }

    void handleGO(String species, int speciesTypeKey) throws Exception {

        log.info("Getting RGD annotations for species "+ species);

        long startTime = System.currentTimeMillis();

        String headerLines = HEADER_COMMON_LINES
                .replace("#SPECIES#", species)
                .replace("##DATE##", headerDate.format(new Date(startTime)));

        Collection<Annotation> annotations = loadAnnotations(speciesTypeKey);

        Set<GoAnnotation> filteredList = new TreeSet<>();

        for( Annotation annotation: annotations ) {
            GoAnnotation result = handleAnnotation(annotation);
            if(result != null) {
                result.setTaxon("taxon:" + SpeciesType.getTaxonomicId(speciesTypeKey));
                filteredList.add(result);
            }
        }

        writeGeneAssociationsFile(filteredList, headerLines);

        validateOutputFileSize();

        log.info("=====");
        log.info("Total Number of GO Annotations in RGD: " + annotations.size());
        log.info("Total Number of Annotations Sent to GO from RGD: " + filteredList.size());
        log.info("Annotations to Obsolete terms: " + obsolete );
        log.info("NotForCuration Annotations: " + notForCuration );
        log.info("Not gene Annotations: " + notGene );
        log.info("IEP and HEP Annotations to MF and CC Ontology: " + iepHep );
        log.info("No Data (ND) evidence code Annotations: " + ndAnnotations );
        log.info("IPI Annotations to Catalytic Terms: " + ipiInCatalytic );
        log.info("IC,IPI,IDA Annotations violating WITH field rule: " + icIpiIda  );
        log.info("IBA annotations from other sources: "+ ibaAnnot );
        log.info("IPI annotations to root terms with null WITH field: " + ipiAnnot  );
        if( badQualifier!=0 ) {
            log.info("annotations with invalid qualifiers (other than NOT, contributes_to, colocalizes_with, ...): " + badQualifier);
        }
        if( noQualifier!=0 ) {
            log.info("annotations with no qualifiers has been supplied with default gaf 2.2 qualifiers: " + noQualifier);
        }
        if( uniProtKbReplacements!=0 ) {
            log.info("annotations with source field 'UniProtKB' replaced with 'UniProt': " + uniProtKbReplacements);
        }
        if( geneProductFormIdCleared!=0 ) {
            log.info("ISO annotations with cleared GENE_PRODUCT_FORM_ID field: " + geneProductFormIdCleared);
        }
        if( rgdIdsInWithInfoReplaced!=0 ) {
            log.info("ISO annotations with RGD IDs in WITH field replaced with MGI/SwissProt ids: " + rgdIdsInWithInfoReplaced);
        }
        if( rgdIdsInWithInfoUnexpectedSpecies!=0 ) {
            log.info("ISO annotations with RGD IDs in WITH field has species other than mouse/human: " + rgdIdsInWithInfoUnexpectedSpecies);
        }
        if( rgdIdsInWithInfoMultipleSwissProt!=0 ) {
            log.info("ISO annotations with RGD IDs in WITH field has multiple SwissPro mappings: " + rgdIdsInWithInfoMultipleSwissProt);
        }
        if( ieaDateAdjusted!=0 ) {
            log.info("IEA annotations with CREATED_DATE adjusted: " + ieaDateAdjusted);
        }
        if( ieaDateAsIs!=0 ) {
            log.info("IEA annotations with CREATED_DATE left as-is: " + ieaDateAsIs);
        }

        if( new File(getGoaFile()).exists() ) {
            BufferedReader br = Utils.openReader(getGoaFile());
            String line;
            while (null != (line = br.readLine())) {

                GoAnnotation goAnnotation = new GoAnnotation();
                String[] tokens = line.split("[\\t]", -1);
                if (tokens.length != 17) {
                    continue;
                }
                goAnnotation.setObjectId(tokens[1]);
                goAnnotation.setObjectSymbol(tokens[2]);
                goAnnotation.setQualifier(tokens[3]);
                goAnnotation.setTermAcc(tokens[4]);
                goAnnotation.setReferences(tokens[5]);
                goAnnotation.setEvidence(tokens[6]);
                goAnnotation.setWithInfo(tokens[7]);
                goAnnotation.setAspect(tokens[8]);
                goAnnotation.setObjectName(tokens[9]);
                goAnnotation.setMeshOrOmimId(tokens[10]);
                goAnnotation.setObjectType(tokens[11]);
                goAnnotation.setTaxon(tokens[12]);
                goAnnotation.setCreatedDate(tokens[13]);
                goAnnotation.setDataSrc(tokens[14]);
                goAnnotation.setAnnotExtension(tokens[15]);
                goAnnotation.setGeneProductId(tokens[16]);

                filteredList.add(goAnnotation);

                if( Utils.isStringEmpty(goAnnotation.getCreatedDate()) ) {
                    throw new Exception("EMPTY CREATED DATE: "+line);
                }
            }
            br.close();
        } else {
            log.warn("   WARNING: failed to find file "+getGoaFile());
        }

        BufferedWriter bw = Utils.openWriter(getOutputFileProtein());
        bw.write(headerLines);
        for( GoAnnotation g:filteredList ){
            writeLine(bw,g);
        }
        bw.close();
    }

    // load annotations, ordered by (RGD_ID,TERM_ACC)
    Collection<Annotation> loadAnnotations(int speciesTypeKey) throws Exception {

        List<Annotation> annotations = new ArrayList<>();

        annotations.addAll(dao.getAnnotationsBySpecies(speciesTypeKey, Aspect.BIOLOGICAL_PROCESS));
        annotations.addAll(dao.getAnnotationsBySpecies(speciesTypeKey, Aspect.MOLECULAR_FUNCTION));
        annotations.addAll(dao.getAnnotationsBySpecies(speciesTypeKey, Aspect.CELLULAR_COMPONENT));

        catalyticTerms = dao.getAllActiveTermDescandantAccIds("GO:0003824");
        obsoleteTerms = dao.getObsoleteTermsForGO();

        return deconsolidateAnnotations(annotations);
    }

    /**
     * in RGD, we store pipeline annotations in consolidated form,
     * f.e. XREF_SOURCE: MGI:MGI:1100157|MGI:MGI:3714678|PMID:17476307|PMID:9398843
     *      NOTES:       MGI:MGI:2156556|MGI:MGI:2176173|MGI:MGI:2177226  (MGI:MGI:1100157|PMID:9398843), (MGI:MGI:3714678|PMID:17476307)
     * but GO spec says, REFERENCES column 6 can contain at most one PMID
     * so we must deconsolidate RGD annotations
     * what means we must split them into multiple, f.e.
     *    XREF_SOURCE1:  MGI:MGI:1100157|PMID:9398843
     *    XREF_SOURCE2:  MGI:MGI:3714678|PMID:17476307
     */
    Collection<Annotation> deconsolidateAnnotations(Collection<Annotation> annotations) throws Exception {

        int deconsolidatedAnnotsIncoming = 0;
        int deconsolidatedAnnotsCreated = 0;

        List<Annotation> result = new ArrayList<>(annotations.size());

        for( Annotation a: annotations ) {

            String xrefSrc = Utils.defaultString(a.getXrefSource());
            int posPmid1 = xrefSrc.indexOf("PMID:");
            int posPmid2 = xrefSrc.lastIndexOf("PMID:");
            if( !(posPmid1>=0 && posPmid2>posPmid1) ) {
                // only one PMID, annotation is already GO spec compliant
                result.add(a);
                continue;
            }
            deconsolidatedAnnotsIncoming++;

            int parPos = a.getNotes().indexOf("(");
            if( parPos<0 ) {
                log.warn("WARNING! CANNOT DECONSOLIDATE ANNOTATION! SKIPPING IT: notes info missing");
                continue;
            }
            String notesOrig = a.getNotes().substring(0, parPos).trim();

            // multi PMID annotation: deconsolidate it
            String[] xrefs = xrefSrc.split("[\\|\\,]");
            for( ;; ){
                // extract PMID from xrefSrc
                String pmid = null;
                for( int i=0; i<xrefs.length; i++ ) {
                    if( xrefs[i].startsWith("PMID:") ) {
                        pmid = xrefs[i];
                        xrefs[i] = "";
                        break;
                    }
                }
                if( pmid==null ) {
                    break;
                }

                // find corresponding PMID info in NOTES field
                int pmidPos = a.getNotes().indexOf(pmid);
                if( pmidPos<0 ) {
                    log.warn("WARNING! CANNOT DECONSOLIDATE ANNOTATION! SKIPPING IT: notes info missing PMID");
                    continue;
                }
                int parStartPos = a.getNotes().lastIndexOf("(", pmidPos);
                int parEndPos = a.getNotes().indexOf(")", pmidPos);
                if( parStartPos<0 || parEndPos<parStartPos ) {
                    log.warn("WARNING! CANNOT DECONSOLIDATE ANNOTATION! SKIPPING IT: notes info malformed PMID");
                    continue;
                }
                String xrefInfo = a.getNotes().substring(parStartPos+1, parEndPos);

                Annotation ann = (Annotation)a.clone();
                ann.setXrefSource(xrefInfo);
                ann.setNotes(notesOrig);
                result.add(ann);
                deconsolidatedAnnotsCreated++;
            }
        }

        log.info(deconsolidatedAnnotsIncoming+" incoming annotations deconsolidated into "+deconsolidatedAnnotsCreated+" annotations");

        return result;
    }

    GoAnnotation handleAnnotation(Annotation a) throws Exception {

        GoAnnotation goAnnotation = new GoAnnotation();

        // Check for Gene Object Type and remove the annotation without it
        if( a.getRgdObjectKey() == RgdId.OBJECT_KEY_GENES ) {
            goAnnotation.setObjectType("gene");
        } else {
            log.info(a.getKey() + " to term" + a.getTermAcc() + " is not annotated to a gene.");
            notGene++;
            return null;
        }



        //GORules:
        if( !qcQualifier(a) ) {
            return null;
        }

        //GO consortium rule GO:0000026
        if(a.getEvidence().equals("IBA") && !a.getDataSrc().equals("PAINT")) {
            log.debug(a.getTermAcc() +" is an IBA annotation: Annotation skipped");
            ibaAnnot++;
            return null;
        }

        //GO consortium rule GO:0000020
        //Check for obsolete terms and filter them
        if(obsoleteTerms.contains(a.getTermAcc())){
            log.debug(a.getTermAcc() +" is an Obsolete Term: Annotation skipped");
            obsolete++;
            return null;
        }

        //GO consortium rule GO:0000010
        //Convert DB reference to RGD|PMID reference if ref_rgd_id is not null
        // ref_rgd_id is null -- use non-null XREF_SOURCE as dbReference
        int refRgdId = a.getRefRgdId()!=null && a.getRefRgdId()>0 ? a.getRefRgdId() : 0;
        if( refRgdId>0 ) {
            goAnnotation.setReferences("RGD:" + refRgdId);
            String pmid = pmidMap.get(refRgdId);
            if( pmid!=null ){
                goAnnotation.setReferences(goAnnotation.getReferences() + "|PMID:" + pmid);
            }
        } else {
            if(a.getXrefSource() != null )
                goAnnotation.setReferences(a.getXrefSource());
        }

        // check for Pub Med id in this field, if it exists tack it on to the dbReference field
        // Remove WithInfo for evidence Codes IDA,NAS,ND and TAS
        goAnnotation.setWithInfo(a.getWithInfo());
        if ( goAnnotation.getWithInfo() == null) {
            goAnnotation.setWithInfo("");
        } else {
            if ( goAnnotation.getWithInfo().contains("PMID:") ) {
                if( goAnnotation.getReferences().length()>0 )
                    goAnnotation.setReferences(goAnnotation.getReferences() +  '|');
                goAnnotation.setReferences(goAnnotation.getReferences()+goAnnotation.withInfo);
            }

            if ( a.getEvidence().equals("IDA") || a.getEvidence().equals("NAS") || a.getEvidence().equals("ND") || a.getEvidence().equals("TAS") ) {
                goAnnotation.setWithInfo("");
            }
            else {
                goAnnotation.setWithInfo(goAnnotation.getWithInfo().trim());
            }
        }

        rgdIdsInWithInfoReplaced += normalizeWithInfoForISO(goAnnotation, a);

        // GO consortium rule GO:0000002 and GO consortium rule GO:0000003 and GO:000005
        // "protein binding" annotation -- GO:0005515 -- must have evidence 'IPI' and non-null WITH field
        //"binding" annotation -- GO:0005488 -- must have evidence 'IPI' and non-null WITH field
        // "protein binding" annotation -- GO:0005515 - no ISS or ISS-related annotations (if block allows only IPI annotations)
        if( (a.getTermAcc().equals("GO:0005515") || a.getTermAcc().equals("GO:0005488"))) {
            if(a.getEvidence().equals("IPI")) {
                if (goAnnotation.getWithInfo().length() == 0) {
                    ipiAnnot++;
                    return null;
                }
            } else { ipiAnnot++;
                return null;}
            log.debug(a.getTermAcc() +" is an "+a.getEvidence()+ " annotation. Only IPI annotation with a non-null WITH field are allowed.");

        }

        // GO consortium rule GO:0000006
        //IEP and HEP annotations are restricted to terms from Biological Process ontology
        if((a.getEvidence().equals("IEP") || a.getEvidence().equals("HEP")) && !a.getAspect().equals("P")) {
            log.warn("*** "+a.getEvidence()+ " annotation skipped -- IEP/HEP annotations are restricted to Biological Process ontology" );
            log.warn("   "+a.dump("|"));
            iepHep++;
            return null;
        }

        // GO consortium rule GO:0000007
        //IPI annotations should not be used with catalytic molecular function terms
        if(a.getEvidence().equals("IPI")  && catalyticTerms.contains(a.getTermAcc())) {
            log.info(a.getTermAcc() +" is an "+a.getEvidence()+ " annotation. They should not be used with catalytic molecular terms." );
            ipiInCatalytic++;
            return null;
        }

        //GO consortium rule GO:0000008
        //Check for Not4Curation Go terms
        if( !dao.isForCuration(a.getTermAcc()) ) {
            notForCuration++;
            logWarning(" term "+a.getTerm()+" is Not4Curation! annotation skipped" );
            return null;
        }




        // GO consortium rule GO:0000011:
        // The No Data (ND) evidence code should be used for annotations to the root nodes only

        // CASE 1: evidence.code = 'ND' AND term.acc NOT IN ( 'GO:0005575', 'GO:0003674', 'GO:0008150' )
        if( a.getEvidence().equals("ND") && a.getTerm().startsWith("GO:")
                && !(a.getTerm().equals("GO:0005575") || a.getTerm().equals("GO:0003674") || a.getTerm().equals("GO:0008150")) ) {
            log.info(a.getTermAcc() +" is an ND annotation. Fails rule GO:0000011");
            ndAnnotations++;
            return null;
        }

        // CASE 2: evidence.code != 'ND' AND term.acc IN ( 'GO:0005575', 'GO:0003674', 'GO:0008150' )
        if( !a.getEvidence().equals("ND")
                && (a.getTerm().equals("GO:0005575") || a.getTerm().equals("GO:0003674") || a.getTerm().equals("GO:0008150")) ) {
            log.info(a.getTermAcc() +" is not an ND annotation.Fails rule GO:0000011");
            ndAnnotations++;
            return null;
        }

        // CASE 3: evidence.code = 'ND' AND term.acc IN ( 'GO:0005575', 'GO:0003674', 'GO:0008150' )
        // and xref_db NOT IN( 'GO_REF:0000015', 'RGD:1598407')
        if( a.getEvidence().equals("ND")
                && (a.getTerm().equals("GO:0005575") || a.getTerm().equals("GO:0003674") || a.getTerm().equals("GO:0008150")) ) {

            if( !goAnnotation.getReferences().equals("GO_REF:0000015") && !goAnnotation.getReferences().equals("RGD:1598407") ) {
                goAnnotation.setReferences("GO_REF:0000015");
            }
        }

        // GO consortium rule GO:0000016 and GO:0000017 and GO:0000018
        // IC and IPI annotations require a WITH field and IDA must not have a WITH field
        if( ((a.getEvidence().equals("IC") || (a.getEvidence().equals("IPI"))) && goAnnotation.withInfo.length()==0 ) ||
                (a.getEvidence().equals("IDA") && goAnnotation.getWithInfo().length()!=0 ) ) {
            log.info("Annotation to term "+a.getTermAcc() + " failed rule: IC and IPI annotations require a WITH field and IDA must not have a WITH field ");
            icIpiIda++;
            return null;
        }



        // GO consortium rule GO:0000029:
        // IEA annotations over an year old should be removed.
        Date createdDate = a.getOriginalCreatedDate();
        if( createdDate==null ) {
            createdDate = a.getCreatedDate();
        }
        String sCreatedDate = formatDate(createdDate);
        goAnnotation.setCreatedDate(sCreatedDate);

        if( a.getTermAcc().startsWith("GO:") && a.getEvidence().equals("IEA") ) {
            if( Utils.stringsCompareTo(sCreatedDate, date11MonthAgo)<0 ) {
                if( getRefRgdIdsForGoPipelines().contains(a.getRefRgdId()) ) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(new Date());
                    calendar.add(Calendar.DATE, -90); // created date should be 3 months back from the current date
                    goAnnotation.setCreatedDate(formatDate(calendar.getTime()));
                } else {
                    goAnnotation.setCreatedDate(formatDate(a.getLastModifiedDate()));
                }
                ieaDateAdjusted++;
            } else {
                ieaDateAsIs++;
            }
        }
        if( Utils.isStringEmpty(goAnnotation.getCreatedDate()) ) {
            throw new Exception("EMPTY CREATED DATE");
        }

        //DB Abbreviation Check for WormBase and UniProt
        if(a.getDataSrc().equalsIgnoreCase("WormBase"))
            goAnnotation.setDataSrc("WB");
        else
        if(a.getDataSrc().equalsIgnoreCase("UniProtKB")) {
            goAnnotation.setDataSrc("UniProt");
            uniProtKbReplacements++;
        } else
            goAnnotation.setDataSrc(a.getDataSrc());

        goAnnotation.setObjectId(a.getAnnotatedObjectRgdId().toString());
        goAnnotation.setObjectSymbol(a.getObjectSymbol());
        goAnnotation.setAspect(a.getAspect());
        goAnnotation.setObjectName(a.getObjectName());
        goAnnotation.setMeshOrOmimId("");
        goAnnotation.setTermAcc(a.getTermAcc());
        goAnnotation.setEvidence(a.getEvidence());
        goAnnotation.setQualifier(a.getQualifier());
        goAnnotation.setAnnotExtension(a.getAnnotationExtension());

        // clear GENE_PRODUCT_FORM_ID for ISO annotations
        // reason: ISO annotations are usually made via orthology, and original GENE_PRODUCT_FORM_ID
        //          cannot be transferred to ISO annotation because it comes from other species
        String geneProductFormId = a.getGeneProductFormId();
        if( a.getEvidence().equals("ISO") && !Utils.isStringEmpty(geneProductFormId) ) {
            geneProductFormId = "";
            geneProductFormIdCleared++;
        }
        goAnnotation.setGeneProductId(geneProductFormId);

        String references = mergeWithXrefSource(goAnnotation.getReferences(), a.getXrefSource(), goAnnotation.getDataSrc(), goAnnotation.getEvidence());
        goAnnotation.setReferences(references);

        return goAnnotation;
    }

    private boolean qcQualifier(Annotation a) {
        //GO consortium rule GO:0000001
        if( Utils.isStringEmpty(a.getQualifier()) ) {
            // no qualifier in the annotation: supply a default qualifier
            if( a.getAspect().equals("F") ) {
                a.setQualifier("enables");
            } else if( a.getAspect().equals("P") ) {
                a.setQualifier("involved_in");
            } else {
                a.setQualifier("located_in");
            }
            noQualifier++;
        }
        else if( a.getQualifier().equals("NOT") ) {
            // no qualifier in the annotation: supply a default qualifier
            if( a.getAspect().equals("F") ) {
                a.setQualifier("NOT|enables");
            } else if( a.getAspect().equals("P") ) {
                a.setQualifier("NOT|involved_in");
            } else {
                a.setQualifier("NOT|located_in");
            }
            noQualifier++;
        }
        else {
            Set<String> allowedQualifiers = null;
            if( a.getAspect().equals("F") ) {
                allowedQualifiers = getAllowedQualifiersForMF();
            } else
            if( a.getAspect().equals("P") ) {
                allowedQualifiers = getAllowedQualifiersForBP();
            } else
            if( a.getAspect().equals("C") ) {
                allowedQualifiers = getAllowedQualifiersForCC();
            }
            if( !allowedQualifiers.contains(a.getQualifier()) ) {
                log.warn("*** RGD:"+a.getAnnotatedObjectRgdId()+" "+a.getTermAcc()+": qualifier "+ a.getQualifier() + " violates gorule 0000001: Annotation skipped");
                badQualifier++;
                return false;
            }
        }
        return true;
    }

    private String mergeWithXrefSource(String references, String xrefSource, String dataSrc, String evidence) {

        Set<String> refs = new TreeSet<>();

        String[] objs = references.split("[\\|\\,\\;]");
        Collections.addAll(refs, objs);

        if( !Utils.isStringEmpty(xrefSource) ) {
            objs = xrefSource.split("[\\|\\,\\;]");
            Collections.addAll(refs, objs);
        }

        //May 2020: per request from GO consortium regarding ISS/ISO annotations from RGD
        // sample REFERENCES field: MGI:MGI:3603471|PMID:14644759|RGD:1624291
        //
        // *Only* the RGD reference should be outputted in this case, since the other 2 IDs correspond to the original data,
        // they do not provide evidence for the ISO annotation
        if( dataSrc.equals("RGD") && (evidence.equals("ISS") || evidence.equals("ISO")) ) {

            // remove all non RGD: entries
            refs.removeIf(ref ->
                !(ref.startsWith("RGD:") || ref.startsWith("GO_REF:"))
            );
        }

        return Utils.concatenate(refs, "|");
    }

    /// Apr 2021: if ISO annotation contains RGD id in WITH field, replace it with MGI id for mouse, and UniProt Swiss id for human
    // return nr of ids replaced
    int normalizeWithInfoForISO(GoAnnotation rec, Annotation a) throws Exception {
        if( !(a.getEvidence().equals("ISO") && rec.getWithInfo().contains("RGD:")) ) {
            return 0;
        }

        int replacedIds = 0;

        // process all RGD IDs
        String[] objIds = rec.getWithInfo().split("[\\|\\,]");
        for( String objId: objIds ) {
            if( !objId.startsWith("RGD:") ) {
                continue;
            }

            // determine species: human or mouse
            int rgdId = Integer.parseInt(objId.substring(4));
            RgdId id = dao.getId(rgdId);
            if( id.getSpeciesTypeKey()==SpeciesType.MOUSE ) {
                List<XdbId> xdbIds = dao.getXdbIds(rgdId, XdbId.XDB_KEY_MGD);
                if( xdbIds.isEmpty() ) {
                    logWarning("  WARNING: cannot find MGI ID for RGD:"+rgdId);
                    continue;
                }
                if( xdbIds.size()>1 ) {
                    logWarning("  WARNING: multiple MGI IDs for RGD:"+rgdId);
                } else {
                    String mgiId = xdbIds.get(0).getAccId();
                    rec.setWithInfo( rec.getWithInfo().replace(objId, mgiId));
                    replacedIds++;
                }
            }
            else if( id.getSpeciesTypeKey()==SpeciesType.HUMAN ) {
                XdbId uniprotId = null;
                List<XdbId> xdbIds = dao.getXdbIds(rgdId, XdbId.XDB_KEY_UNIPROT);
                if( xdbIds.isEmpty() ) {
                    logWarning("  WARNING: cannot find UNIPROT IDs for RGD:"+rgdId);
                } else if( xdbIds.size()>1 ) {
                    XdbId swissProtId = null;
                    for( XdbId xdbId: xdbIds ) {
                        if( Utils.defaultString(xdbId.getSrcPipeline()).contains("Swiss") ) {
                            if( swissProtId == null ) {
                                swissProtId = xdbId;
                            } else {
                                logWarning("  WARNING: multiple SWISS PROT IDs for RGD:"+rgdId);
                                rgdIdsInWithInfoMultipleSwissProt++;
                            }
                        }
                    }
                    uniprotId = swissProtId;
                } else {
                    uniprotId = xdbIds.get(0);
                }

                if( uniprotId!=null ) {
                    String accId = uniprotId.getAccId();
                    rec.setWithInfo( rec.getWithInfo().replace(objId, accId));
                    replacedIds++;
                }
            }
            else {
                logWarning("   WARNING: unexpected species type for RGD:"+rgdId);
                rgdIdsInWithInfoUnexpectedSpecies++;
            }
        }

        return replacedIds;
    }

    void writeGeneAssociationsFile(Collection<GoAnnotation> annotations, String headerLines) throws Exception {

        BufferedWriter bw = Utils.openWriter(getOutputFileRGD());
        bw.write(headerLines);

        for( GoAnnotation annotation: annotations ) {
            writeLine(bw, annotation);
        }
        bw.close();
    }

    synchronized String formatDate(Date dt) {
        return dt != null ? sdt.format(dt) : "";
    }

    void writeLine(BufferedWriter writer, GoAnnotation rec) throws Exception{

        if( Utils.isStringEmpty(rec.getCreatedDate()) ) {
            throw new Exception("EMPTY CREATED DATE");
        }

        // column contents must comply with GAF 2.0 format
        writer.append("RGD")
                .append('\t')
                .append(rec.getObjectId())
                .append('\t')
                .append(checkNull(rec.getObjectSymbol()))
                .append('\t')
                .append(checkNull(rec.getQualifier()))
                .append('\t')
                .append(checkNull(rec.getTermAcc()))
                .append('\t')
                .append(checkNull(rec.getReferences()))
                .append('\t')
                .append(checkNull(rec.getEvidence()))
                .append('\t')
                .append(checkNull(rec.getWithInfo()))
                .append('\t')
                .append(checkNull(rec.getAspect()))
                .append('\t')
                .append(checkNull(rec.getObjectName()))
                .append('\t')
                .append(checkNull(rec.getMeshOrOmimId()))
                .append('\t')
                .append(checkNull(rec.getObjectType()))
                .append('\t')
                .append(checkNull(rec.getTaxon()))
                .append('\t')
                .append(checkNull(rec.getCreatedDate()))
                .append('\t')
                .append(checkNull(rec.getDataSrc()))
                .append('\t')
                .append(checkNull(rec.getAnnotExtension()))
                .append('\t')
                .append(checkNull(rec.getGeneProductId()))
                .append('\n');
    }

    /** the generated file size cannot differ in size more than 5% compared to the last 3 archived files
     *
     */
    void validateOutputFileSize() throws Exception {
        File file = new File(getOutputFileRGD());
        long fileSize = file.length();
        String fileNamePrefix = file.getName()+".";

        // load archived files
        File dir = new File(file.getParent());
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(fileNamePrefix);
            }
        });
        // order archived files by last-modified-time, the most recent ones at the top
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                long res = o2.lastModified() - o1.lastModified();
                if( res<0 ) {
                    return -1;
                } else if( res>0 ) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });

        // compute average file size from 5 most recent backup files
        long fileSizeSum = 0;
        int i;
        for( i=0; i<5 && i<files.length; i++ ) {
            fileSizeSum += files[i].length();
        }
        if( fileSizeSum>0 ) {
            long avgBackupFileSize = fileSizeSum / i;

            // determine allowable file size boundaries
            long minAllowableFileSize = (100 - getFileSizeChangeThresholdInPercent()) * avgBackupFileSize / 100;
            long maxAllowableFileSize = (100 + getFileSizeChangeThresholdInPercent()) * avgBackupFileSize / 100;

            if( fileSize < minAllowableFileSize || fileSize > maxAllowableFileSize ) {
                throw new Exception("File size for "+file.getAbsolutePath()+" of "+fileSize+" is outside of allowed range ["+minAllowableFileSize+", "+maxAllowableFileSize+"]");
            }
        }
    }

    public String checkNull(String str) {
        return str==null ? "" : str.trim();
    }


    CounterPool warnings = new CounterPool();
    void logWarning(String msg) {
        warnings.increment(msg);
    }

    void dumpWarnings() {
        logWarnings.debug(warnings.dumpAlphabetically());
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setPipelineName(String pipelineName) {
        this.pipelineName = pipelineName;
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public Set<Integer> getRefRgdIdsForGoPipelines() {
        return refRgdIdsForGoPipelines;
    }

    public  void setRefRgdIdsForGoPipelines(Set<Integer> refRgdIdsForGoPipelines) {
        this.refRgdIdsForGoPipelines = refRgdIdsForGoPipelines;
    }

    public String getGoaFile() {
        return goaFile;
    }

    public void setGoaFile(String goaFile) {
        this.goaFile = goaFile;
    }

    public String getOutputFileRGD() {
        return outputFileRGD;
    }

    public void setOutputFileRGD(String outputFileRGD) {
        this.outputFileRGD = outputFileRGD;
    }

    public String getOutputFileProtein() {
        return outputFileProtein;
    }

    public void setOutputFileProtein(String outputFileProtein) {
        this.outputFileProtein = outputFileProtein;
    }

    public void setFileSizeChangeThresholdInPercent(int fileSizeChangeThresholdInPercent) {
        this.fileSizeChangeThresholdInPercent = fileSizeChangeThresholdInPercent;
    }

    public int getFileSizeChangeThresholdInPercent() {
        return fileSizeChangeThresholdInPercent;
    }

    public Set<String> getAllowedQualifiersForMF() {
        return allowedQualifiersForMF;
    }

    public void setAllowedQualifiersForMF(Set<String> allowedQualifiersForMF) {
        this.allowedQualifiersForMF = allowedQualifiersForMF;
    }

    public Set<String> getAllowedQualifiersForBP() {
        return allowedQualifiersForBP;
    }

    public void setAllowedQualifiersForBP(Set<String> allowedQualifiersForBP) {
        this.allowedQualifiersForBP = allowedQualifiersForBP;
    }

    public Set<String> getAllowedQualifiersForCC() {
        return allowedQualifiersForCC;
    }

    public void setAllowedQualifiersForCC(Set<String> allowedQualifiersForCC) {
        this.allowedQualifiersForCC = allowedQualifiersForCC;
    }
}

