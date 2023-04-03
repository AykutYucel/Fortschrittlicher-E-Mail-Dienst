package dslab.mailbox;

import dslab.User;
import dslab.protocol.DMAP;
import dslab.util.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MailboxListenerThread implements Runnable {

    private ServerSocket serverSocket;
    private List<User> users;
    private Config mailboxConfig;
    private Config userConfig;
    private String domain;
    private ExecutorService executor;
    private String componentId;

    public MailboxListenerThread(ServerSocket serverSocket, List<User> users, Config mailboxConfig, Config userConfig, String componentId) {
        this.componentId = componentId;
        this.serverSocket = serverSocket;
        this.users = users;
        this.mailboxConfig = mailboxConfig;
        this.userConfig = userConfig;
        this.domain = mailboxConfig.getString("domain");
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (serverSocket.getLocalPort() == mailboxConfig.getInt("dmtp.tcp.port")) {
                    MailboxHandler mailboxHandler = new MailboxHandler(serverSocket.accept(), users, userConfig, domain);
                    executor.execute(mailboxHandler);
                } else if (serverSocket.getLocalPort() == mailboxConfig.getInt("dmap.tcp.port")) {
                    MailboxAccess mailboxAccess = new MailboxAccess(serverSocket.accept(), userConfig, users, componentId);
                    executor.execute(mailboxAccess);
                }
            }
        } catch (SocketException e) {
            System.err.println("Error SocketException while listening: " + e);
        } catch (IOException e) {
            System.err.println("Error IOException while listening: " + e);
        } finally {
            close();
        }
    }

    private void close() {
        try {
            if (!executor.isShutdown()) {
                executor.shutdown();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error IOException while closing server socket: " + e);
        }
    }
}
