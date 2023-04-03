package dslab.transfer;

import java.io.*;
import java.net.ServerSocket;
import java.net.SocketException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.*;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.Message;
import dslab.nameserver.INameserver;
import dslab.nameserver.INameserverRemote;
import dslab.util.Config;

public class TransferServer implements ITransferServer, Runnable {

    private Config config;
    private ServerSocket listener;
    private ExecutorService executorClients;
    private ExecutorService executorMailbox;
    private Shell shell;
    private boolean isShutdown = false;
    private BlockingQueue<Message> messages;

    //for the decentralised domain lookup;
    private Registry registry;
    private INameserverRemote rootNameserverRemote;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.config = config;
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
        executorClients = Executors.newCachedThreadPool();
        executorMailbox = Executors.newCachedThreadPool();
        messages = new LinkedBlockingQueue<>( 500);
    }

    @Override
    public void run() {

        //open socket for this server;
        try {
            listener = new ServerSocket(config.getInt("tcp.port"));
        } catch (IOException e) {
            throw new UncheckedIOException("Error while creating server socket", e);
        }

        //get the root-Nameserver from the registry - for forwarding mails
        try {
            registry = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));
            rootNameserverRemote = (INameserverRemote) registry.lookup(config.getString("root_id"));
        } catch (RemoteException e) {
            System.err.println("Error RemoteException while getting the registry: " + e);
        } catch (NotBoundException e) {
            System.err.println("Error NotBoundExcepiton while looking for rootNameserver in the registry: " + e);
        }

        ClientHandler clientThread;
        executorClients.execute(shell);
        System.out.println("Server is up!");

        //listen for incoming clients and deal with them;
        try {
            while (!isShutdown) {
                clientThread = new ClientHandler(listener.accept(), config, messages, rootNameserverRemote);
                executorClients.execute(clientThread);
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
        if (!executorClients.isShutdown()) {
            executorClients.shutdown();
        }
        if (!executorMailbox.isShutdown()) {
            executorMailbox.shutdown();
        }
        if (listener != null && !listener.isClosed()) {
            try {
                listener.close();
            } catch (IOException e) {
                System.err.println("Error IOException while closing server socket: " + e.getMessage());
            }
        }

    }

    @Override
    @Command
    public void shutdown() {
        isShutdown = true;
        close();
        System.out.println("Exiting transfer server shell.");
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }

}
