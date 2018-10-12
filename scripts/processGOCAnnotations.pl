#!/usr/bin/perl -w
##########################################################################
#
# Program to extract data from RGD database and upload to GO consortium
#
##########################################################################
use File::Basename;
use File::Copy;
use File::Compare;
use MIME::Lite;

# You may need to change these
$HOME='/home/rgddata/pipelines/goc_annotation';
$WGET_PROGRAM='/usr/bin/wget -nv';     # run wget in no-verbose mode
$MAX_BACKUP_FILES=1000;

$SERVER_NAME=`hostname -s | tr -d '\n' | tr '[a-z]' '[A-Z]'`;
$STAFF_EMAIL='mtutaj@mcw.edu';
if( $SERVER_NAME eq "REED") {
    $STAFF_EMAIL='rgd.developers@mcw.edu,jrsmith@mcw.edu,slaulederkind@mcw.edu';
}
$SUBMIT_TO_GITHUB=1;

# Should not have to change these
$RUN_JAVA="java -Dspring.config=../properties/default_db.xml -Dlog4j.configuration=file:properties/log4j.properties -jar goc_annotation.jar ";

# extension for data files
$DATE_EXT=`date +%y%m%d`;
chop $DATE_EXT;

# in -rgdOnly mode, proteins are not exported
my ($mode) = @ARGV;
$ASSOC_FILE="gene_association.rgd";
if( $mode ne "-rgdOnly" ) {
    $ASSOC_FILE="gene_protein_association.rgd";
    $SUBMIT_TO_GITHUB=0;
}
$ASSOC_FILE_GZ="${ASSOC_FILE}.gz";

$DATA_DIR="${HOME}/data";
$GITHUB_DIR="${HOME}/github/rgd-annotation-files"; # github staging dir
$GITHUB_FILE="$GITHUB_DIR/$ASSOC_FILE";
$GITHUB_FILE_GZ="$GITHUB_FILE.gz";

$SCRIPT_ARCHIVE_DIR="$DATA_DIR/script_archive"; # Where downloaded scripts from GOC go
$ONTOLOGY_DIR="$DATA_DIR/ontology";
$DOC_DIR="$DATA_DIR/doc";
$EXTRACT_DIR="$DATA_DIR/extract";
$EXTRACT_REPORT="$EXTRACT_DIR/$ASSOC_FILE.report.$DATE_EXT";
$FTP_UPLOAD_DIR='/home/rgddata/data_release';

# Remote GOC Scripts / data files
$GOC_VERIFY_PROGRAM = "ftp://ftp.geneontology.org/pub/go/software/utilities/filter-gene-association.pl";
$GOC_GENE_ONT_FILE  = "ftp://ftp.geneontology.org/pub/go/ontology/gene_ontology_edit.obo";
$GOC_GO_XREF_FILE   = "ftp://ftp.geneontology.org/pub/go/doc/GO.xrf_abbs";

$VERIFY_PROGRAM          = "${DATA_DIR}/filter-gene-association.pl"; # archive of current PROGRAM IF NEEDED
$VERIFY_PROGRAM_ARCH     = "${SCRIPT_ARCHIVE_DIR}/filter-gene-association.pl"; # archive of current PROGRAM IF NEEDED
$VERIFY_PROGRAM_ARCH_BAK = ""; # backup of current PROGRAM IF NEEDED
$GENE_ONT_FILE  = "$ONTOLOGY_DIR/gene_ontology_edit.obo";
$GO_XREF_FILE   = "$DOC_DIR/GO.xrf_abbs";

# From the GOC Download Pipeline, Append these to the records we then upload
$GOC_UPLOAD_RECORDS="/home/rgddata/pipelines/GOAannotation/data/goa_rgd.txt";

# From ftpFileExtracts pipeline, the GO rat annotations
$RGD_GO_ANNOTS="/home/rgddata/pipelines/ftpFileExtracts/data/annotated_rgd_objects_by_ontology/rattus_genes_go";



# Create needed directories
if ( ! -d "$DATA_DIR" ) {
	mkdir "$DATA_DIR";
}
if ( ! -d "$SCRIPT_ARCHIVE_DIR" ) {
	mkdir "$SCRIPT_ARCHIVE_DIR";
}
if ( ! -d "$ONTOLOGY_DIR" ) {
	mkdir "$ONTOLOGY_DIR";
}
if ( ! -d "$DOC_DIR" ) {
	mkdir "$DOC_DIR";
}
if ( ! -d "$EXTRACT_DIR" ) {
	mkdir "$EXTRACT_DIR";
}

