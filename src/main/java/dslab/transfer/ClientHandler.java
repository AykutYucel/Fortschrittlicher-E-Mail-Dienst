package dslab.transfer;

import dslab.Message;
import dslab.nameserver.INameserverRemote;
import dslab.protocol.DMTP;
import dslab.util.Config;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler implements Runnable {

    private Socket client;
    private PrintWriter writer;
    private BufferedReader reader;
    private Config config;
    private DMTP dmtp;
    private BlockingQueue<Message> messages;
    private ExecutorService executorMailbox;

    private INameserverRemote rootNameserverRemote;

    public ClientHandler(Socket client, Config config, BlockingQueue<Message> messages, INameserverRemote rootNameserverRemote) {
        try {
            this.config = config;
            this.client = client;
            this.messages = messages;
            this.reader = new BufferedReader(new InputStreamReader(this.client.getInputStream()));
            this.writer = new PrintWriter(this.client.getOutputStream(), true);
            executorMailbox = Executors.newCachedThreadPool();

            this.rootNameserverRemote = rootNameserverRemote;
        } catch (IOException e) {
            System.err.println("Error creating reader/writer: " + e);
        }
    }

    public void forwardMessage(){
        executorMailbox.execute(new MailboxConnection(config, messages, rootNameserverRemote));
    }

    @Override
    public void run() {
        String request;
        String response;
        try {
            dmtp = new DMTP();
            // ok DMTP
            response = dmtp.processInput("");
            writer.println(response);

            while ((request = reader.readLine()) != null) {
                response = dmtp.processInput(request);
                writer.println(response);
                if (request.equalsIgnoreCase("send") && response.equals("ok")) {
                    // producer, waits if necessary
                    messages.put(dmtp.getMessage());
                    forwardMessage();
                } else if (response.equals("ok bye") || response.equals("error protocol error")) {
                    break;
                }
            }
        } catch (SocketException e) {
            System.err.println("Error SocketException while handling socket: " + e);
        } catch (IOException e) {
            System.err.println("Error IOException: " + e);
        } catch (InterruptedException e) {
            System.err.println("Error InterruptedException");
        } finally {
            close();
        }
    }

    private void close() {
        if (!executorMailbox.isShutdown()) {
            executorMailbox.shutdown();
        }
        if (client != null && !client.isClosed()) {
            try {
                writer.close();
                reader.close();
                client.close();
            } catch (IOException e) {
                // Ignored because we cannot handle it
            }
        }
    }
}

