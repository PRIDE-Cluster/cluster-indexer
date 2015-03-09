package uk.ac.ebi.pride.cluster.indexer;

import org.apache.commons.math.stat.StatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.pride.cluster.search.model.SolrCluster;
import uk.ac.ebi.pride.cluster.search.service.IClusterIndexService;
import uk.ac.ebi.pride.cluster.search.service.IClusterSearchService;
import uk.ac.ebi.pride.cluster.search.util.LowResUtils;
import uk.ac.ebi.pride.cluster.wsclient.client.cluster.ClusterWsClient;
import uk.ac.ebi.pride.cluster.wsclient.model.cluster.Cluster;
import uk.ac.ebi.pride.cluster.wsclient.model.cluster.ClusterSearchResults;
import uk.ac.ebi.pride.cluster.wsclient.model.spectrum.Spectrum;
import uk.ac.ebi.pride.cluster.wsclient.model.spectrum.SpectrumPeak;
import uk.ac.ebi.pride.spectracluster.repo.utils.paging.Page;

import java.io.IOException;
import java.util.*;

/**
 * @author jadianes <jadianes@gmail.com>
 */
public class ClusterIndexerWs implements IClusterIndexer {

    private static final int PAGE_SIZE = 500;

    private static Logger logger = LoggerFactory.getLogger(ClusterIndexerWs.class);

    private IClusterSearchService clusterSearchService;
    private IClusterIndexService clusterIndexService;
    private ClusterWsClient clusterWsClient;
    private int lowResSize = 20;

    public ClusterIndexerWs(IClusterSearchService clusterSearchService,
                            IClusterIndexService clusterIndexService,
                            ClusterWsClient clusterWsClient,
                            int lowResSize) {
        this.clusterSearchService = clusterSearchService;
        this.clusterIndexService = clusterIndexService;
        this.clusterWsClient = clusterWsClient;

        this.lowResSize = lowResSize;
    }

    @Override
    public void indexCluster(long clusterId) {
        SolrCluster cluster = null;
        try {
            cluster = asSolrCluster(clusterWsClient.get("" + clusterId));
            clusterIndexService.save(cluster);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void indexClusters(Set<Long> clusterIds) {
        for (Long clusterId: clusterIds) {
            indexCluster(clusterId);
        }
    }

    @Override
    public void indexAllClusters() {
        clusterIndexService.deleteAll();
        indexNonExistingClusters();
    }

    @Override
    public void indexNonExistingClusters() {
        List<SolrCluster> clusters = new ArrayList<SolrCluster>(PAGE_SIZE);
        long count = 0;
        int page = 0;
        long numClusters = 0;
        try {
            numClusters = clusterWsClient.totalSearchResults("");
            long startTime;
            long endTime;

            // add all CLUSTERs to index
            startTime = System.currentTimeMillis();

            Page<Long> clusterIds;
            while (count < numClusters) {

                ClusterSearchResults clusterSearchResults = clusterWsClient.search("", page++, PAGE_SIZE);

                for (Cluster cluster : clusterSearchResults.results) {
                    if (!clusterSearchService.existsCluster(Long.parseLong(cluster.id))) {
                        clusters.add(asSolrCluster(cluster));
                    } else {
                        logger.info("Cluster " + cluster.id + " already in the index. SKIPPING...");
                    }
                }
                clusterIndexService.save(clusters);
                logger.debug("COMMITTED " + clusters.size() + " clusters.");

                count += clusterSearchResults.results.length;
            }

            endTime = System.currentTimeMillis();
            logger.info("DONE indexing all CLUSTERs in " + (double) (endTime - startTime) / 1000.0 + " seconds");
        } catch (IOException e) {
            e.printStackTrace();
        }


    }


    private SolrCluster asSolrCluster(Cluster cluster) throws IOException {
        SolrCluster solrCluster = new SolrCluster();

        solrCluster.setId(Long.parseLong(cluster.id));
        List<String> highestRatioPepSequences = new LinkedList<String>();
        highestRatioPepSequences.add(cluster.peptideSequence);
        solrCluster.setHighestRatioPepSequences(highestRatioPepSequences);
        List<String> highestRatioProteinAccessions = new LinkedList<String>();
        highestRatioProteinAccessions.add(cluster.proteinAccession);
        solrCluster.setHighestRatioProteinAccessions(highestRatioProteinAccessions);
        solrCluster.setAveragePrecursorCharge(cluster.averagePrecursorCharge);
        solrCluster.setAveragePrecursorMz(cluster.averagePrecursorMz);
        solrCluster.setClusterQuality(cluster.clusterQuality);
        solrCluster.setMaxRatio(cluster.maxRatio);
        solrCluster.setNumberOfSpectra(cluster.numberOfSpectra);
        Map<String, List<String>> projectAssays = new HashMap<String, List<String>>();
        List<String> assays = new LinkedList<String>();
        assays.add("dummy-assay-accession");
        projectAssays.put("dummy-project-accession",assays);
        solrCluster.setProjectAssays(projectAssays);

        // set consensus spectrum
        Spectrum consensusSpectrum = this.clusterWsClient.consensus(cluster.id);

        // consensus spectrum
        setConsensusSpectrum(solrCluster, consensusSpectrum);

        return solrCluster;
    }

    private void setConsensusSpectrum(SolrCluster solrCluster, Spectrum consensusSpectrum) {

        double[] mzStats = new double[consensusSpectrum.peaks.length];
        double[] intensityStats = new double[consensusSpectrum.peaks.length];

        // iterate and build peak lists while building stats
        int i = 0;
        solrCluster.setConsensusSpectrumMz(new LinkedList<Double>());
        solrCluster.setConsensusSpectrumIntensity(new LinkedList<Double>());
        for (SpectrumPeak peak : consensusSpectrum.peaks) {
            solrCluster.getConsensusSpectrumMz().add(peak.mz);
            mzStats[i] = peak.mz;

            solrCluster.getConsensusSpectrumIntensity().add(peak.intensity);
            intensityStats[i] = peak.intensity;

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
