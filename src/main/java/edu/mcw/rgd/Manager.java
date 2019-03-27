package edu.mcw.rgd;

import edu.mcw.rgd.datamodel.*;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Aspect;
import edu.mcw.rgd.datamodel.ontologyx.TermSynonym;
import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author hsnalabolu
 * @since Mar 21, 2019
 * create annotation file in gaf format ,
 *    based on rgd annotations and go annotations
 */
public class Manager {

    private DAO dao = new DAO();
    private String version;
    private String pipelineName;
    private String outputFileDir;
    private String goaFile;
    private Set<Integer> refRgdIdsForGoPipelines;
    private List<String> catalyticTerms;
    private List<String> obsoleteTerms;
    static private Map<Integer, String> pmidMap;
    final String HEADER_COMMON_LINES =
                    "!gaf-version: 2.1\n"+
                    "!{ As of December 2016, the gene_association.rgd file only contains 'RGD' in column 1 and RGD gene identifiers in column 2. }\n"+
                    "!{ The gene_protein_association.rgd file (available on the RGD ftp site) contains both RGD gene and UniProt protein IDs. }\n";
    SimpleDateFormat sdt = new SimpleDateFormat("yyyyMMdd");
    SimpleDateFormat headerDate = new SimpleDateFormat("yyyy/MM/dd");

    Logger log = Logger.getLogger("core");
    Logger logSkipped = Logger.getLogger("skipped");

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        Manager manager = (Manager) (bf.getBean("manager"));

