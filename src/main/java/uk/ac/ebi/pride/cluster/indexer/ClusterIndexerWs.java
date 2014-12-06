package uk.ac.ebi.pride.cluster.indexer;

import uk.ac.ebi.pride.cluster.search.model.ClusterQuality;
import uk.ac.ebi.pride.cluster.search.model.SolrCluster;
import uk.ac.ebi.pride.cluster.search.service.IClusterIndexService;
import uk.ac.ebi.pride.cluster.search.service.IClusterSearchService;
import uk.ac.ebi.pride.cluster.wsclient.client.cluster.ClusterWsClient;
import uk.ac.ebi.pride.cluster.wsclient.model.cluster.Cluster;

import java.io.IOException;
import java.util.Set;

/**
 * @author jadianes <jadianes@gmail.com>
 */
public class ClusterIndexerWs implements IClusterIndexer {

    private IClusterSearchService clusterSearchService;
    private IClusterIndexService clusterIndexService;
    private ClusterWsClient clusterWsClient;

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

    }

    @Override
    public void indexNonExistingClusters() {

    }


    private SolrCluster asSolrCluster(Cluster cluster) {
        SolrCluster solrCluster = new SolrCluster();

        solrCluster.setId(Long.parseLong(cluster.id));
        solrCluster.setAveragePrecursorCharge(cluster.averagePrecursorCharge);
        solrCluster.setAveragePrecursorMz(cluster.averagePrecursorMz);
        solrCluster.setClusterQuality(ClusterQuality.valueOf(cluster.clusterQuality));
        solrCluster.setMaxRatio(cluster.maxRatio);
        solrCluster.setNumberOfSpectra(cluster.numberOfSpectra);

        return solrCluster;
    }

}
