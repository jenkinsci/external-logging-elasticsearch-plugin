package io.jenkins.plugins.extlogging.elasticsearch;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.AbortException;
import hudson.Extension;
import io.jenkins.plugins.extlogging.elasticsearch.util.ElasticSearchDao;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.Symbol;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Oleg Nenashev
 * @since TODO
 */
@Extension
@Symbol("extLoggingES")
public class ElasticsearchGlobalConfiguration extends GlobalConfiguration {

    @CheckForNull
    private ElasticsearchConfiguration elasticsearch;

    @CheckForNull
    private String key;

    public ElasticsearchGlobalConfiguration() {
        load();
    }

    @CheckForNull
    public static ElasticsearchGlobalConfiguration get() {
        return GlobalConfiguration.all().getInstance(ElasticsearchGlobalConfiguration.class);
    }

    @Nonnull
    public static ElasticsearchGlobalConfiguration getInstance() throws IOException {
        ElasticsearchGlobalConfiguration cfg = get();
        if (cfg == null) {
            throw new IOException("Elasticsearch Ext Logging Global Configuration is not set");
        }
        return cfg;
    }

    @CheckForNull
    public ElasticsearchConfiguration getElasticsearch() {
        return elasticsearch;
    }

    public void setElasticsearch(ElasticsearchConfiguration configuration) {
        this.elasticsearch = configuration;
        save();
    }

    public void setKey(@CheckForNull String key) {
        this.key = key;
        save();
    }

    @CheckForNull
    public String getKey() {
        return key;
    }

    public ElasticSearchDao toDao() throws IOException {
        if (elasticsearch == null) {
            throw new AbortException("Elasticsearch is not configured");
        }
        if (key == null) {
            throw new AbortException("Elasticsearch: Key is not configured");
        }

        URI esURI = null;
        try {
            esURI = new URI(elasticsearch.getUri() + key);
        } catch (URISyntaxException e) {
            throw new IOException("Malformed Elasticsearc URI: " + elasticsearch.getUri(), e);
        }

        String username = null;
        String password = null;
        if (elasticsearch.getCredentialsId() != null) {
            UsernamePasswordCredentials creds = elasticsearch.getCredentialsOrFail();
            username = creds.getUsername();
            password = creds.getPassword().getPlainText();
        }

        return new ElasticSearchDao(esURI, username, password);
    }

    //TODO: Configuration UI & Co

}
