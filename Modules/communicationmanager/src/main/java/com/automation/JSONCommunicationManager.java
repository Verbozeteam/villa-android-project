package com.automation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by hasan on 7/27/16.
 */
public class JSONCommunicationManager implements Runnable {
    private class BYTEARRAYFUCKJAVA {
        public byte[] bytes;
        public BYTEARRAYFUCKJAVA(byte[] b) {bytes = b;}
    }

    private final int BUFFER_SIZE = 256;
    private BYTEARRAYFUCKJAVA[] commandsBuffer = new BYTEARRAYFUCKJAVA[BUFFER_SIZE];;
    private int bufferStart = 0, bufferEnd = 0;
    private String target_IP = "";
    private int target_port = 0;
    private boolean connected = false;
    private boolean is_running = true;
    private ServerDataCallback data_callback = null;
    private ServerConnectedCallback connected_callback = null;
    private ServerDisconnectedCallback disconnected_callback = null;
    private ReentrantLock lock = new ReentrantLock();

    public static class ServerDataCallback {
        public void onData(String json_str) {}
    }

    public static class ServerConnectedCallback {
        public void onConnected() {}
    }

    public static class ServerDisconnectedCallback {
        public void onDisconnected() {}
    }

    public static class DeviceDiscoveryCallback {
        public void onDeviceFound(String addr, String text, int type, String data) {}
    }

    public static JSONCommunicationManager Create(String name, ServerDataCallback dcb, ServerConnectedCallback ccb, ServerDisconnectedCallback dccb) {
        JSONCommunicationManager m = new JSONCommunicationManager(dcb, ccb, dccb);
        Thread thread = new Thread(m, name);
        thread.start();
        return m;
    }

