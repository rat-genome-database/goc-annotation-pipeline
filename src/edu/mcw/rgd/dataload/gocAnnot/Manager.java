package edu.mcw.rgd.dataload.gocAnnot;

import edu.mcw.rgd.process.Utils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.FileSystemResource;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * @author mtutaj
 * @since Mar 7, 2011
 */
public class Manager {

    DAO dao = new DAO();
    Log log = LogFactory.getLog("core");
    private String version;
    private int maxExceptions;
    private List<String> staffEMails;

    static public void main(String[] args) throws Exception {

        XmlBeanFactory bf=new XmlBeanFactory(new FileSystemResource("properties/AppConfigure.xml"));
        Manager manager = (Manager) bf.getBean("manager");

        if( args.length==0 ) {
            System.out.println("Please specify a module or modules to run:\n"+
                    "-generateGocExtract (not implemented)\n"+
                    "-generateGeneAssociations (not implemented)\n"+
                    "-fixNDAnnots (not implemented)\n"+
                    "-sortUnique -inFile=FILENAME -outFile=FILENAME\n"+
                    "-dropPmidsForIso -inFile=FILENAME -outFile=FILENAME [-addCustomHeader]\n"+
                    "-addCustomHeader -inFile=FILENAME -outFile=FILENAME\n"+
                    "-fix_conflicts -gaf_file=FILENAME -report_file=FILENAME");
            return;
        }

        String gafFile = null;
        String reportFile = null;
        boolean fixConflicts = false;

        boolean sortUnique = false;
        String inFile = null;
        String outFile = null;
        boolean dropPmidsForIso = false;
        boolean addCustomHeader = false;

        for( String arg: args ) {
            if( arg.equals("-generateGocExtract") ) {

                GOCExtractor extractor = (GOCExtractor) bf.getBean("gocExtractor");
                extractor.setDAO(manager.dao);
                extractor.run();
            } /*
            else if( arg.equals("-generateGeneAssociations") ) {

                GeneAssociationFileGenerator generator = (GeneAssociationFileGenerator) bf.getBean("gafGenerator");
                generator.setDAO(manager.dao);
                generator.run();
            }   */
            else if( arg.equals("-fixNDAnnots") ) {

                NDAnnotFixer fixer = (NDAnnotFixer) bf.getBean("ndAnnotFixer");
                fixer.setDAO(manager.dao);
                fixer.run();
            }
            else if( arg.equals("-fix_conflicts") ) {
                fixConflicts = true;
            }
            else if( arg.equals("-sortUnique") ) {
                sortUnique = true;
            }
            else if( arg.startsWith("-inFile=") ) {
                inFile = arg.substring(8);
            }
            else if( arg.startsWith("-outFile=") ) {
                outFile = arg.substring(9);
            }
            else if( arg.startsWith("-gaf_file=") ) {
                gafFile = arg.substring(10);
            }
            else if( arg.startsWith("-report_file=") ) {
                reportFile = arg.substring(13);
            }
            else if( arg.equals("-dropPmidsForIso") ) {
                dropPmidsForIso = true;
            }
            else if( arg.equals("-addCustomHeader") ) {
                addCustomHeader = true;
            }
        }

        if( sortUnique ) {
            manager.sortUnique(inFile, outFile);
        }

        if( fixConflicts ) {
            manager.fixConflicts(gafFile, reportFile);
        }

        // addCustomHeader could be used together with dropPmidForIso
        if( dropPmidsForIso ) {
            manager.dropPmidsForIso(inFile, outFile, addCustomHeader);
        } // or be standalone
        else if( addCustomHeader ) {
            manager.addCustomHeader(inFile, outFile);
        }
    }

