
package io.jenkins.plugins.extlogging.logstash;

import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.extlogging.api.Event;
import io.jenkins.plugins.extlogging.api.ExternalLoggingEventWriter;
import io.jenkins.plugins.extlogging.elasticsearch.util.ElasticSearchDao;
import io.jenkins.plugins.extlogging.elasticsearch.util.JSONConsoleNotes;
import jenkins.model.Jenkins;

import net.sf.json.JSONObject;
import org.apache.commons.lang.exception.ExceptionUtils;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RemoteLogstashWriter extends ExternalLoggingEventWriter {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(RemoteLogstashWriter.class.getName());

    @CheckForNull
    private final String prefix;
    @Nonnull
    private final ElasticSearchDao dao;
    private boolean connectionBroken;

    public RemoteLogstashWriter(@CheckForNull String prefix,
                                @Nonnull ElasticSearchDao dao) {
        this.prefix = prefix;
        this.dao = dao;
    }

    @Override
    public void writeMessage(String message) throws IOException {
        super.writeMessage(prefix != null ? prefix + message : message);
    }

    @Override
    public void writeEvent(Event event) {
        JSONObject payload = new JSONObject();
        JSONConsoleNotes.parseToJSON(event.getMessage(), payload);
        // TODO: replace Dao implementation by an independent one
        JSONObject data = payload.getJSONObject("data");
        for (Map.Entry<String, Serializable> entry : event.getData().entrySet()) {
            Serializable value = entry.getValue();
            data.put(entry.getKey(), value != null ? value.toString() : null);
        }

        try {
            dao.push(payload.toString());
        } catch (IOException e) {
            String msg = "[logstash-plugin]: Failed to send log data to " + dao.getDescription() + ".\n"
                    + "[logstash-plugin]: No Further logs will be sent to " + dao.getDescription() + ".\n"
                    + ExceptionUtils.getStackTrace(e);
            logErrorMessage(msg);
        }
    }

    /**
     * @return True if errors have occurred during initialization or write.
     */
    public boolean isConnectionBroken() {
        return connectionBroken || dao == null;
    }

    /**
     * Write error message to errorStream and set connectionBroken to true.
     */
    private void logErrorMessage(String msg) {
        connectionBroken = true;
        LOGGER.log(Level.WARNING, msg);
    }

    @Override
    public void flush() throws IOException {
        // no caching, nothing to do here
    }

    @Override
    public void close() throws IOException {
        // dao handles it
    }
}
