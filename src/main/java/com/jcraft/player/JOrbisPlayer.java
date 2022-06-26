package com.jcraft.player;

/* -*-mode:java; c-basic-offset:2; indent-tabs-mode:nil -*- */
/* JOrbisPlayer -- pure Java Ogg Vorbis player
 *
 * Copyright (C) 2000 ymnk, JCraft,Inc.
 *
 * Written by: 2000 ymnk<ymnk@jcraft.com>
 *
 * Many thanks to 
 *   Monty <monty@xiph.org> and 
 *   The XIPHOPHORUS Company http://www.xiph.org/ .
 * JOrbis has been based on their awesome works, Vorbis codec and
 * JOrbisPlayer depends on JOrbis.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

import java.applet.AppletContext;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.Vector;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

/** JOrbis player. */
public class JOrbisPlayer extends JApplet implements ActionListener, Runnable {

    private static final int SAMPLE_SIZE_IN_BITS = 16;

    /** Logger of the class. */
    private static final Logger LOG = LogManager.getLogger(JOrbisPlayer.class);

    /** Serial tag. */
    private static final long serialVersionUID = 1L;

    /** Buffer size 8 ko. */
    private static final int BUFFER_SIZE = 4096 * 2;

    /** Conversion buffer size (initialized at 16ko). */
    private static int conversionBufferSize = BUFFER_SIZE * 2;

    /** Conversion buffer. */
    private static byte[] conversionBuffer = new byte[conversionBufferSize];

    boolean running_as_applet = true;

    /** The active player thread. */
    private Thread activePlayerThread = null;

    /** The sound bit stream. */
    private InputStream soundBitStream = null;

    int udp_port = -1;
    String udp_baddress = null;

    static AppletContext acontext = null;

    private int RETRY = 3;
    int retry = RETRY;

    String playlistfile = "playlist";

    boolean icestats = false;

    private SyncState syncState;
    private StreamState streamState;
    private Page page;
    private Packet packet;
    private Info info;
    private Comment comment;
    private DspState dspState;
    private Block block;

    private byte[] buffer = null;
    private int bytes = 0;

    int format;
    int rate = 0;
    int channels = 0;
    int left_vol_scale = 100;
    int right_vol_scale = 100;
    SourceDataLine outputLine = null;
    String current_source = null;

    int frameSizeInBytes;
    int bufferLengthInBytes;

    boolean playonstartup = false;

    public void init() {
        running_as_applet = true;

        acontext = getAppletContext();

        String s = getParameter("jorbis.player.playlist");
        playlistfile = s;

        s = getParameter("jorbis.player.icestats");
        if (s != null && s.equals("yes")) {
            icestats = true;
        }

        loadPlaylist();
        initUI();

        if (playlist.size() > 0) {
            s = getParameter("jorbis.player.playonstartup");
            if (s != null && s.equals("yes")) {
                playonstartup = true;
            }
        }

        setBackground(Color.lightGray);
        // setBackground(Color.white);
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(panel);
    }

    public void start() {
        super.start();
        if (playonstartup) {
            play_sound();
        }
    }

    /** Initializes JOrbis fields. */
    private void initJorbis() {
        syncState = new SyncState();
        streamState = new StreamState();
        page = new Page();
        packet = new Packet();

        info = new Info();
        comment = new Comment();
        dspState = new DspState();
        block = new Block(dspState);

        buffer = null;
        bytes = 0;

        syncState.init();
    }

    private SourceDataLine getOutputLine(int channels, int rate) {
        if (outputLine == null || this.rate != rate || this.channels != channels) {
            if (outputLine != null) {
                outputLine.drain();
                outputLine.stop();
                outputLine.close();
            }
            initAudio(channels, rate);
            outputLine.start();
        }
        return outputLine;
    }

