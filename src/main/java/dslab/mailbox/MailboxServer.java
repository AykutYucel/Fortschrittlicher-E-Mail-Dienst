package dslab.mailbox;

import java.io.*;
import java.net.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.User;
import dslab.nameserver.AlreadyRegisteredException;
import dslab.nameserver.INameserverRemote;
import dslab.nameserver.InvalidDomainException;
import dslab.util.Config;

public class MailboxServer implements IMailboxServer, Runnable {

    private Config config;
    private Config userConfig;
    private Shell shell;
    private ServerSocket listenerDMTP;
    private ServerSocket listenerDMAP;
    private ExecutorService executor;
    private boolean isShutdown = false;
    private List<User> users;
    private String componentId;


    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.userConfig = new Config(componentId.replaceAll("mailbox", "users"));
        this.executor = Executors.newCachedThreadPool();
        this.users = Collections.synchronizedList(new ArrayList<>());
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
    }

    @Override
    public void run() {
        try {
            this.listenerDMTP = new ServerSocket(config.getInt("dmtp.tcp.port"));
            this.listenerDMAP = new ServerSocket(config.getInt("dmap.tcp.port"));
            executor.execute(shell);
            System.out.println("Server is up!");
            executor.execute(new MailboxListenerThread(listenerDMTP, users, config, userConfig, componentId));
            executor.execute(new MailboxListenerThread(listenerDMAP, users, config, userConfig, componentId));
        } catch (SocketException e) {

        } catch (IOException e) {
            throw new UncheckedIOException("Error while creating server socket", e);
        }

        registerToNameserver();
    }

    public void registerToNameserver(){
        //failing to register does not stop the server from working
        String domain = config.getString("domain");
        String port = config.getString("dmtp.tcp.port");
        try {
            Registry registry = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));
            INameserverRemote root = (INameserverRemote) registry.lookup(config.getString("root_id"));
            shell.out().println("The remote object of the root nameserver has been found.");
            String ipWithPort = InetAddress.getLocalHost().getHostAddress() + ":" + port;
            shell.out().println("registering " + componentId + " to the nameserver");
            root.registerMailboxServer(domain, ipWithPort);
        } catch (RemoteException e) {
            System.err.println("Could not find the remote root nameserver");
        } catch (NotBoundException e) {
            System.err.println("NotBoundException");
        } catch (UnknownHostException e) {
            System.err.println("Could not find the IP of this server");
        } catch (InvalidDomainException e) {
            //TODO: error message verbessern
            System.err.println("invalid domain exception: could not find nameserver for this domain");
        } catch (AlreadyRegisteredException e) {
            System.err.println("This mailbox is already registered in a nameserver");
        }
    }

    private void close() {
        try {
            if (listenerDMTP != null && !listenerDMTP.isClosed()) {
                listenerDMTP.close();
            }
            if (listenerDMAP != null && !listenerDMAP.isClosed()) {
                listenerDMAP.close();
            }
        } catch (IOException e) {
            System.err.println("Error IOException while closing server socket: " + e.getMessage());
        }
    }

    @Override
    @Command
    public void shutdown() {
        isShutdown = true;
        close();
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
        System.out.println("Exiting mailbox server shell.");
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
        server.run();
    }
}
