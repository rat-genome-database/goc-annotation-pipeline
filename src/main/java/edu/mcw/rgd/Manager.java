package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Aspect;
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

    private List<String> catalyticTerms;
    private List<String> obsoleteTerms;
    static private Map<Integer, String> pmidMap;
    final String HEADER_COMMON_LINES =
        "!gaf-version: 2.1\n" +
        "!{ The gene_association.rgd file is available at the GO Consortium website (http://www.geneontology.org/page/download-go-annotations) and on RGD's FTP site (ftp://ftp.rgd.mcw.edu/pub/data_release/). The file and its contents follow the specifications laid out by the Consortium, currently GO Annotation File (GAF) Format 2.1 located at http://www.geneontology.org/page/go-annotation-file-gaf-format-21.  This requires that some details available for certain annotations on the RGD website and/or in other annotations files found on the RGD FTP site must be excluded from this file in order to conform to the GOC guidelines and to correspond to GAF files from other groups. }\n" +
        "!{ As of December 2016, the gene_association.rgd file only contains 'RGD' in column 1 and RGD gene identifiers in column 2. }\n" +
        "!{ As of March 2018, the gene_association.rgd file no longer includes identifiers for the original references (see below) for ISO annotations in column 6. For ISO annotations, entries in column 6 will be limited to RGD:1624291, RGD's internal reference which explains the assignment of GO ISO annotations to rat genes.  }\n" +
        "!{ The gene_protein_association.rgd file (available on the RGD ftp site at ftp://ftp.rgd.mcw.edu/pub/data_release/) contains both RGD gene and UniProt protein IDs in columns 1/2. The gene_protein_association.rgd file also includes original reference IDs for rat ISO annotations, as well as the ID for RGD's internal reference which explains the assignment of GO ISO annotations to rat genes.  \"Original reference\" refers to the identifier(s), such as PMIDs and/or other database IDs for the references used to assign GO annotations to genes or proteins in other species which are then inferred to rat genes by orthology. }\n" +
        "!{ Additional annotation files can be found on RGD's ftp site in the ftp://ftp.rgd.mcw.edu/pub/data_release/annotated_rgd_objects_by_ontology/ directory and its \"with_terms\" subdirectory (ftp://ftp.rgd.mcw.edu/pub/data_release/annotated_rgd_objects_by_ontology/with_terms/). The annotated_rgd_objects_by_ontology directory contains GAF-formatted files for all of RGD's ontology annotations, that is, annotations for all of the ontologies that RGD uses for all annotated objects from all of the species in RGD.  Files in the \"with_terms\" subdirectory contain the same data with the addition of ontology terms for human-readability as well as additional information in the form of curator notes. }\n" +
        "!{ For additional information about the file formats for files in the annotated_rgd_objects_by_ontology/ directory and it's \"with_terms\" subdirectory see the README files at ftp://ftp.rgd.mcw.edu/pub/data_release/annotated_rgd_objects_by_ontology/README and ftp://ftp.rgd.mcw.edu/pub/data_release/annotated_rgd_objects_by_ontology/with_terms/WITHTERMS_README. }\n" +
        "!{ This file has been generated on ##DATE## }\n";
    SimpleDateFormat sdt = new SimpleDateFormat("yyyyMMdd");
    SimpleDateFormat headerDate = new SimpleDateFormat("yyyy/MM/dd");

    private int notForCuration = 0;
    private int notGene = 0;
    private int ibaAnnot = 0;
    private int obsolete = 0;
    private int ipiAnnot = 0;
    private int ipiInCatalytic = 0;
    private int iepHep = 0;
    private int ndAnnotations = 0;
    private int icIpiIda = 0;

    Logger log = Logger.getLogger("core");

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

        pmidMap = dao.loadPmidMap();
        handleGO(species, speciesTypeKey);

        log.info("END:  time elapsed: " + Utils.formatElapsedTime(startTime, System.currentTimeMillis()));
        log.info("===");
    }

    void handleGO(String species, int speciesTypeKey) throws Exception {

        log.info("Getting RGD annotations for species "+ species);

        long startTime = System.currentTimeMillis();

        String headerLines = HEADER_COMMON_LINES
                .replace("#SPECIES#", species)
                .replace("##DATE##", headerDate.format(new Date(startTime)));

        BufferedWriter bw = new BufferedWriter(new FileWriter(getOutputFileRGD()));
        bw.write(headerLines);

        Collection<Annotation> annotations = loadAnnotations(speciesTypeKey);

        Set<GoAnnotation> filteredList = new TreeSet<>(new Comparator() {
            @Override
            public int compare(Object o1, Object o2) {
                if(((GoAnnotation)o1).equals((GoAnnotation)o2))
                    return 0;
                else
                    return 1;
            }
        });


        for( Annotation annotation: annotations ) {
            GoAnnotation result = handleAnnotation(annotation);
            if(result != null) {
                result.setTaxon("taxon:" + SpeciesType.getTaxonomicId(speciesTypeKey));
                filteredList.add(result);
                writeLine(bw,result);
            }
        }
        bw.close();

        validateOutputFileSize();

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


        BufferedReader br = Utils.openReader(getGoaFile());
        String line;
        while( null != (line = br.readLine())){

            GoAnnotation goAnnotation = new GoAnnotation();
            String tokens[] = line.split("[\\t]", -1);
            if( tokens.length!=17 ) {
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
        }
        br.close();

        bw = new BufferedWriter(new FileWriter(getOutputFileProtein()));
        bw.write(headerLines);
        for( GoAnnotation g:filteredList ){
            writeLine(bw,g);
        }
        bw.close();

        log.info("END:  time elapsed: " + Utils.formatElapsedTime(startTime, System.currentTimeMillis()));
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

        Collections.sort(result, new Comparator<Annotation>() {
            @Override
            public int compare(Annotation o1, Annotation o2) {
                int r = o1.getAnnotatedObjectRgdId() - o2.getAnnotatedObjectRgdId();
                if( r!=0 ) {
                    return r;
                }
                return o1.getTermAcc().compareTo(o2.getTermAcc());
            }
        });

        return result;
    }

    GoAnnotation handleAnnotation(Annotation a) throws Exception {

        log.debug("Verifying annotation as per Go Rules "+ a.getTermAcc());

        GoAnnotation goAnnotation = new GoAnnotation();

        //Check for Gene Object Type and remove the annotation without it
        int objectKey = a.getRgdObjectKey();
        switch( objectKey ) {
            case RgdId.OBJECT_KEY_GENES:
                goAnnotation.setObjectType("gene");
                break;
            default:
                log.info(a.getKey()+ " to term"+ a.getTermAcc() + " is not annotated to a gene.");
                notGene++;
                return null;
        }



        //GORules:
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
            log.info(a.getTermAcc() +" is an "+a.getEvidence()+ " annotation. It is restricted to Biological Process ontology" );
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
            log.info(" term "+a.getTerm()+" is Not4Curation! annotation skipped" );
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
        if( a.getTermAcc().startsWith("GO:") && a.getEvidence().equals("IEA") ) {
            if( getRefRgdIdsForGoPipelines().contains(a.getRefRgdId()) ) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(new Date());
                calendar.add(Calendar.DATE, -90); // created date should be 3 months back from the current date
                goAnnotation.setCreatedDate(formatDate(calendar.getTime()));
            } else {
                goAnnotation.setCreatedDate(formatDate(a.getLastModifiedDate()));
            }
        } else {
            goAnnotation.setCreatedDate(formatDate(a.getCreatedDate()));
        }

        //DB Abbreviation Check
        if(a.getDataSrc().equalsIgnoreCase("WormBase"))
            goAnnotation.setDataSrc("WB");
        else
            goAnnotation.setDataSrc(a.getDataSrc());

        goAnnotation.setObjectId(a.getAnnotatedObjectRgdId().toString());
        String references = mergeWithXrefSource(goAnnotation.getReferences(),a.getXrefSource());
        goAnnotation.setReferences(references);
        goAnnotation.setObjectSymbol(a.getObjectSymbol());
        goAnnotation.setAspect(a.getAspect());
        goAnnotation.setObjectName(a.getObjectName());
        goAnnotation.setMeshOrOmimId("");
        goAnnotation.setTermAcc(a.getTermAcc());
        goAnnotation.setEvidence(a.getEvidence());
        goAnnotation.setQualifier(a.getQualifier());
        return goAnnotation;
    }

    private String mergeWithXrefSource(String references, String xrefSource) {

        if( Utils.isStringEmpty(xrefSource) ) {
            return references;
        }

        Set<String> refs = new TreeSet<>();

        String[] objs = references.split("[\\|\\,\\;]");
        Collections.addAll(refs, objs);

        objs = xrefSource.split("[\\|\\,\\;]");
        Collections.addAll(refs, objs);

        return Utils.concatenate(refs, "|");
    }

    synchronized String formatDate(java.util.Date dt) {
        return dt != null ? sdt.format(dt) : "";
    }

    void writeLine(BufferedWriter writer, GoAnnotation rec) throws Exception{


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
}

