#!/usr/bin/env bash
. /etc/profile

set -e
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
if [ "$SERVER" == "REED" ]; then
  EMAILLIST=jrsmith@mcw.edu,mtutaj@mcw.edu
else
  EMAILLIST=mtutaj@mcw.edu
fi

APPNAME=goc-annotation-pipeline
APPDIR=/home/rgddata/pipelines/$APPNAME
APPDATADIR=$APPDIR/data
FTP_UPLOAD_DIR=/home/rgddata/data_release
DATE_EXT=`date +%Y%m%d`
FILENAME=rgd.gpi

echo "=== Archive the old file in data directory  ==="
cp $APPDATADIR/$FILENAME $APPDATADIR/$FILENAME.$DATE_EXT

cd $APPDIR


java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configurationFile=file://$APPDIR/properties/log4j2.xml \
    -jar lib/${APPNAME}.jar --generateGpiFile "$@"

mailx -s "[$SERVER] GPI pipeline - Summary Report " $EMAILLIST<logs/summary.log


echo "=== Copy the file to FTP directory  ==="
cp $FILENAME.gz $FTP_UPLOAD_DIR
