package io.jenkins.plugins.extlogging.elasticsearch;

import hudson.console.AnnotatedLargeText;
import hudson.model.Run;
import jenkins.model.logging.LogBrowser;

import javax.annotation.CheckForNull;

/**
 * @author Oleg Nenashev
 * @since TODO
 */
public class ElasticsearchLogBrowser extends LogBrowser {

    public ElasticsearchLogBrowser(Run<?,?> run) {
        super(run);
    }

    @Override
    protected Run<?, ?> getOwner() {
        return (Run<?,?>)super.getOwner();
    }

    //TODO: Cache values instead of refreshing them each time
    @Override
    public AnnotatedLargeText overallLog() {
        return new ElasticsearchLogLargeTextProvider(getOwner(), null).getLogText();
    }

    @Override
    public AnnotatedLargeText stepLog(@CheckForNull String stepId, boolean b) {
        return new ElasticsearchLogLargeTextProvider(getOwner(), stepId).getLogText();
    }
}
