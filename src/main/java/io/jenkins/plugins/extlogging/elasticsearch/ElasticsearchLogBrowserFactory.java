package io.jenkins.plugins.extlogging.elasticsearch;

import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.extlogging.api.ExternalLogBrowser;
import io.jenkins.plugins.extlogging.api.ExternalLogBrowserFactory;
import io.jenkins.plugins.extlogging.api.ExternalLogBrowserFactoryDescriptor;
import jenkins.model.logging.Loggable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;

/**
 * Produces {@link ElasticsearchLogBrowser}s.
 * @author Oleg Nenashev
 * @since TODO
 */
public class ElasticsearchLogBrowserFactory extends ExternalLogBrowserFactory {

    @DataBoundConstructor
    public ElasticsearchLogBrowserFactory() {

    }

    @CheckForNull
    @Override
    public ExternalLogBrowser create(Loggable loggable) {
        if (loggable instanceof Run<?, ?>) {
            return new ElasticsearchLogBrowser((Run<?, ?>) loggable);
        }
        return null;
    }

    @Extension
    @Symbol("elasticsearch")
    public static class DescriptorImpl extends ExternalLogBrowserFactoryDescriptor {

    }
}