    /**
     * If there are more than $MAX_EXCEPTIONS exceptions just generate email.
     * <p>
     * strip from gaf file all lines that are marked as conflicting in report file;
     * also strip from gaf file all lines that do contain text 'occurs_in(CL:0000002)';
     * (per gaf validation report: [The id 'CL:0000002' in the c16 annotation extension
     *    is an obsolete class, suggested replacements: CLO:0000019]
     * @param gafFile file name of gaf file
     * @param reportFile file name of report file
     * @throws Exception
     */
    public void fixConflicts(String gafFile, String reportFile) throws Exception {

        logIt("start fix conflicts, gaf_file="+gafFile+", report_file="+reportFile);

        // read report file and numbers of conflicting lines
        Set<Integer> conflictingLineNumbers = getConflictingLineNumbers(reportFile);

        int conflictLines = 0;

        // create copy of gaf file without conflicts
        String gafFile2 = gafFile+".tmp";
        BufferedReader reader = new BufferedReader(new FileReader(gafFile));
        BufferedWriter writer = new BufferedWriter(new FileWriter(gafFile2));
        BufferedWriter writerForConflicts = new BufferedWriter(new FileWriter(gafFile+".conflicts"));
        int lineNumber = 0;
        String line;
        while( (line=reader.readLine())!=null ) {
            lineNumber++;
            if( !conflictingLineNumbers.contains(lineNumber) &&
                !line.contains("occurs_in(CL:0000002)") )
            {
                writer.write(line);
                writer.newLine();
            }
            else {
                conflictLines++;
                writerForConflicts.write(line);
                writerForConflicts.newLine();
            }
        }
        reader.close();
        writer.close();
        writerForConflicts.close();

        // delete original file
        new File(gafFile).delete();

        // rename gafFile2 to gafFile
        new File(gafFile2).renameTo(new File(gafFile));

        logIt("end fix conflicts, gaf_file="+gafFile+", report_file="+reportFile);
        logIt("  removed "+conflictLines+" conflicting lines from gaf file "+gafFile);
    }

    void parseTotalErrors(String line) throws UnknownHostException {
        if( !line.contains("TOTAL ERRORS") )
            return;
        int eqPos = line.lastIndexOf('=');
        if( eqPos<0 )
            return;

        int errorCountFound = Integer.parseInt(line.substring(eqPos+1).trim());

        logIt("Verifying number of errors found through GOC check program...\nFound "+errorCountFound+" errors.");
        if( errorCountFound > getMaxExceptions() ) {
            String title = "many exceptions in file";
            String msg = "WARNING: Many Exceptions in GOA Pipeline file "+errorCountFound+" ( "+getMaxExceptions()+" Allowed ). But we're still uploading the file to GOC.\n";
            logIt(msg);
            emailWarning(title, msg);
        }
    }

    Set<Integer> getConflictingLineNumbers(String reportFile) throws Exception {

        Set<Integer> numbers = new HashSet<>();

        BufferedReader reader = new BufferedReader(new FileReader(reportFile));
        String line;
        while( (line=reader.readLine())!=null ) {

            parseTotalErrors(line);

            // sample line:
            // 139020: Date column=14 IEA evidence code present with a date more than a year old "20120707"
            int colonPos = line.indexOf(':');
            if( colonPos <=0 )
                continue;

            // try to parse the string part before colon as integer
            try {
                int lineNumber = Integer.parseInt(line.substring(0, colonPos));
                numbers.add(lineNumber);
            }
            catch(NumberFormatException ignore) {
            }
        }
        reader.close();
        return numbers;
    }

    void sortUnique(String inFile, String outFile) throws Exception {

        BufferedReader reader = new BufferedReader(new FileReader(inFile));
        String line;
        Set<String> lines = new TreeSet<>();
        while( (line=reader.readLine())!=null ) {
            lines.add(line);
        }
        reader.close();

        BufferedWriter writer = new BufferedWriter(new FileWriter(outFile));
        for( String l: lines ) {
            writer.write(l);
            writer.write('\n');
        }
        writer.close();
    }

