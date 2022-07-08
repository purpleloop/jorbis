package com.jcraft.player.playlist;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Utilities on playLists. */
public final class PlayListUtils {

    /** Logger of the class. */
    private static final Logger LOG = LogManager.getLogger(PlayListUtils.class);

    /** Empty constructor. */
    private PlayListUtils() {
        // Noop
    }

    /**
     * Fetches items to play from a 'pls' file.
     * 
     * @param pls the pls file
     */
    public static String fetchPls(String pls) {
        InputStream pstream = null;
        if (pls.startsWith("http://")) {
            try {

                LOG.debug("Opening url {}", pls);
                URL url = new URL(pls);

                URLConnection urlc = url.openConnection();
                pstream = urlc.getInputStream();
            } catch (Exception ee) {
                LOG.error("Exception while opening url", ee);
                return null;
            }
        }
        if (pstream == null) {
            try {

                LOG.debug("Opening file {}", pls);

                pstream = new FileInputStream(System.getProperty("user.dir")
                        + System.getProperty("file.separator") + pls);
            } catch (Exception ee) {
                LOG.error("Exception while opening file stream", ee);
                return null;
            }
        }

        String line = null;
        while (true) {
            try {
                line = readline(pstream);
            } catch (Exception e) {
                LOG.error("Error reading pls file", e);
            }
            if (line == null) {
                break;
            }
            if (line.startsWith("File1=")) {
                byte[] foo = line.getBytes();
                int i = 6;
                for (; i < foo.length; i++) {
                    if (foo[i] == 0x0d) {
                        break;
                    }
                }
                return line.substring(6, i);
            }
        }
        return null;
    }

    /**
     * Fetches items to play from a 'm3u' file.
     * 
     * @param m3u the m3u file
     */
    public static String fetchM3u(String m3u) {
        InputStream pstream = null;
        if (m3u.startsWith("http://")) {
            try {
                URL url = new URL(m3u);

                URLConnection urlc = url.openConnection();
                pstream = urlc.getInputStream();
            } catch (Exception ee) {
                LOG.error("Exception while reading url", ee);
                return null;
            }
        }
        if (pstream == null) {
            try {
                pstream = new FileInputStream(System.getProperty("user.dir")
                        + System.getProperty("file.separator") + m3u);
            } catch (Exception ee) {
                LOG.error("Exception while opening file", ee);
                return null;
            }
        }

        String line = null;
        while (true) {
            try {
                line = readline(pstream);
            } catch (Exception e) {
            }
            if (line == null) {
                break;
            }
            return line;
        }
        return null;
    }

    /**
     * Read a line froon a string.
     * 
     * @param is the stream
     * @return read line, or null
     */
    public static String readline(InputStream is) {
        StringBuffer rtn = new StringBuffer();
        int temp;
        do {
            try {
                temp = is.read();
            } catch (Exception e) {
                return (null);
            }
            if (temp == -1) {
                String str = rtn.toString();
                if (str.length() == 0) {
                    return (null);
                }
                return str;
            }
            if (temp != 0 && temp != '\n' && temp != '\r') {
                rtn.append((char) temp);
            }
        } while (temp != '\n' && temp != '\r');
        return (rtn.toString());
    }

}
