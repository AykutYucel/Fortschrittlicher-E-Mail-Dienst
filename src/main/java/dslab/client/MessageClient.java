package dslab.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.Message;
import dslab.util.Config;
import dslab.util.Keys;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MessageClient implements IMessageClient, Runnable {

    private Config config;
    private Shell shell;
    private ExecutorService executor;
    private SecretKeySpec secretKeySpec;
    private Mac hMac;
    private MessageClientMailboxHandler mcmh;

    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) {
        this.config = config;
        this.executor = Executors.newCachedThreadPool();
        shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(componentId + "> ");
    }

    @Override
    public void run() {
        File hmacFile = new File("./keys/hmac.key"); // constructor of file class having file as argument
        try {
            secretKeySpec = Keys.readSecretKey(hmacFile); // loading shared secret key
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            hMac = Mac.getInstance("HmacSHA256"); // creating a mac instance with sha256 algorithm
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        try {
            hMac.init(secretKeySpec); // mac initialization with shared secret key
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }

        mcmh = new MessageClientMailboxHandler(config);
        executor.execute(mcmh);
        executor.execute(shell);

        System.out.println("Message client is up!");
    }

    @Command
    @Override
    public void inbox() {
        try {
            String list = mcmh.sendAndReadSecure("list");
            String[] listArray = list.split("\n");

            if (listArray.length < 2){
                shell.out().println("Inbox is empty!");
            }else{
                for(String text : listArray){
                    int spaceIndex = text.indexOf(" ");
                    if (spaceIndex != -1){
                        String messageId = text.substring(0, spaceIndex);
                        String messageContent = mcmh.sendAndReadSecure("show " + messageId);
                        shell.out().println("Message ID: "+ messageId);
                        shell.out().println(messageContent);
                    }
                }
            }
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Command
    @Override
    public void delete(String id) {
        try {
            String response = mcmh.sendAndReadSecure("delete " + id);
            shell.out().println(response);
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Command
    @Override
    public void verify(String id) {
        String messageContent;
        try {
            messageContent = (mcmh.sendAndReadSecure("show " + id)).trim();
            String[] messageContentArray = messageContent.split(System.lineSeparator());
            String[] organizedMessageContentArray = new String[5];
            for (int i = 0; i < (messageContentArray.length - 1); i++) {
                organizedMessageContentArray[i] = messageContentArray[i].split(" ", 2)[1];
            }
            String organizedMessageContent = String.join("\n", organizedMessageContentArray[0], organizedMessageContentArray[1], organizedMessageContentArray[2], organizedMessageContentArray[3]);
            byte[] messageContentByte = organizedMessageContent.getBytes();
            hMac.update(messageContentByte);
            byte[] computedHash = hMac.doFinal(); // computedHash is the HMAC of the received plaintext
            byte[] receivedHash = Base64.getDecoder().decode(organizedMessageContentArray[4]); // receivedHash is the HMAC that was sent by the communication partner

            boolean validHash = MessageDigest.isEqual(computedHash, receivedHash);

            if (validHash){
                shell.out().println("Hash value is valid!");
            }else {
                shell.out().println("Hash value is invalid!");
            }

        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Command
    @Override
    public void msg(String to, String subject, String data) {
        try {
            if (mcmh.connectDMTP()){
                String messageContent = String.join("\n", config.getString("transfer.email"), to, subject, data);
                byte[] messageContentByte = messageContent.getBytes();
                hMac.update(messageContentByte);
                String computedHashString = Base64.getEncoder().encodeToString(hMac.doFinal());
                String response = mcmh.sendMessage(to, subject, data, computedHashString);
                shell.out().println(response);
                mcmh.closeDMTP();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Command
    @Override
    public void shutdown() {
        mcmh.close();
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
        System.out.println("Exiting mailbox server shell.");
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
