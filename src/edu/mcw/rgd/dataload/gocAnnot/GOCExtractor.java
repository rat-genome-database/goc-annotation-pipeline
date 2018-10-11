package edu.mcw.rgd.dataload.gocAnnot;

import edu.mcw.rgd.datamodel.RgdId;
import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.XdbId;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.process.FileDownloader;
import edu.mcw.rgd.process.FileExternalSort;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: Mar 7, 2011
 * Time: 2:49:06 PM
 */
public class GOCExtractor {

    DAO dao;

    private String scriptArchiveDir;
    private String gocVerifyProgram;
    private String ontologyDir;
    private String docDir;
    private String extractDir;
    private String gocGeneOntFile;
    private String gocXrfAbbsFile;
    private String goaGafFile;

    FileDownloader downloader = new FileDownloader();
    Log log = LogFactory.getLog(this.getClass());

    public void run() throws Exception {

        // download perl verification script from GOC
        String verificationScript = downloadVerificationScript();

        // Had this script changed .. if so generate email to developers letting them know
        notifyStaffIfVerificationScriptChanged();

        // download gene_ontology_edit.obo
        String geneOntEditFile = downloadFile(getGocGeneOntFile(), getOntologyDir());

        // download GO.xrf_abbs file from goc
        String goXrfAbbsFile = downloadFile(getGocXrfAbbsFile(), getDocDir());

        // generate gaf file from active annotations for rat genes
        String ratGafFile = generateGafFile();

        // add goa annotations to the gaf extract file
        // sort the resulting file and remove duplicate lines
        // the output file will be created in extract dir
        String geneAssociationRgdFile = mergeGafFiles(ratGafFile, getGoaGafFile());

        // run just generated gene association rgd file through goc verification program
        // and email the report to the staff
        runGocVerificationProgram(verificationScript, geneAssociationRgdFile, geneOntEditFile, goXrfAbbsFile);

        // upload gene association rgd file to GO cvs site
        uploadGeneAssociationFile(geneAssociationRgdFile);
    }

    /**
     * download perl verification script from GOC
     * @return verification script path
     */
    String downloadVerificationScript() throws Exception {

        String downloadedFile = downloadFile(getGocVerifyProgram(), getScriptArchiveDir());

        // make the downloaded file executable
        File file = new File(downloadedFile);
        file.setExecutable(true, true);

        return downloadedFile;
    }

    // download an external file to a local directory;
    // the file name will be prepended with date in 'yyyymmdd' format
    String downloadFile(String externalFile, String localDir) throws Exception {

        // ensure local directory exists
        File file = new File(localDir);
        file.mkdir();

        // extract the file name from external file path
        int at = externalFile.lastIndexOf('/');
        String fileName = at<0 ? externalFile : externalFile.substring(at+1);

        downloader.setExternalFile(externalFile);
        downloader.setLocalFile(localDir+"/"+fileName);
        downloader.setPrependDateStamp(true);
        return downloader.download();
    }