##########################################################################
#
# Download perl verification Script from GOC
#
chdir "$SCRIPT_ARCHIVE_DIR";

print " testing for old $VERIFY_PROGRAM"; 
if ( -e "$VERIFY_PROGRAM_ARCH" ) { 
	print "\nArchiving previous file $VERIFY_PROGRAM_ARCH \n";
	$VERIFY_PROGRAM_ARCH_BAK = archive_file ( "$VERIFY_PROGRAM_ARCH"); 
	print "Made archived of verification file to $VERIFY_PROGRAM_ARCH_BAK \n";
}
$retStr = `$WGET_PROGRAM $GOC_VERIFY_PROGRAM 2>/dev/null`;
$retVal = $?;
if ( $? != 0 ) { 
	print "\nWARNING: Download of $GOC_VERIFY_PROGRAM file failed with message :\n $retStr\n";
}
print "Fixing perms on $VERIFY_PROGRAM_ARCH\n";
`chmod u+x $VERIFY_PROGRAM_ARCH`;
if ( $? != 0 ) { 
	print "\nWARNING: cannot fix permissions  on  $VERIFY_PROGRAM_ARCH\n";
}
chdir "$DATA_DIR";
if ( ! -l "$VERIFY_PROGRAM" ) {
	print "ln -s $VERIFY_PROGRAM_ARCH $VERIFY_PROGRAM";
	`ln -s $VERIFY_PROGRAM_ARCH $VERIFY_PROGRAM`;
}

##########################################################################
#
# Had this script changed .. if so generate email to developers letting them know
#

if ( compare($VERIFY_PROGRAM_ARCH, $VERIFY_PROGRAM_ARCH_BAK) ) { 
	email_warning("change in GOC verification program", "IMPORTANT: A Change in GOC verification program has been detected !\n") ;
}


##########################################################################
#
# Download gene_ontology_edit.obo file from GOC
#
print "\nDownloading gene_ontology_edit.obo file from GOC... \n" ; 
if ( -e "$GENE_ONT_FILE" ) { 
	$ONTOLOGY_DIR_ARCH = archive_file ( "$GENE_ONT_FILE" ); 
}
chdir "$ONTOLOGY_DIR";
$retStr = `$WGET_PROGRAM $GOC_GENE_ONT_FILE`;
$retVal = $?;
if ( $? != 0 ) { 
	print "\nWARNING: Download of $GOC_GENE_ONT_FILE file failed with message :\n $retStr\n";
	email_warning("failed to download gene_ontology_edit.obo", "WARNING: Download of $GOC_GENE_ONT_FILE file failed with message :\n $retStr\n");
}

##########################################################################
#
# Download GO.xrf_abbs from GOC
#

print "\nDownloading GO.xrf_abbs file from GOC... \n" ; 
if ( -e "$GO_XREF_FILE" ) { 
	$ONTOLOGY_DIR_ARCH = archive_file ( "$GO_XREF_FILE" ); 
}
chdir "$DOC_DIR";
$retStr = `$WGET_PROGRAM $GOC_GO_XREF_FILE`;
$retVal = $?;
if ( $? != 0 ) { 
	print "\nWARNING: Download of $GOC_GO_XREF_FILE file failed with message :\n $retStr\n";
	email_warning("failed to download go.xrf_abbs", "WARNING: Download of $GOC_GO_XREF_FILE file failed with message :\n $retStr\n") ;
}

##########################################################################
#
# copy rat annotations for GO into extract directory
#
my $tmpFile="$EXTRACT_DIR/$ASSOC_FILE.x$DATE_EXT";
$retStr = `cp $RGD_GO_ANNOTS $tmpFile`;
if ( $retVal != 0 ) { 
	print "\nERROR: Failed to copy $RGD_GO_ANNOTS:\n $retStr\n";
	email_warning("failed to copy rattus_genes_go", "ERROR: Failed to copy $RGD_GO_ANNOTS:\n $retStr\n");
}

