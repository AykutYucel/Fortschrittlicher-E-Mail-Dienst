package dslab.protocol;

import dslab.Message;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DMTP {

    private static final int WAITING = 0;
    private static final int BEGIN = 1;
    private static final int EMAIL = 2;
    private int state = WAITING;
    private Message message;

    public DMTP() {
        message = new Message();
    }

    public Message getMessage() {
        return message;
    }

    public String processInput(String request) {
        String response = null;
        String[] parts;
        if (request.equalsIgnoreCase("quit")) {
            return "ok bye";
        }
        if (state == WAITING) {
            state = BEGIN;
            return "ok DMTP2.0";
        } else if (state == BEGIN) {
            if (request.equalsIgnoreCase("begin")) {
                response = "ok";
                state = EMAIL;
            } else {
                response = "error protocol error";
            }
        } else if (state == EMAIL) {
            if (request.startsWith("subject ")) {
                parts = request.split("^(subject )");
                if (parts.length <= 1) {
                    return "error no subject";
                }
                message.setSubject(parts[1]);
                response = "ok";
            } else if (request.startsWith("from ")){
                parts = request.split("^(from )");
                if (parts.length <= 1) {
                    return "error no sender";
                }
                Matcher matcher = Pattern.compile("(^\\w+@\\w+\\.\\w+$)").matcher(parts[1]);
                if (matcher.find()) {
                    message.setSender(matcher.group());
                } else {
                    return "error invalid sender";
                }
                response = "ok";
            } else if (request.startsWith("data")){
                parts = request.split("^(data )");
                if (parts.length <= 1) {
                    return  "error no content";
                }
                message.setData(parts[1]);
                response = "ok";
            } else if (request.startsWith("to ")) {
                parts = request.split("^(to )");
                if (parts.length <= 1) {
                    return "error no recipients";
                }
                if (!message.getRecipients().isEmpty()) {
                    message.getRecipients().clear();
                }
                Matcher matcher = Pattern.compile("(\\w+@\\w+\\.\\w+)").matcher(parts[1]);
                while (matcher.find()) {
                    message.getRecipients().add(matcher.group());
                }
                if (message.getRecipients().size() < 1) {
                    return "error no recipients";
                }
                response = "ok " + message.getRecipients().size();
            } else if (request.startsWith("hash")) {
                parts = request.split("^(hash )");
                if (parts.length <= 1) {
                    return "error no hash value";
                }
                message.setHash(parts[1]);
                response = "ok";
            } else if (request.equalsIgnoreCase("send")) {
                if (message.getSubject() == null || message.getSubject().equalsIgnoreCase("")) {
                    return "error no subject";
                }
                if (message.getSender() == null || message.getSender().equalsIgnoreCase("")) {
                    return "error no sender";
                }
                if (message.getData() == null || message.getData().equalsIgnoreCase("")) {
                    return "error no content";
                }
                if (message.getRecipients() == null || message.getRecipients().size() < 1) {
                    return "error no recipients";
                }
                response = "ok";
            } else {
                return "error protocol error";
            }
        }
        return response;
    }

    public String validateRequest(String request, Set<String> users, String domain) {
        String response = null;
        String[] parts;
        if (request.equalsIgnoreCase("quit")) {
            return "ok bye";
        }
        if (state == WAITING) {
            state = BEGIN;
            return "ok DMTP2.0";
        } else if (state == BEGIN) {
            if (request.equalsIgnoreCase("begin")) {
                response = "ok";
                state = EMAIL;
            } else {
                response = "error protocol error";
            }
        } else if (state == EMAIL) {
            if (request.startsWith("subject ")) {
                parts = request.split("^(subject )");
                if (parts.length <= 1) {
                    return "error no subject";
                }
                message.setSubject(parts[1]);
                response = "ok";
            } else if (request.startsWith("from ")){
                parts = request.split("^(from )");
                if (parts.length <= 1) {
                    return "error no sender";
                }
                message.setSender(parts[1]);
                response = "ok";
            } else if (request.startsWith("data")) {
                parts = request.split("^(data )");
                if (parts.length <= 1) {
                    return "error no content";
                }
                message.setData(parts[1]);
                response = "ok";
            } else if (request.startsWith("to ")) {
                parts = request.split("^(to )");
                if (parts.length <= 1) {
                    return "error no recipients";
                }
                if (!message.getRecipients().isEmpty()) {
                    message.getRecipients().clear();
                }
                Matcher matcher = Pattern.compile("(\\w+@" + domain + ")").matcher(parts[1]);
                String unknownRecipients = "error unknown recipient ";
                message.getRecipients().add(parts[1].replace(",", ""));
                while (matcher.find()) {
                    String recipient = matcher.group();
//                    message.getRecipients().add(recipient);
                    if (!users.contains(recipient.split("@")[0])) {
                        unknownRecipients = unknownRecipients  + recipient.split("@" + domain)[0] + " ";
                    }
                }
                if (!unknownRecipients.equals("error unknown recipient ")) {
                    return unknownRecipients;
                }
                if (message.getRecipients().size() < 1) {
                    return "error no valid recipients";
                }
                response = "ok " + message.getRecipients().size();
            } else if (request.contains("hash ")) {
                parts = request.split("^(hash )");
                if (parts.length <= 1) {
                    return "error no hash value";
                }
                message.setHash(parts[1]);
                response = "ok";
            } else if (request.equalsIgnoreCase("send")) {
                response = "ok";
            } else {
                return "error protocol error";
            }
        }
        return response;
    }
}