    String generateGafFile() throws Exception {

        // download active go annotations for genes
        List<Annotation> annots = dao.getAnnotationsForOntology(RgdId.OBJECT_KEY_GENES, SpeciesType.RAT, "GO%");

        // counters
        int gafLinesWritten = 0;
        int gafLinesSkipped = 0;

        // open the gaf file for printing
        String outFileName = getExtractDir()+"/rattus_gene_go.gaf";
        PrintWriter file = new PrintWriter(new FileWriter(outFileName));
        file.append( "!gaf-version: 2.0\n");

        String db = "RGD";  // database this data is from ... us !
        String dbObjectType = "gene";
        // db taxon for a given species
        String taxon = "taxon:"+Integer.toString(SpeciesType.getTaxonomicId(SpeciesType.RAT));
        // for formatting the date
        SimpleDateFormat sdt = new SimpleDateFormat("yyyyMMdd");

        // process all annotations
        for( Annotation row: annots ) {
            String dbObjectID = row.getAnnotatedObjectRgdId().toString(); // our RGD_ID
            String dbObjectSymbol= row.getObjectSymbol(); // PHO3

            String qualifier = row.getQualifier(); // NOT|contributes_to ( optional )
            if( qualifier == null) {
                qualifier = "";
            } else {
                qualifier = qualifier.trim();
            }

            String goID	= row.getTermAcc().trim(); // GO:0003993
            String dbReference = "";
            String evidence	= row.getEvidence().trim();	//IMP, IDA, NAS, ND, TAS , etc.
            String aspect = row.getAspect();		// F, C or P

            String dbObjectName	= row.getObjectName(); 	//acid phosphatase
            if( dbObjectName == null) {
                dbObjectName = "";
            } else {
                dbObjectName = dbObjectName.trim();
            }
            String dbObjectSynonym 	= "";				//(|Synonym) 	YBR092C
            String assignedBy = row.getDataSrc(); 		// where did this data come from ? RGD , Unitprot , etc.
            java.util.Date dt = row.getCreatedDate();		//20010118
            String createdDate = dt!=null ? sdt.format(dt) : "";

            int refRgdId = row.getRefRgdId()!=null && row.getRefRgdId()>0 ? row.getRefRgdId() : 0;
            if( refRgdId>0 ) {
                //(|DB:Reference) 	RGD:47763|PMID:2676709
                dbReference = "RGD:" + refRgdId;
                for( XdbId xdbId: dao.getXdbIdsByRgdId(XdbId.XDB_KEY_PUBMED, refRgdId) ) {
                    dbReference += "|PMID:" + xdbId.getAccId();
                }
            } else {
                // ref_rgd_id is null -- use non-null XREF_SOURCE as dbReference
                if( row.getXrefSource() != null )
                    dbReference = row.getXrefSource();
            }

            String with = row.getWithInfo(); //(or) From
            if ( with == null) {
                with = "";
            } else {
                // check for Pub Med id in this field, if it exists tack it on to the dbReference field
                if ( with.contains("PMID:") ) {
                    if( dbReference.length()>0 )
                        dbReference += '|';
                    dbReference += with;
                    // print "Added With Field " + With + " to DBreference " + DBReference + "\n";
                }

                // Certain evidence codes cannot have with fields
                if ( evidence.equals("IDA") || evidence.equals("NAS") || evidence.equals("ND") || evidence.equals("TAS") ) {
                    with = "";
                } else {
                    with = with.trim();
                }
            }

            // GO consortium rule:
            // "protein binding" annotation -- GO:0005515 -- must have evidence 'IPI'
            //                   and non-null WITH field
            boolean skipRow = false;
            if( goID.equals("GO:0005515") && (!evidence.equals("IPI") || with.length()==0 )) {
                // "protein binding" rule violation -- skip this row
                skipRow = true;
            }

            if( skipRow ) {
                gafLinesSkipped++;
            } else {
                file
                .append(db).append('\t')
                .append(dbObjectID).append('\t')
                .append(dbObjectSymbol).append('\t')
                .append(qualifier).append('\t')
                .append(goID).append('\t')
                .append(dbReference).append('\t')
                .append(evidence).append('\t')
                .append(with).append('\t')
                .append(aspect).append('\t')
                .append(dbObjectName).append('\t')
                .append(dbObjectSynonym).append('\t')
                .append(dbObjectType).append('\t')
                .append(taxon).append('\t')
                .append(createdDate).append('\t')
                .append(assignedBy).append('\t')
                .append('\t')
                .append('\n');

                gafLinesWritten++;
            }
        }
        file.close();

        log.warn(gafLinesWritten+" lines was written to file "+outFileName);
        log.warn(gafLinesSkipped+" lines skipped: GO:0005515 annot must have 'IPI' evidence and non-null WITH field");
        return outFileName;
    }

    String mergeGafFiles(String ratGafFile, String goaGafFile) throws IOException {

        // create output file name
        SimpleDateFormat sdt = new SimpleDateFormat("yyyyMMdd");
        String outFileName = getExtractDir()+"/"+sdt.format(new java.util.Date())+"_gene_association.rgd";

        // concatenate these two files, sort them and remove duplicate lines
        String[] inFiles = new String[]{ratGafFile, goaGafFile};
        boolean skipDuplicates = true;
        int lineCount = FileExternalSort.mergeAndSortFiles(inFiles, outFileName, skipDuplicates);
        log.warn(ratGafFile+" merged with "+goaGafFile+"; "+lineCount+" lines were written");
        return outFileName;
    }

