package io.jenkins.plugins.extlogging.elasticsearch;

import hudson.console.AnnotatedLargeText;
import io.jenkins.plugins.extlogging.elasticsearch.util.ElasticSearchDao;
import jenkins.model.logging.LogBrowser;
import jenkins.model.logging.Loggable;
import jenkins.model.logging.impl.BrokenAnnotatedLargeText;

import javax.annotation.CheckForNull;

/**
 * Log browser for Elasticsearch.
 * @author Oleg Nenashev
 * @since TODO
 */
public class ElasticsearchLogBrowser extends LogBrowser {

    public ElasticsearchLogBrowser(Loggable loggable) {
        super(loggable);
    }

    //TODO: Cache values instead of refreshing them each time
    @Override
    public AnnotatedLargeText overallLog() {
        ElasticSearchDao dao;
        try {
            dao = ElasticsearchConfiguration.getOrFail().toDao();
        } catch (Exception ex) {
            return new BrokenAnnotatedLargeText(ex);
        }

        return new ElasticsearchLogLargeTextProvider(dao, getOwner(), null).getLogText();
    }

    @Override
    public AnnotatedLargeText stepLog(@CheckForNull String stepId, boolean b) {
        ElasticSearchDao dao;
        try {
            dao = ElasticsearchConfiguration.getOrFail().toDao();
        } catch (Exception ex) {
            return new BrokenAnnotatedLargeText(ex);
        }

        return new ElasticsearchLogLargeTextProvider(dao, getOwner(), stepId).getLogText();
    }
}
