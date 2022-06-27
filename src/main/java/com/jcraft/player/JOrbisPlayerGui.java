package com.jcraft.player;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** JOrbis player GUI. */
public class JOrbisPlayerGui extends JFrame implements ActionListener, JOrbisPlayerContext {

    /** Serial tag. */
    private static final long serialVersionUID = 8503266743761444447L;

    /** Logger of the class. */
    private static final Logger LOG = LogManager.getLogger(JOrbisPlayerGui.class);

    /** Start label to use on start/stop button. */
    private static final String START_LABEL_BUTTON = "start";

    /** Stop label to use on start/stop button. */
    private static final String STOP_LABEL_BUTTON = "stop";

    /** Stats. */
    public static final boolean icestats = false;

    /** The main panel. */
    private JPanel panel;

    /** A combo box used to select sound files. */
    private JComboBox<String> comboBox;

    /** Button for starting/stop playing. */
    private JButton startButton;

    /** A button to display stats. */
    private JButton statsButton;

    /** The playlist. */
    private PlayList playlist;

    /** The associated JorbisPlayer. */
    private JOrbisPlayer player;

    /**
     * Constructor of the player UI.
     * 
     * @param playListArgs the playlist entries
     */
    public JOrbisPlayerGui(List<String> playListArgs) {
        super("JOrbisPlayer");

        playlist = new PlayList(playListArgs);

        player = new JOrbisPlayer(this);

        panel = new JPanel();

        setContentPane(panel);

        comboBox = new JComboBox<>(playlist.getVector());
        comboBox.setEditable(true);
        panel.add(comboBox);

        startButton = new JButton(START_LABEL_BUTTON);
        startButton.addActionListener(this);
        panel.add(startButton);

        if (icestats) {
            statsButton = new JButton("IceStats");
            statsButton.addActionListener(this);
            panel.add(statsButton);
        }

        setBackground(Color.lightGray);
        setBackground(Color.white);

        pack();

    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getSource() == getStatsButton()) {
            stats();
            return;
        }

        String command = ((JButton) (e.getSource())).getText();
        if (command.equals(START_LABEL_BUTTON)) {

            startPlay();
        } else {

            stopPlay();
        }
    }

    private void startPlay() {

        if (player.hasActivePlayerThread()) {
            return;
        }

        String item = getSelectedItem();
        int currentIndex = getIndexOfItem(item);

        if (player.playSound()) {
            startButton.setText(STOP_LABEL_BUTTON);
        }
    }
    
    private void stopPlay() {

        if (player.hasActivePlayerThread()) {

            if (player.stopSound()) {
                startButton.setText(START_LABEL_BUTTON);
            }
        }

    }

    private void stats() {
        String item = getSelectedItem();
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
        return;

    }

    /** @return the playlist */
    public PlayList getPlayList() {
        return playlist;
    }

    public String getSelectedItem() {
        return (String) comboBox.getSelectedItem();
    }

    @Override
    public String getCurrentItem() {
        return getSelectedItem();
    }

    public String getItemAtIndex(int currentIndex) {
        return comboBox.getItemAt(currentIndex);
    }

    public void setSelectedIndex(int currentIndex) {
        comboBox.setSelectedIndex(currentIndex);
    }

    public int getItemCount() {
        return comboBox.getItemCount();
    }

    @Override
    public void next() {
        LOG.debug("Advance in playlist");
        int currentIndex = getIndexOfItem(getSelectedItem()) + 1;
        if (currentIndex >= getItemCount()) {

            LOG.debug("End of playlist restart at beginning");
            currentIndex = 0;
        }

        setSelectedIndex(currentIndex);
    }

    public int getIndexOfItem(String item) {
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            String foo = comboBox.getItemAt(i);
            if (item.equals(foo)) {
                return i;
            }
        }
        comboBox.addItem(item);
        return comboBox.getItemCount() - 1;
    }

    public JButton getStatsButton() {
        return statsButton;
    }

    public void addItem(String item) {
        comboBox.addItem(item);
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

    @Override
    public void handleEndOfPlay() {
        startButton.setText(START_LABEL_BUTTON);
    }

}
