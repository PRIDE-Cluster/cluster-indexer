package uk.ac.ebi.pride.cluster.indexer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.pride.archive.ontology.model.OntologyTerm;
import uk.ac.ebi.pride.archive.ontology.search.service.OntologyTermSearchService;
import uk.ac.ebi.pride.cluster.search.model.Cluster;
import uk.ac.ebi.pride.cluster.search.service.IClusterIndexService;
import uk.ac.ebi.pride.cluster.search.service.IClusterSearchService;
import uk.ac.ebi.pride.cluster.search.util.QualityAssigner;
import uk.ac.ebi.pride.spectracluster.repo.dao.IClusterReadDao;
import uk.ac.ebi.pride.spectracluster.repo.model.AssaySummary;
import uk.ac.ebi.pride.spectracluster.repo.model.ClusterSummary;
import uk.ac.ebi.pride.spectracluster.repo.model.ClusteredPSMSummary;
import uk.ac.ebi.pride.spectracluster.repo.model.PSMSummary;
import uk.ac.ebi.pride.spectracluster.repo.utils.paging.Page;

import java.util.*;

/**
 * @author ntoro
 * @since 14/11/14 13:30
 */
public class ClusterIndexer {

    private static final int PAGE_SIZE = 500;

    private static Logger logger = LoggerFactory.getLogger(ClusterIndexer.class);

    private IClusterSearchService clusterSearchService;
    private IClusterIndexService clusterIndexService;
    private IClusterReadDao clusterReadDao;
    private OntologyTermSearchService ontologyTermSearchService;

    public ClusterIndexer(IClusterSearchService clusterSearchService,
                          IClusterIndexService clusterIndexService,
                          IClusterReadDao clusterReadDao,
                          OntologyTermSearchService ontologyTermSearchService) {
        this.clusterSearchService = clusterSearchService;
        this.clusterIndexService = clusterIndexService;
        this.clusterReadDao = clusterReadDao;
        this.ontologyTermSearchService = ontologyTermSearchService;
    }

    public void indexCluster(long clusterId){
        Cluster cluster = asSolrCluster(clusterReadDao.findCluster(clusterId));
        clusterIndexService.save(cluster);
    }

    public void indexClusters(List<Long> clusterIds){

        List<Cluster> clusters = new ArrayList<Cluster>(clusterIds.size());

        for (Long clusterId : clusterIds) {
            clusters.add(asSolrCluster(clusterReadDao.findCluster(clusterId)));
        }

        clusterIndexService.save(clusters);
    }

    public void indexAllClusters(){

        List<Cluster> clusters;
        long count = 0;
        int page = 0;
        long numClusters = clusterReadDao.getNumberOfClusters();

        long startTime;
        long endTime;

        // add all CLUSTERs to index
        startTime = System.currentTimeMillis();

        clusterIndexService.deleteAll();

        Page<ClusterSummary> clusterSummaryPage;
        while (count < numClusters) {
            clusterSummaryPage = clusterReadDao.getAllClusters(page++, PAGE_SIZE);
            clusters = asSolrCluster(clusterSummaryPage.getPageItems());
            clusterIndexService.save(clusters);
            logger.debug("COMMITTED " + clusters.size() + " clusters.");

            count += clusterSummaryPage.getPageItems().size();
        }

        endTime = System.currentTimeMillis();
        logger.info("DONE indexing all CLUSTERs in " + (double) (endTime - startTime) / 1000.0 + " seconds");

    }

    public void indexNonExistingClusters() {

        List<Cluster> clusters = new ArrayList<Cluster>(PAGE_SIZE);
        long count = 0;
        int page = 0;
        long numClusters = clusterReadDao.getNumberOfClusters();

        long startTime;
        long endTime;

        // add all CLUSTERs to index
        startTime = System.currentTimeMillis();

        Page<ClusterSummary> clusterSummaryPage;
        while (count < numClusters) {
            clusterSummaryPage = clusterReadDao.getAllClusters(page++, PAGE_SIZE);

            for (ClusterSummary clusterSummary : clusterSummaryPage.getPageItems()) {
                if(!clusterSearchService.existsCluster(clusterSummary.getId())){
                    clusters.add(asSolrCluster(clusterSummary));
                } else {
                    logger.info("Cluster " + clusterSummary.getId() + " already in the index. SKIPPING...");
                }
            }
            clusterIndexService.save(clusters);
            logger.debug("COMMITTED " + clusters.size() + " clusters.");

            count += clusterSummaryPage.getPageItems().size();
        }

        endTime = System.currentTimeMillis();
        logger.info("DONE indexing all CLUSTERs in " + (double) (endTime - startTime) / 1000.0 + " seconds");

    }

