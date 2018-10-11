. /etc/profile
cd /home/rgddata/pipelines/goc_annotation
java -Dspring.config=../properties/default_db.xml -Dlog4j.configuration=file:properties/log4j.properties -jar goc_annotation.jar -fixNDAnnots
