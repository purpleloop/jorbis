package com.jcraft.player;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Represents a playlist. */
public class PlayList {

    /** Logger of the class. */
    private static final Logger LOG = LogManager.getLogger(PlayList.class);

    /** Extension for M3U playlist files. */
    public static final String M3U_EXTENSION = ".m3u";

    /** Extension for PLS playlist files. */
    public static final String PLS_EXTENSION = ".pls";

    String playlistfile = "playlist";

    private Vector<String> playlist = new Vector<>();

    /**
     * Creates a playlist.
     * 
     * @param playListArgs playlist startup elements
     */
    public PlayList(List<String> playListArgs) {

        for (String playListArg : playListArgs) {
            playlist.add(playListArg);
        }

        if (playlistfile == null) {
            return;
        }

        try {
            InputStream playlistInputStream = null;
            try {
                URL url = new URL(playlistfile);

                URLConnection urlc = url.openConnection();
                playlistInputStream = urlc.getInputStream();
            } catch (Exception ee) {
            }
            if (playlistInputStream == null) {
                try {
                    playlistInputStream = new FileInputStream(System.getProperty("user.dir")
                            + System.getProperty("file.separator") + playlistfile);
                } catch (Exception ee) {
                }
            }

            if (playlistInputStream == null) {
                return;
            }

            while (true) {
                String playListLine = readline(playlistInputStream);
                if (playListLine == null) {
                    break;
                }
                byte[] foo = playListLine.getBytes();
                for (int i = 0; i < foo.length; i++) {
                    if (foo[i] == 0x0d) {
                        playListLine = new String(foo, 0, i);
                        break;
                    }
                }
                playlist.add(playListLine);
            }
        } catch (Exception e) {
            LOG.error("Exception setting urls", e);
        }
    }

    public void add(String string) {
        playlist.addElement(string);
    }

    public Vector<String> getVector() {
        return playlist;
    }

    private String readline(InputStream is) {
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

    String fetchPls(String pls) {
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

    String fetchM3u(String m3u) {
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

}