    private List<Cluster> asSolrCluster(List<ClusterSummary> clusterSummaries) {
        List<Cluster> clusters = new ArrayList<Cluster>(PAGE_SIZE);

        for (ClusterSummary clusterSummary : clusterSummaries) {
            clusters.add(asSolrCluster(clusterSummary));
        }

        return clusters;

    }

    private Cluster asSolrCluster(ClusterSummary clusterSummary) {

        String projectAccession;
        String assayAccession;
        List<AssaySummary> assaySummaries;

        List<String> projects = new ArrayList<String>();
        List<Long> assaysIds = new ArrayList<Long>();

        List<String> speciesNames = new ArrayList<String>();
        List<String> speciesAccessions = new ArrayList<String>();

        Set<String> pepSequences = new LinkedHashSet<String>();
        Set<String> proteinAccs = new LinkedHashSet<String>();

        Set<String> speciesDescendantsNames = new LinkedHashSet<String>();
        Set<String> speciesDescendantsAccessions = new LinkedHashSet<String>();

        Map<String, List<String>> projectAssays = new HashMap<String, List<String>>();

        Cluster solrCluster = new Cluster();
        solrCluster.setId(clusterSummary.getId());
        solrCluster.setNumberOfSpectra(clusterSummary.getNumberOfSpectra());
        solrCluster.setAveragePrecursorCharge(clusterSummary.getAveragePrecursorCharge());
        solrCluster.setAveragePrecursorMz(clusterSummary.getAveragePrecursorMz());
        solrCluster.setMaxRatio(clusterSummary.getMaxPeptideRatio());

        List<ClusteredPSMSummary> clusteredPSMSummaries = clusterSummary.getClusteredPSMSummaries();

        //Highest rank peptides and protein accessions
        for (ClusteredPSMSummary clusteredPSMSummary : clusteredPSMSummaries) {
            if (clusteredPSMSummary.getRank() == 1) {
                PSMSummary psmSummary = clusteredPSMSummary.getPsmSummary();
                if (psmSummary != null) {
                    pepSequences.add(psmSummary.getSequence());
                    proteinAccs.add(psmSummary.getProteinAccession());
                    // TODO protein group
                    // proteinAccs.addAll(psmSummary.getProteinGroup());
                    assaysIds.add(psmSummary.getAssayId());
                }
            }
        }

        assaySummaries = clusterReadDao.findAssays(assaysIds);

        //Processing assay information
        for (AssaySummary repoAssay : assaySummaries) {
            projectAccession = repoAssay.getProjectAccession();
            projects.add(projectAccession);

            assayAccession = repoAssay.getAccession();

            //Species metadata
            //TODO Multispecies
            speciesNames.add(repoAssay.getSpecies());
            speciesAccessions.add(repoAssay.getTaxonomyId());

            if (projectAssays.containsKey(projectAccession)) {
                projectAssays.get(projectAccession).add(assayAccession);
            } else {
                projectAssays.put(projectAccession, new ArrayList<String>(Arrays.asList(assayAccession)));
            }
        }

        solrCluster.setHighestRatioProteinAccessions(proteinAccs);
        solrCluster.setHighestRatioPepSequences(pepSequences);
        solrCluster.setProjectAssays(projectAssays);
        solrCluster.setProjects(projects);
        solrCluster.setClusterQuality(QualityAssigner.calculateQuality(solrCluster.getNumberOfSpectra(), solrCluster.getMaxRatio()));

        // species
        solrCluster.setSpeciesNames(speciesNames);
        solrCluster.setSpeciesAccessions(speciesAccessions);

        for (int i=0; i < speciesAccessions.size(); i++) {
            String speciesAccession = speciesAccessions.get(i);
            String speciesName = speciesNames.get(i);
            List<OntologyTerm> relativeSpecies = ontologyTermSearchService.findAllByDescendant(speciesAccession);
            for (OntologyTerm relativeOntologyTerm: relativeSpecies) {

                speciesDescendantsAccessions.add(relativeOntologyTerm.getAccession());
                speciesDescendantsNames.add(relativeOntologyTerm.getName());
            }

            speciesDescendantsAccessions.add(speciesAccession);
            speciesDescendantsNames.add(speciesName);
        }

        solrCluster.setSpeciesAscendantsAccessions(new ArrayList<String>(speciesDescendantsAccessions));
        solrCluster.setSpeciesAscendantsNames(new ArrayList<String>(speciesDescendantsNames));

        return solrCluster;

    }
}
