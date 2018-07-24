package io.jenkins.plugins.extlogging.logstash;

import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.OutputStream;
import java.util.List;

import io.jenkins.plugins.extlogging.api.ExternalLoggingEventWriter;
import io.jenkins.plugins.extlogging.api.OutputStreamWrapper;
import io.jenkins.plugins.extlogging.api.ExternalLoggingMethod;
import io.jenkins.plugins.extlogging.api.impl.ExternalLoggingOutputStream;
import io.jenkins.plugins.extlogging.elasticsearch.ElasticsearchLogBrowser;
import jenkins.model.logging.LogBrowser;
import jenkins.plugins.logstash.LogstashConfiguration;
import jenkins.plugins.logstash.persistence.ElasticSearchDao;
import jenkins.plugins.logstash.persistence.LogstashIndexerDao;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Perform logging to {@link LogstashIndexerDao}.
 *
 * @author Oleg Nenashev
 */
public class LogstashDaoLoggingMethod extends ExternalLoggingMethod {

    @CheckForNull
    private final String prefix;

    public LogstashDaoLoggingMethod(Run<?, ?> run, @CheckForNull String prefix) {
        super(run);
        this.prefix = prefix;
    }

    @Override
    protected Run<?, ?> getOwner() {
        return (Run<?, ?>)super.getOwner();
    }

    @Override
    public LogBrowser getDefaultLogBrowser() {
        LogstashIndexerDao dao = LogstashConfiguration.getInstance().getIndexerInstance();
        if (dao instanceof ElasticSearchDao) {
            return new ElasticsearchLogBrowser(getOwner());
        }
        return null;
    }

    @Override
    protected ExternalLoggingEventWriter _createWriter() {
        LogstashIndexerDao dao = LogstashConfiguration.getInstance().getIndexerInstance();
        return new RemoteLogstashWriter(getOwner(), TaskListener.NULL, prefix, dao);
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

        private final RemoteLogstashWriter wr;
        private final List<String> passwordStrings;

        public LogstashOutputStreamWrapper(RemoteLogstashWriter wr, List<String> passwordStrings, String prefix) {
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