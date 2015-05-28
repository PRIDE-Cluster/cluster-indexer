package uk.ac.ebi.pride.cluster.indexer;

import org.apache.commons.math.stat.StatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.pride.archive.dataprovider.identification.ModificationProvider;
import uk.ac.ebi.pride.archive.ontology.model.OntologyTerm;
import uk.ac.ebi.pride.archive.ontology.search.service.OntologyTermSearchService;
import uk.ac.ebi.pride.cluster.search.model.SolrCluster;
import uk.ac.ebi.pride.cluster.search.service.IClusterIndexService;
import uk.ac.ebi.pride.cluster.search.service.IClusterSearchService;
import uk.ac.ebi.pride.cluster.search.util.LowResUtils;
import uk.ac.ebi.pride.spectracluster.repo.dao.cluster.IClusterReadDao;
import uk.ac.ebi.pride.spectracluster.repo.model.*;
import uk.ac.ebi.pride.spectracluster.repo.utils.paging.Page;

import java.util.*;

/**
 * @author ntoro
 * @since 14/11/14 13:30
 */
public class ClusterIndexerDB implements IClusterIndexer {

    private static final int PAGE_SIZE = 500;

    private static final ClusterQuality DEFAULT_CLUSTER_QUALITY = ClusterQuality.HIGH;

    private static Logger logger = LoggerFactory.getLogger(ClusterIndexerDB.class);

    private IClusterSearchService clusterSearchService;
    private IClusterIndexService clusterIndexService;
    private IClusterReadDao clusterReadDao;
    private OntologyTermSearchService ontologyTermSearchService;
    private int lowResSize = 20;

    public ClusterIndexerDB(IClusterSearchService clusterSearchService,
                            IClusterIndexService clusterIndexService,
                            IClusterReadDao clusterReadDao,
                            OntologyTermSearchService ontologyTermSearchService,
                            int lowResSize) {
        this.clusterSearchService = clusterSearchService;
        this.clusterIndexService = clusterIndexService;
        this.clusterReadDao = clusterReadDao;
        this.ontologyTermSearchService = ontologyTermSearchService;
        this.lowResSize = lowResSize;
    }

    public void indexCluster(long clusterId) {
        SolrCluster cluster = asSolrCluster(clusterId);
        clusterIndexService.save(cluster);
    }

    public void indexClusters(Set<Long> clusterIds) {

        List<SolrCluster> clusters = new ArrayList<SolrCluster>(clusterIds.size());

        for (Long clusterId : clusterIds) {
            clusters.add(asSolrCluster(clusterId));
        }

        clusterIndexService.save(clusters);
    }

    public void indexAllClusters() {

        List<SolrCluster> clusters;
        long count = 0;
        int page = 1;
        long numClusters = clusterReadDao.getNumberOfClusters();

        long startTime;
        long endTime;

        // add all CLUSTERs to index
        startTime = System.currentTimeMillis();

        clusterIndexService.deleteAll();

        Page<Long> clusterIds;
        while (count < numClusters) {
            clusterIds = clusterReadDao.getAllClusterIdsByQuality(page++, PAGE_SIZE, DEFAULT_CLUSTER_QUALITY);
            clusters = asSolrCluster(clusterIds.getPageItems());
            clusterIndexService.save(clusters);
            logger.debug("COMMITTED " + clusters.size() + " clusters.");

            count += clusterIds.getPageItems().size();
        }

        endTime = System.currentTimeMillis();
        logger.info("DONE indexing all clusters in " + (double) (endTime - startTime) / 1000.0 + " seconds");

    }

