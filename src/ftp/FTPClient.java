package ftp;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class FTPClient {

    private Socket controlSocket;
    private BufferedReader controlIn;
    private PrintWriter controlOut;

    private FTPResponse readResponse() throws IOException {
        return FTPResponse.parse(controlIn);
    }

    // mask password so it doesn't show in log
    private void send(String command) {
        String logged = command.startsWith("PASS ") ? "PASS *********" : command;
        System.out.println(">>> " + logged);
        controlOut.println(command);
    }

    // validate the response code
    private FTPResponse expect(int code) throws IOException {
        FTPResponse response = readResponse();
        System.out.println("<<< [" + code + "] " + response);
        if (!response.is(code)) throw new FTPException(response);
        return response;
    }

    // some commands are fine with two codes (e.g. mkdir accepts 257 or 550)
    private FTPResponse expect(int code1, int code2) throws IOException {
        FTPResponse response = readResponse();
        System.out.println("<<< [" + code1 + "/" + code2 + "] " + response);
        if (!response.is(code1) && !response.is(code2)) throw new FTPException(response);
        return response;
    }

    public void connect(String host) throws IOException {
        System.out.println("[CONNECT] Connecting to " + host + ":21");
        controlSocket = new Socket(host, 21);
        controlIn  = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
        controlOut = new PrintWriter(controlSocket.getOutputStream(), true);
        expect(220); // welcome banner
        System.out.println();
    }

    public void quit() throws IOException {
        System.out.println("[QUIT]");
        send("QUIT");
        expect(221);
        controlSocket.close();
        System.out.println();
    }

    // 530 from server means wrong password — stays on login screen
    public void login(String username, String password) throws IOException {
        System.out.println("[LOGIN] " + username);
        send("USER " + username);
        expect(331);
        send("PASS " + password);
        FTPResponse response = readResponse();
        if (response.is(530)) throw new FTPException(response);
        if (!response.is(230)) throw new FTPException(response);
        System.out.println();
    }

    public String pwd() throws IOException {
        System.out.println("[PWD]");
        send("PWD");
        FTPResponse response = expect(257);
        System.out.println();
        // response format: 257 "/some/path" is current directory
        String msg = response.message;
        int start = msg.indexOf('"');
        int end   = msg.lastIndexOf('"');
        return (start >= 0 && end > start) ? msg.substring(start + 1, end) : msg;
    }

    public void cd(String dir) throws IOException {
        System.out.println("[CD] " + dir);
        send("CWD " + dir);
        expect(250);
        System.out.println();
    }

    // retry 5 times since some ports get blocked by firewall
    private Socket openDataConnection() throws IOException {
        return openDataConnection(5);
    }

    /*
    PASV gives ip+port as (h1,h2,h3,h4,p1,p2).
    We ignore the ip and use the control socket's address to handle NAT.
    */
    private Socket openDataConnection(int retriesLeft) throws IOException {
        send("PASV");
        FTPResponse response = expect(227);

        FTPParser address = FTPParser.parse(response);
        System.out.println("    Address: " + address);

        Socket dataSocket = new Socket();
        try {
            dataSocket.connect(new InetSocketAddress(controlSocket.getInetAddress(), address.port), 5000);
            System.out.println("    Data socket opened on port: " + dataSocket.getPort());
            return dataSocket;
        } catch (IOException e) {
            dataSocket.close();
            if (retriesLeft <= 1) throw e;
            System.out.println("    Data connection failed, retrying... (" + (retriesLeft - 1) + " left)");
            return openDataConnection(retriesLeft - 1);
        }
    }

    public List<String> ls() throws IOException {
        System.out.println("[LS]");
        Socket dataSocket = openDataConnection();

        send("LIST");
        expect(150);

        List<String> files = new ArrayList<>();
        BufferedReader dataIn = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
        String line;
        while ((line = dataIn.readLine()) != null) {
            System.out.println("    " + line);
            files.add(line);
        }

        dataSocket.close();
        expect(226);
        System.out.println();
        return files;
    }

    // 550 means already exists
    public void mkdir(String dir) throws IOException {
        System.out.println("[MKDIR] " + dir);
        send("MKD " + dir);
        expect(257, 550);
        System.out.println();
    }

    public void rmdir(String dir) throws IOException {
        System.out.println("[RMDIR] " + dir);
        send("RMD " + dir);
        expect(250);
        System.out.println();
    }

    // TYPE I = binary mode so file bytes aren't modified in transit
    public void get(String remoteFilename, String localPath) throws IOException {
        System.out.println("[GET] " + remoteFilename + " -> " + localPath);

        send("TYPE I");
        expect(200);

        Socket dataSocket = openDataConnection();
        send("RETR " + remoteFilename);  // only the remote filename goes to server
        expect(150);

        InputStream dataIn    = dataSocket.getInputStream();
        FileOutputStream fileOut = new FileOutputStream(localPath);  // full local path to write
        byte[] buffer = new byte[4096];
        int bytesRead, total = 0;
        while ((bytesRead = dataIn.read(buffer)) != -1) {
            fileOut.write(buffer, 0, bytesRead);
            total += bytesRead;
        }
        System.out.println("    Downloaded: " + total + " bytes -> " + localPath);

        fileOut.close();
        dataSocket.close();
        expect(226);
        System.out.println();
    }

    // localPath = full path to read from disk; remoteName = bare filename sent to STOR
    public void put(String localPath, String remoteName) throws IOException {
        System.out.println("[PUT] " + localPath + " -> " + remoteName);

        send("TYPE I");
        expect(200);

        Socket dataSocket = openDataConnection();
        send("STOR " + remoteName);  // get the filename
        expect(150);

        FileInputStream fileIn  = new FileInputStream(localPath);
        OutputStream    dataOut = dataSocket.getOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead, total = 0;
        while ((bytesRead = fileIn.read(buffer)) != -1) {
            dataOut.write(buffer, 0, bytesRead);
            total += bytesRead;
        }
        System.out.println("    Uploaded: " + total + " bytes <- " + localPath);

        fileIn.close();
        dataOut.close();
        dataSocket.close();
        expect(226);
        System.out.println();
    }

    public void delete(String filename) throws IOException {
        System.out.println("[DELETE] " + filename);
        send("DELE " + filename);
        expect(250);
        System.out.println();
    }
}