    void notifyStaffIfVerificationScriptChanged() throws Exception {

        // list all files in script archive directory
        File file = new File(getScriptArchiveDir());
        String[] files = file.list();
        // sort the file names
        Arrays.sort(files);
        // get the most recent file name except the just downloaded verificationScript
        // the file format expected: 'yyyymmdd_filter-gene-association.pl'
        String script1 = null;
        String script2 = null;
        for( int i=files.length-1; i>=0; i-- ) {
            String script = files[i];
            if( script.startsWith("20") && script.endsWith("_filter-gene-association.pl") ) {
                if( script1==null )
                    script1 = script;
                else {
                    script2 = script;
                    break;
                }
            }
        }

        // if we have two scripts, load both of them into memory and compare their contents
        if( script1!=null  &&  script2!=null ) {
            byte[] bytes1 = loadFile(getScriptArchiveDir()+"/"+script1);
            byte[] bytes2 = loadFile(getScriptArchiveDir()+"/"+script2);
            if( !Arrays.equals(bytes1, bytes2) ) {
                // send email to staff
                sendEmailToStaff("[KIRWAN] GOC IMPORTANT", "A Change in GOC verification program has been detected !");
            }
        }
    }

    // aux method: load a file from disk into a byte array
    byte[] loadFile(String fileName) throws IOException {
        File file = new File(fileName);
        FileInputStream is = new FileInputStream(file);

        // Get the size of the file
        long length = file.length();

        // Create the byte array to hold the data
        byte[] bytes = new byte[(int)length];


        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
               && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
            offset += numRead;
        }

        // Ensure all the bytes have been read in
        if (offset < bytes.length) {
            throw new IOException("The file was not completely read: "+file.getName());
        }

        // Close the input stream, all file contents are in the bytes variable
        is.close();

