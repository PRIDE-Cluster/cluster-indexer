package uk.ac.ebi.pride.cluster.indexer;

import java.util.Set;

/**
 * @author jadianes <jadianes@gmail.com>
 */
public interface IClusterIndexer {

    void indexCluster(long clusterId);
    void indexClusters(Set<Long> clusterIds);
    void indexAllClusters();
    void indexNonExistingClusters();

}