    public void indexNonExistingClusters() {

        List<SolrCluster> clusters = new ArrayList<SolrCluster>(PAGE_SIZE);
        long count = 0;
        int page = 0;
        long numClusters = clusterReadDao.getNumberOfClusters();

        long startTime;
        long endTime;

        // add all clusters to index
        startTime = System.currentTimeMillis();

        Page<Long> clusterIds;
        while (count < numClusters) {
            clusterIds = clusterReadDao.getAllClusterIdsByQuality(page++, PAGE_SIZE, DEFAULT_CLUSTER_QUALITY);

            for (Long clusterId : clusterIds.getPageItems()) {
                if (!clusterSearchService.existsCluster(clusterId)) {
                    clusters.add(asSolrCluster(clusterId));
                } else {
                    logger.info("Cluster " + clusterIds + " already in the index. SKIPPING...");
                }
            }
            clusterIndexService.save(clusters);
            logger.debug("COMMITTED " + clusters.size() + " clusters.");

            count += clusterIds.getPageItems().size();
        }

        endTime = System.currentTimeMillis();
        logger.info("DONE indexing all clusters in " + (double) (endTime - startTime) / 1000.0 + " seconds");

    }

    private List<SolrCluster> asSolrCluster(List<Long> clusterIds) {
        List<SolrCluster> clusters = new ArrayList<SolrCluster>(PAGE_SIZE);

        for (Long clusterId : clusterIds) {
            clusters.add(asSolrCluster(clusterId));
        }

        return clusters;

    }

    private SolrCluster asSolrCluster(Long clusterId) {
        String projectAccession;
        String assayAccession;
        ClusterDetail clusterDetail;
        List<AssayDetail> assayDetails;

        Set<String> projects = new LinkedHashSet<String>();

        Set<String> speciesNames = new LinkedHashSet<String>();
        Set<String> speciesAccessions = new LinkedHashSet<String>();

        Set<String> pepSequences = new HashSet<String>();
        Set<String> proteinAccs = new HashSet<String>();

        Set<String> speciesDescendantsNames = new LinkedHashSet<String>();
        Set<String> speciesDescendantsAccessions = new LinkedHashSet<String>();

        Map<String, List<String>> projectAssays = new HashMap<String, List<String>>();

        clusterDetail = clusterReadDao.findCluster(clusterId);

        SolrCluster solrCluster = new SolrCluster();
        solrCluster.setId(clusterDetail.getId());
        solrCluster.setNumberOfSpectra(clusterDetail.getNumberOfSpectra());
        solrCluster.setTotalNumberOfSpectra(clusterDetail.getTotalNumberOfSpectra());
        solrCluster.setNumberOfProjects(clusterDetail.getNumberOfProjects());
        solrCluster.setTotalNumberOfProjects(clusterDetail.getTotalNumberOfProjects());
        solrCluster.setNumberOfSpecies(clusterDetail.getNumberOfSpecies());
        solrCluster.setTotalNumberOfSpecies(clusterDetail.getTotalNumberOfSpecies());
        solrCluster.setNumberOfModifications(clusterDetail.getNumberOfModifications());
        solrCluster.setTotalNumberOfModifications(clusterDetail.getTotalNumberOfModifications());
        solrCluster.setAveragePrecursorCharge(clusterDetail.getAveragePrecursorCharge());
        solrCluster.setAveragePrecursorMz(clusterDetail.getAveragePrecursorMz());
        solrCluster.setMaxRatio(clusterDetail.getMaxPeptideRatio());

        List<ClusteredPSMDetail> clusteredPSMSummaries = clusterDetail.getClusteredPSMDetails();

        //Highest rank peptides and protein accessions
        for (ClusteredPSMDetail clusteredPSMDetail : clusteredPSMSummaries) {
            if (clusteredPSMDetail.getRank() == 1.1f) {
                PSMDetail psmDetail = clusteredPSMDetail.getPsmDetail();
                pepSequences.add(psmDetail.getSequence());
                // modifications
                Set<ModificationProvider> modifications = new HashSet<ModificationProvider>();
                for (ModificationProvider modification : psmDetail.getModifications()) {
                    modifications.add(modification);
                }
                solrCluster.setModifications(new ArrayList<ModificationProvider>(modifications));
                proteinAccs.add(psmDetail.getProteinAccession());
                // TODO protein group
                // proteinAccs.addAll(psmDetail.getProteinGroup());
            }
        }

        assayDetails = clusterDetail.getAssayDetails();

        //Processing assay information
        for (AssayDetail repoAssay : assayDetails) {
            projectAccession = repoAssay.getProjectAccession();
            projects.add(projectAccession);

            assayAccession = repoAssay.getAccession();

            //Species metadata
            speciesNames.addAll(repoAssay.getSpeciesEntries());
            speciesAccessions.addAll(repoAssay.getTaxonomyIdEntries());

            if (projectAssays.containsKey(projectAccession)) {
                projectAssays.get(projectAccession).add(assayAccession);
            } else {
                projectAssays.put(projectAccession, new ArrayList<String>(Arrays.asList(assayAccession)));
            }
        }

        List<String> highestRationProteinAccessions = new LinkedList<String>();
        highestRationProteinAccessions.addAll(proteinAccs);
        solrCluster.setHighestRatioProteinAccessions(highestRationProteinAccessions);
        List<String> highestRationPeptideSequences = new LinkedList<String>();
        highestRationPeptideSequences.addAll(pepSequences);
        solrCluster.setHighestRatioPepSequences(highestRationPeptideSequences);
        solrCluster.setProjectAssays(projectAssays);
        solrCluster.setProjects(new ArrayList<String>(projects));
        solrCluster.setClusterQuality(clusterDetail.getQuality().toString());

        // Expand species
        solrCluster.setSpeciesNames(new ArrayList<String>(speciesNames));
        solrCluster.setSpeciesAccessions(new ArrayList<String>(speciesAccessions));


        //TODO Review this part
        for (int i = 0; i < speciesAccessions.size(); i++) {
            String speciesAccession = solrCluster.getSpeciesAccessions().get(i);
            List<OntologyTerm> relativeSpecies = ontologyTermSearchService.findAllByDescendant(speciesAccession);
            for (OntologyTerm relativeOntologyTerm : relativeSpecies) {

                speciesDescendantsAccessions.add(relativeOntologyTerm.getAccession());
                speciesDescendantsNames.add(relativeOntologyTerm.getName());
            }

            speciesDescendantsAccessions.add(speciesAccession);
        }

        speciesDescendantsNames.addAll(solrCluster.getSpeciesNames());

        solrCluster.setSpeciesAscendantsAccessions(new ArrayList<String>(speciesDescendantsAccessions));
        solrCluster.setSpeciesAscendantsNames(new ArrayList<String>(speciesDescendantsNames));


        // consensus spectrum
        setConsensusSpectrum(solrCluster, clusterDetail);

        return solrCluster;

    }