        return bytes;
    }

    void sendEmailToStaff(String subject, String body) {

	    log.info("sending email of report\n");
        sendMail("localhost", new String[]{"mtutaj@mcw.edu"}, subject, body);
	    log.info("Done sending email message\n");
    }

    public void sendMail(String mailServer, String[] recipients, String subject, String message) {

        //if(subject!=null && message!=null )
        //    return; // temporarily disable emails

        try {
            Socket s = new Socket(mailServer, 25);
            BufferedReader in = new BufferedReader (new InputStreamReader(s.getInputStream(), "8859_1"));
            BufferedWriter out = new BufferedWriter (new OutputStreamWriter(s.getOutputStream(), "8859_1"));

            send(in, out, "HELO theWorld");
            send(in, out, "MAIL FROM: rgddata@kirwan.rgd.mcw.edu");
            for( String recipient: recipients ) {
                send(in, out, "RCPT TO: " + recipient);
            }
            send(in, out, "DATA");
            send(out, "Subject: "+subject);
            send(out, "From: rgddata@kirwan.rgd.mcw.edu");
            send(out, "");

            // message body
            send(out, message);
            send(out, ".");
            send(in, out, "QUIT");
            s.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void send(BufferedReader in, BufferedWriter out, String s) throws IOException {
        out.write(s + "\r\n");
        out.flush();
        System.out.println(s);
        s = in.readLine();
        System.out.println(s);
    }

    public void send(BufferedWriter out, String s) {
      try {
         out.write(s + "\r\n");
         out.flush();
         System.out.println(s);
      }
      catch (Exception e) {
         e.printStackTrace();
      }
    }

    public int runGocVerificationProgram(String verificationScript, String dataFile, String geneOntEditFile, String goXrfAbbsFile) throws Exception {

        // create commnad line to run goc verification script
        SimpleDateFormat sdt = new SimpleDateFormat("yyyyMMdd");
        String reportFile = getExtractDir()+"/"+sdt.format(new java.util.Date())+"_gene_association.rgd.report";
        log.info("reportFile="+reportFile+"\n");
        String cmdLine = verificationScript+" -p rgd -d -i "+dataFile+" -o "+geneOntEditFile+" -x "+goXrfAbbsFile;
        log.info("cmdLine="+cmdLine+"\n");

        // execute the process
        String[] command = {verificationScript, "-p", "rgd", "-d", "-i", dataFile, "-o", geneOntEditFile, "-x", goXrfAbbsFile};
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        // merge error stream with output stream
        processBuilder.redirectErrorStream(true);
        // start the process
        Process process = processBuilder.start();

        // read process output stream and save it to disk
        BufferedWriter out = new BufferedWriter(new FileWriter(reportFile));
        BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder report = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            out.write(line);
            out.newLine();
            report.append(line).append('\n');
        }
        in.close();
        out.close();

        // wait until process finishes and read process exit code
        int exitValue = process.waitFor();
        log.info("exitValue="+exitValue+'\n');

        // send error email to staff
        if( exitValue!=0 )
            sendEmailToStaff("[KIRWAN] GOC ERROR", "GOC Verification program failed with error code: "+exitValue);

        log.info("reportContent="+report.toString());
        sendEmailToStaff("[KIRWAN] GOC Verification program status report", report.toString());

        return exitValue;
    }

    void uploadGeneAssociationFile(String geneAssociationFile) {

        /**
# Check in extract file to GOC and copy to FTP directory on local machine
# fordistribution to our public FTP site
#
# CVS questions : You can contact sysadmin@genome.stanford.edu if/when you have a
# systems question or problem.
#
$ENV{'CVSROOT'} = "$CVSROOT";
# copy file to cvs , gzip and transfer
if (! -d "$CVS_DIR" ) {
	print "\nERROR: CVS directory does not exist , please create this first. See the README.txt file for the command to do this.\n \n";
	email_warning( "\nERROR: CVS directory does not exist , please create this first. See the README.txt file for the command to do this.\n \n");
}

$old_archive_file = '';
chdir "$CVS_DIR";
if ( -e "$CVS_FILE_GZ" ) {
	print "Removing old $CVS_FILE_GZ\n";
	$old_archive_file = archive_file ( $CVS_FILE_GZ ) ;
}
copy ( "$EXTRACT_DIR/gene_association.rgd.$DATE_EXT", "$CVS_FILE");
# first Compress file to correct name
$retStr = `$GZIP_PROGRAM $CVS_FILE`;
$retVal = $?;
if ( $retVal != 0 ) {
	email_warning( "\nERROR: GZIP program failed with message :\n $retStr\n");
	die( "\nERROR: GZIP program failed with message :\n $retStr\n");
}

chdir "$CVS_DIR_RELATIVE";
print "\nCheckin file to cvs ...\n";
print     "$CVS_PROGRAM commit -m \'Automated daily commit from RGD for $DATE_EXT \' $CVS_FILE_GZ_RELATIVE";
$retStr = `$CVS_PROGRAM commit -m 'Automated daily commit from RGD for $DATE_EXT' $CVS_FILE_GZ_RELATIVE  2>&1 `;
$retVal = $?;
if ( $retVal != 0 ) {
	email_warning( "\nERROR: CVS Checking program failed with message :\n $retStr\n");
	die( "\nERROR: CVS Checking program failed with message :\n $retStr\n");
}

# Copy Compressed file to FTP directory for distribution to production
print  "cp $CVS_FILE_GZ $FTP_UPLOAD_DIR"  ;
$retStr = copy ( "$CVS_FILE_GZ" , "$FTP_UPLOAD_DIR" ) ;
$retVal = $?;
if ( $retVal != 0 ) {
	email_warning( "\nERROR: Could not copy zipped file to ftp directory :\n $retStr\n");
	die( "\nERROR:  Could not copy zipped file to ftp directory  :\n $retStr\n");
}

print "\n\n Program completed.\n";
         */
    }

    public DAO getDAO() {
        return dao;
    }

    public void setDAO(DAO dao) {
        this.dao = dao;
    }

    public String getScriptArchiveDir() {
        return scriptArchiveDir;
    }

    public void setScriptArchiveDir(String scriptArchiveDir) {
        this.scriptArchiveDir = scriptArchiveDir;
    }

    public String getGocVerifyProgram() {
        return gocVerifyProgram;
    }

    public void setGocVerifyProgram(String gocVerifyProgram) {
        this.gocVerifyProgram = gocVerifyProgram;
    }

    public String getOntologyDir() {
        return ontologyDir;
    }

    public void setOntologyDir(String ontologyDir) {
        this.ontologyDir = ontologyDir;
    }

    public String getDocDir() {
        return docDir;
    }

    public void setDocDir(String docDir) {
        this.docDir = docDir;
    }

    public String getExtractDir() {
        return extractDir;
    }

    public void setExtractDir(String extractDir) {
        this.extractDir = extractDir;
    }

    public String getGocGeneOntFile() {
        return gocGeneOntFile;
    }

    public void setGocGeneOntFile(String gocGeneOntFile) {
        this.gocGeneOntFile = gocGeneOntFile;
    }

    public String getGocXrfAbbsFile() {
        return gocXrfAbbsFile;
    }

    public void setGocXrfAbbsFile(String gocXrfAbbsFile) {
        this.gocXrfAbbsFile = gocXrfAbbsFile;
    }

    public String getGoaGafFile() {
        return goaGafFile;
    }

    public void setGoaGafFile(String goaGafFile) {
        this.goaGafFile = goaGafFile;
    }
}