    void emailWarning(String title, String msg) throws UnknownHostException {
        // get current host name, f.e. kirwan.rgd.mcw.edu
        String hostName = InetAddress.getLocalHost().getHostName();
        // get short host name upper case, f.e KIRWAN
        String shortHostName;
        int dotPos = hostName.indexOf('.');
        if( dotPos>0 )
            shortHostName = hostName.substring(0, dotPos).toUpperCase();
        else
            shortHostName = hostName.toUpperCase();

        String from = "rgddata@"+hostName;

        String[] to = new String[getStaffEMails().size()];
        for( int i=0; i<getStaffEMails().size(); i++ ) {
            to[i] = getStaffEMails().get(i);
        }

        String subject = "["+shortHostName+"] GOC Ontology pipeline - "+title;

        Utils.sendMail("localhost", from, to, subject, msg);
    }

    void logIt(String msg) {
        log.info(msg);
        //System.out.println(msg);
    }

    /**
     * @throws Exception
     */
    public void dropPmidsForIso(String inFile, String outFile, boolean addCustomHeader) throws Exception {

        logIt("drop PMIDs from ISO annotations, input_file="+inFile+", output_file="+outFile);

        // create copy of gaf file without conflicts
        BufferedReader reader = new BufferedReader(new FileReader(inFile));
        BufferedWriter writer;
        if( outFile.endsWith(".gz") ) {
            writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outFile))));
        } else {
            writer = new BufferedWriter(new FileWriter(outFile));
        }

        String line = addCustomHeader ? handleHeaderLines(reader, writer) : reader.readLine();

        int linesUpdatedRatGoPipeline = 0;
        int linesUpdatedNonRatGoPipeline = 0;
        int linesProcessed = 0;
        do {
            linesProcessed++;

            // line must contain an ISO evidence code
            int posForISO = line.indexOf("\tISO\t");
            if( posForISO < 0 ) {
                writer.write(line);
                writer.write("\n");
                continue;
            }

            // RULE1: When the REF_RGD_ID is RGD:1624291 ("Rat ISS GO annotation pipeline"),
            // that should be the only thing in the reference field
            String lineUpdated = handleRatIssGoPipeline(line);
            if( lineUpdated!=null ) {
                linesUpdatedRatGoPipeline++;
                writer.write(lineUpdated);
                writer.write("\n");
                continue;
            }

            // line must contain a PMID
            int posForPMID = line.indexOf("PMID:");
            if( posForPMID < 0 ) {
                writer.write(line);
                writer.write("\n");
                continue;
            }

            linesUpdatedNonRatGoPipeline++;

            // drop PMID
            int colStart = line.lastIndexOf('\t', posForPMID)+1;
            int colEnd = line.indexOf('\t', posForPMID);
            String col = line.substring(colStart, colEnd);
            String[] vals = col.split("[\\|\\,]");
            List<String> valList = new ArrayList<>();
            for( String val: vals ) {
                if( !val.startsWith("PMID:") ) {
                    valList.add(val);
                }
            }
            String colUpdated = Utils.concatenate(valList, "|");

            lineUpdated = line.substring(0, colStart) + colUpdated + line.substring(colEnd);

            writer.write(lineUpdated);
            writer.write("\n");
        } while( (line=reader.readLine())!=null );

        reader.close();
        writer.close();

        logIt("  total lines processed "+linesProcessed);
        logIt("  cleaned up DB_Ref for Rat ISS GO pipeline for "+linesUpdatedRatGoPipeline+" lines");
        logIt("  removed PMIDs from DB_Ref for other refs  for "+linesUpdatedNonRatGoPipeline+" lines");
    }

    String handleHeaderLines(BufferedReader in, BufferedWriter out) throws IOException {

        final String customHeader = "!gaf-version: 2.1\n" +
            "!{ The gene_association.rgd file is available at the GO Consortium website (http://www.geneontology.org/page/download-go-annotations) and on RGD's FTP site (ftp://ftp.rgd.mcw.edu/pub/data_release/). The file and its contents follow the specifications laid out by the Consortium, currently GO Annotation File (GAF) Format 2.1 located at http://www.geneontology.org/page/go-annotation-file-gaf-format-21.  This requires that some details available for certain annotations on the RGD website and/or in other annotations files found on the RGD FTP site must be excluded from this file in order to conform to the GOC guidelines and to correspond to GAF files from other groups. }\n" +
            "!{ As of December 2016, the gene_association.rgd file only contains 'RGD' in column 1 and RGD gene identifiers in column 2. }\n" +
            "!{ As of March 2018, the gene_association.rgd file no longer includes identifiers for the original references (see below) for ISO annotations in column 6. For ISO annotations, entries in column 6 will be limited to RGD:1624291, RGD's internal reference which explains the assignment of GO ISO annotations to rat genes.  }\n" +
            "!{ The gene_protein_association.rgd file (available on the RGD ftp site at ftp://ftp.rgd.mcw.edu/pub/data_release/) contains both RGD gene and UniProt protein IDs in columns 1/2. The gene_protein_association.rgd file also includes original reference IDs for rat ISO annotations, as well as the ID for RGD's internal reference which explains the assignment of GO ISO annotations to rat genes.  \"Original reference\" refers to the identifier(s), such as PMIDs and/or other database IDs for the references used to assign GO annotations to genes or proteins in other species which are then inferred to rat genes by orthology. }\n" +
            "!{ Additional annotation files can be found on RGD's ftp site in the ftp://ftp.rgd.mcw.edu/pub/data_release/annotated_rgd_objects_by_ontology/ directory and its \"with_terms\" subdirectory (ftp://ftp.rgd.mcw.edu/pub/data_release/annotated_rgd_objects_by_ontology/with_terms/). The annotated_rgd_objects_by_ontology directory contains GAF-formatted files for all of RGD's ontology annotations, that is, annotations for all of the ontologies that RGD uses for all annotated objects from all of the species in RGD.  Files in the \"with_terms\" subdirectory contain the same data with the addition of ontology terms for human-readability as well as additional information in the form of curator notes. }\n" +
            "!{ For additional information about the file formats for files in the annotated_rgd_objects_by_ontology/ directory and it's \"with_terms\" subdirectory see the README files at ftp://ftp.rgd.mcw.edu/pub/data_release/annotated_rgd_objects_by_ontology/README and ftp://ftp.rgd.mcw.edu/pub/data_release/annotated_rgd_objects_by_ontology/with_terms/WITHTERMS_README. }\n";

        // append custom header
        out.write(customHeader);

        // skip original header lines
        String line;
        while( (line=in.readLine())!=null ) {
            if( !line.startsWith("!") ) {
                break;
            }
        }
        return line;
    }

    String handleRatIssGoPipeline(String line) {
        //
        // line must contain RGD:1624291 (Rat ISS Go Pipeline)
        int posForRef = line.indexOf("RGD:1624291");
        if( posForRef < 0 ) {
            return null;
        }

        // drop PMID
        int colStart = line.lastIndexOf('\t', posForRef)+1;
        int colEnd = line.indexOf('\t', posForRef);
        return line.substring(0, colStart) + "RGD:1624291" + line.substring(colEnd);
    }

    public void addCustomHeader(String inFile, String outFile) throws Exception {

        logIt("adding custom header, input_file="+inFile+", output_file="+outFile);

        // create copy of gaf file without conflicts
        BufferedReader reader = new BufferedReader(new FileReader(inFile));
        BufferedWriter writer;
        if( outFile.endsWith(".gz") ) {
            writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outFile))));
        } else {
            writer = new BufferedWriter(new FileWriter(outFile));
        }

        String line = handleHeaderLines(reader, writer);

        int linesProcessed = 0;
        do {
            linesProcessed++;

            writer.write(line);
            writer.write("\n");
        } while( (line=reader.readLine())!=null );

        reader.close();
        writer.close();

        logIt("  total lines processed "+linesProcessed);
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setMaxExceptions(int maxExceptions) {
        this.maxExceptions = maxExceptions;
    }

    public int getMaxExceptions() {
        return maxExceptions;
    }

    public void setStaffEMails(List<String> staffEMails) {
        this.staffEMails = staffEMails;
    }

    public List<String> getStaffEMails() {
        return staffEMails;
    }
}
