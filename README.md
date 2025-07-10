# goc-annotation-pipeline
Generate GO annotation files in GAF 2.2 format.

Candidate GO annotations in RGD are always passed through QC with rules described here:
https://github.com/geneontology/go-site/tree/master/metadata/rules. All annotations violating
any of these rules are reported in the summary email and excluded from submission to GOC.

GAF file:

- special processing for RAT ISO annotations (REF_RGD_ID=1624291):
    all original GO_REFs are removed; GO_REF:0000121 is put instead
    (GO_REF:0000121: 'RGD ISO annotations to rat from other mammalian species')