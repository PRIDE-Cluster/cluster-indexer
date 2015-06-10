package uk.ac.ebi.pride.cluster.indexer.tools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;
import uk.ac.ebi.pride.archive.ontology.search.service.OntologyTermSearchService;
import uk.ac.ebi.pride.cluster.indexer.ClusterIndexerDB;
import uk.ac.ebi.pride.cluster.indexer.ClusterIndexerWs;
import uk.ac.ebi.pride.cluster.indexer.IClusterIndexer;
import uk.ac.ebi.pride.cluster.search.service.IClusterIndexService;
import uk.ac.ebi.pride.cluster.search.service.IClusterSearchService;
import uk.ac.ebi.pride.cluster.search.service.ISynonymService;
import uk.ac.ebi.pride.cluster.wsclient.client.cluster.ClusterWsClient;
import uk.ac.ebi.pride.cluster.wsclient.config.ClusterWsConfigDev;
import uk.ac.ebi.pride.spectracluster.repo.dao.cluster.IClusterReadDao;

/**
 * @author ntoro
 * @author Jose A. Dianes <jadianes@gmail.com>
 * @since 14/11/14 14:29
 */

@Component
public class ClusterIndexBuilder {


    //Solr Repositories
    @Autowired
    private OntologyTermSearchService ontologyTermSearchService;

    @Autowired
    private IClusterIndexService clusterIndexService;

    @Autowired
    private IClusterSearchService clusterSearchService;

    @Autowired
    private IClusterReadDao clusterReadDao;

    @Autowired
    private ISynonymService synonymService;

    @Autowired
    private ClusterWsConfigDev clusterWsConfigDev;

    private static IClusterIndexer clusterIndexer;

    public static void main(String[] args) {
        ApplicationContext context = new ClassPathXmlApplicationContext("spring/app-context.xml");

        ClusterIndexBuilder clusterIndexBuilder = context.getBean(ClusterIndexBuilder.class);

        if (args.length==3 && "db".equals(args[0].toLowerCase())) {
            clusterIndexer = new ClusterIndexerDB(
                    clusterIndexBuilder.clusterSearchService,
                    clusterIndexBuilder.clusterIndexService,
                    clusterIndexBuilder.clusterReadDao,
                    clusterIndexBuilder.ontologyTermSearchService,
                    clusterIndexBuilder.synonymService,
                    Integer.parseInt(args[2])
            );
        } else if (args.length==3 && "ws".equals(args[0].toLowerCase())) {
            clusterIndexer = new ClusterIndexerWs(
                    clusterIndexBuilder.clusterSearchService,
                    clusterIndexBuilder.clusterIndexService,
                    new ClusterWsClient(clusterIndexBuilder.clusterWsConfigDev),
                    Integer.parseInt(args[2])
            );
        } else {
            System.out.println("Arguments:");
            System.out.println("   [db/ws]   - database OR web service data source");
            System.out.println("   [inc/all]   - incremental OR complete");
            System.out.println("   LOW_RES_SIZE   - a number indicating how many peaks to store for low res cluster version");
            return;
        }

        if (args.length==3 && "inc".equals(args[1].toLowerCase())) {
            clusterIndexer.indexNonExistingClusters();
        } else if (args.length==3 && "all".equals(args[1].toLowerCase())) {
            clusterIndexer.indexAllClusters();
        } else {
            System.out.println("Arguments:");
            System.out.println("   [db/ws]   - database OR web service data source");
            System.out.println("   [inc/all]   - incremental OR complete");
            System.out.println("   LOW_RES_SIZE   - a number indicating how many peaks to store for low res cluster version");
        }

    }

}
