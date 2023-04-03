package dslab.transfer;

import dslab.Message;
import dslab.nameserver.INameserverRemote;
import dslab.util.Config;

import java.io.*;
import java.net.*;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.MissingResourceException;
import java.util.concurrent.BlockingQueue;

public class MailboxConnection implements Runnable {

    private Socket transferServer;
    private PrintWriter writer;
    private BufferedReader reader;
    private Config config;
    private BlockingQueue<Message> messages;

    private boolean unknownRecipientFailure;
    private String unknownRecipient;

    private boolean domainLookupFailed;

    //remote object of the root nameserver
    private INameserverRemote rootNameserverRemote;

    public MailboxConnection(Config config, BlockingQueue<Message> messages, INameserverRemote rootNameserverRemote) {
        this.config = config;
        this.messages = messages;
        this.rootNameserverRemote = rootNameserverRemote;
    }

    @Override
    public void run() {
        String[] splitRecipientEmailAddress;
        String[] splitRecipientDomain; //Domain of the Mailbox-Server that a mail is being forwarded to

        List<String> uniqueDomains = new LinkedList<>();
        Message failure = null;
        Message message = null;
        try {
            //consumer, waits if necessary
            message = messages.take();
            for (String recipient : message.getRecipients()) {
                splitRecipientEmailAddress = recipient.split("@"); //Domain of the "to"-field that was set in the Mail that is being forwarded

                    String resultOfDomainLookup = lookupDomainOfMailbox(splitRecipientEmailAddress[1]);

                    unknownRecipientFailure = false;

                    if(resultOfDomainLookup != null){ //domain-lookup failed, either because the domain is not known or the nameserver is not running
                        splitRecipientDomain = resultOfDomainLookup.split(":");

                        // prevents sending multiple mails, to recipients of the same domain
                        // ensures that only one email is sent to each domain
                        if (!uniqueDomains.contains(splitRecipientEmailAddress[1]) && splitRecipientDomain.length > 1) {
                            setUpConnectionToMailbox(splitRecipientDomain);
                            System.out.println(splitRecipientEmailAddress[1]);
                            uniqueDomains.add(splitRecipientEmailAddress[1]);
                            send(message);

                            if(unknownRecipientFailure){
                                failure = new Message();
                                sendDeliveryFailure(failure, message, recipient, unknownRecipientFailure);
                            }
                        }
                    }
                    else{
                        // at least one unknown recipient domain
                        // send delivery failure to sender
                        try {
                            failure = new Message();
                            sendDeliveryFailure(failure, message, recipient, unknownRecipientFailure);
                        } catch (MissingResourceException e1) {
                            // unknown sender domain
                            return;
                        } finally {
                            close();
                        }
                    }
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error while forwarding mail: " + e);
        }

        if (failure != null) {
            sendUdpPackage(failure.getSender());
        }
        sendUdpPackage(message.getSender());
    }

    public void setUpConnectionToMailbox(String[] senderDomain) throws IOException {
        String host = senderDomain[0];
        int port = Integer.parseInt(senderDomain[1]);
        transferServer = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(transferServer.getInputStream()));
        writer = new PrintWriter(transferServer.getOutputStream(), true);
    }

    private void sendDeliveryFailure(Message failure, Message message, String recipient, boolean unknownRecipientFailure) throws IOException {
        String[] sender = message.getSender().split("@");
        String[] senderMailboxDomain;

        String resultOfDomainLookup = lookupDomainOfMailbox(sender[1]);

        if(resultOfDomainLookup != null) { //Wenn auch die Sender-Mailbox nicht existiert: domain-lookup failed, either because the domain is not known or the nameserver is not running
            senderMailboxDomain = resultOfDomainLookup.split(":");
            setUpConnectionToMailbox(senderMailboxDomain);
            List<String> recipientFailure = new ArrayList<>();
            recipientFailure.add(message.getSender());
            failure.setRecipients(recipientFailure);
            failure.setSubject("error delivery failure");
            if(unknownRecipientFailure){
                failure.setData("Declined from mailbox - Unknown recipient: " + unknownRecipient);
            }else {
                failure.setData("could not find domain for recipient(s): " + recipient);
            }

            failure.setSender("mailer@" + InetAddress.getLocalHost().getHostAddress());
            send(failure);
        }
        //else - mail verwerfen
    }

