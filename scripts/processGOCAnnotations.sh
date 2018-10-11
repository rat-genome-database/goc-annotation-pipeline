#
cd /home/rgddata/pipelines/goc_annotation

# generate gaf file with RGD only annotations (gene_associations.rgd.gz), and submit the file to GOC
./processGOCAnnotations.pl -rgdOnly

# generate gaf file with RGD and UniProtKB (and others) annotations (gene_protein_associations.rgd.gz)
./processGOCAnnotations.pl -withProteins