    public static void DiscoverDevices(final DeviceDiscoveryCallback cb) {
        Thread discoverer = new Thread() {
            @Override
            public void run() {
                DatagramSocket s = null;
                try {
                    s = new DatagramSocket();
                    s.setBroadcast(true);
                    InetAddress address = InetAddress.getByName("255.255.255.255");
                    byte[] sendData = {(byte) 0x29, (byte) 0xad, 0, 0};
                    DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, 7991);
                    s.send(sendPacket);

                    s.setSoTimeout(2000);

                    DatagramPacket pack = new DatagramPacket(new byte[128], 128);
                    while (true) {
                        s.receive(pack);
                        final String addr = pack.getAddress().toString().replaceAll("/", "");
                        byte[] bytes = pack.getData();
                        if (bytes[0] == (byte) 0x29 && bytes[1] == (byte) 0xad) {
                            final int type = bytes[2];
                            String not_final_text = "";
                            int len = bytes[3];
                            for (int i = 4; i < 4 + len; i++)
                                not_final_text += (char) bytes[i];
                            int colon_index = not_final_text.indexOf(':');
                            String not_final_data = "";
                            if (colon_index != -1) {
                                not_final_data = not_final_text.substring(colon_index+1);
                                not_final_text = not_final_text.substring(0, colon_index);
                            }
                            final String text = not_final_text; // now me make it final lol
                            final String data = not_final_data;
                            cb.onDeviceFound(addr, text, type, data);
                        }
                    }
                } catch (SocketTimeoutException e) {
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (s != null && s.isConnected())
                        s.close();
                }
            }
        };
        discoverer.start();
    }

    public JSONCommunicationManager(ServerDataCallback dcb, ServerConnectedCallback ccb, ServerDisconnectedCallback dccb) {
        data_callback = dcb;
        connected_callback = ccb;
        disconnected_callback = dccb;
    }

    public synchronized void addToQueue( String cmd ) {
        int len = cmd.length();
        byte[] new_cmd = new byte[4+len];
        byte[] cmd_bytes = cmd.getBytes();
        new_cmd[0] = (byte)((len      ) & 0xff);
        new_cmd[1] = (byte)((len >> 8 ) & 0xff);
        new_cmd[2] = (byte)((len >> 16) & 0xff);
        new_cmd[3] = (byte)((len >> 24) & 0xff);
        for (int i = 0; i < len; i++)
            new_cmd[i+4] = cmd_bytes[i];

        if ((bufferEnd + 1) % BUFFER_SIZE != bufferStart) {
            commandsBuffer[bufferEnd] = new BYTEARRAYFUCKJAVA(new_cmd);
            bufferEnd = (bufferEnd+1) % BUFFER_SIZE;
        }
    }

    public synchronized void SetServerAddress(String IP, int port) {
        bufferStart = bufferEnd;
        target_port = port;
        target_IP = IP;
    }

    public void Stop() {
        lock.lock();
        is_running = false;
        lock.unlock();
    }

    @Override
    public void run() {
        Socket socket = null;
        OutputStream output = null;
        InputStream input = null;
        String IP = target_IP;
        int port = target_port;
        long readTmr = 0;
        long beat_timer = 0;
        ArrayList<Byte> buffer = new ArrayList<>();
        boolean force_exit = false;

        while (true) {
            // check if we're done
            lock.lock();
            if (!is_running) {
                lock.unlock();
                break;
            }
            lock.unlock();

            long curTime = System.currentTimeMillis();
            if (!IP.equals(target_IP)) {
                try {
                    if (socket != null)
                        socket.close();
                    disconnected_callback.onDisconnected();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                socket = null;
                connected = false;
                IP = target_IP;
                port = target_port;
            }

            if (!connected) {
                try {
                    InetAddress addr = InetAddress.getByName(IP);
                    socket = new Socket(addr, port);
                    output = socket.getOutputStream();
                    input = socket.getInputStream();
                    connected = true;
                    force_exit = false;
                    readTmr = System.currentTimeMillis();
                    buffer.clear();
                    connected_callback.onConnected();
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch(InterruptedException e2) {
                        System.err.println(e2.getMessage());
                    }
                    continue;
                }
            }

            // we are connected, do connected stuff
            try {
                while (input.available() > 0) {
                    readTmr = curTime;
                    byte[] bb = new byte[1];
                    input.read(bb, 0, 1);
                    buffer.add(bb[0]);
                }
            } catch (IOException e) {
                e.printStackTrace();
                force_exit = true;
            }
            ProcessBuffer(buffer);

            if (curTime - beat_timer > 3000) {
                beat_timer = curTime;
                try {
                    byte[] bytes = {2, 0, 0, 0, '{', '}'};
                    output.write(bytes);
                    readTmr = curTime;
                } catch (Exception e) {
                    force_exit = true;
                }
            }
            int bufend = bufferEnd;
            while (bufend != bufferStart) {
                byte[] cmd = commandsBuffer[bufferStart].bytes;
                commandsBuffer[bufferStart] = null;
                bufferStart = (bufferStart + 1) % BUFFER_SIZE;
                try {
                    output.write(cmd);
                } catch (IOException e) {
                    e.printStackTrace();
                    force_exit = true;
                }
            }


            synchronized (this) {
                try {
                    wait(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // bookkeeping
            if (socket == null)
                connected = false;
            else if (socket.isConnected() == false)
                connected = false;
            else if ((curTime - readTmr > 8000) || force_exit) {
                try {
                    socket.close();
                } catch (IOException e) {}
                connected = false;
                disconnected_callback.onDisconnected();
            }
        }

        data_callback = null;
        connected_callback = null;
        disconnected_callback = null;
    }

    private void ProcessBuffer(ArrayList<Byte> buffer) {
        if (buffer.size() > 4) {
            int b1 = buffer.get(0);
            int b2 = buffer.get(1);
            int b3 = buffer.get(2);
            int b4 = buffer.get(3);
            int len = ((b1 & 0xff)) | ((b2 & 0xff) << 8) | ((b3 & 0xff) << 16) | ((b4 & 0xff) << 24);
            if (buffer.size() >= len+4) {
                byte[] bytes = new byte[len];
                for (int i = 0; i < len; i++)
                    bytes[i] = buffer.get(i+4);

                String s = new String(bytes);
                data_callback.onData(s);

                for (int i = 0; i < len+4; i++)
                    buffer.remove(0);
            }
        }
    }
}