    private void send(Message message) {
        String[] request;
        String response;
        try {
            // send to mailbox
            request = parseMessage(message);
            reader.readLine();
            for (String input: request) {
                writer.println(input);
                response = reader.readLine();

                if (response != null && response.contains("error unknown recipient")) {
                    unknownRecipientFailure = true;
                    unknownRecipient = response.substring(24);
                    //break;
                }
                //System.out.println("MailConnection: Request: " + input + ", Response "+ response);
            }
        } catch (IOException e) {
            System.err.println("Error IOException while reading/writing: " + e);
        }
    }

    private void sendUdpPackage(String sender) {
        // send udp package <host>:<port> sender
        DatagramSocket monitor;
        try {
            monitor = new DatagramSocket();
            String udpPacket = InetAddress.getLocalHost().getHostAddress() + ":" + config.getString("tcp.port") + " " + sender;
            byte[] buffer = udpPacket.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(config.getString("monitoring.host")), config.getInt("monitoring.port"));
            //System.out.println("Packet Mailconnection: " + new String(packet.getData()));
            monitor.send(packet);
        } catch (IOException e) {
            System.err.println("Error IOException while creating/sending datagram packet: Request: " + e);

        }
    }

    //iterative MailboxServer-Domain-Lookup
    private String lookupDomainOfMailbox(String recipientDomain){
        INameserverRemote iteratedRemote = rootNameserverRemote;
        String[] splitDomain = recipientDomain.split("\\.");

        boolean lookupFailed = false;

        if(splitDomain != null){
            System.out.println("THE TOP LEVEL DOMAIN IS: " + splitDomain[0]);
        } else System.out.println("THE DOMAIN IS NON EXISTENT");

        for (int i = splitDomain.length - 1; i > 0; i--) { //Mailboxserver earth.planet kann existieren, ohne dass Nameserver earth.planet existieren muss (deshalb geht der Index bis exkl. 0)
            try {
                iteratedRemote = iteratedRemote.getNameserver(splitDomain[i]);

                if(iteratedRemote == null){
                    System.err.println("Error while getting Nameserver: Nameserver for zone " + splitDomain[i] + " does not exist.");
                    lookupFailed = true;
                    break;
                }
            } catch (RemoteException e) {
                System.err.println("Error: RemoteException while getting Nameserver: " + splitDomain[i] + e);
                lookupFailed = true;
                break;
            }
        }

        if(!lookupFailed){ //falls der Nameserver gefunden wurde, der die Recipient-Mailbox managen sollte
            try {
                return iteratedRemote.lookup(splitDomain[0]);
            } catch (RemoteException e) {
                //TODO: loggen, dass zwar der Nameserver gefunden wurde, in dem die Mailbox gespeichert sein sollte, aber der Lookup fehlgeschlagen ist (Mailbox server nicht online)
                e.printStackTrace();
            }
        }

        //wenn null, dann wird die failure-Mail abgeschickt
        return null;
    }

    private String[] parseMessage(Message message) {
        String recipients = "";
        for (String recipient : message.getRecipients()) {
            recipients = recipients + recipient + ", ";
        }
        recipients = recipients.substring(0, recipients.length() - 2);
        return new String[]{
                "begin",
                "from " + message.getSender(),
                "to " + recipients,
                "subject " + message.getSubject(),
                "data " + message.getData(),
                "hash " + message.getHash(),
                "send",
                "quit"
        };
    }

    private void close() {
        if (transferServer != null  && !transferServer.isClosed()) {
            try {
                writer.close();
                reader.close();
                transferServer.close();
            } catch (IOException e) {
                // Ignored because we cannot handle it
            }
        }
    }
}