    private void setConsensusSpectrum(SolrCluster solrCluster, ClusterSummary repoCluster) {

        // get the peaks as a string and split them
        String[] peaksMz = repoCluster.getConsensusSpectrumMz().split(",");
        String[] peaksIntensities = repoCluster.getConsensusSpectrumIntensity().split(",");
        double[] mzStats = new double[peaksMz.length];
        double[] intensityStats = new double[peaksIntensities.length];

        // iterate and build peak lists while building stats
        int i = 0;
        solrCluster.setConsensusSpectrumMz(new LinkedList<Double>());
        solrCluster.setConsensusSpectrumIntensity(new LinkedList<Double>());
        for (String peak : peaksMz) {
            double mzValue = Double.parseDouble(peak);
            solrCluster.getConsensusSpectrumMz().add(mzValue);
            mzStats[i] = mzValue;

            double intensityValue = Double.parseDouble(peaksIntensities[i]);
            solrCluster.getConsensusSpectrumIntensity().add(intensityValue);
            intensityStats[i] = intensityValue;

            i++;
        }

        // set statistics
        double[] mzValues = LowResUtils.toLowResByBucketMean(mzStats, this.lowResSize);
        solrCluster.setConsensusSpectrumMzMeans(mzValues);
        solrCluster.setConsensusSpectrumMzSem(StatUtils.variance(mzStats, StatUtils.mean(mzStats)) / mzStats.length);

        double[] intensityValues = LowResUtils.toLowResByBucketMean(intensityStats, this.lowResSize);
        solrCluster.setConsensusSpectrumIntensityMeans(intensityValues);
        solrCluster.setConsensusSpectrumIntensitySem(StatUtils.variance(intensityStats, StatUtils.mean(intensityStats)) / intensityStats.length);

    }


}