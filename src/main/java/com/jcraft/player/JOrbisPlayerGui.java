package com.jcraft.player;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Vector;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jcraft.player.playlist.ComboPlayListHolder;
import com.jcraft.player.playlist.PlayListHolder;
import com.jcraft.player.playlist.PlayListUtils;

/** JOrbis player GUI. */
public class JOrbisPlayerGui extends JFrame implements JOrbisPlayerContext {

    /** Serial tag. */
    private static final long serialVersionUID = 8503266743761444447L;

    /** Logger of the class. */
    private static final Logger LOG = LogManager.getLogger(JOrbisPlayerGui.class);

    /** Are stats active ? */
    public static final boolean USE_ICESTATS = false;

    /** Start label to use on start/stop button. */
    private static final String START_LABEL_BUTTON = "start";

    /** Start label to use on start/stop button. */
    private static final String STOP_LABEL_BUTTON = "stop";

    /** The state label to use on stats button. */
    private static final String STATS_LABEL_BUTTON = "IceStats";

    /** The main panel. */
    private JPanel mainPanel;

    /** A combo box used to select sound files. */
    private JComboBox<String> comboBox;

    /** A button for starting/stop playing. */
    private JButton startStopButton;

    /** A button to display stats, when active. */
    private JButton statsButton;

    /** The associated JorbisPlayer. */
    private JOrbisPlayer player;

    /** The playList holder (here, a ComoboBox managed playList). */
    private PlayListHolder playListHolder;

    /** Action for start playing. */
    private Action startAction = new AbstractAction(START_LABEL_BUTTON) {

        /** Serial tag. */
        private static final long serialVersionUID = -6018293552421623165L;

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!player.hasActivePlayerThread() && player.playSound()) {
                startStopButton.setAction(stopAction);
            }
        }

    };

    /** Action for stop playing. */
    private Action stopAction = new AbstractAction(STOP_LABEL_BUTTON) {

        /** Serial tag. */
        private static final long serialVersionUID = -2144568494787570807L;

        @Override
        public void actionPerformed(ActionEvent e) {
            if (player.hasActivePlayerThread() && player.stopSound()) {
                startStopButton.setAction(startAction);
            }
        }

    };

    /** Action for showing statistics. */
    private Action statsAction = new AbstractAction(STATS_LABEL_BUTTON) {

        /** Serial tag. */
        private static final long serialVersionUID = 9084528436238492442L;

        @Override
        public void actionPerformed(ActionEvent e) {
            stats();
        }
    };

    /**
     * Constructor of the player UI.
     * 
     * @param playListArgs the playList entries
     */
    public JOrbisPlayerGui(Vector<String> playListArgs) {
        super("JOrbisPlayer");

        setBackground(Color.white);

        mainPanel = new JPanel();
        setContentPane(mainPanel);

        comboBox = new JComboBox<>();
        comboBox.setEditable(true);
        mainPanel.add(comboBox);

        // Associates the playList with the ComboBox.
        playListHolder = new ComboPlayListHolder(playListArgs, comboBox);

        player = new JOrbisPlayer(this, playListHolder);

        startStopButton = new JButton(startAction);
        mainPanel.add(startStopButton);

        if (USE_ICESTATS) {
            statsButton = new JButton(statsAction);
            mainPanel.add(statsButton);
        }

        pack();
    }

    /** Fetches stats. */
    private void stats() {
        String item = playListHolder.getCurrentItem();
        if (!item.startsWith("http://")) {
            // Not an http resource
            return;
        }
        if (item.endsWith(PlayListUtils.PLS_EXTENSION)) {
            item = PlayListUtils.fetchPls(item);
            if (item == null) {
                return;
            }
        } else if (item.endsWith(PlayListUtils.M3U_EXTENSION)) {
            item = PlayListUtils.fetchM3u(item);
            if (item == null) {
                return;
            }
        }
        byte[] foo = item.getBytes();
        for (int i = foo.length - 1; i >= 0; i--) {
            if (foo[i] == '/') {
                item = item.substring(0, i + 1) + "stats.xml";
                break;
            }
        }
        LOG.info("Selected item is {}.", item);
        try {
            URL url = new URL(item);

            BufferedReader stats = new BufferedReader(
                    new InputStreamReader(url.openConnection().getInputStream()));
            while (true) {
                String bar = stats.readLine();
                if (bar == null) {
                    break;
                }
                LOG.info("bar {}", bar);
            }
        } catch (Exception ee) {
            LOG.error("Error reading urls", ee);
        }

    }

    /** @return the playListHolder */
    public PlayListHolder getPlayListHolder() {
        return playListHolder;
    }

    @Override
    public void handleEndOfPlay() {
        startStopButton.setText(START_LABEL_BUTTON);
    }

    /**
     * Collects the elements for the play list.
     * 
     * @param args plaList elements provided at startup arguments
     */
    public static Vector<String> composePlayList(String[] args) {

        Vector<String> playList = new Vector<>();

        // Each argument is added to the playlist.
        for (String playListArg : args) {
            playList.add(playListArg);
        }

        String playlistfile = "playlist";

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

                    String currentFoderPlaylist = System.getProperty("user.dir")
                            + System.getProperty("file.separator") + playlistfile;
                    playlistInputStream = new FileInputStream(currentFoderPlaylist);
                } catch (Exception ee) {
                }
            }

            if (playlistInputStream != null) {

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(playlistInputStream));) {
                    String playListLine;
                    while ((playListLine = br.readLine()) != null) {
                        playList.add(playListLine);
                    }
                }
            }

        } catch (Exception e) {
            LOG.error("Exception setting urls", e);
        }

        return playList;
    }

    /**
     * Entry point.
     * 
     * @param arg command line arguments
     */
    public static void main(String[] arg) {

        LOG.info("Starting JOrbisPlayer interface");

        JOrbisPlayerGui jOrbisPlayerFrame = new JOrbisPlayerGui(composePlayList(arg));
        jOrbisPlayerFrame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        jOrbisPlayerFrame.setVisible(true);
    }

}
