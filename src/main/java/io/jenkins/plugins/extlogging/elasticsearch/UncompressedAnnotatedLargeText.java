package io.jenkins.plugins.extlogging.elasticsearch;

import hudson.console.AnnotatedLargeText;
import hudson.console.ConsoleAnnotationOutputStream;
import hudson.console.ConsoleAnnotator;
import hudson.remoting.ObjectInputStreamEx;
import jenkins.model.Jenkins;
import jenkins.security.CryptoConfidentialKey;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
//TODO: replace
import com.trilead.ssh2.crypto.Base64;

import static java.lang.Math.abs;

public class UncompressedAnnotatedLargeText<T> extends AnnotatedLargeText<T> {

    private T context;
    private ByteBuffer memory;

    public UncompressedAnnotatedLargeText(ByteBuffer memory, Charset charset, boolean completed, T context) {
        super(memory, charset, completed, context);
        this.context = context;
        this.memory = memory;
    }

    @Override
    public long writeHtmlTo(long start, Writer w) throws IOException {
        ConsoleAnnotationOutputStream caw = new ConsoleAnnotationOutputStream(
                w, createAnnotator(Stapler.getCurrentRequest()), context, charset);
        long r = super.writeLogTo(start, caw);
        caw.flush();
        long initial = memory.length();
        /*
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Cipher sym = PASSING_ANNOTATOR.encrypt();
        ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new CipherOutputStream(baos, sym)));
        oos.writeLong(System.currentTimeMillis()); // send timestamp to prevent a replay attack
        oos.writeObject(caw.getConsoleAnnotator());
        oos.close();
        StaplerResponse rsp = Stapler.getCurrentResponse();
        if (rsp != null) {
            rsp.setHeader("X-ConsoleAnnotator", new String(Base64.encode(baos.toByteArray())));
        }
        return r;
        */

        /*
        try {
            memory.writeTo(caw);
        } finally {
            caw.flush();
            caw.close();
        }*/
        return initial - memory.length();
    }

    /**
    * Used for sending the state of ConsoleAnnotator to the client, because we are deserializing this object later.
    */
    private static final CryptoConfidentialKey PASSING_ANNOTATOR = new CryptoConfidentialKey(AnnotatedLargeText.class,"consoleAnnotator");


    private ConsoleAnnotator createAnnotator(StaplerRequest req) throws IOException {
        try {
            String base64 = req!=null ? req.getHeader("X-ConsoleAnnotator") : null;
            if (base64!=null) {
                Cipher sym = PASSING_ANNOTATOR.decrypt();

                ObjectInputStream ois = new ObjectInputStreamEx(new GZIPInputStream(
                        new CipherInputStream(new ByteArrayInputStream(Base64.decode(base64.toCharArray())),sym)),
                        Jenkins.getInstance().pluginManager.uberClassLoader);
                try {
                    long timestamp = ois.readLong();
                    if (TimeUnit.HOURS.toMillis(1) > abs(System.currentTimeMillis()-timestamp))
                        // don't deserialize something too old to prevent a replay attack
                        return (ConsoleAnnotator)ois.readObject();
                } finally {
                    ois.close();
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IOException(e);
        }
        // start from scratch
        return ConsoleAnnotator.initial(context==null ? null : context.getClass());
    }
}