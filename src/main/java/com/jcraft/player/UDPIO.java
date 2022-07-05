package com.jcraft.player;

import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UDPIO extends InputStream {

    /** Logger of the class. */
    private static final Logger LOG = LogManager.getLogger(UDPIO.class);

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
