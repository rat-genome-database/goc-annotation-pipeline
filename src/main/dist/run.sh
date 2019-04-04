#!/usr/bin/env bash
# shell script to run Annotation Count pipeline
. /etc/profile

set -e
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
if [ "$SERVER" == "REED" ]; then
#  EMAILLIST=jrsmith@mcw.edu,RGD.Developers@mcw.edu
  EMAILLIST=mtutaj@mcw.edu,hsnalabolu@mcw.edu
else
  EMAILLIST=mtutaj@mcw.edu,hsnalabolu@mcw.edu
fi

APPNAME=goc_annotation
APPDIR=/home/rgddata/pipelines/$APPNAME
APPDATADIR=$APPDIR/data
GOAFILE=/home/rgddata/pipelines/GOAannotation/data/goa_rgd.txt
FTP_UPLOAD_DIR=/home/rgddata/data_release

echo "=== Copy Rat annotations file from GO to data directory  ==="
cp $GOAFILE $APPDATADIR
cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/${APPNAME}.jar "$@"

mailx -s "[$SERVER]GOC Ontology pipeline - Summary Report " $EMAILLIST<logs/skipped.log


##########################################################################
#
# Submit the final file to RGD GITHUB (GOC is pulling it from RGD GITHUB)
#
#
GITHUBDIR=$APPDATADIR/github
FILENAME=gene_association_rgd
GITHUB_FILE=$GITHUBDIR/FILENAME
GITHUB_FILE_GZ=$GITHUB_FILE.gz

echo "=== Copy the file to github directory  ==="
cp $FILENAME /github
cd $GITHUB_DIR

echo "=== Checkin the file to github ==="
git add $GITHUB_FILE.gz

echo "=== Committing the file to github ==="
git commit -m \"weekly commit for $DATE_EXT\"

echo "=== Fetch and pull the file from github ==="
git fetch
git pull

echo "=== Pushing the new file to github ==="
git push origin dev

echo "=== Copy the file to FTP directory  ==="
cp $GITHUB_FILE_GZ $FTP_UPLOAD_DIR