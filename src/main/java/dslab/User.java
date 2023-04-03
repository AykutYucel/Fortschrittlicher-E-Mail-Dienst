package dslab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class User {
    private String name;
    private ConcurrentHashMap<Integer, Message> messages;

    public User(String name) {
        this.name = name;
        messages = new ConcurrentHashMap<>();
    }

    public void addMessage(Message message) {
        if (!this.messages.isEmpty()) {
            List<Integer> keys = new ArrayList<>(this.messages.keySet());
            Collections.sort(keys, Collections.reverseOrder());
            this.messages.put(keys.get(0) + 1, message);
        } else {
            this.messages.put(this.messages.size() + 1, message);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConcurrentHashMap<Integer, Message> getMessages() {
        return messages;
    }

    public void setMessages(ConcurrentHashMap<Integer, Message> messages) {
        this.messages = messages;
    }
}
