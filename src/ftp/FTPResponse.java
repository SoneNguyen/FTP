package ftp;

import java.io.BufferedReader;
import java.io.IOException;

// this class acts as an FTP response parser for consuming String from FTP server
// every reply from server starts with a 3 digit code then a space or dash then a message
public class FTPResponse {
    public final int code;
    public final String message;

    private FTPResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    // parse one full response from the control connection
    // need to pass BufferedReader so we can keep reading lines for multiline responses
    public static FTPResponse parse(BufferedReader reader) throws IOException {
        String line = reader.readLine();

        // first 3 characters are always the response code
        int code = Integer.parseInt(line.substring(0, 3));

        // everything after position 4 is the message (skip the space or dash at position 3)
        StringBuilder message = new StringBuilder(line.substring(4));

        // if 4th character is '-' it means more lines are coming
        // keep reading until we see code + space (not dash) which means last line
        while (line.charAt(3) == '-') {
            line = reader.readLine();
            message.append("\n").append(line.substring(4));
        }

        return new FTPResponse(code, message.toString());
    }

    // check if this response matches an expected code
    public boolean is(int code) { return this.code == code; }

    @Override
    public String toString() { return code + " " + message; }
}