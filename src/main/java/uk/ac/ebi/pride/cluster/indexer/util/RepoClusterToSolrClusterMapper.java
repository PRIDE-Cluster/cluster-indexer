package uk.ac.ebi.pride.cluster.indexer.util;

import uk.ac.ebi.pride.cluster.search.model.Cluster;
import uk.ac.ebi.pride.cluster.search.util.QualityAssigner;
import uk.ac.ebi.pride.spectracluster.repo.dao.IClusterReadDao;
import uk.ac.ebi.pride.spectracluster.repo.model.AssaySummary;
import uk.ac.ebi.pride.spectracluster.repo.model.ClusterSummary;
import uk.ac.ebi.pride.spectracluster.repo.model.ClusteredPSMSummary;
import uk.ac.ebi.pride.spectracluster.repo.model.PSMSummary;
import uk.ac.ebi.pride.spectracluster.repo.utils.paging.Page;

import java.util.*;

/**
 * @author Jose A. Dianes <jdianes@ebi.ac.uk>
 *
 */
public class RepoClusterToSolrClusterMapper {

    public static List<Cluster> asClusterList(Page<ClusterSummary> repoClusters, IClusterReadDao clusterReadDao) {
        List<Cluster> res = new LinkedList<Cluster>();

        for (ClusterSummary repoCluster : repoClusters.getPageItems()) {
            res.add(asCluster(repoCluster, clusterReadDao));
        }

        return res;
    }

    public static Cluster asCluster(ClusterSummary repoCluster, IClusterReadDao clusterReadDao) {

        Cluster solrCluster = new Cluster();

        //TODO List or Set (boost the results?)
        Set<String> pepSequences = new HashSet<String>();
        Set<String> proteinAccs = new HashSet<String>();

        Map<String, List<String>> projectAssays = new HashMap<String, List<String>>();

        List<String> projects = new ArrayList<String>();
        List<Long> assaysIds = new ArrayList<Long>();

        solrCluster.setId(repoCluster.getId());
        solrCluster.setNumberOfSpectra(repoCluster.getNumberOfSpectra());
        solrCluster.setAveragePrecursorCharge(repoCluster.getAveragePrecursorCharge());
        solrCluster.setAveragePrecursorMz(repoCluster.getAveragePrecursorMz());
        solrCluster.setMaxRatio(repoCluster.getMaxPeptideRatio());

        List<ClusteredPSMSummary> clusteredPSMSummaries = repoCluster.getClusteredPSMSummaries();

        //Highest rank peptides and protein accessions
        for (ClusteredPSMSummary clusteredPSMSummary : clusteredPSMSummaries) {
            if(clusteredPSMSummary.getRank() == 1) {
                PSMSummary psmSummary = clusteredPSMSummary.getPsmSummary();
                if(psmSummary != null){
                    pepSequences.add(psmSummary.getSequence());
                    proteinAccs.add(psmSummary.getProteinAccession());
                    // TODO protein group
                    // proteinAccs.addAll(psmSummary.getProteinGroup());

                    assaysIds.add(psmSummary.getAssayId());
                }
            }
        }

        List<AssaySummary> repoAssays = clusterReadDao.findAssays(assaysIds);
        String projectAccession;
        String assayAccession;
        for (AssaySummary repoAssay : repoAssays) {
            projectAccession = repoAssay.getProjectAccession();
            projects.add(projectAccession);

            assayAccession = repoAssay.getAccession();

            if(projectAssays.containsKey(projectAccession)){
                projectAssays.get(projectAccession).add(assayAccession);
            }
            else {
                projectAssays.put(projectAccession, new ArrayList<String>(Arrays.asList(assayAccession)));

            }

        }

        solrCluster.setHighestRatioProteinAccessions(proteinAccs);
        solrCluster.setHighestRatioPepSequences(pepSequences);
        solrCluster.setProjectAssays(projectAssays);
        solrCluster.setProjects(projects);
        solrCluster.setClusterQuality(QualityAssigner.calculateQuality(solrCluster.getNumberOfSpectra(),solrCluster.getMaxRatio()));
        return solrCluster;
    }

}
