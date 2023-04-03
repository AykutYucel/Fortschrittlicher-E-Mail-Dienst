package dslab.monitoring;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ConcurrentHashMap;

public class MonitoringHandler implements Runnable {

    private DatagramSocket datagramSocket;
    private ConcurrentHashMap<String, Integer> addresses;
    private ConcurrentHashMap<String, Integer> servers;

    public MonitoringHandler(DatagramSocket datagramSocket, ConcurrentHashMap<String, Integer> addresses, ConcurrentHashMap<String, Integer> servers) {
        this.datagramSocket = datagramSocket;
        this.addresses = addresses;
        this.servers = servers;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (true) {
            try {
                datagramSocket.receive(packet);
            } catch (IOException e) {
                System.err.println("Error IOException while receiving packet: " + e);
                break;
            }
            String request = new String(packet.getData(), 0, packet.getLength());
            String[] parts = request.split("\\s");
            if (parts.length == 2) {
                // remove 0 bytes from packet
                String transferServer = parts[0].trim();
                String clientAddress = parts[1].trim();
                if (servers.containsKey(transferServer)) {
                    int count = servers.get(transferServer);
                    servers.put(transferServer, count + 1);
                } else {
                    servers.put(transferServer, 1);
                }
                if (addresses.containsKey(clientAddress)) {
                    int count = addresses.get(clientAddress);
                    addresses.put(clientAddress, count + 1);
                } else {
                    addresses.put(clientAddress, 1);
                }
            }
        }
    }
}
