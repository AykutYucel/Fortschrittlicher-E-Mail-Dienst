package dslab.monitoring;

import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class MonitoringServer implements IMonitoringServer {

    private Config config;
    private ExecutorService executor;
    private ConcurrentHashMap<String, Integer> addresses;
    private ConcurrentHashMap<String, Integer> servers;
    private Shell shell;
    private DatagramSocket datagramSocket;
    boolean isShutdown = false;
    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.config = config;
        this.executor = Executors.newCachedThreadPool();
        this.addresses = new ConcurrentHashMap<>();
        this.servers = new ConcurrentHashMap<>();
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
    }

    @Override
    public void run() {
        try {
            datagramSocket = new DatagramSocket(config.getInt("udp.port"));
        } catch (SocketException e) {
            throw new RuntimeException("Cannot listen on UDP port.", e);
        }
        System.out.println("Server is up!");
        executor.execute(new MonitoringHandler(datagramSocket, addresses, servers));
        shell.run();
        System.out.println("Exiting monitoring server shell.");

    }

    private void close() {
        if (datagramSocket != null && !datagramSocket.isClosed()) {
            datagramSocket.close();
        }
    }

    @Override
    @Command
    public void addresses() {
        for (Map.Entry<String, Integer> entry : addresses.entrySet()) {
            shell.out().println(entry.getKey() + " " + entry.getValue());
        }
    }

    @Override
    @Command
    public void servers() {
        for (Map.Entry<String, Integer> entry : servers.entrySet()) {
            shell.out().println(entry.getKey() + " " + entry.getValue());
        }
    }

    @Override
    @Command
    public void shutdown() {
        isShutdown = true;
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
        close();
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMonitoringServer server = ComponentFactory.createMonitoringServer(args[0], System.in, System.out);
        server.run();
    }
}
