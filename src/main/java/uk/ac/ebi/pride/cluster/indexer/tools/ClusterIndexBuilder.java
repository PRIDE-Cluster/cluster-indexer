package uk.ac.ebi.pride.cluster.indexer.tools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;
import uk.ac.ebi.pride.archive.ontology.search.service.OntologyTermSearchService;
import uk.ac.ebi.pride.cluster.indexer.ClusterIndexer;
import uk.ac.ebi.pride.cluster.search.service.IClusterIndexService;
import uk.ac.ebi.pride.cluster.search.service.IClusterSearchService;
import uk.ac.ebi.pride.spectracluster.repo.dao.IClusterReadDao;

/**
 * @author ntoro
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

    public static void main(String[] args) {
        ApplicationContext context = new ClassPathXmlApplicationContext("spring/app-context.xml");

        ClusterIndexBuilder clusterIndexBuilder = context.getBean(ClusterIndexBuilder.class);
        indexClusters(clusterIndexBuilder);

        if (args.length>0 && "inc".equals(args[0].toLowerCase())) {
            indexNonExistingClusters(clusterIndexBuilder);
        } else if (args.length>0 && "all".equals(args[0].toLowerCase())) {
            indexClusters(clusterIndexBuilder);
        } else {
            System.out.println("Arguments:");
            System.out.println("   inc   - index cluster not already in the index");
            System.out.println("   all   - deletes the index and index all clusters");
        }

    }

    private static void indexNonExistingClusters(ClusterIndexBuilder clusterIndexBuilder) {

        ClusterIndexer clusterIndexer = new ClusterIndexer(
                clusterIndexBuilder.clusterSearchService,
                clusterIndexBuilder.clusterIndexService,
                clusterIndexBuilder.clusterReadDao,
                clusterIndexBuilder.ontologyTermSearchService
        );

        clusterIndexer.indexNonExistingClusters();
    }

    public static void indexClusters(ClusterIndexBuilder clusterIndexBuilder) {

        ClusterIndexer clusterIndexer = new ClusterIndexer(
                clusterIndexBuilder.clusterSearchService,
                clusterIndexBuilder.clusterIndexService,
                clusterIndexBuilder.clusterReadDao,
                clusterIndexBuilder.ontologyTermSearchService
        );

        clusterIndexer.indexAllClusters();
    }
}
