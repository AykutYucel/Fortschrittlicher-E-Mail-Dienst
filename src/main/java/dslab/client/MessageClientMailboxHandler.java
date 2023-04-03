package dslab.client;

import dslab.util.Config;
import dslab.util.Keys;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.io.*;
import java.net.Socket;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;

public class MessageClientMailboxHandler implements Runnable {

    private Config config;
    private Socket client;
    private PrintWriter writer;
    private BufferedReader reader;
    private Socket clientDMTP;
    private PrintWriter writerDMTP;
    private BufferedReader readerDMTP;
    private Cipher decoder;
    private Cipher encoder;
    private String user;
    private int password;

    public MessageClientMailboxHandler(Config config) {
        this.config = config;
        this.user = config.getString("mailbox.user");
        this.password = config.getInt("mailbox.password");
    }


    @Override
    public void run() {
        try {
            if (connect() && startSecure()) {
                login();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean startSecure() throws Exception {
        String request;
        String response;
        // first client request
        response = sendAndRead("startsecure");

        // first server response
        if (response.startsWith("ok mailbox-")) {
            // AES Secret Key
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256);
            SecretKey secretKey = keyGenerator.generateKey();

            // challenge & iv
            byte[] challenge = new byte[32];
            byte[] iv = new byte[16];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(challenge);
            secureRandom.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // AES cipher
            decoder = Cipher.getInstance("AES/CTR/NoPadding");
            decoder.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            encoder = Cipher.getInstance("AES/CTR/NoPadding");
            encoder.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

            // server: ok <component-id>
            // get server public key
            String componentId = response.split(" ")[1];
            PublicKey publicKeyRSA = Keys.readPublicKey(new File("keys/client/" + componentId + "_pub.der"));

            //RSA cipher
            Cipher cipherRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipherRSA.init(Cipher.ENCRYPT_MODE, publicKeyRSA);

            // second client request
            // ok <challenge> <secret-key> <iv>
            request = "ok " +
                    Base64.getEncoder().encodeToString(challenge) + " " +
                    Base64.getEncoder().encodeToString(secretKey.getEncoded()) + " " +
                    Base64.getEncoder().encodeToString(iv);

            // RSA encrypt and encode again
            byte[] encryptedRequest = cipherRSA.doFinal(request.getBytes());
            String encryptedRequestBase64 = Base64.getEncoder().encodeToString(encryptedRequest);

            // send Base64 encoded, RSA encrypted request to mailbox
            send(encryptedRequestBase64);

            // second server response
            // AES: ok <client-challenge>
            response = readSecure();
            byte[] responseChallenge = Base64.getDecoder().decode(response.split(" ")[1]);

//            System.out.println();
//            System.out.println("challenge         : " + Base64.getEncoder().encodeToString(challenge));
//            System.out.println("challenge response: " + Base64.getEncoder().encodeToString(responseChallenge));

            // check if both challenge and responseChallenge have the same content
            if (Arrays.equals(challenge, responseChallenge)) {
                // third client request
                // ok
                sendSecure("ok");
//                System.out.println("AES sollte jetzt gehen");
                return true;
            } else {
//                System.out.println("challenges stimmen nicht überein!");
                return false;
            }
        } else if (response.startsWith("error")) {
            close();
            return false;
        } else {
            // sollte nie passieren
            close();
            return false;
        }
    }

    public boolean login() throws BadPaddingException, IOException, IllegalBlockSizeException {
        return sendAndReadSecure("login " + user + " " + password).equals("ok");
    }

    public boolean connect() throws IOException {
        client = new Socket(config.getString("mailbox.host"), config.getInt("mailbox.port"));
        writer = new PrintWriter(client.getOutputStream(), true);
        reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
        return read().equals("ok DMAP2.0");
    }

    public boolean connectDMTP() throws IOException {
        clientDMTP = new Socket(config.getString("transfer.host"), config.getInt("transfer.port"));
        writerDMTP = new PrintWriter(clientDMTP.getOutputStream(), true);
        readerDMTP = new BufferedReader(new InputStreamReader(clientDMTP.getInputStream()));
        return readDMTP().equals("ok DMTP2.0");
    }

    public String sendMessage(String to, String subject, String data, String hash){
        try {
            String response;
            response = sendAndReadDMTP("begin");
            if(!response.startsWith("ok")){ return response; }
            response = sendAndReadDMTP("from " + config.getString("transfer.email"));
            if(!response.startsWith("ok")){ return response; }
            response = sendAndReadDMTP("to " + to);
            if(!response.startsWith("ok")){ return response; }
            response = sendAndReadDMTP("subject " + subject);
            if(!response.startsWith("ok")){ return response; }
            response = sendAndReadDMTP("data " + data);
            if(!response.startsWith("ok")){ return response; }
            response = sendAndReadDMTP("hash " + hash);
            if(!response.startsWith("ok")){ return response; }
            response = sendAndReadDMTP("send");
            if(!response.startsWith("ok")){ return response; }
        } catch (IOException e) {
            return "Message delivery failure: " + e.getMessage();
        }
        return "ok";
    }

    public String sendAndReadDMTP(String s) throws IOException {
        sendDMTP(s);
        return readDMTP();
    }

    public void sendDMTP(String s) {
        writerDMTP.println(s);
    }

    public String readDMTP() throws IOException {
        return readerDMTP.readLine();
    }

    public String read() throws IOException {
        return reader.readLine();
    }

    public String readSecure() throws IOException, BadPaddingException, IllegalBlockSizeException {
        String s = read();
        return s == null ? null : decode(s);
    }

    public void sendSecure(String s) throws BadPaddingException, IllegalBlockSizeException {
        writer.println(encode(s));
    }

    public void send(String s) {
        writer.println(s);
    }

    public String sendAndRead(String s) throws IOException {
        send(s);
        return read();
    }

    public String sendAndReadSecure(String s) throws IOException, BadPaddingException, IllegalBlockSizeException {
        sendSecure(s);
        return readSecure();
    }

    public String decode(String s) throws BadPaddingException, IllegalBlockSizeException {
        return new String(decoder.doFinal(Base64.getDecoder().decode(s.getBytes())));
    }

    public String encode(String s) throws BadPaddingException, IllegalBlockSizeException {
        return Base64.getEncoder().encodeToString(encoder.doFinal(s.getBytes()));
    }

    public void closeDMTP() {
        if (clientDMTP != null && !clientDMTP.isClosed()) {
            try {
                writerDMTP.close();
                readerDMTP.close();
                clientDMTP.close();
            } catch (IOException e) {
                // Ignored because we cannot handle it
            }
        }
    }

    public void close() {

        try {
            sendAndReadSecure("quit");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }

        if (client != null && !client.isClosed()) {
            try {
                writer.close();
                reader.close();
                client.close();
            } catch (IOException e) {
                // Ignored because we cannot handle it
            }
        }
    }
}
