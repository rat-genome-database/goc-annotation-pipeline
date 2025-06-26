#!/usr/bin/env bash
. /etc/profile

set -e
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
if [ "$SERVER" == "REED" ]; then
  EMAILLIST="jrsmith@mcw.edu mtutaj@mcw.edu"
else
  EMAILLIST=mtutaj@mcw.edu
fi

APPNAME=goc-annotation-pipeline
APPDIR=/home/rgddata/pipelines/$APPNAME
APPDATADIR=$APPDIR/data
DATA_RELEASE_DIR=/home/rgddata/data_release
DATE_EXT=`date +%Y%m%d`
FILENAME=rgd.gpi
GITHUBDIR=github/rgd-annotation-files

echo "=== Archive the old file in data directory  ==="
cp -p $APPDATADIR/$FILENAME $APPDATADIR/$FILENAME.$DATE_EXT

cd $APPDIR


java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configurationFile=file://$APPDIR/properties/log4j2.xml \
    -jar lib/${APPNAME}.jar --generateGpiFile "$@"

mailx -s "[$SERVER] GPI pipeline - Summary Report " $EMAILLIST<logs/summary.log


echo "=== Copy the file to FTP directory  ==="
cp -p $APPDATADIR/$FILENAME $DATA_RELEASE_DIR



if [ "$SERVER" == "REED" ]; then
#
# Submit the final file to RGD GITHUB (GOC is pulling it from RGD GITHUB)
#

  echo "=== Copy new file to github directory ==="
  cp -p $APPDATADIR/$FILENAME $GITHUBDIR

  cd $GITHUBDIR
  echo "=== Compress new file in github directory ==="
  gzip --force $FILENAME

  echo "=== Checkin the file to github ==="
  git add $FILENAME.gz

  echo "=== Committing the file to github ==="
  git commit -m "weekly commit of rgd.gpi.gz file for $DATE_EXT"

  echo "=== Fetch and pull the file from github ==="
  git fetch
  git pull origin master

  echo "=== Pushing the new file to github ==="
  git push origin master

fi
