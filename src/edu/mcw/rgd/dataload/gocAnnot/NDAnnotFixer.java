package edu.mcw.rgd.dataload.gocAnnot;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: mtutaj
 * Date: Mar 7, 2011
 * Time: 2:53:19 PM
 *
 * A new data cleanup question came up in the curators' meeting and we thought it might be something
 * you could set up relatively quickly and easily to run periodically.
 *
 * When a curator curates GO for a gene and finds there is no experimental rat data out there
 * in the literature for that gene (at least none that can be used to make GO annotations),
 * they do an "ND" annotation, that is, one that says there is no data, e.g. see the cellular
 * component annotation for RGD: 2026.
 *
 * Later, that gene might come up in another context and another curator looks again and finds that
 * research has been done on that rat gene and they are then able to make manual GO annotations
 * for the gene. The problem is that because the curators can't easily delete an annotation,
 * there is a backlog of cases wehre a gene has an ND annotation and one or more manual annotations
 * for the same gene, same "aspect" (function, process or cellular component). In those cases
 * the ND annotation is wrong and needs to be removed.
 */
public class NDAnnotFixer {

    DAO dao;

    public DAO getDAO() {
        return dao;
    }

    public void setDAO(DAO dao) {
        this.dao = dao;
    }

    public void run() throws Exception {

        // get list of 'ND' annotations having 'IDA', 'IMP', 'IEP', 'IPI', 'IGI', 'EXP' too
        Map<Integer, DAO.NDAnnot> ndAnnots = dao.getNDAnnotations();

        // delete every of such annotations
        int rowsDeleted = 0;
        for( Map.Entry<Integer, DAO.NDAnnot> entry: ndAnnots.entrySet() ) {

            DAO.NDAnnot nd = entry.getValue();
            System.out.println("deleting ND annotation: RGDID="+nd.rgdId+", ASPECT="+nd.aspect
                +", EVIDENCES="+nd.evidences+", TERM_ACC="+nd.termAcc+", SYMBOL="+nd.objectSymbol
                +", TERM="+nd.term);
            rowsDeleted += dao.deleteAnnotation(entry.getKey());
        }

        System.out.println("=========================");
        System.out.println("Total ND annotations deleted "+rowsDeleted);
    }
}
