package edu.mcw.rgd;

import edu.mcw.rgd.process.Utils;

import java.util.Objects;

/**
 * Created by hsnalabolu on 3/21/2019.
 */
public class GoAnnotation implements Comparable<GoAnnotation> {

    public String objectId;
    public String objectSymbol;
    public String qualifier;
    public String termAcc;
    public String references;
    public String evidence;
    public String withInfo;
    public String aspect;
    public String objectName;
    public String meshOrOmimId;
    public String objectType;
    public String taxon;
    public String createdDate;
    public String dataSrc;
    public String annotExtension;
    public String geneProductId;

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getObjectSymbol() {
        return objectSymbol;
    }

    public void setObjectSymbol(String objectSymbol) {
        this.objectSymbol = objectSymbol;
    }

    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    public String getTermAcc() {
        return termAcc;
    }

    public void setTermAcc(String termAcc) {
        this.termAcc = termAcc;
    }

    public String getReferences() {
        return references;
    }

    public void setReferences(String references) {
        this.references = references;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getWithInfo() {
        return withInfo;
    }

    public void setWithInfo(String withInfo) {
        this.withInfo = withInfo;
    }

    public String getAspect() {
        return aspect;
    }

    public void setAspect(String aspect) {
        this.aspect = aspect;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getMeshOrOmimId() {
        return meshOrOmimId;
    }

    public void setMeshOrOmimId(String meshOrOmimId) {
        this.meshOrOmimId = meshOrOmimId;
    }

    public String getObjectType() {
        return objectType;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public String getTaxon() {
        return taxon;
    }

    public void setTaxon(String taxon) {
        this.taxon = taxon;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public String getDataSrc() {
        return dataSrc;
    }

    public void setDataSrc(String dataSrc) {
        this.dataSrc = dataSrc;
    }

    public String getAnnotExtension() {
        return annotExtension;
    }

    public void setAnnotExtension(String annotExtension) {
        this.annotExtension = annotExtension;
    }

    public String getGeneProductId() {
        return geneProductId;
    }

    public void setGeneProductId(String geneProductId) {
        this.geneProductId = geneProductId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GoAnnotation that = (GoAnnotation) o;
        return Objects.equals(objectId, that.objectId) &&
                Objects.equals(objectSymbol, that.objectSymbol) &&
                Objects.equals(qualifier, that.qualifier) &&
                Objects.equals(termAcc, that.termAcc) &&
                Objects.equals(references, that.references) &&
                Objects.equals(evidence, that.evidence) &&
                Objects.equals(withInfo, that.withInfo) &&
                Objects.equals(aspect, that.aspect) &&
                Objects.equals(objectName, that.objectName) &&
                Objects.equals(meshOrOmimId, that.meshOrOmimId) &&
                Objects.equals(objectType, that.objectType) &&
                Objects.equals(taxon, that.taxon) &&
                Objects.equals(dataSrc, that.dataSrc) &&
                Objects.equals(annotExtension, that.annotExtension) &&
                Objects.equals(geneProductId, that.geneProductId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectId, objectSymbol, qualifier, termAcc, references, evidence, withInfo, aspect, objectName, meshOrOmimId, objectType, taxon, dataSrc, annotExtension, geneProductId);
    }

    @Override
    public int compareTo(GoAnnotation o) {
        int r = Utils.stringsCompareTo(objectId, o.objectId);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(objectSymbol, o.objectSymbol);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(termAcc, o.termAcc);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(qualifier, o.qualifier);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(references, o.references);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(evidence, o.evidence);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(withInfo, o.withInfo);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(aspect, o.aspect);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(objectName, o.objectName);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(meshOrOmimId, o.meshOrOmimId);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(objectType, o.objectType);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(taxon, o.taxon);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(dataSrc, o.dataSrc);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(annotExtension, o.annotExtension);
        if( r!= 0 ) {
            return r;
        }
        r = Utils.stringsCompareTo(geneProductId, o.geneProductId);
        return r;
    }
}