    /**
     * Initialize the audio system.
     * 
     * @param sampleRate the sample rate
     * @param channels numbers of channels
     */
    private void initAudio(int channels, int sampleRate) {
        try {

            // PCM Signed and littleEndian
            AudioFormat audioFormat = new AudioFormat(sampleRate, SAMPLE_SIZE_IN_BITS, channels,
                    true, false);
            DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat,
                    AudioSystem.NOT_SPECIFIED);
            if (!AudioSystem.isLineSupported(lineInfo)) {
                LOG.debug("Line {} is not supported.", lineInfo);
                return;
            }

            try {
                outputLine = (SourceDataLine) AudioSystem.getLine(lineInfo);
                // outputLine.addLineListener(this);
                outputLine.open(audioFormat);
            } catch (LineUnavailableException ex) {
                LOG.error("Unable to open the sourceDataLine during audio initialization", ex);
                return;
            } catch (IllegalArgumentException ex) {
                LOG.error("Illegal argument error during audio initialization", ex);
                return;
            }

            frameSizeInBytes = audioFormat.getFrameSize();
            int bufferLengthInFrames = outputLine.getBufferSize() / frameSizeInBytes / 2;
            bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;

            // if(originalClassLoader!=null)
            // Thread.currentThread().setContextClassLoader(originalClassLoader);

            this.rate = sampleRate;
            this.channels = channels;
        } catch (Exception ee) {
            LOG.error("Error during audio initialization", ee);
        }
    }

    private int item2index(String item) {
        for (int i = 0; i < cb.getItemCount(); i++) {
            String foo = (String) (cb.getItemAt(i));
            if (item.equals(foo)) {
                return i;
            }
        }
        cb.addItem(item);
        return cb.getItemCount() - 1;
    }

    public void run() {
        Thread me = Thread.currentThread();
        String item = (String) (cb.getSelectedItem());
        int current_index = item2index(item);
        while (true) {
            item = (String) (cb.getItemAt(current_index));
            cb.setSelectedIndex(current_index);
            soundBitStream = selectSource(item);
            if (soundBitStream != null) {
                if (udp_port != -1) {
                    play_udp_stream(me);
                } else {
                    playStream(me);
                }
            } else if (cb.getItemCount() == 1) {
                break;
            }
            if (activePlayerThread != me) {
                break;
            }
            soundBitStream = null;
            current_index++;
            if (current_index >= cb.getItemCount()) {
                current_index = 0;
            }
            if (cb.getItemCount() <= 0) {
                break;
            }
        }
        activePlayerThread = null;
        start_button.setText("start");
    }

    /**
     * Plays a stream on the playing thread.
     * 
     * @param me the thread
     */
    private void playStream(Thread me) {

        boolean chained = false;

        initJorbis();

        retry = RETRY;

        LOG.info("Playing stream");

        loop: while (true) {
            int eos = 0;

            int index = syncState.buffer(BUFFER_SIZE);
            buffer = syncState.data;
            try {
                bytes = soundBitStream.read(buffer, index, BUFFER_SIZE);
            } catch (Exception e) {
                LOG.error("Error while reading the sound stream into the buffer", e);
                return;
            }
            syncState.wrote(bytes);

            if (chained) {
                chained = false;
            } else {
                if (syncState.pageout(page) != 1) {
                    if (bytes < BUFFER_SIZE) {
                        break;
                    }
                    LOG.error("Input does not appear to be an Ogg bitstream.");
                    return;
                }
            }
            streamState.init(page.serialno());
            streamState.reset();

            info.init();
            comment.init();

            if (streamState.pagein(page) < 0) {
                // error; stream version mismatch perhaps
                LOG.error("Error reading first page of Ogg bitstream data.");
                return;
            }

            retry = RETRY;

            if (streamState.packetout(packet) != 1) {
                // no page? must not be Vorbis
                LOG.error("Error reading initial header packet.");
                break;
                // return;
            }

            if (info.synthesis_headerin(comment, packet) < 0) {
                // error case; not a Vorbis header
                LOG.error("This Ogg bitstream does not contain Vorbis audio data.");
                return;
            }

            int i = 0;

            while (i < 2) {
                while (i < 2) {
                    int result = syncState.pageout(page);
                    if (result == 0) {
                        break; // Need more data
                    }
                    if (result == 1) {
                        streamState.pagein(page);
                        while (i < 2) {
                            result = streamState.packetout(packet);
                            if (result == 0) {
                                break;
                            }
                            if (result == -1) {
                                LOG.error("Corrupt secondary header. Exiting.");

                                break loop;
                            }
                            info.synthesis_headerin(comment, packet);
                            i++;
                        }
                    }
                }

                index = syncState.buffer(BUFFER_SIZE);
                buffer = syncState.data;
                try {
                    bytes = soundBitStream.read(buffer, index, BUFFER_SIZE);
                } catch (Exception e) {
                    LOG.error("Exception while reading from the sound stream", e);
                    return;
                }
                if (bytes == 0 && i < 2) {
                    LOG.error("End of file before finding all Vorbis headers.");
                    return;
                }
                syncState.wrote(bytes);
            }

            {
                byte[][] ptr = comment.user_comments;
                StringBuffer sb = null;
                if (acontext != null) {
                    sb = new StringBuffer();
                }

                for (int j = 0; j < ptr.length; j++) {
                    if (ptr[j] == null) {
                        break;
                    }
                    LOG.info("Comment: " + new String(ptr[j], 0, ptr[j].length - 1));
                    if (sb != null) {
                        sb.append(" " + new String(ptr[j], 0, ptr[j].length - 1));
                    }
                }
                LOG.info("Bitstream is " + info.channels + " channel, " + info.rate + "Hz");
                LOG.info("Encoded by: " + new String(comment.vendor, 0, comment.vendor.length - 1)
                        + "\n");
                if (sb != null) {
                    acontext.showStatus(sb.toString());
                }
            }

            conversionBufferSize = BUFFER_SIZE / info.channels;

            dspState.synthesis_init(info);
            block.init(dspState);

            float[][][] _pcmf = new float[1][][];
            int[] _index = new int[info.channels];

            getOutputLine(info.channels, info.rate);

            while (eos == 0) {
                while (eos == 0) {

                    if (activePlayerThread != me) {
                        try {
                            soundBitStream.close();
                            outputLine.drain();
                            outputLine.stop();
                            outputLine.close();
                            outputLine = null;
                        } catch (Exception ee) {
                        }
                        return;
                    }

                    int result = syncState.pageout(page);
                    if (result == 0) {
                        // need more data
                        break;
                    }
                    if (result == -1) {
                        // missing or corrupt data at this page position
                        LOG.warn("Corrupt or missing data in bitstream; continuing...");
                    } else {
                        streamState.pagein(page);

                        if (page.granulepos() == 0) {
                            chained = true;
                            eos = 1;
                            break;
                        }

                        while (true) {
                            result = streamState.packetout(packet);
                            if (result == 0) {
                                // need more data
                                break;
                            }
                            if (result == -1) {
                                // missing or corrupt data at this page position
                                // no reason to complain; already complained
                                // above

                                // LOG.error("no reason to complain;
                                // already complained above");
                            } else {
                                // we have a packet. Decode it
                                int samples;
                                if (block.synthesis(packet) == 0) {
                                    // test for success!
                                    dspState.synthesis_blockin(block);
                                }
                                while ((samples = dspState.synthesis_pcmout(_pcmf, _index)) > 0) {
                                    float[][] pcmf = _pcmf[0];
                                    int bout = (samples < conversionBufferSize ? samples
                                            : conversionBufferSize);

                                    // convert doubles to 16 bit signed ints
                                    // (host order) and
                                    // interleave
                                    for (i = 0; i < info.channels; i++) {
                                        int ptr = i * 2;
                                        // int ptr=i;
                                        int mono = _index[i];
                                        for (int j = 0; j < bout; j++) {
                                            int val = (int) (pcmf[i][mono + j] * 32767.);
                                            if (val > 32767) {
                                                val = 32767;
                                            }
                                            if (val < -32768) {
                                                val = -32768;
                                            }
                                            if (val < 0)
                                                val = val | 0x8000;
                                            conversionBuffer[ptr] = (byte) (val);
                                            conversionBuffer[ptr + 1] = (byte) (val >>> 8);
                                            ptr += 2 * (info.channels);
                                        }
                                    }
                                    outputLine.write(conversionBuffer, 0, 2 * info.channels * bout);
                                    dspState.synthesis_read(bout);
                                }
                            }
                        }
                        if (page.eos() != 0) {
                            eos = 1;
                        }
                    }
                }

                if (eos == 0) {
                    index = syncState.buffer(BUFFER_SIZE);
                    buffer = syncState.data;
                    try {
                        bytes = soundBitStream.read(buffer, index, BUFFER_SIZE);
                    } catch (Exception e) {
                        LOG.error("Error while reading form the sound stream", e);
                        return;
                    }
                    if (bytes == -1) {
                        break;
                    }
                    syncState.wrote(bytes);
                    if (bytes == 0) {
                        eos = 1;
                    }
                }
            }

            streamState.clear();
            block.clear();
            dspState.clear();
            info.clear();
        }

        syncState.clear();

        try {
            if (soundBitStream != null) {
                soundBitStream.close();
            }
        } catch (Exception e) {
        }
    }

    private void play_udp_stream(Thread me) {
        initJorbis();

        try {
            loop: while (true) {
                int index = syncState.buffer(BUFFER_SIZE);
                buffer = syncState.data;
                try {
                    bytes = soundBitStream.read(buffer, index, BUFFER_SIZE);
                } catch (Exception e) {
                    System.err.println(e);
                    return;
                }

                syncState.wrote(bytes);
                if (syncState.pageout(page) != 1) {
                    // if(bytes<BUFSIZE)break;
                    LOG.error("Input does not appear to be an Ogg bitstream.");
                    return;
                }

                streamState.init(page.serialno());
                streamState.reset();

                info.init();
                comment.init();
                if (streamState.pagein(page) < 0) {
                    // error; stream version mismatch perhaps
                    LOG.error("Error reading first page of Ogg bitstream data.");
                    return;
                }

                if (streamState.packetout(packet) != 1) {
                    // no page? must not be vorbis
                    LOG.error("Error reading initial header packet.");
                    // break;
                    return;
                }

                if (info.synthesis_headerin(comment, packet) < 0) {
                    // error case; not a vorbis header
                    LOG.error("This Ogg bitstream does not contain Vorbis audio data.");
                    return;
                }

                int i = 0;
                while (i < 2) {
                    while (i < 2) {
                        int result = syncState.pageout(page);
                        if (result == 0) {
                            break; // Need more data
                        }
                        if (result == 1) {
                            streamState.pagein(page);
                            while (i < 2) {
                                result = streamState.packetout(packet);
                                if (result == 0) {
                                    break;
                                }
                                if (result == -1) {
                                    LOG.error("Corrupt secondary header.  Exiting.");
                                    // return;
                                    break loop;
                                }
                                info.synthesis_headerin(comment, packet);
                                i++;
                            }
                        }
                    }

                    if (i == 2) {
                        break;
                    }

                    index = syncState.buffer(BUFFER_SIZE);
                    buffer = syncState.data;
                    try {
                        bytes = soundBitStream.read(buffer, index, BUFFER_SIZE);
                    } catch (Exception e) {
                        LOG.error("Error while reading from the sound stream", e);
                        return;
                    }
                    if (bytes == 0 && i < 2) {
                        LOG.error("End of file before finding all Vorbis headers!");
                        return;
                    }
                    syncState.wrote(bytes);
                }
                break;
            }
        } catch (Exception e) {
        }

        try {
            soundBitStream.close();
        } catch (Exception e) {
        }

        UDPIO io = null;
        try {
            io = new UDPIO(udp_port);
        } catch (Exception e) {
            return;
        }

        soundBitStream = io;
        playStream(me);
    }

    public void stop() {
        if (activePlayerThread == null) {
            try {
                outputLine.drain();
                outputLine.stop();
                outputLine.close();
                outputLine = null;
                if (soundBitStream != null) {
                    soundBitStream.close();
                }
            } catch (Exception e) {
                LOG.error("Error on closing line ", e);
            }
        }
        activePlayerThread = null;
    }

    Vector playlist = new Vector();

    public void actionPerformed(ActionEvent e) {

        if (e.getSource() == stats_button) {
            String item = (String) (cb.getSelectedItem());
            if (!item.startsWith("http://")) {
                return;
            }
            if (item.endsWith(".pls")) {
                item = fetch_pls(item);
                if (item == null) {
                    return;
                }
            } else if (item.endsWith(".m3u")) {
                item = fetch_m3u(item);
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
            LOG.info("Selelected item is {}.", item);
            try {
                URL url = null;
                if (running_as_applet) {
                    url = new URL(getCodeBase(), item);
                } else {
                    url = new URL(item);
                }
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

        String command = ((JButton) (e.getSource())).getText();
        if (command.equals("start") && activePlayerThread == null) {
            play_sound();
        } else if (activePlayerThread != null) {
            stopSound();
        }
    }

    public String getTitle() {
        return (String) (cb.getSelectedItem());
    }

    public void play_sound() {
        if (activePlayerThread != null) {
            return;
        }
        activePlayerThread = new Thread(this);
        start_button.setText("stop");
        activePlayerThread.start();
    }

    public void stopSound() {
        if (activePlayerThread == null) {
            return;
        }
        activePlayerThread = null;
        start_button.setText("start");
    }

    InputStream selectSource(String item) {
        if (item.endsWith(".pls")) {
            item = fetch_pls(item);
            if (item == null) {
                return null;
            }
            LOG.info("fetch: {}", item);
        } else if (item.endsWith(".m3u")) {
            item = fetch_m3u(item);
            if (item == null) {
                return null;
            }
            LOG.info("fetch: {}", item);
        }

        if (!item.endsWith(".ogg")) {
            return null;
        }

        InputStream is = null;
        URLConnection urlc = null;
        try {
            URL url = null;
            if (running_as_applet) {
                url = new URL(getCodeBase(), item);
            } else {
                url = new URL(item);
            }
            urlc = url.openConnection();
            is = urlc.getInputStream();
            current_source = url.getProtocol() + "://" + url.getHost() + ":" + url.getPort()
                    + url.getFile();
        } catch (Exception ee) {
            LOG.error("Exception while opening applet url", ee);
        }

        if (is == null && !running_as_applet) {
            try {
                is = new FileInputStream(System.getProperty("user.dir")
                        + System.getProperty("file.separator") + item);
                current_source = null;
            } catch (Exception ee) {
                System.err.println(ee);
            }
        }

        if (is == null) {
            return null;
        }

        LOG.info("Select: {}", item);

        {
            boolean find = false;
            for (int i = 0; i < cb.getItemCount(); i++) {
                String foo = (String) (cb.getItemAt(i));
                if (item.equals(foo)) {
                    find = true;
                    break;
                }
            }
            if (!find) {
                cb.addItem(item);
            }
        }

        int i = 0;
        String s = null;
        String t = null;
        udp_port = -1;
        udp_baddress = null;
        while (urlc != null && true) {
            s = urlc.getHeaderField(i);
            t = urlc.getHeaderFieldKey(i);
            if (s == null) {
                break;
            }
            i++;
            if (t != null && t.equals("udp-port")) {
                try {
                    udp_port = Integer.parseInt(s);
                } catch (Exception ee) {
                    LOG.error("Exception while parsing UDP port", ee);
                }
            } else if (t != null && t.equals("udp-broadcast-address")) {
                udp_baddress = s;
            }
        }
        return is;
    }

    String fetch_pls(String pls) {
        InputStream pstream = null;
        if (pls.startsWith("http://")) {
            try {
                URL url = null;
                if (running_as_applet) {
                    url = new URL(getCodeBase(), pls);
                } else {
                    url = new URL(pls);
                }
                URLConnection urlc = url.openConnection();
                pstream = urlc.getInputStream();
            } catch (Exception ee) {
                LOG.error("Exception while opening url", ee);
                return null;
            }
        }
        if (pstream == null && !running_as_applet) {
            try {
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

    String fetch_m3u(String m3u) {
        InputStream pstream = null;
        if (m3u.startsWith("http://")) {
            try {
                URL url = null;
                if (running_as_applet) {
                    url = new URL(getCodeBase(), m3u);
                } else {
                    url = new URL(m3u);
                }
                URLConnection urlc = url.openConnection();
                pstream = urlc.getInputStream();
            } catch (Exception ee) {
                LOG.error("Exception while reading url", ee);
                return null;
            }
        }
        if (pstream == null && !running_as_applet) {
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

    void loadPlaylist() {

        if (running_as_applet) {
            String s = null;
            for (int i = 0; i < 10; i++) {
                s = getParameter("jorbis.player.play." + i);
                if (s == null) {
                    break;
                }
                playlist.addElement(s);
            }
        }

        if (playlistfile == null) {
            return;
        }

        try {
            InputStream is = null;
            try {
                URL url = null;
                if (running_as_applet) {
                    url = new URL(getCodeBase(), playlistfile);
                } else {
                    url = new URL(playlistfile);
                }
                URLConnection urlc = url.openConnection();
                is = urlc.getInputStream();
            } catch (Exception ee) {
            }
            if (is == null && !running_as_applet) {
                try {
                    is = new FileInputStream(System.getProperty("user.dir")
                            + System.getProperty("file.separator") + playlistfile);
                } catch (Exception ee) {
                }
            }

            if (is == null) {
                return;
            }

            while (true) {
                String line = readline(is);
                if (line == null) {
                    break;
                }
                byte[] foo = line.getBytes();
                for (int i = 0; i < foo.length; i++) {
                    if (foo[i] == 0x0d) {
                        line = new String(foo, 0, i);
                        break;
                    }
                }
                playlist.addElement(line);
            }
        } catch (Exception e) {
            LOG.error("Exception setting urls", e);
        }
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

    public JOrbisPlayer() {
    }

    JPanel panel;
    JComboBox cb;
    JButton start_button;
    JButton stats_button;

    void initUI() {
        panel = new JPanel();

        cb = new JComboBox(playlist);
        cb.setEditable(true);
        panel.add(cb);

        start_button = new JButton("start");
        start_button.addActionListener(this);
        panel.add(start_button);

        if (icestats) {
            stats_button = new JButton("IceStats");
            stats_button.addActionListener(this);
            panel.add(stats_button);
        }
    }

    class UDPIO extends InputStream {
        InetAddress address;
        DatagramSocket socket = null;
        DatagramPacket sndpacket;
        DatagramPacket recpacket;
        byte[] buf = new byte[1024];
        // String host;
        int port;
        byte[] inbuffer = new byte[2048];
        byte[] outbuffer = new byte[1024];
        int instart = 0, inend = 0, outindex = 0;

        UDPIO(int port) {
            this.port = port;
            try {
                socket = new DatagramSocket(port);
            } catch (Exception e) {
                LOG.error("Exception while opening socket", e);
            }
            recpacket = new DatagramPacket(buf, 1024);
        }

        void setTimeout(int i) {
            try {
                socket.setSoTimeout(i);
            } catch (Exception e) {
                LOG.error("Error while setting socket timeout", e);
            }
        }

        int getByte() throws java.io.IOException {
            if ((inend - instart) < 1) {
                read(1);
            }
            return inbuffer[instart++] & 0xff;
        }

        int getByte(byte[] array) throws java.io.IOException {
            return getByte(array, 0, array.length);
        }

        int getByte(byte[] array, int begin, int length) throws java.io.IOException {
            int i = 0;
            int foo = begin;
            while (true) {
                if ((i = (inend - instart)) < length) {
                    if (i != 0) {
                        System.arraycopy(inbuffer, instart, array, begin, i);
                        begin += i;
                        length -= i;
                        instart += i;
                    }
                    read(length);
                    continue;
                }
                System.arraycopy(inbuffer, instart, array, begin, length);
                instart += length;
                break;
            }
            return begin + length - foo;
        }

        int getShort() throws java.io.IOException {
            if ((inend - instart) < 2) {
                read(2);
            }
            int s = 0;
            s = inbuffer[instart++] & 0xff;
            s = ((s << 8) & 0xffff) | (inbuffer[instart++] & 0xff);
            return s;
        }

        int getInt() throws java.io.IOException {
            if ((inend - instart) < 4) {
                read(4);
            }
            int i = 0;
            i = inbuffer[instart++] & 0xff;
            i = ((i << 8) & 0xffff) | (inbuffer[instart++] & 0xff);
            i = ((i << 8) & 0xffffff) | (inbuffer[instart++] & 0xff);
            i = (i << 8) | (inbuffer[instart++] & 0xff);
            return i;
        }

        void getPad(int n) throws java.io.IOException {
            int i;
            while (n > 0) {
                if ((i = inend - instart) < n) {
                    n -= i;
                    instart += i;
                    read(n);
                    continue;
                }
                instart += n;
                break;
            }
        }

        void read(int n) throws java.io.IOException {
            if (n > inbuffer.length) {
                n = inbuffer.length;
            }
            instart = inend = 0;
            int i;
            while (true) {
                recpacket.setData(buf, 0, 1024);
                socket.receive(recpacket);

                i = recpacket.getLength();
                System.arraycopy(recpacket.getData(), 0, inbuffer, inend, i);
                if (i == -1) {
                    throw new java.io.IOException();
                }
                inend += i;
                break;
            }
        }

        public void close() throws java.io.IOException {
            socket.close();
        }

        public int read() throws java.io.IOException {
            return 0;
        }

        public int read(byte[] array, int begin, int length) throws java.io.IOException {
            return getByte(array, begin, length);
        }
    }

    public static void main(String[] arg) {

        JFrame frame = new JFrame("JOrbisPlayer");
        frame.setBackground(Color.lightGray);
        frame.setBackground(Color.white);
        frame.getContentPane().setLayout(new BorderLayout());

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        JOrbisPlayer player = new JOrbisPlayer();
        player.running_as_applet = false;

        if (arg.length > 0) {
            for (int i = 0; i < arg.length; i++) {
                player.playlist.addElement(arg[i]);
            }
        }

        player.loadPlaylist();
        player.initUI();

        frame.getContentPane().add(player.panel);
        frame.pack();
        frame.setVisible(true);
    }
}