##########################################################################
#
# Append GO Annotations to Extract file
#
if( $mode ne "-rgdOnly" ) {
    print "\nAppending $GOC_UPLOAD_RECORDS to $tmpFile ...\n";
    if ( -f "$GOC_UPLOAD_RECORDS" ) {
        $retStr = `cat $GOC_UPLOAD_RECORDS >> $tmpFile`;
        $retVal = $?;
        if ( $retVal != 0 ) {
            print "\nERROR: Combining to data files failed :\n $retStr\n";
            email_warning("failed to append GO annots to extract file", "ERROR: Combining to data files failed :\n $retStr\n");
        }
    } else {
        email_warning("GOA file not found", "ERROR: GOA file not found  : $GOC_UPLOAD_RECORDS\n");
    }
} else {
    print "\nRGD ONLY MODE: UniProt lines are skipped\n";
}

# sort the lines and remove duplicates
chdir "$HOME";
my $cmd="$RUN_JAVA -sortUnique -inFile=$tmpFile -outFile=$EXTRACT_DIR/$ASSOC_FILE.$DATE_EXT";
print "\n $cmd";
$retStr = `$cmd`;
$retVal = $?;
if ( $retVal != 0 ) {
	email_warning("failed when fixing gaf errors", "ERROR: failed fixing gaf errors:\n $retStr\n");
	die( "\nERROR: failed fixing gaf errors:\n $retStr\n");
}
print "\n$retStr\n";


# remove the temporary file
$retStr = `rm $tmpFile`;
$retVal = $?;
if ( $retVal != 0 ) {
    print "\nERROR: Failed removal of $tmpFile: \n $retStr\n";
    email_warning("failed to remove tmp extract file", "ERROR: Failed removal of $tmpFile: \n $retStr\n");
}
 
##########################################################################
#
# Run GOC Verification program
#
chdir "$DATA_DIR";
print "Running GOC Verification program : $VERIFY_PROGRAM ...\n" ; 

$cmd="${VERIFY_PROGRAM} -p rgd -d -i $EXTRACT_DIR/$ASSOC_FILE.$DATE_EXT > $EXTRACT_REPORT 2>&1";
print($cmd);
system($cmd);
$resVal = $?; 
if ($resVal != 0) {
	print "\nERROR: Verification program failed with message :\n $resVal\n";
	email_warning("GOC Verification program failed", "ERROR: Verification program failed with message :\n $resVal");
} else { 
	print "Status " . $resVal ."\n";
}
email_file( "GOC Verification program status report " , $EXTRACT_REPORT);

##########################################################################
# 
# Generate staff email with errors parsed out
#
$reportValue = getStaffReportText ( $EXTRACT_REPORT, "$EXTRACT_DIR/$ASSOC_FILE.$DATE_EXT" ) ;
if ( $reportValue ne '' ) { 
	email_warning("GOC Verification program detailed status report", "GOC Verification program detailed status report" . $reportValue ) ; 
}

##########################################################################
# report by email if number of exceptions exceed the limit (f.e. 1000)
# remove lines causing exceptions
#
chdir "$HOME";
print "\n $RUN_JAVA -fix_conflicts -gaf_file=$EXTRACT_DIR/$ASSOC_FILE.$DATE_EXT -report_file=$EXTRACT_REPORT";
$retStr = `$RUN_JAVA -fix_conflicts -gaf_file=$EXTRACT_DIR/$ASSOC_FILE.$DATE_EXT -report_file=$EXTRACT_REPORT`;
$retVal = $?;
if ( $retVal != 0 ) {
	email_warning("failed when fixing gaf errors", "ERROR: failed fixing gaf errors:\n $retStr\n");
	die( "\nERROR: failed fixing gaf errors:\n $retStr\n");
}
print "\n$retStr\n";

##########################################################################
#
# rerun verification program
#
chdir "$DATA_DIR";
print "Rerunning GOC Verification program : $VERIFY_PROGRAM ...\n" ;

print( "${VERIFY_PROGRAM} -p rgd -d -i $EXTRACT_DIR/$ASSOC_FILE.$DATE_EXT > $EXTRACT_REPORT.final 2>&1\n");
system( "${VERIFY_PROGRAM} -p rgd -d -i $EXTRACT_DIR/$ASSOC_FILE.$DATE_EXT > $EXTRACT_REPORT.final 2>&1");
$resVal = $?;
if ($resVal != 0) {
	print "\nERROR: Verification program failed with message :\n $resVal\n";
	email_warning("Verification program failed", "ERROR: Verification program failed with message :\n $resVal");
} else {
	print "Status " . $resVal ."\n";
}
email_file( "GOC Verification program final status report", "$EXTRACT_REPORT.final");

