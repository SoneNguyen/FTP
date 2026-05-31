package ftp;

// this class parses the PASV response from the server to extract ip and port
// server sends the address in format: (h1,h2,h3,h4,p1,p2)
// we need to convert that into a usable ip string and port number
public class FTPParser {
    public final String ip;
    public final int port;

    private FTPParser(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public static FTPParser parse(FTPResponse response) {
        String raw = response.message;

        // extract everything between the parentheses then split by comma
        // gives us an array of 6 strings: [h1, h2, h3, h4, p1, p2]
        String[] parts = raw.substring(raw.indexOf('(') + 1, raw.indexOf(')')).split(",");

        // join first four parts with dots to form ip address like h1.h2.h3.h4
        String ip = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];

        // FTP splits port into two bytes p1 and p2
        // to get original port number: p1 * 256 + p2 (because one byte = 0 to 255, so base 256)
        int port = Integer.parseInt(parts[4].trim()) * 256 + Integer.parseInt(parts[5].trim());

        return new FTPParser(ip, port);
    }

    @Override
    public String toString() { return ip + ":" + port; }
}