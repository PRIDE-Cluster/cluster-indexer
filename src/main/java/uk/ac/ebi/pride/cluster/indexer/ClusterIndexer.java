package uk.ac.ebi.pride.cluster.indexer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.pride.cluster.indexer.util.RepoClusterToSolrClusterMapper;
import uk.ac.ebi.pride.cluster.search.model.Cluster;
import uk.ac.ebi.pride.cluster.search.service.ClusterIndexService;
import uk.ac.ebi.pride.cluster.search.service.ClusterSearchService;
import uk.ac.ebi.pride.spectracluster.repo.dao.IClusterReadDao;

import java.util.LinkedList;
import java.util.List;

/**
 * @author ntoro
 * @since 14/11/14 13:30
 */
public class ClusterIndexer {

    private static Logger logger = LoggerFactory.getLogger(ClusterIndexer.class);

    private ClusterSearchService clusterSearchService;
    private ClusterIndexService clusterIndexService;
    private IClusterReadDao clusterReaderDao;

    public ClusterIndexer(ClusterSearchService clusterSearchService,
                          ClusterIndexService clusterIndexService,
                          IClusterReadDao clusterReaderDao) {
        this.clusterSearchService = clusterSearchService;
        this.clusterIndexService = clusterIndexService;
        this.clusterReaderDao = clusterReaderDao;
    }

    public void indexCluster(long clusterId){

        Cluster cluster = RepoClusterToSolrClusterMapper.asCluster(clusterReaderDao.findCluster(clusterId));
        clusterIndexService.save(cluster);
    }


    private void retrieveCluster() {

    }

    public void indexAllClusters(){
        List<Cluster> clusters = new LinkedList<Cluster>();

        long startTime;
        long endTime;

        // add all CLUSTERs to index
        startTime = System.currentTimeMillis();

        clusterIndexService.save(clusters);
        logger.debug("COMMITTED " + clusters.size() + " clusters.");

        endTime = System.currentTimeMillis();
        logger.info("DONE indexing all CLUSTERs in " + (double) (endTime - startTime) / 1000.0 + " seconds");

    }

    public void deleteAllClusters() {
    //TODO
    }

}