##########################################################################
#
# Generate staff email with errors parsed out
#

$reportValue = getStaffReportText ( "$EXTRACT_REPORT.final", "$EXTRACT_DIR/$ASSOC_FILE.$DATE_EXT" ) ;
if ( $reportValue ne '' ) {
	email_warning("GOC Verification program detailed final status report","GOC Verification program detailed final status report" . $reportValue ) ;
}

##########################################################################
#
# Submit the final file to RGD GITHUB (GOC is pulling it from RGD GITHUB)
# and copy to FTP directory on local machine for distribution to our public FTP site
#
#

if( $SUBMIT_TO_GITHUB ) {
    # copy file to svn, gzip and transfer
    if (! -d "$GITHUB_DIR" ) {
        print "\nERROR: GITHUB directory does not exist!\n \n";
        email_warning("GITHUB directory does not exist!", "ERROR: GITHUB directory does not exist!\n \n");
    }

    $old_archive_file = '';
    chdir "$GITHUB_DIR";
    if ( -e "$GITHUB_FILE_GZ" ) {
        print "Removing old $GITHUB_FILE_GZ\n";
        $old_archive_file = archive_file ( $GITHUB_FILE_GZ ) ;
    }

    chdir "$HOME";
    my $cmd="$RUN_JAVA -dropPmidsForIso -inFile=$EXTRACT_DIR/$ASSOC_FILE.$DATE_EXT -outFile=$GITHUB_FILE_GZ -addCustomHeader";
    print "\n $cmd\n";
    $retStr = `$cmd`;
    $retVal = $?;
    if ( $retVal != 0 ) {
        email_warning("failed when dropping PMIDs", "ERROR: failed dropping PMIDs:\n $retStr\n");
        die( "\nERROR: failed when dropping PMIDs:\n $retStr\n");
    }
    print "\n$retStr\n";

    # Copy Compressed file to FTP directory for distribution to production
    print  "cp $GITHUB_FILE_GZ $FTP_UPLOAD_DIR\n";
    $retStr = copy ( "$GITHUB_FILE_GZ" , "$FTP_UPLOAD_DIR" );
    $retVal = $?;
    if ( $retVal != 0 ) {
        email_warning("copy failed", "ERROR: Could not copy zipped goc file to ftp directory :\n $retStr\n");
        die( "\nERROR:  Could not copy zipped goc file to ftp directory  :\n $retStr\n");
    }

    chdir "$GITHUB_DIR";
    print "\nCheckin file to  GITHUB...\n";
	
	$cmd = "git add $ASSOC_FILE_GZ";
	print "\n $cmd\n";
    $retStr = `$cmd`;
    $retVal = $?;
    if ( $retVal != 0 ) {
        email_warning("failed git add", "ERROR: failed git add\n $retStr\n");
        die( "\nERROR: failed git add:\n $retStr\n");
    }
    print "\n$retStr\n";

	$cmd = "git commit -m \"weekly commit for $DATE_EXT\"";
	print "\n $cmd\n";
    $retStr = `$cmd`;
    $retVal = $?;
    if ( $retVal != 0 ) {
        email_warning("failed git commit", "ERROR: failed git commit\n $retStr\n");
        die( "\nERROR: failed git commit:\n $retStr\n");
    }
    print "\n$retStr\n";
	
	$cmd = "git push origin master";
	print "\n $cmd\n";
    $retStr = `$cmd`;
    $retVal = $?;
    if ( $retVal != 0 ) {
        email_warning("failed git push", "ERROR: failed git push\n $retStr\n");
        die( "\nERROR: failed git push:\n $retStr\n");
    }
    print "\n$retStr\n";
} else {
    chdir "$HOME";
    my $cmd="$RUN_JAVA -addCustomHeader -inFile=$EXTRACT_DIR/$ASSOC_FILE.$DATE_EXT -outFile=$EXTRACT_DIR/$ASSOC_FILE.gz";
    print "\n $cmd\n";
    $retStr = `$cmd`;
    $retVal = $?;
    if ( $retVal != 0 ) {
        email_warning("failed to add custom header", "ERROR: failed to add custom header:\n $retStr\n");
        die( "\nERROR: failed to add custom header:\n $retStr\n");
    }
    print "\n$retStr\n";


    # Copy Compressed file to FTP directory for distribution to production
    print  "cp $EXTRACT_DIR/$ASSOC_FILE.gz $FTP_UPLOAD_DIR\n";
    $retStr = copy ( "$EXTRACT_DIR/$ASSOC_FILE.gz" , "$FTP_UPLOAD_DIR" );
    $retVal = $?;
    if ( $retVal != 0 ) {
        email_warning("copy failed", "ERROR: Could not copy zipped file to ftp directory :\n $retStr\n");
        die( "\nERROR:  Could not copy zipped file to ftp directory  :\n $retStr\n");
    }

    print "\nSKIPPING SUBMIT TO GOC\n";

}

