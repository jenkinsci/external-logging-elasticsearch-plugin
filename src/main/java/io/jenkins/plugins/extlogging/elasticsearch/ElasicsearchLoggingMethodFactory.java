package io.jenkins.plugins.extlogging.elasticsearch;

import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.extlogging.api.ExternalLoggingMethod;
import io.jenkins.plugins.extlogging.api.ExternalLoggingMethodFactory;
import io.jenkins.plugins.extlogging.api.ExternalLoggingMethodFactoryDescriptor;
import jenkins.model.logging.Loggable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;

/**
 * @author Oleg Nenashev
 * @since TODO
 */
public class ElasicsearchLoggingMethodFactory extends ExternalLoggingMethodFactory {

    @CheckForNull
    private String prefix;

    @DataBoundConstructor
    public ElasicsearchLoggingMethodFactory() {

    }

    @DataBoundSetter
    public void setPrefix(@CheckForNull String prefix) {
        this.prefix = prefix;
    }

    @Override
    public ExternalLoggingMethod create(Loggable loggable) {
        if (loggable instanceof Run<?, ?>) {
            return new ElasticsearchLoggingMethod((Run<?, ?>) loggable, prefix);
        }
        return null;
    }

    @Extension
    @Symbol("elasticsearch")
    public static final class DescriptorImpl extends ExternalLoggingMethodFactoryDescriptor {

    }
}
