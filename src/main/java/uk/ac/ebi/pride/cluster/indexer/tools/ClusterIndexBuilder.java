package uk.ac.ebi.pride.cluster.indexer.tools;

import org.apache.solr.client.solrj.SolrServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;
import uk.ac.ebi.pride.archive.ontology.search.service.OntologyTermSearchService;
import uk.ac.ebi.pride.cluster.indexer.ClusterIndexer;
import uk.ac.ebi.pride.cluster.search.service.ClusterIndexService;
import uk.ac.ebi.pride.cluster.search.service.ClusterSearchService;

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
    private ClusterIndexService clusterIndexService;

    @Autowired
    private ClusterSearchService clusterSearchService;

    /*
    HttpSolrServer is thread-safe and if you are using the following constructor,
    you *MUST* re-use the same instance for all requests.  If instances are created on
    the fly, it can cause a connection leak. The recommended practice is to keep a
    static instance of HttpSolrServer per solr server url and share it for all requests.
    See https://issues.apache.org/jira/browse/SOLR-861 for more details
    */

    @Autowired
    private SolrServer solrClusterServer;

    public static void main(String[] args) {
        ApplicationContext context = new ClassPathXmlApplicationContext("spring/app-context.xml");

        ClusterIndexBuilder clusterIndexBuilder = context.getBean(ClusterIndexBuilder.class);
        indexClusters(clusterIndexBuilder);

    }

    public static void indexClusters(ClusterIndexBuilder clusterIndexBuilder) {

        ClusterIndexer clusterIndexer = new ClusterIndexer(
                clusterIndexBuilder.clusterSearchService,
                clusterIndexBuilder.clusterIndexService
        );

        clusterIndexer.indexAllClusters();
    }
}
