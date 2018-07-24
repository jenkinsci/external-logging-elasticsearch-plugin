package io.jenkins.plugins.extlogging.elasticsearch;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.matchers.IdMatcher;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author Oleg Nenashev
 * @since TODO
 */
public class ElasticsearchConfiguration implements Describable<ElasticsearchConfiguration> {

    @Nonnull
    private final String uri;

    @CheckForNull
    private String credentialsId;

    @DataBoundConstructor
    public ElasticsearchConfiguration(@Nonnull String uri) {
        this.uri = uri;
    }

    @DataBoundSetter
    public void setCredentialsId(@CheckForNull String credentialsId) {
        this.credentialsId = credentialsId;
    }

    @Nonnull
    public String getUri() {
        return uri;
    }

    @CheckForNull
    public String getCredentialsId() {
        return credentialsId;
    }

    @CheckForNull
    public UsernamePasswordCredentials getCredentials() {
        if (credentialsId == null) {
            return null;
        }
        return getCredentials(uri, credentialsId);
    }

    @Nonnull
    public UsernamePasswordCredentials getCredentialsOrFail() throws IOException {
        UsernamePasswordCredentials creds = getCredentials();
        if (creds == null) {
            throw new IOException("Cannot find credentials with id=" + credentialsId);
        }
        return creds;
    }

    @CheckForNull
    private static UsernamePasswordCredentials getCredentials(@Nonnull String uri, @Nonnull String id) {
        //TODO: URL Filter
        UsernamePasswordCredentials credential = null;
        List<UsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentials(
                UsernamePasswordCredentials.class, Jenkins.get(), ACL.SYSTEM, Collections.emptyList());
        IdMatcher matcher = new IdMatcher(id);
        for (UsernamePasswordCredentials c : credentials) {
            if (matcher.matches(c)) {
                credential = c;
            }
        }
        return credential;
    }

    @Override
    public Descriptor<ElasticsearchConfiguration> getDescriptor() {
        return Jenkins.get().getDescriptor(ElasticsearchConfiguration.class);
    }

    @Extension
    @Symbol("elasticsearch")
    public static class DescriptorImpl extends Descriptor<ElasticsearchConfiguration> {
        //TODO: Connection verifucation and other common stuff
    }

    @Nonnull
    @Restricted(NoExternalUse.class)
    public static ElasticsearchConfiguration getOrFail() throws IOException {
        ElasticsearchGlobalConfiguration cfg = ElasticsearchGlobalConfiguration.get();
        ElasticsearchConfiguration esCfg = cfg != null ? cfg.getElasticsearch() : null;
        if (esCfg == null) {
            throw new AbortException("External Logging ES configuration is not set");
        }
        return esCfg;
    }
}
