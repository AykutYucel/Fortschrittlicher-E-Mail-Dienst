package dslab.mailbox;

import dslab.Message;
import dslab.User;
import dslab.protocol.DMTP;
import dslab.util.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailboxHandler implements Runnable {

    private Socket socket;
    private List<User> users;
    private Config userConfig;
    private String domain;
    private PrintWriter writer;
    private BufferedReader reader;
    private DMTP dmtp;

    public MailboxHandler(Socket socket, List<User> users, Config userConfig, String domain) {
        this.socket = socket;
        this.users = users;
        this.userConfig = userConfig;
        this.domain = domain;
    }

    @Override
    public void run() {
        try {
            while (true) {
                this.reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
                this.writer = new PrintWriter(this.socket.getOutputStream(), true);
                String request = "";
                String response = "";
                dmtp = new DMTP();
                // ok DMTP
                response = dmtp.validateRequest(request, userConfig.listKeys(), domain);
                writer.println(response);
                while ((request = reader.readLine()) != null) {
                    response = dmtp.validateRequest(request, userConfig.listKeys(), domain);
                    writer.println(response);
                    System.out.println(request + " " + response);
                    if (response.equals("ok bye") || response.equals("error protocol error")) {
                        break;
                    } else if (request.equals("send") && response.equals("ok")) {
                        send();
                    }
                    //System.out.println("From mailbox: Request: " + request + ", Response " + response);
                }
                if (response.equals("ok bye") || response.equals("error protocol error")) {
                    break;
                }
            }
        } catch (SocketException e) {
            System.err.println("Error SocketException while handling message: " + e);
        } catch (IOException e) {
            System.err.println("Error IOException while handling message: " + e);
        } finally {
            close();
        }
    }

    private void send(){
        Message message = dmtp.getMessage();
        String recipient = message.getRecipients().get(0);
        Matcher matcher = Pattern.compile("(\\w+@" + domain + ")").matcher(recipient);
        while (matcher.find()) {
            String recipientName = matcher.group();
            recipientName = recipientName.split("@")[0];
            User newUser = new User(recipientName);
            if (!userConfig.containsKey(recipientName)) {
                continue;
            }
            if (!checkIfUserExists(recipientName)) {
                users.add(newUser);
            }
            addMessageToUser(recipientName, message);
        }
    }

    private boolean checkIfUserExists(String name) {
        if (users != null) {
            for (User user : users) {
                if (user.getName().equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addMessageToUser(String name, Message message) {
        if (users != null) {
            for (User user : users) {
                if (user.getName().equals(name)) {
                    synchronized (users) {
                        user.addMessage(message);
                    }
                    break;
                }
            }
        }
    }

    private void close() {
        if (socket != null && !socket.isClosed()) {
            try {
                writer.close();
                reader.close();
                socket.close();
            } catch (IOException e) {
                // Ignored because we cannot handle it
            }
        }
    }
}
