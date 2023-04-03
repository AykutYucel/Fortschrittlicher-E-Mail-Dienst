package dslab.nameserver;

import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;

public class Nameserver implements INameserver, INameserverRemote{

    private Shell shell;
    private String componentId;
    private Config config;

    private Registry registry;

    private INameserverRemote rootRemote;
    private INameserverRemote remoteOfThisServer;

    private Boolean isRoot;

    private ConcurrentHashMap<String, INameserverRemote> childReferences;
    private ConcurrentHashMap<String, String> mailboxReferences;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
        isRoot = false;

        childReferences = new ConcurrentHashMap<>();
        mailboxReferences = new ConcurrentHashMap<>();
    }

    @Override
    public void run() {
        if(!config.containsKey("domain")){ //root server
            try {
                //create the registry
                registry = LocateRegistry.createRegistry(config.getInt("registry.port")); //create Registry
                System.out.println("The registry has been created");

                //exports the supplied remote object to receive incoming remote method invocations
                rootRemote = (INameserverRemote) UnicastRemoteObject.exportObject(this, 0);
                remoteOfThisServer = rootRemote;

                System.out.println("Root server has been initialized");

                registry.bind(config.getString("root_id"), rootRemote); //bind root to registry

                System.out.println("Root Nameserver started!");
                isRoot = true;
            } catch (RemoteException | AlreadyBoundException f) {
                f.printStackTrace();
            }
        } else { //zone nameserver
            try{
                registry = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));

                rootRemote = (INameserverRemote) registry.lookup(config.getString("root_id"));
                remoteOfThisServer = (INameserverRemote) UnicastRemoteObject.exportObject(this, 0);

                if(rootRemote != null){
                    rootRemote.registerNameserver(config.getString("domain"), this);
                }
            } catch (AccessException e) {
                e.printStackTrace();
            } catch (AlreadyRegisteredException e) {
                e.printStackTrace();
            } catch (NotBoundException e) {
                e.printStackTrace();
            } catch (InvalidDomainException e) {
                e.printStackTrace();
            } catch (RemoteException e) {
                System.err.println("Registry does not exist. Make sure the root nameserver is running!");
            }
        }

        shell.run();
    }

    @Command
    @Override
    public void nameservers() {
        HashMap<String, INameserverRemote> nameservers = null;

        if(isRoot){
            nameservers = new HashMap<>(childReferences);
        } else{
            nameservers = new HashMap<>(childReferences);
        }

        StringBuilder nameserverList = new StringBuilder();

        //TODO: sort nameservers alphabetically
        int alphabeticalOrder = 0;

        if(nameservers != null && nameservers.size() != 0){
            for (String key : nameservers.keySet()) {
                alphabeticalOrder++;
                nameserverList.append(alphabeticalOrder).append(". ").append(key).append(" ").append("\n");
            }
            shell.out().println(nameserverList.toString());
        } else shell.out().println("This server manages no new zones");
    }

    @Command
    @Override
    public void addresses() {
        HashMap<String, String> addresses = null;

        if(rootRemote == null){
            System.out.println("ROOT IST NULL");
        }

        if(!isRoot){
            addresses = new HashMap<>(mailboxReferences);
        }

        StringBuilder mailboxList = new StringBuilder();

        int index = 0;
        if(addresses != null && addresses.size() != 0){
            for (String key : addresses.keySet()) {
            index++;
            mailboxList.append(index).append(". ").append(key).append(" ").append(addresses.get(key)).append("\n");
            }

            shell.out().println(mailboxList.toString());
        } else shell.out().println("This server manages no addresses");
    }

    @Override
    public void registerNameserver(String domain, INameserverRemote nameserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        //TODO LOGGING
        System.out.println("Registering nameserver for zone '" + domain + "'");

        String[] splitDomain = domain.split("\\.");
        String topLevel = splitDomain[splitDomain.length-1];

        if(splitDomain.length>=2 && childReferences.containsKey(topLevel)){ //forward Nameserver
            String[] splitDomainWithoutToplevel = Arrays.copyOf(splitDomain, splitDomain.length-1);
            String domainShorterByOne = String.join(".", splitDomainWithoutToplevel);

            childReferences.get(topLevel).registerNameserver(domainShorterByOne, childReferences.get(topLevel)); //recursive
        } else if(splitDomain.length == 1 && !childReferences.containsKey(domain)){ //register Nameserver
            childReferences.put(domain, nameserver);
            System.out.println("I am the parent nameserver, this is my child: " + domain);
        } else if(splitDomain.length == 1 && childReferences.containsKey(domain)){ //already registered
            throw new AlreadyRegisteredException(domain + "is already registered in the nameserver-network!");
        } else {
            throw new InvalidDomainException("Domain is invalid! The responsible nameserver wasnt found.");
        }
    }

    @Override
    public void registerMailboxServer(String domain, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        System.out.println("Registering mailbox " + address + " for " + domain + ".");
        String[] splitDomain = domain.split("\\.");
        String topLevel = splitDomain[splitDomain.length-1];

        if(splitDomain.length>=2 && childReferences.containsKey(topLevel)){ //forward MailboxServer
            String[] splitDomainWithoutToplevel = Arrays.copyOf(splitDomain, splitDomain.length-1);
            String domainShorterByOne = String.join(".", splitDomainWithoutToplevel);

            childReferences.get(topLevel).registerMailboxServer(domainShorterByOne, address);
        } else if(splitDomain.length == 1 && !mailboxReferences.containsKey(domain)){ //register MailboxServer
            mailboxReferences.put(domain, address);
        } else if(splitDomain.length == 1 && mailboxReferences.containsKey(domain)){ //already registered
            throw new AlreadyRegisteredException(domain + "is already registered as a mailbox-server!");
        } else {
            throw new InvalidDomainException("Domain is invalid! The responsible nameserver for the zone " + topLevel + " could not be found.");
        }
    }

    @Override
    public INameserverRemote getNameserver(String zone) throws RemoteException {
        //TODO: LOG out.println("Nameserver for " + zone + " requested.");
            INameserverRemote nameserver =  childReferences.get(zone);

            return nameserver;
    }

    @Override
    public String lookup(String username) throws RemoteException {
        if (mailboxReferences.containsKey(username)) {
            return mailboxReferences.get(username);
        } else return null;
    }

    @Command
    @Override
    public void shutdown() {
        try {
            System.out.println("Unexported remote object!");
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException e) {
            System.err.println("Error while unexporting object: " + e.getMessage());
        }

        try {
            // unbind the remote object so that a client can't find it anymore
            if(isRoot){
                System.out.println("Closed registry!");
                UnicastRemoteObject.unexportObject(registry, true);
                registry.unbind(config.getString("root_id"));
            }
        } catch (Exception e) {
            System.err.println("Error while unbinding object: " + e.getMessage());
        }

        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }
}
