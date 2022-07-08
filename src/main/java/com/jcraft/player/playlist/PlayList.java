package com.jcraft.player.playlist;

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
                String playListLine = PlayListUtils.readline(playlistInputStream);
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

  

}
