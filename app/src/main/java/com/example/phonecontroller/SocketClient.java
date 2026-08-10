package com.example.phonecontroller;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SocketClient {

    public interface Listener {
        void onConnected(String host, int port);
        void onDisconnected(String reason);
    }

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Listener listener;
    private Socket socket;
    private OutputStream out;
    private volatile boolean connected = false;
    private Thread writerThread;

    public SocketClient(Listener listener) {
        this.listener = listener;
    }

    public void connect(final String host, final int port) {
        disconnect();
        Thread t = new Thread(() -> {
            try {
                Socket s = new Socket();
                s.setKeepAlive(true);
                s.setTcpNoDelay(true);
                s.connect(new InetSocketAddress(host, port), 5000);
                socket = s;
                out = s.getOutputStream();
                connected = true;
                startWriter();
                if (listener != null) listener.onConnected(host, port);
            } catch (Exception e) {
                connected = false;
                if (listener != null) listener.onDisconnected(e.getMessage());
            }
        }, "connect-thread");
        t.start();
    }

    private void startWriter() {
        writerThread = new Thread(() -> {
            while (connected) {
                try {
                    String message = queue.take();
                    if (out != null) {
                        byte[] data = (message + "\n").getBytes(StandardCharsets.UTF_8);
                        out.write(data);
                        out.flush();
                    }
                } catch (InterruptedException ie) {
                    break;
                } catch (Exception e) {
                    connected = false;
                    queue.clear();
                    if (listener != null) listener.onDisconnected(e.getMessage());
                    break;
                }
            }
        }, "writer-thread");
        writerThread.start();
    }

    public void send(String message) {
        if (!connected) return;
        queue.offer(message);
    }

    public void disconnect() {
        connected = false;
        queue.clear();
        if (writerThread != null) {
            writerThread.interrupt();
            writerThread = null;
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
            socket = null;
        }
        out = null;
    }
}
