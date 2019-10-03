#!/usr/bin/env bash
. /etc/profile

set -e
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
if [ "$SERVER" == "REED" ]; then
  EMAILLIST=jrsmith@mcw.edu,mtutaj@mcw.edu,hsnalabolu@mcw.edu
else
  EMAILLIST=mtutaj@mcw.edu,hsnalabolu@mcw.edu
fi

APPNAME=goc_annotation
APPDIR=/home/rgddata/pipelines/$APPNAME
APPDATADIR=$APPDIR/data
GOAFILE=/home/rgddata/pipelines/GOAannotation/data/goa_rgd.txt
FTP_UPLOAD_DIR=/home/rgddata/data_release
DATE_EXT=`date +%Y%m%d`
FILENAME=gene_association.rgd
PROTEIN_FILE=gene_protein_association.rgd
GITHUBDIR=github/rgd-annotation-files

echo "=== Copy Rat annotations file from GO to data directory  ==="
cp $GOAFILE $APPDATADIR

echo "=== Archive the old file in data directory  ==="
cp $APPDATADIR/$FILENAME $APPDATADIR/$FILENAME.$DATE_EXT
cp $APPDATADIR/$PROTEIN_FILE $APPDATADIR/$PROTEIN_FILE.$DATE_EXT

cd $APPDIR



java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/${APPNAME}.jar "$@"

mailx -s "[$SERVER]GOC Ontology pipeline - Summary Report " $EMAILLIST<logs/skipped.log


if [ "$SERVER" == "REED" ]; then
#
# Submit the final file to RGD GITHUB (GOC is pulling it from RGD GITHUB)
#

  echo "=== Copy new file to github directory ==="
  cp data/$FILENAME $GITHUBDIR
  cp data/$PROTEIN_FILE $GITHUBDIR

  cd $GITHUBDIR
  echo "=== Compress new file in github directory ==="
  gzip --force $FILENAME
  gzip --force $PROTEIN_FILE

  echo "=== Checkin the file to github ==="
  git add $FILENAME.gz

  echo "=== Committing the file to github ==="
  git commit -m "weekly commit for $DATE_EXT"

  echo "=== Fetch and pull the file from github ==="
  git fetch
  git pull origin master

  echo "=== Pushing the new file to github ==="
  git push origin master

fi

echo "=== Copy the file to FTP directory  ==="
cp $FILENAME.gz $FTP_UPLOAD_DIR
cp $PROTEIN_FILE.gz $FTP_UPLOAD_DIR