print  "\n=== DONE! ===\n";


##########################################################################
# Subroutines needed 
#
# Archive the program passed in with a number after the file and returns the new file name
# Same as wget does , but we then know the returned value. 
#
sub archive_file { 
	my $orig_file_path = shift; 
	if ( -e "$orig_file_path" ) { 
		($file,$dir,$ext) = fileparse($orig_file_path,qr/\.[^.]*/); 
		foreach ( $i = 1; $i < $MAX_BACKUP_FILES ; $i++ ) { 
			if ( -e "$dir/${file}${ext}.$i" ) { 
				next; 
			} else { 
				last;
			}
		}
		print  "To many archive files , please clean up the directory $dir" if $i >= $MAX_BACKUP_FILES;
		die ( "To many archive files , please clean up the directory $dir") if $i >= $MAX_BACKUP_FILES;

		$new_file_path = "$dir/${file}${ext}.$i" ; 
		move($orig_file_path, $new_file_path) ; 
		return $new_file_path ;
	} else {
		die ("Cannot find original file to archive $orig_file_path\n"); 
	}
}
##########################################################################
#
# Email exception list to staff in any case
#
sub email_file {
	$message = shift;
	$file_path = shift;

	print "sending by email file $file_path\n";
	
	my $doc = do {
		local $/ = undef;
		open my $fh, "<", $file_path or die "could not open $file_path: $!";
		<$fh>;
	};
	
	my $msg = MIME::Lite->new(
		From    => 'rgddata@reed <RGD Data Account>',
		To      => $STAFF_EMAIL,
		Subject => "[$SERVER_NAME] GOC Ontology pipeline Exception Report",
		Type    => 'text/plain',
		Data    => $message."\n\n".$doc,
	);
	$msg->send;	
}
##########################################################################
#
# Email exception list to staff in any case
#
sub email_warning {
	$subject = shift;
	$message = shift;

	print "sending email of report\n";
	
	my $msg = MIME::Lite->new(
		From    => 'rgddata@reed <RGD Data Account>',
		To      => $STAFF_EMAIL,
		Subject => "[$SERVER_NAME] GOC Ontology pipeline - $subject",
		Type    => 'text/plain',
		Data    => $message,
	);
	$msg->send;	
	print "Done sending email message\n";
}

sub getStaffReportText { 

	$reportFile = shift; 
	$dataFile = shift; 
	$returnString = '';

	#print "R: $reportFile\n";
	#print "D: $dataFile\n";
	my %finalArray ;
	open ( PS, "grep '^[0-9]*:' $reportFile |") or die ( "Cannot open ps ");
	while ( <PS>) {
		#print "L: $_";
		@array = split ( /:/); 
		$finalArray{$array[0]} = $array[1];
		#print "Setting key $array[0] to $array[1]\n";
	}

	$dataFileLineCount = 1; 
	open ( FH , "<$dataFile" ) || die " Cannot open datafile\n";
	while ( <FH> ) { 
		$dataline = $_; 
		chomp $dataline;
		if ( exists( $finalArray{$dataFileLineCount} ) ) {
			$returnString .= "-------------------------------------------------------------------\n";
			$returnString .= "Error on line $dataFileLineCount\n";
			$returnString .= "Error Message:\n $finalArray{$dataFileLineCount}\n";
			$outputline =  $dataline;
			$outputline =~ s/\t/|/g;
			$returnString .= "Data:\n $outputline\n"
		}
		$dataFileLineCount++;
	}
	$returnString;
}
