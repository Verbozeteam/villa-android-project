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
public class CommunicationManager implements Runnable {
    private final int BUFFER_SIZE = 256;
    private String[] commandsBuffer;
    private int bufferStart, bufferEnd;
    private String target_IP = "";
    private boolean connected = false;
    private boolean is_running = true;
    private ServerDataCallback callback = null;
    private ReentrantLock lock = new ReentrantLock();

    public static class ServerDataCallback {
        public void onServerData(int[] arg1, int[] arg2, int[] arg3, int[] arg4, int[] arg5) {}
    }

    public static class DeviceDiscoveryCallback {
        public void onDeviceFound(String addr, String text, int type, String data) {}
    }

    public static CommunicationManager Create(String name, ServerDataCallback callback) {
        CommunicationManager m = new CommunicationManager(callback);
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

    public CommunicationManager(ServerDataCallback c) {
        callback = c;
    }

    public synchronized void addToQueue( String cmd ) {
        if (commandsBuffer[(bufferEnd+BUFFER_SIZE-1) % BUFFER_SIZE] == cmd)
            return;
        if ((bufferEnd + 1) % BUFFER_SIZE != bufferStart) {
            commandsBuffer[bufferEnd] = cmd;
            bufferEnd = (bufferEnd+1) % BUFFER_SIZE;
        }
    }

    public synchronized void SetServerIP(String IP) {
        target_IP = IP;
    }

    public void Stop() {
        lock.lock();
        is_running = false;
        lock.unlock();
    }

    @Override
    public void run() {
        commandsBuffer = new String[BUFFER_SIZE];
        for (int i = 0; i < BUFFER_SIZE; i++)
            commandsBuffer[i] = null;
        bufferEnd = bufferStart = 0;
        Socket socket = null;
        OutputStream output = null;
        InputStream input = null;
        String IP = target_IP;
        int port = 7990;
        int kitchen_port = 7992;
        long readTmr = 0;
        long beat_timer = 0;
        ArrayList<Byte> buffer = new ArrayList<>();
        boolean isKicthen = false;
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
            if (IP != target_IP) {
                isKicthen = false;
                if (target_IP.charAt(0) == 'k') {
                    isKicthen = true;
                    target_IP = target_IP.replaceAll("k", "");
                }
                try {
                    if (socket != null)
                        socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                socket = null;
                connected = false;
                IP = target_IP;
                bufferStart = bufferEnd;
            }

            if (!connected) {
                try {
                    InetAddress addr = InetAddress.getByName(IP);
                    if (isKicthen)
                        socket = new Socket(addr, kitchen_port);
                    else
                        socket = new Socket(addr, port);
                    output = socket.getOutputStream();
                    input = socket.getInputStream();
                    connected = true;
                    force_exit = false;
                    readTmr = System.currentTimeMillis();
                    buffer.clear();
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
            ProcessBuffer(buffer, isKicthen);

            if (curTime - beat_timer > 2500) {
                beat_timer = curTime;
                try {
                    output.write("S\n".getBytes());
                } catch (Exception e) {}
            }
            int bufend = bufferEnd;
            while (bufend != bufferStart) {
                String cmd = commandsBuffer[bufferStart];
                commandsBuffer[bufferStart] = null;
                bufferStart = (bufferStart + 1) % BUFFER_SIZE;
                try {
                    output.write(cmd.getBytes());
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
            else if ((curTime - readTmr > 8000 && !isKicthen) || force_exit) {
                try {
                    socket.close();
                } catch (IOException e) {}
                connected = false;
            }
        }

        callback = null;
    }

    private void ProcessBuffer(ArrayList<Byte> buffer, boolean isKitchen) {
        if (isKitchen) {
            // every packet is [session%256][order_index][is_accepted] (3 bytes)
            while (buffer.size() >= 3) {
                final int[] x = {buffer.get(0), buffer.get(1), buffer.get(2)};
                if (x[0] == -1 && x[1] == -1 && x[2] == -1) { // still initializing
                    if (buffer.size() >= 4) {
                        int num_foods = buffer.get(3);
                        if (buffer.size() < 4 + 128*num_foods)
                            break;
                        int[] L = new int [128*num_foods];
                        for (int i = 0; i < L.length; i++)
                            L[i] = buffer.get(i+4);
                        callback.onServerData(null, L, null, null, null);
                        for (int i = 0; i < 4 + 128*num_foods; i++)
                            buffer.remove(0);
                    }
                } else { // order response
                    callback.onServerData(x, null, null, null, null);
                    for (int i = 0; i < 3; i++)
                        buffer.remove(0);
                }
            }
        } else {
            while (true) {
                while (buffer.size() > 0) {
                    if (buffer.get(0) != -2)
                        buffer.remove(0);
                    else
                        break;
                }

                int reqsize = 2;
                if (buffer.size() < reqsize)
                    return;

                int numacs = buffer.get(reqsize - 1);
                reqsize += 1 + numacs * 3;
                if (buffer.size() < reqsize)
                    return;
                int numdimmers = buffer.get(reqsize - 1);
                reqsize += 1 + numdimmers;
                if (buffer.size() < reqsize)
                    return;
                int numlights = buffer.get(reqsize - 1);
                reqsize += 1 + numlights;
                if (buffer.size() < reqsize)
                    return;
                int numcurtains = buffer.get(reqsize - 1);
                reqsize += 1;
                if (buffer.size() < reqsize)
                    return;
                int ender = buffer.get(reqsize - 1);
                if (ender != -1) {
                    for (int i = 0; i < reqsize - 1; i++)
                        buffer.remove(0);
                    continue;
                }

                int[] ACFanSpds = new int[numacs];
                int[] ACSetPoints = new int[numacs];
                int[] ACTemps = new int[numacs];
                for (int i = 0; i < numacs; i++) {
                    ACFanSpds[i] = buffer.get(2 + i);
                    ACSetPoints[i] = buffer.get(2 + i + numacs);
                    ACTemps[i] = buffer.get(2 + i + 2 * numacs);
                }
                int[] Dimmers = new int[numdimmers];
                int[] Lights = new int[numlights];
                int Num_curtains = numcurtains;
                for (int i = 0; i < numdimmers; i++)
                    Dimmers[i] = buffer.get(2 + 3 * numacs + 1 + i);
                for (int i = 0; i < numlights; i++)
                    Lights[i] = buffer.get(2 + 3 * numacs + (numdimmers + 1) + 1 + i);

                callback.onServerData(Lights, Dimmers, ACSetPoints, ACFanSpds, ACTemps);

                for (int i = 0; i < reqsize; i++)
                    buffer.remove(0);
            }
        }
    }
}
