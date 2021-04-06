# goc-annotation-pipeline
Generate GO annotation files in GAF 2.2 format.

Candidate GO annotations in RGD are always passed through QC with rules described here:
https://github.com/geneontology/go-site/tree/master/metadata/rules. All annotations violating
any of these rules are reported in the summary email and excluded from submission to GOC.
