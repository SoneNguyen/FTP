package ftp;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class Client {

    Socket controlSocket;
    BufferedReader controlIn;
    PrintWriter controlOut;

    // mask password so it doesn't show in log
    private void send(String command) {
        String logged = command.startsWith("PASS ") ? "PASS *********" : command;
        System.out.println(">>> " + logged);
        controlOut.println(command);
    }

    // validate the response code
    private ResponseParser expect(int code) throws IOException {
        ResponseParser response = ResponseParser.parse(controlIn);
        System.out.println("<<< [" + code + "] " + response);
        if (!response.is(code)) throw new ClientException(response);
        return response;
    }

    // mkdir is the only command that accepts two codes (257 = created, 550 = already exists)
    private void expectMkd() throws IOException {
        ResponseParser response = ResponseParser.parse(controlIn);
        System.out.println("<<< [257/550] " + response);
        if (!response.is(257) && !response.is(550)) throw new ClientException(response); // anything else is a real error
    }

    public void connect(String host) throws IOException {
        System.out.println("[CONNECT] Connecting to " + host + ":21");
        controlSocket = new Socket(host, 21);                                                            // open control connection on port 21
        controlIn     = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));        // wrap input stream for reading text lines
        controlOut    = new PrintWriter(controlSocket.getOutputStream(), true);                          // wrap output stream, auto-flush on println
        expect(220);                                                                                      // welcome banner
        System.out.println();
    }

    public void quit() throws IOException {
        System.out.println("[QUIT]");
        send("QUIT");
        expect(221);           // server confirms goodbye
        controlSocket.close(); // close the control connection
        System.out.println();
    }

    // 530 from server means wrong password — stays on login screen
    public void login(String username, String password) throws IOException {
        System.out.println("[LOGIN] " + username);
        send("USER " + username);
        expect(331);                              // server asks for password
        send("PASS " + password);
        ResponseParser response = ResponseParser.parse(controlIn);  // read login result manually to distinguish 530
        if (response.is(530)) throw new ClientException(response);   // wrong credentials
        if (!response.is(230)) throw new ClientException(response);  // anything else unexpected
        System.out.println();
    }

    public String pwd() throws IOException {
        System.out.println("[PWD]");
        send("PWD");
        ResponseParser response = expect(257);     // 257 carries the current path in quotes
        System.out.println();
        // response format: 257 "/some/path" is current directory
        String msg = response.message;
        int start = msg.indexOf('"');
        int end   = msg.lastIndexOf('"');
        return (start >= 0 && end > start) ? msg.substring(start + 1, end) : msg; // extract path between quotes
    }

    public void cd(String dir) throws IOException {
        System.out.println("[CD] " + dir);
        send("CWD " + dir);
        expect(250);    // 250 = command successful
        System.out.println();
    }

    /*
    PASV gives ip+port as (h1,h2,h3,h4,p1,p2).
    We ignore the ip and use the control socket's address to handle NAT.
    */
    private Socket openDataConnection() throws IOException {
        return openDataConnection(5, 0); // default: 5 retries, first attempt
    }

    private Socket openDataConnection(int retriesLeft, int attempt) throws IOException {
        send("PASV");
        ResponseParser response = expect(227);                 // 227 = entering passive mode, contains ip+port

        PassiveAddressParser address = PassiveAddressParser.parse(response);      // extract ip and port from PASV response
        System.out.println("    Address: " + address);

        Socket dataSocket = new Socket();                   // create unconnected socket so we can set a timeout
        try {
            dataSocket.connect(new InetSocketAddress(controlSocket.getInetAddress(), address.port), 5000); // use control socket's address to avoid NAT issues
            if (attempt > 0) System.out.println();          // newline after the animated retry dots
            System.out.println("    Data socket opened on port: " + dataSocket.getPort());
            return dataSocket;
        } catch (IOException e) {
            dataSocket.close();
            if (retriesLeft <= 1) {
                System.out.println(); // newline before the exception
                throw e;
            }
            String dots = ".".repeat((attempt % 3) + 1);
            System.out.print("\r    Data connection failed, retrying" + dots + "   "); // animated dots
            return openDataConnection(retriesLeft - 1, attempt + 1);                   // recursive retry
        }
    }

    public List<String> ls() throws IOException {
        System.out.println("[LS]");
        Socket dataSocket = openDataConnection(); // open data channel first, then send LIST

        send("LIST");
        expect(150);          // 150 = data connection opening, transfer starting

        List<String> files = new ArrayList<>();
        BufferedReader dataIn = new BufferedReader(new InputStreamReader(dataSocket.getInputStream())); // read listing line by line
        String line;
        while ((line = dataIn.readLine()) != null) {
            System.out.println("    " + line);
            files.add(line);
        }

        dataSocket.close(); // close data socket before reading 226
        expect(226);        // 226 = transfer complete
        System.out.println();
        return files;
    }

    // 550 means already exists
    public void mkdir(String dir) throws IOException {
        System.out.println("[MKDIR] " + dir);
        send("MKD " + dir);
        expectMkd();        // 257 = created, 550 = already exists — both are acceptable
        System.out.println();
    }

    public void rmdir(String dir) throws IOException {
        System.out.println("[RMDIR] " + dir);
        send("RMD " + dir);
        expect(250);    // 250 = command successful
        System.out.println();
    }

    // TYPE I = binary mode so file bytes aren't modified in transit
    public void get(String remoteFilename, String localPath) throws IOException {
        System.out.println("[GET] " + remoteFilename + " -> " + localPath);

        send("TYPE I");
        expect(200);    // 200 = binary mode set

        Socket dataSocket = openDataConnection(); // open data channel before sending RETR
        send("RETR " + remoteFilename);           // only the remote filename goes to server
        expect(150);                              // 150 = file status okay, about to open data connection

        InputStream      dataIn  = dataSocket.getInputStream();
        FileOutputStream fileOut = new FileOutputStream(localPath); // full local path to write
        byte[] buffer = new byte[4096];
        int bytesRead, total = 0;
        while ((bytesRead = dataIn.read(buffer)) != -1) { // read until server closes the data connection
            fileOut.write(buffer, 0, bytesRead);
            total += bytesRead;
        }
        System.out.println("    Downloaded: " + total + " bytes -> " + localPath);

        fileOut.close();    // flush and close file before closing socket
        dataSocket.close(); // closing data socket signals end of transfer to server
        expect(226);        // 226 = transfer complete
        System.out.println();
    }

    // localPath = full path to read from disk; remoteName = bare filename sent to STOR
    public void put(String localPath, String remoteName) throws IOException {
        System.out.println("[PUT] " + localPath + " -> " + remoteName);

        send("TYPE I");
        expect(200);    // 200 = binary mode set

        Socket dataSocket = openDataConnection(); // open data channel before sending STOR
        send("STOR " + remoteName);              // just the filename, not the full local path
        expect(150);                             // 150 = ready to receive data

        FileInputStream fileIn  = new FileInputStream(localPath);
        OutputStream    dataOut = dataSocket.getOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead, total = 0;
        while ((bytesRead = fileIn.read(buffer)) != -1) { // read local file and stream to server
            dataOut.write(buffer, 0, bytesRead);
            total += bytesRead;
        }
        System.out.println("    Uploaded: " + total + " bytes <- " + localPath);

        fileIn.close();  // done reading the file
        dataOut.close(); // closing the output stream sends TCP FIN — signals end of file to server
        dataSocket.close();
        expect(226);     // 226 = transfer complete, server confirms it received everything
        System.out.println();
    }

    public void delete(String filename) throws IOException {
        System.out.println("[DELETE] " + filename);
        send("DELE " + filename);
        expect(250);    // 250 = file deleted successfully
        System.out.println();
    }
}