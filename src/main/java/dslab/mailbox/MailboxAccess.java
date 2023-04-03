package dslab.mailbox;

import dslab.User;
import dslab.protocol.DMAP;
import dslab.util.Config;
import dslab.util.Keys;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.security.*;
import java.util.Base64;
import java.util.List;

public class MailboxAccess implements Runnable {

    private Config userConfig;
    private PrintWriter writer;
    private BufferedReader reader;
    private List<User> users;
    private Socket client;
    private String componentId;
    private boolean secure;
    private boolean aesEncryption;
    private Cipher decoder;
    private Cipher encoder;
    private SecretKey originalKey;
    private IvParameterSpec ivSpec;


    public MailboxAccess(Socket socket, Config userConfig, List<User> users, String componentId)  {
        this.componentId = componentId;
        this.client = socket;
        this.userConfig = userConfig;
        this.users = users;
        this.secure = false;
        this.aesEncryption = false;
    }

    @Override
    public void run() {
        try {
            writer = new PrintWriter(client.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            String request;
            String response;
            DMAP dmap = new DMAP(userConfig, users, componentId);
            // ok DMAP2.0
            response = dmap.processInput("");
            send(response);
            while (true) {
                if (aesEncryption && (request = readSecure()) != null) {
                    // decrypt & decode request
                    response = dmap.processInput(request);
                    // encrypt & encode response
                    sendSecure(response);
                } else if ((request = read()) != null){
                    // without encryption
                    if (!secure && request.equalsIgnoreCase("startsecure")) {
                        secure = true;
                    }
                    // second client request
                    // client-challenge, secret key and iv
                    if (secure && !request.equalsIgnoreCase("startsecure")) {
                        aesEncryption = startSecure(request, dmap);
                        if (!aesEncryption) {
                            break;
                        } else {
                            continue;
                        }
                    }
                    response = dmap.processInput(request);
                    send(response);
                }
                if (response.equals("ok bye") || response.equals("error protocol error")) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    public boolean startSecure(String input, DMAP dmap) {
        try {
            // get server private key
            PrivateKey privateKeyRSA = Keys.readPrivateKey(new File("keys/server/" + componentId + ".der"));

            //RSA cipher
            Cipher cipherRSA = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipherRSA.init(Cipher.DECRYPT_MODE, privateKeyRSA);

            // decrypt & decode request
            byte[] decodedRequestRSA = Base64.getDecoder().decode(input);
            byte[] decryptedRequestRSA = cipherRSA.doFinal(decodedRequestRSA);

            // 0 - ok
            // 1 - challenge
            // 2 - secret key
            // 3 - iv
            String[] requestParts = new String(decryptedRequestRSA).split(" ");
            byte[] challenge = Base64.getDecoder().decode(requestParts[1]);
            byte[] secretKey = Base64.getDecoder().decode(requestParts[2]);
            byte[] iv = Base64.getDecoder().decode(requestParts[3]);

            originalKey = new SecretKeySpec(secretKey, "AES");
            ivSpec = new IvParameterSpec(iv);

            // AES cipher
            decoder = Cipher.getInstance("AES/CTR/NoPadding");
            decoder.init(Cipher.DECRYPT_MODE, originalKey, ivSpec);
            encoder = Cipher.getInstance("AES/CTR/NoPadding");
            encoder.init(Cipher.ENCRYPT_MODE, originalKey, ivSpec);

            // second server response
            // AES: ok <client-challenge>
            sendSecure("ok " + Base64.getEncoder().encodeToString(challenge));

            // third client request
            // wait for client ok
            String request;
            if ((request = readSecure()) != null) {
                // challenges match
                // start using aes encryption
                if (request.equals("ok")) {
                    String response = dmap.processInput(request);
                    return true;
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            close();
        }
        return false;
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

    private void close() {
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
