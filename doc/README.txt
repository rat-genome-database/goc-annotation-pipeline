README for the  GOC Annotation pipeline : 
----------------------------------------

The ~HOME_DIR/cvs directory was first created with the following command: 

cd goc_cvs
cvs -qz3 -d :ext:kowalski@ext.geneontology.org:/share/go/cvs checkout go/gene-associations/submission/gene_association.rgd.gz

See the Wiki for more details on this process: 

http://dowler.hmgc.mcw.edu/wiki/index.php/Export_of_Annotations_to_the_Go_Consortium


Email from Mike Cherry: 

George,

The webcvs is a view of the Anonymous CVS repository.  It is updated once a day from the main CVS repository.  So your check via the command line to the main GO CVS repository is the appropriate check.

The new files in the /submissions/ directory are processed twice a day, 4AM and 8PM Pacific time.  The result is the file in the main gene-associations directory.


-Mike

