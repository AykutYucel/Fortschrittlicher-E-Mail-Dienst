package dslab.protocol;

import dslab.Message;
import dslab.User;
import dslab.util.Config;

import java.util.List;
import java.util.Map;

public class DMAP {

    private static final int WAITING = 0;
    private static final int LOGIN = 1;
    private static final int COMMANDS = 2;
    private static final int SECURE = 3;
    private int state = WAITING;
    private Config userConfig;
    private List<User> users;
    private User loggedInUser;
    private boolean secure;
    private String componentId;

    public DMAP(Config userConfig, List<User> users, String componentId) {
        this.componentId = componentId;
        this.userConfig = userConfig;
        this.users = users;
        this.secure = false;
    }

    private boolean login(String user, String password) {
        return userConfig.getString(user).equals(password);
    }

    private String show(int id) {
        if (!loggedInUser.getMessages().containsKey(id)) {
            return "error unknown message id";
        }
        Message message = loggedInUser.getMessages().get(id);
        String recipients = "";
        for (String recipient : message.getRecipients()) {
            recipients = recipients + recipient;
        }
        recipients = recipients.replace(" ", ",");
        //System.out.println(recipients);
        //recipients = recipients.substring(0, recipients.length() - 2);
        return "from " + message.getSender() + System.lineSeparator() +
                "to " + recipients + System.lineSeparator() +
                "subject " + message.getSubject() + System.lineSeparator() +
                "data " + message.getData() + System.lineSeparator() +
                "hash " + ((message.getHash() != null) ? message.getHash() : "") + System.lineSeparator() +
                "ok";
    }

    private String delete(int id) {
        if (!loggedInUser.getMessages().containsKey(id)) {
            return "error unknown message id";
        }
        loggedInUser.getMessages().remove(id);
        return "ok";
    }

    public String processInput(String request) {
        String[] parts;
        if (request.equalsIgnoreCase("quit")) {
            loggedInUser = null;
            state = LOGIN;
            secure = false;
            return "ok bye";
        }
        if (request.equalsIgnoreCase("startsecure") && !secure) {
            state = SECURE;
            secure = true;
            return "ok " + componentId;
        }
        if (state == WAITING) {
            state = LOGIN;
            return "ok DMAP2.0";
        } else if (state == SECURE) {
            if (request.equalsIgnoreCase("ok")) {
                state = LOGIN;
            } else {
                loggedInUser = null;
                state = WAITING;
                secure = false;
                return "";
            }
        } else if (state == LOGIN) {
            if (loggedInUser != null) {
                state = COMMANDS;
            }
            if (request.startsWith("login ")) {
                // login user password
                parts = request.split("\\s");
                if (parts.length == 3) {
                    String name = parts[1];
                    String password = parts[2];
                    if (!userConfig.containsKey(parts[1])) {
                        return "error user not found";
                    }
                    if (login(name, password)) {
                        state = COMMANDS;
                        for (User user : users) {
                            if (user.getName().equals(name)) {
                                loggedInUser = user;
                                break;
                            }
                        }
                        if (loggedInUser == null) {
                            loggedInUser = new User(name);
                            users.add(loggedInUser);
                        }
                        return "ok";
                    } else {
                        return "error wrong password";
                    }
                } else {
                    return "error protocol error";
                }
            } else {
                return "error protocol error";
            }
        } else if (state == COMMANDS) {
            if (request.equalsIgnoreCase("logout")) {
                state = LOGIN;
                loggedInUser = null;
                secure = false;
                return "ok";
            }
            if (loggedInUser == null) {
                return "no data available for this user";
            }
            if (request.equalsIgnoreCase("list")) {
                String list = "";
                    for (Map.Entry<Integer, Message> entry : loggedInUser.getMessages().entrySet()) {
                        list = list + entry.getKey() + " " + entry.getValue().getSender() + " " + entry.getValue().getSubject() + System.lineSeparator();
                    }
                return list != "" ? list.trim() + System.lineSeparator() + "ok" : "ok";
            } else if (request.startsWith("show")) {
                parts = request.split("\\s");
                if (parts.length <= 1) {
                    return "error no message id given";
                } else if (parts.length > 2) {
                    return "error to many parameter";
                }
                return show(Integer.parseInt(parts[1]));
            } else if (request.startsWith("delete")) {
                parts = request.split("\\s");
                if (parts.length <= 1) {
                    return "error no message id given";
                } else if (parts.length > 2) {
                    return "error to many parameter";
                }
                return delete(Integer.parseInt(parts[1]));
            }
        }
        return "error unknown error";
    }
}