        try {
            manager.run();
        } catch (Exception e) {
            manager.log.error(e);
            throw e;
        }
    }

    public void run() throws Exception {
        Date dateStart = new Date();

        run(SpeciesType.RAT);

        log.info("=== DONE ===  elapsed: " + Utils.formatElapsedTime(dateStart.getTime(), System.currentTimeMillis()));
    }

    public void run(int speciesTypeKey) throws Exception {

        long startTime = System.currentTimeMillis();

        String species = SpeciesType.getCommonName(speciesTypeKey);
        log.info("START: species = " + species);
        pmidMap = dao.loadPmidMap();
        handleGO(species, speciesTypeKey);

        log.info("END:  time elapsed: " + Utils.formatElapsedTime(startTime, System.currentTimeMillis()));
        log.info("===");
    }

    void handleGO(String species, int speciesTypeKey) throws Exception {

        log.info("Getting RGD annotations for species "+ species);
        logSkipped.info("Skipped Annotations \n");
        long startTime = System.currentTimeMillis();
        String fileName = getOutputFileDir() + getFileName(speciesTypeKey);
        String headerLines = HEADER_COMMON_LINES
                .replace("#SPECIES#", species)
                .replace("#DATE#", headerDate.format(new Date()));
        FileWriter w = new FileWriter(fileName);
        w.append(headerLines);


        List<Annotation> annotations = dao.getAnnotationsBySpecies(speciesTypeKey, Aspect.BIOLOGICAL_PROCESS);
        annotations.addAll(dao.getAnnotationsBySpecies(speciesTypeKey,Aspect.MOLECULAR_FUNCTION));
        annotations.addAll(dao.getAnnotationsBySpecies(speciesTypeKey,Aspect.CELLULAR_COMPONENT));
        catalyticTerms = dao.getAllActiveTermDescandantAccIds("GO:0003824");
        obsoleteTerms = dao.getObsoleteTermsForGO();
        Set<GoAnnotation> filteredList = new TreeSet<>(new Comparator() {
            @Override
            public int compare(Object o1, Object o2) {
                if(((GoAnnotation)o1).equals((GoAnnotation)o2))
                    return 0;
                else
                    return ((GoAnnotation)o1).getTermAcc().compareTo(((GoAnnotation)o2).getTermAcc());
            }
        });
        annotations.parallelStream().forEach( annotation -> {
            try {
                GoAnnotation result = handleAnnotation(annotation);
                if(result != null) {
                    result.setTaxon("taxon:" + SpeciesType.getTaxonomicId(speciesTypeKey));
                    filteredList.add(result);
                }
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        });


        FileReader fr=new FileReader(getGoaFile());
        BufferedReader br=new BufferedReader(fr);
        while(br.readLine() != null){
            GoAnnotation goAnnotation = new GoAnnotation();
            String tokens[] = br.readLine().split("[\\t]", -1);
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

        for(GoAnnotation g:filteredList){
            writeLine(w,g);
        }
        log.info("END:  time elapsed: " + Utils.formatElapsedTime(startTime, System.currentTimeMillis()));


    }
    GoAnnotation handleAnnotation(Annotation a) throws Exception {

        log.info("Verifying annotation as per Go Rules "+ a.getTermAcc());
        long startTime = System.currentTimeMillis();
        GoAnnotation goAnnotation = new GoAnnotation();

        //Check for Gene Object Type and remove the annotation without it
        int objectKey = a.getRgdObjectKey();
        switch( objectKey ) {
            case RgdId.OBJECT_KEY_GENES:
                goAnnotation.setObjectType("gene");
                break;
            default:
                log.info("object type " + objectKey + " for annot key=" + a.getKey() + "is not gene");
                logSkipped.info(a.getKey()+ " to term"+ a.getTermAcc() + " is not annotated to a gene.");
                return null;
        }



        //GORules:

        //GO consortium rule GO:0000026
        //Check for obsolete terms and filter them
        if(obsoleteTerms.contains(a.getTermAcc())){
            log.info(a.getTermAcc() +" is an Obsolete Term: Annotation skipped");
            logSkipped.info(a.getTermAcc() +" is an Obsolete Term: Annotation skipped");
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
        if( (a.getTermAcc().equals("GO:0005515") || a.getTermAcc().equals("GO:0005488"))&& (!a.getEvidence().equals("IPI") || goAnnotation.getWithInfo().length()==0 )) {
            logSkipped.info(a.getTermAcc() +" is an "+a.getEvidence()+ " Annotation. Only IPI annotation with a non-null WITH field are allowed.");
            return null;
        }

        // GO consortium rule GO:0000006
        //IEP and HEP annotations are restricted to terms from Biological Process ontology
        if((a.getEvidence().equals("IEP") || a.getEvidence().equals("HEP")) && !a.getAspect().equals("P")) {
            logSkipped.info(a.getTermAcc() +" is an "+a.getEvidence()+ "annotation. It is restricted to Biological Process ontology" );
            return null;
        }

        // GO consortium rule GO:0000007
        //IPI annotations should not be used with catalytic molecular function terms
        if(a.getEvidence().equals("IPI")  && catalyticTerms.contains(a.getTermAcc())) {
            logSkipped.info(a.getTermAcc() +" is an "+a.getEvidence()+ "annotation. They should not be used with catalytic molecular terms." );
            return null;
        }

        //GO consortium rule GO:0000008
        //Check for Not4Curation Go terms
        if( !dao.isForCuration(a.getTermAcc()) ) {
            log.info(" term "+a.getTerm()+" is Not4Curation! annotation skipped" );
            logSkipped.info(a.getTermAcc() +" is Not for curation: Annotation skipped");
            return null;
        }




        // GO consortium rule GO:0000011:
        // The No Data (ND) evidence code should be used for annotations to the root nodes only

        // CASE 1: evidence.code = 'ND' AND term.acc NOT IN ( 'GO:0005575', 'GO:0003674', 'GO:0008150' )
        if( a.getEvidence().equals("ND") && a.getTerm().startsWith("GO:")
                && !(a.getTerm().equals("GO:0005575") || a.getTerm().equals("GO:0003674") || a.getTerm().equals("GO:0008150")) ) {
            logSkipped.info(a.getTermAcc() +" is an ND annotation. Fails rule GO:0000011");
            return null;
        }

        // CASE 2: evidence.code != 'ND' AND term.acc IN ( 'GO:0005575', 'GO:0003674', 'GO:0008150' )
        if( !a.getEvidence().equals("ND")
                && (a.getTerm().equals("GO:0005575") || a.getTerm().equals("GO:0003674") || a.getTerm().equals("GO:0008150")) ) {
            logSkipped.info(a.getTermAcc() +" is not an ND annotation.Fails rule GO:0000011");
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
            logSkipped.info("Annotation to term "+a.getTermAcc() + " failed rule: IC and IPI annotations require a WITH field and IDA must not have a WITH field ");
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

        goAnnotation.setObjectId(a.getAnnotatedObjectRgdId().toString());
        String references = mergeWithXrefSource(goAnnotation.getReferences(),a.getXrefSource());
        goAnnotation.setReferences(references);
        goAnnotation.setObjectSymbol(a.getObjectSymbol());
        goAnnotation.setAspect(a.getAspect());
        goAnnotation.setObjectName(a.getObjectName());
        goAnnotation.setMeshOrOmimId("");
        goAnnotation.setTermAcc(a.getTermAcc());
        goAnnotation.setDataSrc(a.getDataSrc());

        log.info("END:  time elapsed: " + Utils.formatElapsedTime(startTime, System.currentTimeMillis()));


        return goAnnotation;
    }


    private String getFileName(int speciesTypeKey) {

        String fileName = "";
        String taxName = SpeciesType.getTaxonomicName(speciesTypeKey).toLowerCase();
        int spacePos = taxName.indexOf(' ');
        if( spacePos>0 )
            fileName = taxName.substring(0, spacePos)+"_" + RgdId.getObjectTypeName(RgdId.OBJECT_KEY_GENES).toLowerCase() + "s_go";
        else
            fileName = taxName + RgdId.getObjectTypeName(RgdId.OBJECT_KEY_GENES).toLowerCase() + "s_go";

        return fileName;
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

    void writeLine(FileWriter writer, GoAnnotation rec) throws Exception{

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

    public String getGoaFile() {
        return goaFile;
    }

    public void setGoaFile(String goaFile) {
        this.goaFile = goaFile;
    }

    public String getOutputFileDir() {
        return outputFileDir;
    }

    public void setOutputFileDir(String outputFileDir) {
        this.outputFileDir = outputFileDir;
    }

    public  void setRefRgdIdsForGoPipelines(Set<Integer> refRgdIdsForGoPipelines) {
        this.refRgdIdsForGoPipelines = refRgdIdsForGoPipelines;
    }
 }

