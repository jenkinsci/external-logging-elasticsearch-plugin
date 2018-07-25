package io.jenkins.plugins.extlogging.elasticsearch.util;

import hudson.console.ConsoleNote;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

//TODO: Move to External Logging API
/**
 * Utilities for extracting and reinserting {@link ConsoleNote}s.
 * @author Jesse Glick
 * @author Oleg Nenashev
 */
@Restricted(Beta.class)
public class JSONConsoleNotes {

    private static final String MESSAGE_KEY = "message";
    private static final String ANNOTATIONS_KEY = "annotations";
    private static final String POSITION_KEY = "position";
    private static final String NOTE_KEY = "note";

    public static void parseToJSON(byte[] b, int len, Charset charset, JSONObject dest) {
        assert len > 0 && len <= b.length;
        int eol = len;
        while (eol > 0) {
            byte c = b[eol - 1];
            if (c == '\n' || c == '\r') {
                eol--;
            } else {
                break;
            }
        }
        String line = new String(b, 0, eol, charset);
        parseToJSON(line, dest);
    }

    public static void parseToJSON(String line, JSONObject dest) {

        // Would be more efficient to do searches at the byte[] level, but too much bother for now,
        // especially since there is no standard library method to do offset searches like String has.
        if (!line.contains(ConsoleNote.PREAMBLE_STR)) {
            // Shortcut for the common case that we have no notes.
            dest.put(MESSAGE_KEY, line);
        } else {
            StringBuilder buf = new StringBuilder();
            JSONArray annotations = new JSONArray();
            int pos = 0;
            while (true) {
                int preamble = line.indexOf(ConsoleNote.PREAMBLE_STR, pos);
                if (preamble == -1) {
                    break;
                }
                int endOfPreamble = preamble + ConsoleNote.PREAMBLE_STR.length();
                int postamble = line.indexOf(ConsoleNote.POSTAMBLE_STR, endOfPreamble);
                if (postamble == -1) {
                    // Malformed; stop here.
                    break;
                }
                buf.append(line, pos, preamble);

                JSONObject annotation = new JSONObject();
                annotation.put(POSITION_KEY, buf.length());
                annotation.put(NOTE_KEY, line.substring(endOfPreamble, postamble));
                annotations.add(annotation);
                pos = postamble + ConsoleNote.POSTAMBLE_STR.length();
            }

            buf.append(line, pos, line.length()); // append tail
            dest.put(MESSAGE_KEY, buf.toString());
            dest.put(ANNOTATIONS_KEY, annotations);
        }
    }

    public static void jsonToMessage(Writer w, JSONObject json) throws IOException {
        String message = json.getString(MESSAGE_KEY);
        JSONArray annotations = json.optJSONArray(ANNOTATIONS_KEY);
        if (annotations == null) {
            w.write(message);
        } else {
            int pos = 0;
            for (Object o : annotations) {
                JSONObject annotation = (JSONObject) o;
                int position = annotation.getInt(POSITION_KEY);
                String note = annotation.getString(NOTE_KEY);
                w.write(message, pos, position - pos);
                w.write(ConsoleNote.PREAMBLE_STR);
                w.write(note);
                w.write(ConsoleNote.POSTAMBLE_STR);
                pos = position;
            }
            w.write(message, pos, message.length() - pos);
        }
        w.write('\n');
    }

    private JSONConsoleNotes() {}

}