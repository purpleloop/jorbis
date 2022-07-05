package com.jcraft.player;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** JOrbis player GUI. */
public class JOrbisPlayerGui extends JFrame implements JOrbisPlayerContext {

    /** Serial tag. */
    private static final long serialVersionUID = 8503266743761444447L;

    /** Logger of the class. */
    private static final Logger LOG = LogManager.getLogger(JOrbisPlayerGui.class);

    /** Start label to use on start/stop button. */
    private static final String START_LABEL_BUTTON = "start";

    /** Start label to use on start/stop button. */
    private static final String STOP_LABEL_BUTTON = "stop";

    /** The state label to use on stats button. */
    private static final String STATS_LABEL_BUTTON = "IceStats";

    /** Stats. */
    public static final boolean icestats = false;

    /** The main panel. */
    private JPanel mainPanel;

    /** A combo box used to select sound files. */
    private JComboBox<String> comboBox;

    /** Button for starting/stop playing. */
    private JButton startStopButton;

    /** A button to display stats. */
    private JButton statsButton;

    /** The playlist. */
    private PlayList playlist;

    /** The associated JorbisPlayer. */
    private JOrbisPlayer player;

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
     * @param playListArgs the playlist entries
     */
    public JOrbisPlayerGui(List<String> playListArgs) {
        super("JOrbisPlayer");

        setBackground(Color.white);

        mainPanel = new JPanel();
        setContentPane(mainPanel);
        
        playlist = new PlayList(playListArgs);

        player = new JOrbisPlayer(this);


        comboBox = new JComboBox<>(playlist.getVector());
        comboBox.setEditable(true);
        mainPanel.add(comboBox);

        startStopButton = new JButton(startAction);
        mainPanel.add(startStopButton);

        if (icestats) {
            statsButton = new JButton(statsAction);
            mainPanel.add(statsButton);
        }


        pack();
    }

    private void stats() {
        String item = getCurrentItem();
        if (!item.startsWith("http://")) {
            // Not an http resource
            return;
        }
        if (item.endsWith(PlayList.PLS_EXTENSION)) {
            item = playlist.fetchPls(item);
            if (item == null) {
                return;
            }
        } else if (item.endsWith(PlayList.M3U_EXTENSION)) {
            item = playlist.fetchM3u(item);
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

    /** @return the playlist */
    public PlayList getPlayList() {
        return playlist;
    }

    @Override
    public String getCurrentItem() {
        return (String) comboBox.getSelectedItem();
    }

    public String getItemAtIndex(int currentIndex) {
        return comboBox.getItemAt(currentIndex);
    }

    public int getItemCount() {
        return comboBox.getItemCount();
    }

    @Override
    public void next() {
        LOG.debug("Advance in playlist");
        int nextIndex = comboBox.getSelectedIndex() + 1;
        if (nextIndex >= getItemCount()) {

            LOG.debug("End of playlist restart at beginning");
            nextIndex = 0;
        }

        comboBox.setSelectedIndex(nextIndex);
    }

    public void addItem(String item) {
        comboBox.addItem(item);
    }

    @Override
    public void handleEndOfPlay() {
        startStopButton.setText(START_LABEL_BUTTON);
    }

    /**
     * Entry point.
     * 
     * @param arg command line arguments
     */
    public static void main(String[] arg) {

        LOG.info("Starting JOrbisPlayer interface");

        // Each command line argument is added to the playlist.
        List<String> playListArgs = new ArrayList<>();
        if (arg.length > 0) {
            for (int i = 0; i < arg.length; i++) {
                playListArgs.add(arg[i]);

            }
        }

        JOrbisPlayerGui jOrbisPlayerFrame = new JOrbisPlayerGui(playListArgs);
        jOrbisPlayerFrame.addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        jOrbisPlayerFrame.setVisible(true);
    }

}
