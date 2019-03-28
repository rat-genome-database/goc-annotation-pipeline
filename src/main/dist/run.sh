#!/usr/bin/env bash
# shell script to run Annotation Count pipeline
. /etc/profile

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
cp $GOAFILE $APPDATADIR
cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/${APPNAME}.jar "$@"

mailx -s "[$SERVER]GOC Ontology pipeline - Summary Report " $EMAILLIST<skipped.log