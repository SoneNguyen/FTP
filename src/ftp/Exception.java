package ftp;

/*
    custom exception class for FTP errors
    extends IOException so we can throw it from methods that already throw IOException
    the benefit over plain IOException is we also store the FTP response code
    so the caller can check exactly which error code the server sent back
*/
public class Exception extends java.io.IOException {
    public final int code;

    // take the full FTPResponse and store the code
    // pass the response string as the exception message so it shows in stack traces
    public Exception(ResponseParser response) {
        super(response.toString());
        this.code = response.code;
    }
}