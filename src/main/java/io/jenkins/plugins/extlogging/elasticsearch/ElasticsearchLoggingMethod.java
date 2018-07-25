package io.jenkins.plugins.extlogging.elasticsearch;


import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import io.jenkins.plugins.extlogging.api.ExternalLoggingEventWriter;
import io.jenkins.plugins.extlogging.api.OutputStreamWrapper;
import io.jenkins.plugins.extlogging.api.ExternalLoggingMethod;
import io.jenkins.plugins.extlogging.api.impl.ExternalLoggingOutputStream;
import io.jenkins.plugins.extlogging.elasticsearch.util.ElasticSearchDao;
import jenkins.model.logging.LogBrowser;
import jenkins.model.logging.Loggable;

import javax.annotation.CheckForNull;

/**
 * Perform logging to Elasticsearch.
 *
 * @author Oleg Nenashev
 */
public class ElasticsearchLoggingMethod extends ExternalLoggingMethod {

    @CheckForNull
    private final String prefix;

    public ElasticsearchLoggingMethod(Loggable loggable, @CheckForNull String prefix) {
        super(loggable);
        this.prefix = prefix;
    }

    @Override
    public LogBrowser getDefaultLogBrowser() {
        return new ElasticsearchLogBrowser(getOwner());
    }

    @Override
    protected ExternalLoggingEventWriter _createWriter() throws IOException {
        ElasticSearchDao dao = ElasticsearchGlobalConfiguration.getInstance().toDao();
        return new ElasticsearchEventWriter(prefix, dao);
    }

   // @Override
    //public OutputStream decorateLogger(OutputStream logger) {
      //  LogstashWriter logstash = new LogstashWriter(run, TaskListener.NULL, logger, prefix);
      //  RemoteLogstashOutputStream los = new RemoteLogstashOutputStream(logstash, "prefix");
      //  return los.maskPasswords(SensitiveStringsProvider.getAllSensitiveStrings(run));
        // TODO: implement
    //    return null;
    //}

    private static class LogstashOutputStreamWrapper implements OutputStreamWrapper {

        private final ElasticsearchEventWriter wr;
        private final List<String> passwordStrings;

        public LogstashOutputStreamWrapper(ElasticsearchEventWriter wr, List<String> passwordStrings, String prefix) {
            this.wr = wr;
            this.passwordStrings = passwordStrings;
        }

        public Object readResolve() {
            return ExternalLoggingOutputStream.createOutputStream(wr, passwordStrings);
        }

        @Override
        public OutputStream toSerializableOutputStream() {
            return ExternalLoggingOutputStream.createOutputStream(wr, passwordStrings);
        }
    }

}