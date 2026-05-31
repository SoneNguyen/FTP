package gui;

import ftp.Client;
import ftp.Exception;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class App extends Application {

    // shared styles
    private static final String MONO       = "Monospaced";
    private static final String BTN        = "-fx-background-color: #1a1a1a; -fx-text-fill: #ffffff; -fx-font-family: 'Monospaced'; -fx-cursor: hand; -fx-padding: 5 12 5 12;";
    private static final String BTN_RED    = "-fx-background-color: #cc0000; -fx-text-fill: #ffffff; -fx-font-family: 'Monospaced'; -fx-cursor: hand; -fx-padding: 5 12 5 12;";
    private static final String FIELD      = "-fx-font-family: 'Monospaced'; -fx-font-size: 11;";
    private static final String STATUS_OK  = "-fx-font-family: 'Monospaced'; -fx-font-size: 10; -fx-background-color: #f0f0f0; -fx-padding: 3 6 3 6; -fx-border-color: #cccccc;";
    private static final String STATUS_ERR = "-fx-font-family: 'Monospaced'; -fx-font-size: 10; -fx-background-color: #fff0f0; -fx-padding: 3 6 3 6; -fx-border-color: #cc0000; -fx-text-fill: #cc0000;";
    private static final String DARK_BAR   = "-fx-background-color: #2a2a2a; -fx-text-fill: #ffffff; -fx-font-family: 'Monospaced'; -fx-font-weight: bold; -fx-padding: 5 8 5 8;";
    private static final String PATH_LABEL = "-fx-font-family: 'Monospaced'; -fx-font-size: 11; -fx-background-color: #2a2a2a; -fx-text-fill: #aaaaaa; -fx-padding: 5 8 5 4;";
    private static final String SIDEBAR    = "-fx-background-color: #f5f5f5; -fx-border-color: #cccccc; -fx-padding: 6 5 6 5;";

    // state
    private Client client  = new Client();
    private boolean   logOpen = false;

    private final TextArea              logArea      = new TextArea();
    private final TableView<RemoteFile> remoteTable  = new TableView<>();
    private final TableView<LocalFile>  localTable   = new TableView<>();
    private final Label                 remotePath   = new Label("/");
    private final Label                 remoteStatus = new Label("Not connected");
    private final Label                 localStatus  = new Label("Ready");

    private File  localDir     = new File(System.getProperty("user.dir"));
    private Label localPathLbl;

    // data models
    public static class RemoteFile {
        private final String permissions, size, date, name, rawLine;

        public RemoteFile(String permissions, String size, String date, String name, String rawLine) {
            this.permissions = permissions;
            this.size        = size;
            this.date        = date;
            this.name        = name;
            this.rawLine     = rawLine;
        }

        // parse one line from LIST output (unix format)
        public static RemoteFile parse(String line) {
            try {
                String[] p = line.trim().split("\\s+", 9);
                if (p.length < 9) return null; // malformed line, skip it
                return new RemoteFile(p[0], p[4], p[5] + " " + p[6] + " " + p[7], p[8], line);
            } catch (java.lang.Exception e) { return null; }
        }

        public boolean isDirectory()   { return permissions.startsWith("d"); }
        public String getPermissions() { return permissions; }
        public String getSize()        { return size; }
        public String getDate()        { return date; }
        public String getName()        { return name; }
        public String getRawLine()     { return rawLine; }
    }

    public static class LocalFile {
        private final String type, size, name;
        private final File   file;

        public LocalFile(File f) {
            this.file = f;
            this.name = f.getName();
            this.type = f.isDirectory() ? "DIR" : "FILE";
            this.size = f.isDirectory() ? "" : f.length() + " B"; // empty size for dirs
        }

        public String getType() { return type; }
        public String getSize() { return size; }
        public String getName() { return name; }
        public File   getFile() { return file; }
    }

    // app entry
    @Override
    public void start(Stage stage) {
        redirectOutput();                  // pipe System.out -> log area
        stage.setTitle("FTP Client");
        stage.setScene(loginScene(stage));
        stage.setResizable(false);
        stage.show();
    }

    // login scene
    private Scene loginScene(Stage stage) {
        Label title = new Label("FTP Client");
        title.setFont(Font.font(MONO, FontWeight.BOLD, 22));

        Label sub = new Label("Connect to a server");
        sub.setFont(Font.font(MONO, 12));
        sub.setStyle("-fx-text-fill: #888888;");

        TextField     hostFld = new TextField();     hostFld.setPromptText("ftp.example.com"); hostFld.setStyle(FIELD); hostFld.setPrefWidth(200);
        TextField     userFld = new TextField();     userFld.setPromptText("username");        userFld.setStyle(FIELD); userFld.setPrefWidth(200);
        PasswordField passFld = new PasswordField(); passFld.setPromptText("password");        passFld.setStyle(FIELD); passFld.setPrefWidth(200);

        Label  errLbl     = new Label("");
        errLbl.setFont(Font.font(MONO, 11));
        errLbl.setStyle("-fx-text-fill: #cc0000;");

        Button connectBtn = new Button("Connect & Login");
        connectBtn.setStyle(BTN);

        // form grid: right-aligned labels + fields
        GridPane form = new GridPane();
        form.setHgap(10); form.setVgap(12); form.setAlignment(Pos.CENTER);
        ColumnConstraints labelCol = new ColumnConstraints(55);
        labelCol.setHalignment(javafx.geometry.HPos.RIGHT);
        form.getColumnConstraints().addAll(labelCol, new ColumnConstraints(200));

        Label hostLbl = new Label("Host:"); hostLbl.setFont(Font.font(MONO, 12));
        Label userLbl = new Label("User:"); userLbl.setFont(Font.font(MONO, 12));
        Label passLbl = new Label("Pass:"); passLbl.setFont(Font.font(MONO, 12));

        form.add(hostLbl, 0, 0); form.add(hostFld, 1, 0);
        form.add(userLbl, 0, 1); form.add(userFld, 1, 1);
        form.add(passLbl, 0, 2); form.add(passFld, 1, 2);

        connectBtn.setOnAction(e -> {
            // basic validation before hitting the network
            if (hostFld.getText().trim().isEmpty() || userFld.getText().trim().isEmpty()) {
                errLbl.setText("Host and username are required."); return;
            }
            if (passFld.getText().isEmpty()) {
                errLbl.setText("Password is required."); return;
            }
            errLbl.setStyle("-fx-text-fill: #888888; -fx-font-family: 'Monospaced'; -fx-font-size: 11;");
            errLbl.setText("Connecting...");
            connectBtn.setDisable(true);

            new Thread(() -> {
                try {
                    client.connect(hostFld.getText().trim());
                    client.login(userFld.getText().trim(), passFld.getText());
                    Platform.runLater(() -> {
                        stage.setScene(mainScene(stage)); // switch to main scene on success
                        stage.setResizable(true);
                        stage.setWidth(1050);
                        stage.setHeight(680);
                    });
                } catch (Exception ex) {
                    // server said no (e.g. 530 wrong password)
                    Platform.runLater(() -> {
                        errLbl.setStyle("-fx-text-fill: #cc0000; -fx-font-family: 'Monospaced'; -fx-font-size: 11;");
                        errLbl.setText(ex.getMessage());
                        connectBtn.setDisable(false);
                    });
                } catch (IOException ex) {
                    // network failure, DNS error, etc.
                    Platform.runLater(() -> {
                        errLbl.setStyle("-fx-text-fill: #cc0000; -fx-font-family: 'Monospaced'; -fx-font-size: 11;");
                        errLbl.setText("Connection failed: " + ex.getMessage());
                        connectBtn.setDisable(false);
                    });
                }
            }).start();
        });

        VBox root = new VBox(14, title, sub, form, connectBtn, errLbl);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(48));
        root.setStyle("-fx-background-color: #ffffff;");
        return new Scene(root, 420, 340);
    }

    // main scene
    private Scene mainScene(Stage stage) {

        // remote panel header bar
        Label remoteHdr = new Label("Remote:");
        remoteHdr.setStyle(DARK_BAR);
        remoteHdr.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(remoteHdr, Priority.ALWAYS);

        remotePath.setStyle(PATH_LABEL);
        HBox remoteBar = new HBox(remoteHdr, remotePath);
        remoteBar.setStyle("-fx-background-color: #2a2a2a;");
        remoteBar.setAlignment(Pos.CENTER_LEFT);

        remoteTable.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 11;");
        remoteTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(remoteTable, Priority.ALWAYS);

        TableColumn<RemoteFile, String> rPerms = col("Permissions", "permissions", 110);
        TableColumn<RemoteFile, String> rSize  = col("Size",        "size",         80);
        TableColumn<RemoteFile, String> rDate  = col("Date",        "date",        130);
        TableColumn<RemoteFile, String> rName  = col("Name",        "name",        180);
        rSize.setStyle("-fx-alignment: CENTER-RIGHT;");
        remoteTable.getColumns().addAll(rPerms, rSize, rDate, rName);

        // double-click a dir to cd into it
        remoteTable.setOnMouseClicked(e -> {
            if (e.getClickCount() != 2) return;
            RemoteFile sel = remoteTable.getSelectionModel().getSelectedItem();
            if (sel != null && sel.isDirectory())
                ftpThread(() -> { client.cd(sel.getName()); refreshRemote(); }, "cd");
        });

        remoteStatus.setStyle(STATUS_OK);
        remoteStatus.setMaxWidth(Double.MAX_VALUE);

        // remote sidebar buttons
        Button    cdUpBtn      = new Button("cd ..");      cdUpBtn.setStyle(BTN);
        Button    refreshBtn   = new Button("Refresh");    refreshBtn.setStyle(BTN);
        Button    downloadBtn  = new Button("Download ▼"); downloadBtn.setStyle(BTN);
        TextField newFolderFld = new TextField();          newFolderFld.setPromptText("folder name"); newFolderFld.setStyle(FIELD); newFolderFld.setMaxWidth(Double.MAX_VALUE);
        Button    mkdirBtn     = new Button("New folder"); mkdirBtn.setStyle(BTN);
        Button    deleteBtn    = new Button("Delete");     deleteBtn.setStyle(BTN);
        Button    disconnectBtn= new Button("Disconnect"); disconnectBtn.setStyle(BTN_RED);

        for (Button b : new Button[]{cdUpBtn, refreshBtn, downloadBtn, mkdirBtn, deleteBtn, disconnectBtn})
            b.setMaxWidth(Double.MAX_VALUE);

        Region remoteSpacer = new Region();                // spacer pushes Disconnect to the bottom
        VBox.setVgrow(remoteSpacer, Priority.ALWAYS);

        VBox remoteSidebar = new VBox(6,
                cdUpBtn, refreshBtn, new Separator(),
                downloadBtn, new Separator(),
                newFolderFld, mkdirBtn, deleteBtn,
                remoteSpacer,
                disconnectBtn
        );
        remoteSidebar.setStyle(SIDEBAR);
        remoteSidebar.setPrefWidth(120); remoteSidebar.setMinWidth(120);
        remoteSidebar.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(remoteSidebar, Priority.ALWAYS);

        VBox remoteCenter = new VBox(remoteTable, remoteStatus);
        VBox.setVgrow(remoteCenter, Priority.ALWAYS);
        HBox.setHgrow(remoteCenter, Priority.ALWAYS);

        HBox remoteBody = new HBox(remoteSidebar, remoteCenter); // sidebar LEFT, table right
        HBox.setHgrow(remoteBody, Priority.ALWAYS);
        VBox.setVgrow(remoteBody, Priority.ALWAYS);

        VBox leftPanel = new VBox(remoteBar, remoteBody);
        VBox.setVgrow(leftPanel, Priority.ALWAYS);

        // remote button actions
        cdUpBtn.setOnAction(e ->
                ftpThread(() -> { client.cd(".."); refreshRemote(); }, "cd .."));

        refreshBtn.setOnAction(e ->
                ftpThread(this::refreshRemote, "refresh"));

        downloadBtn.setOnAction(e -> {
            RemoteFile sel = remoteTable.getSelectionModel().getSelectedItem();
            if (sel == null)       { remoteError("No file selected.");           return; }
            if (sel.isDirectory()) { remoteError("Cannot download a directory."); return; }

            String remoteName = sel.getName();
            String baseName   = remoteName.contains("/")  ? remoteName.substring(remoteName.lastIndexOf("/")  + 1)
                    : remoteName.contains("\\") ? remoteName.substring(remoteName.lastIndexOf("\\") + 1)
                    : remoteName;
            File dest = new File(localDir, baseName);

            // show confirmation dialog on FX thread before touching background thread
            if (dest.exists()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        baseName + " already exists. Overwrite?", ButtonType.YES, ButtonType.NO);
                alert.setHeaderText(null);
                boolean confirmed = alert.showAndWait()
                        .map(btn -> btn == ButtonType.YES)
                        .orElse(false);
                if (!confirmed) return; // user said no, nothing to do
            }

            String localPath = dest.getAbsolutePath(); // full path to write the file to
            ftpThread(() -> {
                client.get(remoteName, localPath);
                Platform.runLater(this::refreshLocalTable);
                remoteOk("Downloaded: " + baseName);
            }, "download");
        });

        mkdirBtn.setOnAction(e -> {
            String name = newFolderFld.getText().trim();
            if (name.isEmpty()) return;
            ftpThread(() -> {
                client.mkdir(name);
                refreshRemote();
                Platform.runLater(newFolderFld::clear);
            }, "mkdir");
        });

        deleteBtn.setOnAction(e -> {
            RemoteFile sel = remoteTable.getSelectionModel().getSelectedItem();
            if (sel == null) { remoteError("No file selected."); return; }
            ftpThread(() -> {
                if (sel.isDirectory()) client.rmdir(sel.getName());  // RMD for folders
                else                   client.delete(sel.getName()); // DELE for files
                refreshRemote();
            }, "delete");
        });

        disconnectBtn.setOnAction(e ->
                ftpThread(() -> {
                    client.quit();
                    client = new Client(); // reset client so next login starts fresh
                    Platform.runLater(() -> {
                        stage.setScene(loginScene(stage));
                        stage.setResizable(false);
                        stage.setWidth(420);
                        stage.setHeight(340);
                    });
                }, "disconnect"));

        // local panel header bar
        Label localHdr = new Label("Local:");
        localHdr.setStyle(DARK_BAR);
        localHdr.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(localHdr, Priority.ALWAYS);

        localPathLbl = new Label(localDir.getAbsolutePath());
        localPathLbl.setStyle(PATH_LABEL);
        HBox.setHgrow(localPathLbl, Priority.ALWAYS);

        HBox localBar = new HBox(localHdr, localPathLbl);
        localBar.setStyle("-fx-background-color: #2a2a2a;");
        localBar.setAlignment(Pos.CENTER_LEFT);

        localTable.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 11;");
        localTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(localTable, Priority.ALWAYS);

        TableColumn<LocalFile, String> lType = col("Type", "type",  50);
        TableColumn<LocalFile, String> lSize = col("Size", "size",  80);
        TableColumn<LocalFile, String> lName = col("Name", "name", 250);
        lSize.setStyle("-fx-alignment: CENTER-RIGHT;");
        localTable.getColumns().addAll(lType, lSize, lName);

        // double-click a dir to navigate into it
        localTable.setOnMouseClicked(e -> {
            if (e.getClickCount() != 2) return;
            LocalFile sel = localTable.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getFile().isDirectory()) {
                localDir = sel.getFile();
                localPathLbl.setText(localDir.getAbsolutePath());
                refreshLocalTable();
            }
        });

        localStatus.setStyle(STATUS_OK);
        localStatus.setMaxWidth(Double.MAX_VALUE);
        refreshLocalTable(); // populate on load

        // local sidebar buttons — symmetric to remote: cd .. top, upload mirrors download
        Button localCdUpBtn = new Button("cd ..");    localCdUpBtn.setStyle(BTN);
        Button uploadBtn    = new Button("Upload ▲"); uploadBtn.setStyle(BTN);
        Button logBtn       = new Button("Log ▼");    logBtn.setStyle(BTN);

        for (Button b : new Button[]{localCdUpBtn, uploadBtn, logBtn})
            b.setMaxWidth(Double.MAX_VALUE);

        Region localSpacer = new Region(); // spacer mirrors remote side so Log sits at the bottom
        VBox.setVgrow(localSpacer, Priority.ALWAYS);

        VBox localSidebar = new VBox(6,
                localCdUpBtn, new Separator(),
                uploadBtn,
                localSpacer,
                logBtn
        );
        localSidebar.setStyle(SIDEBAR);
        localSidebar.setPrefWidth(120); localSidebar.setMinWidth(120);
        localSidebar.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(localSidebar, Priority.ALWAYS);

        VBox localCenter = new VBox(localTable, localStatus);
        VBox.setVgrow(localCenter, Priority.ALWAYS);
        HBox.setHgrow(localCenter, Priority.ALWAYS);

        HBox localBody = new HBox(localCenter, localSidebar); // table left, sidebar RIGHT
        HBox.setHgrow(localBody, Priority.ALWAYS);
        VBox.setVgrow(localBody, Priority.ALWAYS);

        VBox rightPanel = new VBox(localBar, localBody);
        VBox.setVgrow(rightPanel, Priority.ALWAYS);

        // local button actions
        localCdUpBtn.setOnAction(e -> {
            File parent = localDir.getParentFile();
            if (parent != null) {
                localDir = parent;
                localPathLbl.setText(localDir.getAbsolutePath());
                refreshLocalTable();
            }
        });

        uploadBtn.setOnAction(e -> {
            LocalFile sel = localTable.getSelectionModel().getSelectedItem();
            if (sel == null)                 { remoteError("No file selected.");          return; }
            if (sel.getFile().isDirectory()) { remoteError("Cannot upload a directory."); return; }
            // pass full local path to read from, but only the filename goes to STOR
            ftpThread(() -> {
                client.put(sel.getFile().getAbsolutePath(), sel.getName());
                refreshRemote();
            }, "upload");
        });

        logBtn.setOnAction(e -> {
            logOpen = !logOpen;
            logArea.setVisible(logOpen);
            logArea.setManaged(logOpen);
            logBtn.setText(logOpen ? "Log ▲" : "Log ▼"); // toggle arrow direction
        });

        SplitPane splitPane = new SplitPane(leftPanel, rightPanel); // both panels side by side
        splitPane.setDividerPositions(0.5);                          // start at 50/50
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        // log hidden until toggled
        logArea.setEditable(false);
        logArea.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 10;");
        logArea.setPrefHeight(150);
        logArea.setVisible(false);
        logArea.setManaged(false);

        VBox root = new VBox(splitPane, logArea);
        root.setStyle("-fx-background-color: #ffffff;");
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        ftpThread(this::refreshRemote, "initial ls"); // load remote listing right after login

        return new Scene(root, 1050, 680);
    }

    // helpers

    // run FTP work on a background thread, show error in status bar on failure
    private void ftpThread(FTPTask task, String label) {
        new Thread(() -> {
            try { task.run(); }
            catch (IOException ex) { remoteError(label + " failed: " + ex.getMessage()); }
        }).start();
    }

    @FunctionalInterface
    private interface FTPTask { void run() throws IOException; }

    // fetch remote listing and update the table + path label
    private void refreshRemote() throws IOException {
        List<String>     lines = client.ls();
        List<RemoteFile> files = new ArrayList<>();
        for (String line : lines) {
            RemoteFile f = RemoteFile.parse(line);
            if (f != null) files.add(f); // skip malformed lines
        }
        String pwd = client.pwd();
        Platform.runLater(() -> {
            remoteTable.setItems(FXCollections.observableArrayList(files));
            remotePath.setText(pwd);
            remoteOk(files.size() + " items");
        });
    }

    // list localDir contents — dirs first, then alphabetical
    private void refreshLocalTable() {
        File[] files = localDir.listFiles();
        if (files == null) return;
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1; // dirs before files
            return a.getName().compareToIgnoreCase(b.getName());
        });
        List<LocalFile> items = new ArrayList<>();
        for (File f : files) items.add(new LocalFile(f));
        Platform.runLater(() -> {
            localTable.setItems(FXCollections.observableArrayList(items));
            localStatus.setText(files.length + " items"); // path is already shown in the header bar
            if (localPathLbl != null) localPathLbl.setText(localDir.getAbsolutePath());
        });
    }

    private void remoteOk(String msg) {
        Platform.runLater(() -> { remoteStatus.setStyle(STATUS_OK);  remoteStatus.setText(msg); });
    }

    private void remoteError(String msg) {
        Platform.runLater(() -> {
            remoteStatus.setStyle(STATUS_ERR);
            remoteStatus.setText(msg);
            appendLog("ERROR: " + msg);
        });
    }

    private void appendLog(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    // redirect System.out to the log area so FTPClient prints appear there
    private void redirectOutput() {
        PrintStream ps = new PrintStream(new OutputStream() {
            private final StringBuilder buf = new StringBuilder();
            @Override
            public void write(int b) {
                char c = (char) b;
                if (c == '\n') { appendLog(buf.toString()); buf.setLength(0); } // flush on newline
                else buf.append(c);
            }
        });
        System.setOut(ps);
    }

    // UI factory — avoids repeating TableColumn setup for every column
    private <T> TableColumn<T, String> col(String title, String prop, double width) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(new PropertyValueFactory<>(prop));
        c.setPrefWidth(width);
        return c;
    }

    public static void main(String[] args) { launch(args); }
}