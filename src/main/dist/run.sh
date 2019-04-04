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
DATE_EXT=`date +%y%m%d`
FILENAME=gene_association.rgd
FILE_GZ=$FILENAME.gz

echo "=== Copy Rat annotations file from GO to data directory  ==="
cp $GOAFILE $APPDATADIR
cd $APPDIR

echo "=== Archive the old file in data directory  ==="
cp data/$FILENAME data/$FILENAME.$DATE_EXT

java -Dspring.config=$APPDIR/../properties/default_db.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/${APPNAME}.jar "$@"

mailx -s "[$SERVER]GOC Ontology pipeline - Summary Report " $EMAILLIST<logs/skipped.log


##########################################################################
#
# Submit the final file to RGD GITHUB (GOC is pulling it from RGD GITHUB)
#
#

echo "=== Checkin the file to github ==="
git add $FILE.gz

echo "=== Committing the file to github ==="
git commit -m \"weekly commit for $DATE_EXT\"

echo "=== Fetch and pull the file from github ==="
git fetch
git pull

echo "=== Pushing the new file to github ==="
git push origin dev

echo "=== Copy the file to FTP directory  ==="
cp $FILE.gz $FTP_UPLOAD_DIR