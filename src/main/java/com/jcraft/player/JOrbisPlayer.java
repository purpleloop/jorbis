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

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

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
public class JOrbisPlayer implements Runnable {

    /** Sample size, in bits. */
    private static final int SAMPLE_SIZE_IN_BITS = 16;

    /** Logger of the class. */
    private static final Logger LOG = LogManager.getLogger(JOrbisPlayer.class);

    /** Buffer size 8 ko. */
    private static final int BUFFER_SIZE = 4096 * 2;

    /** Conversion buffer size (initialized at 16ko). */
    private static int conversionBufferSize = BUFFER_SIZE * 2;

    /** Conversion buffer. */
    private static byte[] conversionBuffer = new byte[conversionBufferSize];

    /** The active player thread. */
    private Thread activePlayerThread = null;

    /** The sound bit stream. */
    private InputStream soundBitStream = null;

    /** UDP port for streaming. */
    int udpPort = -1;

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

    private int rate = 0;
    private int channels = 0;

    private SourceDataLine outputLine = null;

    /** Context of the player. */
    private JOrbisPlayerContext playerContext;

    /**
     * Constructor of the player.
     * 
     * @param playerContext the player context
     */
    public JOrbisPlayer(JOrbisPlayerContext playerContext) {
        this.playerContext = playerContext;
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

            this.outputLine = (SourceDataLine) AudioSystem.getLine(lineInfo);
            this.outputLine.open(audioFormat);
            this.rate = sampleRate;
            this.channels = channels;

        } catch (LineUnavailableException ex) {
            LOG.error("Unable to open the sourceDataLine during audio initialization", ex);
        } catch (IllegalArgumentException ex) {
            LOG.error("Illegal argument error during audio initialization", ex);
        } catch (Exception ee) {
            LOG.error("Error during audio initialization", ee);
        }
    }

    public void run() {

        Thread localThread = Thread.currentThread();
        LOG.debug("Player thread started {}", localThread.getId());

        // Playing items of the playlist in loop
        while ((activePlayerThread == localThread) && (playerContext.getItemCount() > 0)) {

            String playlistItem = playerContext.getCurrentItem();
            soundBitStream = selectSource(playlistItem);

            if (soundBitStream != null) {

                // We have an usable sound steam to play
                if (udpPort != -1) {
                    playUdpStream(localThread);
                } else {
                    playStream(localThread);
                }

                LOG.debug("Playing of {} finished", playlistItem);

            } 

            soundBitStream = null;
            
            // If this thread is still active, select next item in list
            if (activePlayerThread == localThread) {
                playerContext.next();
            }
            
        }

        LOG.debug("Play loop terminated, handling end");
        activePlayerThread = null;
        playerContext.handleEndOfPlay();
    }

    /**
     * Plays a stream on the given playing thread.
     * 
     * @param playingThread the playing thread
     */
    private void playStream(Thread playingThread) {

        boolean chained = false;

        initJorbis();

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

            if (streamState.packetout(packet) != 1) {
                // no page? must not be Vorbis
                LOG.error("Error reading initial header packet.");
                break;
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
                        // Need more data
                        break;
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

            handleMetaInfo();

            conversionBufferSize = BUFFER_SIZE / info.channels;

            dspState.synthesis_init(info);
            block.init(dspState);

            float[][][] pcmf2 = new float[1][][];
            int[] index2 = new int[info.channels];

            getOutputLine(info.channels, info.rate);

            while (eos == 0) {
                while (eos == 0) {

                    if (activePlayerThread != playingThread) {
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
                                decodePacket(pcmf2, index2);
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

    /** Handle meta informations. */
    private void handleMetaInfo() {

        byte[][] userCommentsData = comment.user_comments;

        Map<String, String> infos = new HashMap<>();

        List<String> commentLines = new ArrayList<>();

        byte[] commentByteBuffer;

        for (int commentIndex = 0; commentIndex < userCommentsData.length; commentIndex++) {
            commentByteBuffer = userCommentsData[commentIndex];
            if (commentByteBuffer != null) {

                String line = new String(commentByteBuffer, 0, commentByteBuffer.length - 1);

                int pos = line.indexOf('=');
                if (pos != -1) {
                    infos.put(line.substring(0, pos), line.substring(pos + 1));
                } else {
                    commentLines.add(line);
                }
            }
        }

        infos.put("Bitstream channels", Integer.toString(info.channels));
        infos.put("Bitstream rate (Hz)", Integer.toString(info.rate));
        infos.put("Encoded by", new String(comment.vendor, 0, comment.vendor.length - 1));

        for (Entry<String, String> entry : infos.entrySet()) {
            LOG.info("{} : {}", entry.getKey(), entry.getValue());
        }

        LOG.info("Comments: ");
        for (String commentLine : commentLines) {
            LOG.info(" - {}", commentLine);
        }

    }

    /** Decode a packet. */
    private void decodePacket(float[][][] pcmf2, int[] index2) {

        if (block.synthesis(packet) == 0) {
            // test for success!
            dspState.synthesis_blockin(block);
        }

        int sampleSize;
        while ((sampleSize = dspState.synthesis_pcmout(pcmf2, index2)) > 0) {
            float[][] pcmf = pcmf2[0];
            int bufferOutputSize = (sampleSize < conversionBufferSize ? sampleSize
                    : conversionBufferSize);

            // convert doubles to 16 bit signed ints (host order) and
            // interleave
            for (int channelIndex = 0; channelIndex < info.channels; channelIndex++) {
                int bufferPosition = channelIndex * 2;

                int mono = index2[channelIndex];
                for (int j = 0; j < bufferOutputSize; j++) {
                    int val16BitsSigned = floatTo16BitsSignedInt(pcmf[channelIndex][mono + j]);
                    conversionBuffer[bufferPosition] = (byte) (val16BitsSigned);
                    conversionBuffer[bufferPosition + 1] = (byte) (val16BitsSigned >>> 8);
                    bufferPosition += 2 * (info.channels);
                }
            }
            outputLine.write(conversionBuffer, 0, 2 * info.channels * bufferOutputSize);
            dspState.synthesis_read(bufferOutputSize);
        }
    }

    /**
     * Transforms a float [-1;1] to an 16 bit signed integer bounded in
     * [-32768;32767].
     * 
     * @param f the float value to convert
     * @return the integer value bounded a 16 bit signed value
     */
    private static int floatTo16BitsSignedInt(float f) {
        int val = (int) (f * 32767.);
        if (val > 32767) {
            val = 32767;
        }
        if (val < -32768) {
            val = -32768;
        }
        if (val < 0) {
            val = val | 0x8000;
        }
        return val;
    }

    private void playUdpStream(Thread me) {
        initJorbis();

        try {
            loop: while (true) {
                int index = syncState.buffer(BUFFER_SIZE);
                buffer = syncState.data;
                try {
                    bytes = soundBitStream.read(buffer, index, BUFFER_SIZE);
                } catch (Exception e) {
                    LOG.error("Error while reading from sound stream", e);
                    return;
                }

                syncState.wrote(bytes);
                if (syncState.pageout(page) != 1) {
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
                            // Need more data
                            break;
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
            io = new UDPIO(udpPort);
        } catch (Exception e) {
            return;
        }

        soundBitStream = io;
        playStream(me);
    }

    /**
     * Starts to play a sound.
     * 
     * @return true if the command is accepted, false otherwise.
     */
    public boolean playSound() {
        if (activePlayerThread != null) {
            return false;
        }
        activePlayerThread = new Thread(this);

        activePlayerThread.start();

        return true;
    }

    /**
     * Ends the play of a sound.
     * 
     * @return true if the command is accepted, false otherwise.
     */
    public boolean stopSound() {
        if (activePlayerThread == null) {
            return false;
        }

        LOG.debug("Stop requested : player thread is no more active and must terminate ...");
        activePlayerThread = null;
        return true;
    }

    /**
     * Selects and open the sound source to play.
     * 
     * @param item the item to play
     */
    InputStream selectSource(String item) {

        LOG.debug("Selecting source {}", item);
        PlayList playlist = playerContext.getPlayList();

        if (item.endsWith(PlayList.PLS_EXTENSION)) {
            item = playlist.fetchPls(item);
            if (item == null) {
                return null;
            }
            LOG.info("fetch: {}", item);
        } else if (item.endsWith(PlayList.M3U_EXTENSION)) {
            item = playlist.fetchM3u(item);
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
            URL url = new URL(item);

            urlc = url.openConnection();
            is = urlc.getInputStream();

        } catch (Exception ee) {
            LOG.error("Exception while opening applet url", ee);
        }

        if (is == null) {
            try {
                is = new FileInputStream(System.getProperty("user.dir")
                        + System.getProperty("file.separator") + item);
            } catch (Exception ee) {
                LOG.error("Exception while opening file", ee);
            }
        }

        if (is == null) {
            return null;
        }

        LOG.info("Select: {}", item);

        boolean find = false;
        for (int i = 0; i < playerContext.getItemCount(); i++) {
            String foo = playerContext.getItemAtIndex(i);
            if (item.equals(foo)) {
                find = true;
                break;
            }
        }
        if (!find) {
            playerContext.addItem(item);
        }

        int i = 0;
        String s = null;
        String t = null;
        udpPort = -1;

        while (urlc != null) {
            s = urlc.getHeaderField(i);
            t = urlc.getHeaderFieldKey(i);
            if (s == null) {
                break;
            }
            i++;
            if (t != null && t.equals("udp-port")) {
                try {
                    udpPort = Integer.parseInt(s);
                } catch (Exception ee) {
                    LOG.error("Exception while parsing UDP port", ee);
                }
            }
        }
        return is;
    }

    /** @return true if the player has an active thread, false otherwise */
    public boolean hasActivePlayerThread() {
        return activePlayerThread != null;
    }

}
