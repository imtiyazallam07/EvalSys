import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;

/**
 * Computer Based Assessment System - Dynamic CSV-based question loader.
 * Reads .csv files from the "qstns" folder. Each file becomes a tab.
 * CSV format: Question | Option 1 | Option 2 | Option 3 | Option 4
 *
 * config.json is downloaded from CONFIG_URL at startup.
 * Expected config.json format:
 * { "dur": <minutes>, "solution": "<url-to-solution.json>" }
 *
 * Responses are saved to: response/responses.json
 * Format: {"Subject1": {"questionId": answer, ...}, "Subject2": {...}, ...}
 *
 * Solution JSON is downloaded after the test ends and deleted once written.
 */
public class Main extends JFrame {

    // ── Remote URLs ──────────────────────────────────────────────────────────
    /** URL of config.json to download at startup. Edit this as needed. */
    public static String CONFIG_URL = "https://raw.githubusercontent.com/imtiyaz-allam/questions/refs/heads/main/config.json";
    public static String QUESTIONS_URL = "https://raw.githubusercontent.com/imtiyaz-allam/questions/refs/heads/main/question.json";

    // Solution URL is read from config.json at runtime
    private static String solutionUrl = "";

    // Environment variables loaded from .env
    private static boolean preExist = false;
    private static String solutionLink = "";
    private static boolean adminMode = false;

    private static final ArrayList<File> downloadedFiles = new ArrayList<>();

    private static java.util.Map<String, String> loadEnv() {
        java.util.Map<String, String> env = new java.util.HashMap<>();
        java.io.File envFile = new java.io.File(".env");
        if (!envFile.exists()) {
            envFile = new java.io.File("../.env");
        }
        if (!envFile.exists()) {
            return env;
        }
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String val = parts[1].trim();
                    if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                        val = val.substring(1, val.length() - 1);
                    }
                    env.put(key, val);
                }
            }
        } catch (java.io.IOException e) {
            System.err.println("Error reading .env file: " + e.getMessage());
        }
        return env;
    }

    // --- Dynamic data ---
    private final ArrayList<String> subjectNames = new ArrayList<>();
    private final ArrayList<HashMap<Integer, String[]>> allQuestions = new ArrayList<>();
    private final ArrayList<ArrayList<Integer>> shuffledIndices = new ArrayList<>();
    private final ArrayList<int[]> allAnswers = new ArrayList<>();
    private final ArrayList<boolean[]> allAttempts = new ArrayList<>();

    private long getQuestionID(String text) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return crc.getValue();
    }

    // --- Per-tab UI references ---
    private final ArrayList<JLabel> questionLabels = new ArrayList<>();
    private final ArrayList<JRadioButton[]> optionButtons = new ArrayList<>();
    private final ArrayList<ButtonGroup> optionGroups = new ArrayList<>();

    // --- Shared UI ---
    private JTabbedPane tabs;
    private JPanel navPanel;
    private JPanel navButtonGrid;
    private final ArrayList<JButton> navButtons = new ArrayList<>();
    private JLabel titleLabel;
    private JLabel timeLabel;
    private JButton endTestButton;

    // --- State ---
    public int currentQuestion = 1;
    public int currentTabIndex = 0;
    static int sec = 10800; // overwritten by config.json
    public boolean endTestB = false;

    private final boolean tabSwitching = false;
    private boolean showingDialog = false;

    // =========================================================================
    // CONFIG DOWNLOAD (replaces the old loadDuration() + adds solution URL)
    // =========================================================================

    /**
     * Downloads config.json from CONFIG_URL, extracts:
     * "dur" → exam duration in minutes (default 180)
     * "solution" → URL of the solution JSON (default "")
     * Must be called before the UI is built.
     */
    public static void parseConfigJson(String json) {
        // Parse "dur"
        java.util.regex.Matcher mDur = java.util.regex.Pattern
                .compile("\"dur\"\\s*:\\s*(\\d+)")
                .matcher(json);
        if (mDur.find()) {
            int minutes = Integer.parseInt(mDur.group(1));
            sec = minutes * 60;
            System.out.println("Exam duration: " + minutes + " min");
        } else {
            System.err.println("'dur' not found in config.json — defaulting to 180 min.");
        }

        // Parse "solution"
        java.util.regex.Matcher mSol = java.util.regex.Pattern
                .compile("\"solution\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(json);
        if (mSol.find()) {
            solutionUrl = mSol.group(1).trim();
            System.out.println("Solution URL: " + solutionUrl);
        } else {
            System.err.println("'solution' URL not found in config.json.");
        }
    }

    public static void loadLocalConfig() {
        File configFile = new File("config.json");
        if (configFile.exists()) {
            try {
                String json = Files.readString(configFile.toPath());
                parseConfigJson(json);
            } catch (IOException e) {
                System.err.println("Error reading local config.json: " + e.getMessage());
            }
        } else {
            System.out.println("Local config.json not found, using default duration (180 min).");
        }
    }

    public static void downloadConfig() {
        try {
            java.net.URL url = new java.net.URL(CONFIG_URL);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() != 200) {
                System.err.println("Failed to fetch config.json: HTTP " + conn.getResponseCode()
                        + " — using defaults (180 min, no solution URL).");
                return;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = in.readLine()) != null)
                    sb.append(line);
            }
            String json = sb.toString();

            // Save config.json locally so evaluator or next runs can use it
            try {
                Files.writeString(Path.of("config.json"), json);
                System.out.println("Saved config.json locally.");
            } catch (IOException e) {
                System.err.println("Could not save config.json locally: " + e.getMessage());
            }

            parseConfigJson(json);

        } catch (IOException e) {
            System.err.println("Error downloading config.json: " + e.getMessage()
                    + " — using defaults.");
        }
    }

    // =========================================================================
    // SOLUTION DOWNLOAD (called right after test ends, before cleanup)
    // =========================================================================

    /**
     * Downloads the solution JSON from {@link #solutionUrl} and writes it to
     * solutions/solution.json. The file is scheduled for deletion on JVM exit.
     * Does nothing if solutionUrl is blank.
     */
    private void downloadSolution() {
        String urlToUse = (solutionLink != null && !solutionLink.isBlank()) ? solutionLink : solutionUrl;
        if (urlToUse == null || urlToUse.isBlank()) {
            System.err.println("[Solution] No solution URL configured — skipping download.");
            return;
        }
        try {
            Files.createDirectories(Path.of("solutions"));
            java.net.URL url = new java.net.URL(urlToUse);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            if (conn.getResponseCode() != 200) {
                System.err.println("[Solution] HTTP " + conn.getResponseCode() + " — download failed.");
                return;
            }

            Path outPath = Path.of("solutions", "solution.json");
            try (java.io.InputStream in = conn.getInputStream()) {
                Files.copy(in, outPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            File solFile = outPath.toFile();
            solFile.deleteOnExit(); // auto-cleanup on JVM exit
            downloadedFiles.add(solFile);
            System.out.println("[Solution] Downloaded → " + outPath.toAbsolutePath());

        } catch (IOException e) {
            System.err.println("[Solution] Download error: " + e.getMessage());
        }
    }

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public Main() {
        setTitle("EvalSys");
        try {
            java.net.URL iconUrl = getClass().getResource("/logo.png");
            if (iconUrl != null) {
                setIconImage(new javax.swing.ImageIcon(iconUrl).getImage());
            }
        } catch (Exception ignored) {}
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setUndecorated(true);
        setAlwaysOnTop(true);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        // Focus lost listener
        addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent evt) {
            }

            @Override
            public void windowLostFocus(java.awt.event.WindowEvent evt) {
                if (!endTestB && !showingDialog) {
                    showingDialog = true;
                    setAlwaysOnTop(true);
                    toFront();
                    requestFocus();
                    javax.swing.Timer warningTimer = new javax.swing.Timer(300, e -> {
                        JOptionPane.showMessageDialog(Main.this,
                                "You can't leave this window until the test gets over",
                                "Warning", JOptionPane.WARNING_MESSAGE);
                        setAlwaysOnTop(true);
                        toFront();
                        requestFocus();
                        showingDialog = false;
                    });
                    warningTimer.setRepeats(false);
                    warningTimer.start();
                }
            }
        });

        // Prevent minimizing
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowIconified(java.awt.event.WindowEvent evt) {
                if (!endTestB)
                    setExtendedState(JFrame.MAXIMIZED_BOTH);
            }
        });

        // Periodic focus guard
        java.util.Timer focusGuard = new java.util.Timer(true);
        focusGuard.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                if (!endTestB && !showingDialog && Main.this.isVisible()) {
                    SwingUtilities.invokeLater(() -> {
                        if (!Main.this.isFocused() && !endTestB && !showingDialog) {
                            setAlwaysOnTop(true);
                            setState(JFrame.ICONIFIED);
                            setState(JFrame.NORMAL);
                            setExtendedState(JFrame.MAXIMIZED_BOTH);
                            toFront();
                            requestFocus();
                        }
                    });
                }
            }
        }, 2000, 500);
    }

    // =========================================================================
    // RESPONSE JSON BUILDER
    // =========================================================================

    private String buildCombinedResponseJson() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < subjectNames.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append("\"").append(escapeJson(subjectNames.get(i))).append("\": {");
            HashMap<Integer, String[]> questions = allQuestions.get(i);
            int[] answers = allAnswers.get(i);
            boolean first = true;
            for (int lineNum = 1; lineNum <= answers.length; lineNum++) {
                String[] data = questions.get(lineNum);
                if (data != null) {
                    long qid = getQuestionID(data[0]);
                    if (!first)
                        sb.append(", ");
                    sb.append("\"").append(qid).append("\":").append(answers[lineNum - 1]);
                    first = false;
                }
            }
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    private void saveResponsesToFile() throws IOException {
        Files.createDirectories(Path.of("response"));
        Path ansFile = Path.of("response", "responses.json");
        Files.writeString(ansFile, buildCombinedResponseJson());
        System.out.println("Responses saved to: " + ansFile.toAbsolutePath());
    }

    // =========================================================================
    // DATA LOADING
    // =========================================================================

    public static void downloadQuestions() {
        try {
            File qstnsDir = new File("qstns");
            if (!qstnsDir.exists())
                qstnsDir.mkdirs();

            java.net.URL url = new java.net.URL(QUESTIONS_URL);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() != 200) {
                System.out.println("Failed to fetch questions.json: HTTP " + conn.getResponseCode());
                return;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null)
                    response.append(inputLine);
            }

            String jsonStr = response.toString();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(jsonStr);
            while (m.find()) {
                String fileUrl = m.group(2);
                if (!fileUrl.startsWith("http"))
                    fileUrl = "http://" + fileUrl;
                String fileName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
                if (!fileName.endsWith(".csv"))
                    fileName += ".csv";

                File targetFile = new File(qstnsDir, fileName);
                try (java.io.InputStream in = new java.net.URL(fileUrl).openStream()) {
                    Files.copy(in, targetFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("Downloaded: " + fileName);
                    targetFile.deleteOnExit();
                    downloadedFiles.add(targetFile);
                } catch (Exception e) {
                    System.err.println("Failed to download " + fileUrl + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error downloading questions: " + e.getMessage());
        }
    }

    public void loadData() {
        File qstnsDir = new File("qstns");
        if (!qstnsDir.exists() || !qstnsDir.isDirectory()) {
            JOptionPane.showMessageDialog(null,
                    "The 'qstns' folder was not found.\nPlace .csv question files in a folder named 'qstns'.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        File[] csvFiles = qstnsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv"));
        if (csvFiles == null || csvFiles.length == 0) {
            JOptionPane.showMessageDialog(null,
                    "No .csv files found in the 'qstns' folder.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        Arrays.sort(csvFiles, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File csvFile : csvFiles) {
            String name = csvFile.getName();
            String subjectName = name.substring(0, name.lastIndexOf('.'));
            HashMap<Integer, String[]> questions = fetchCSV(csvFile);

            subjectNames.add(subjectName);
            allQuestions.add(questions);
            ArrayList<Integer> indices = new ArrayList<>(questions.keySet());
            java.util.Collections.shuffle(indices);
            shuffledIndices.add(indices);
            allAnswers.add(new int[questions.size()]);
            allAttempts.add(new boolean[questions.size()]);
        }

        System.out.println("Loaded " + subjectNames.size() + " subjects: " + subjectNames);
    }

    public HashMap<Integer, String[]> fetchCSV(File file) {
        HashMap<Integer, String[]> questionAndAns = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                lineNum++;
                String[] data = line.split("\\|");
                if (data.length != 5) {
                    JOptionPane.showMessageDialog(null,
                            "Invalid data in " + file.getName() + " at line " + lineNum +
                                    ":\nExpected 5 pipe-separated fields, got " + data.length +
                                    "\nLine: " + line,
                            "Error", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
                for (int i = 0; i < data.length; i++)
                    data[i] = data[i].trim();
                questionAndAns.put(lineNum, data);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null,
                    "Error reading file: " + file.getName() + "\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        if (questionAndAns.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "No questions found in " + file.getName(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        System.out.println("  " + file.getName() + ": " + questionAndAns.size() + " questions");
        return questionAndAns;
    }

    // =========================================================================
    // UI BUILDING
    // =========================================================================

    public void buildUI() {
        titleLabel = new JLabel("EvalSys: Computer Based Assessment System");
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 36));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        try {
            titleLabel.setIcon(new ImageIcon(getClass().getResource(
                    "/68747470733a2f2f612e6673646e2e636f6d2f616c6c7572612f702f6c69622d61737465726f69642f69636f6e3f646661626537643833636334303662636631336133303833626338326333376262323632343535303530336434363731366239623861363433.png")));
        } catch (Exception e) {
            /* icon optional */ }

        tabs = new JTabbedPane();
        for (int i = 0; i < subjectNames.size(); i++) {
            tabs.addTab(subjectNames.get(i), createTabPanel(i));
        }
        tabs.addChangeListener(evt -> {
            if (!tabSwitching) {
                saveResponse(currentTabIndex);
                currentQuestion = 1;
                currentTabIndex = tabs.getSelectedIndex();
                rebuildNavButtons();
                loadTabAttempts();
                setQuestion();
            }
        });

        navPanel = new JPanel();
        navPanel.setLayout(new BoxLayout(navPanel, BoxLayout.Y_AXIS));

        String initSec1 = (sec % 60 < 10) ? "0" + sec % 60 : "" + sec % 60;
        timeLabel = new JLabel(sec / 60 + ":" + initSec1);
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        timeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        timeLabel.setAlignmentX(0.5f);
        timeLabel.setMaximumSize(new Dimension(280, 30));
        navPanel.add(timeLabel);
        navPanel.add(Box.createVerticalStrut(10));

        navButtonGrid = new JPanel();
        rebuildNavButtons();
        JPanel navGridWrapper = new JPanel(new java.awt.BorderLayout());
        navGridWrapper.add(navButtonGrid, java.awt.BorderLayout.NORTH);
        navPanel.add(navGridWrapper);

        endTestButton = new JButton("End Test");
        endTestButton.setForeground(Color.WHITE);
        endTestButton.setAlignmentX(0.5f);
        endTestButton.addActionListener(evt -> endTest());
        navPanel.add(endTestButton);
        navPanel.add(Box.createVerticalStrut(6));

        JPanel mainPanel = new JPanel(new java.awt.BorderLayout());
        mainPanel.add(titleLabel, java.awt.BorderLayout.NORTH);
        mainPanel.add(tabs, java.awt.BorderLayout.CENTER);
        mainPanel.add(navPanel, java.awt.BorderLayout.EAST);

        setContentPane(mainPanel);
    }

    private JPanel createTabPanel(int subjectIndex) {
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        JLabel questionLabel = new JLabel("Question");
        questionLabel.setFont(new Font("Cambria Math", Font.PLAIN, 24));
        questionLabel.setAlignmentX(0.0f);
        questionLabels.add(questionLabel);
        contentPanel.add(questionLabel);
        contentPanel.add(Box.createVerticalStrut(6));

        ButtonGroup group = new ButtonGroup();
        JRadioButton[] options = new JRadioButton[4];
        for (int i = 0; i < 4; i++) {
            options[i] = new JRadioButton("Option " + (i + 1));
            options[i].setFont(new Font("Cambria Math", Font.PLAIN, 14));
            options[i].setAlignmentX(0.0f);
            group.add(options[i]);
            contentPanel.add(options[i]);
            contentPanel.add(Box.createVerticalStrut(6));
        }
        optionButtons.add(options);
        optionGroups.add(group);

        panel.add(contentPanel, java.awt.BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));

        JButton savePrev = new JButton("Save & Previous");
        savePrev.addActionListener(evt -> {
            saveResponse(-1);
            if (currentQuestion > 1)
                currentQuestion--;
            setQuestion();
        });

        JButton saveNext = new JButton("Save & Next");
        saveNext.addActionListener(evt -> {
            saveResponse(-1);
            int qCount = allQuestions.get(tabs.getSelectedIndex()).size();
            if (currentQuestion < qCount)
                currentQuestion++;
            setQuestion();
        });

        JButton deselect = new JButton("Deselect");
        deselect.addActionListener(evt -> optionGroups.get(tabs.getSelectedIndex()).clearSelection());

        bottomPanel.add(savePrev);
        bottomPanel.add(saveNext);
        bottomPanel.add(deselect);
        panel.add(bottomPanel, java.awt.BorderLayout.SOUTH);

        return panel;
    }

    private void rebuildNavButtons() {
        int tabIdx = tabs != null ? tabs.getSelectedIndex() : 0;
        int qCount = allQuestions.get(tabIdx).size();

        navButtonGrid.removeAll();
        navButtons.clear();

        int cols = 5;
        int rows = (int) Math.ceil((double) qCount / cols);
        navButtonGrid.setLayout(new GridLayout(rows, cols, 4, 4));

        for (int i = 1; i <= qCount; i++) {
            JButton btn = new JButton(String.valueOf(i));
            btn.setForeground(Color.WHITE);
            btn.setPreferredSize(new Dimension(40, 40));
            btn.setMinimumSize(new Dimension(40, 40));
            btn.setMaximumSize(new Dimension(40, 40));
            btn.setMargin(new java.awt.Insets(2, 2, 2, 2));
            final int qNum = i;
            btn.addActionListener(evt -> {
                saveResponse(-1);
                currentQuestion = qNum;
                setQuestion();
            });
            navButtons.add(btn);
            navButtonGrid.add(btn);
        }

        navButtonGrid.revalidate();
        navButtonGrid.repaint();
    }

    // =========================================================================
    // QUESTION DISPLAY & RESPONSE
    // =========================================================================

    public void setQuestion() {
        int tab = tabs.getSelectedIndex();
        HashMap<Integer, String[]> questions = allQuestions.get(tab);

        int lineNum = shuffledIndices.get(tab).get(currentQuestion - 1);
        String[] data = questions.get(lineNum);
        if (data == null)
            return;

        long qid = getQuestionID(data[0]);
        questionLabels.get(tab).setText(
                "<html><b>[Q-ID: " + qid + "]</b> " + data[0] + "</html>");

        JRadioButton[] opts = optionButtons.get(tab);
        opts[0].setText("<html>" + data[1] + "</html>");
        opts[1].setText("<html>" + data[2] + "</html>");
        opts[2].setText("<html>" + data[3] + "</html>");
        opts[3].setText("<html>" + data[4] + "</html>");

        int selected = allAnswers.get(tab)[lineNum - 1];
        if (selected >= 1 && selected <= 4) {
            opts[selected - 1].setSelected(true);
        } else {
            optionGroups.get(tab).clearSelection();
        }
    }

    public void saveResponse(int tab) {
        if (tab == -1)
            tab = tabs.getSelectedIndex();
        if (tab < 0 || tab >= subjectNames.size())
            return;

        JRadioButton[] opts = optionButtons.get(tab);
        int answer = 0;
        for (int i = 0; i < 4; i++) {
            if (opts[i].isSelected()) {
                answer = i + 1;
                break;
            }
        }
        int lineNum = shuffledIndices.get(tab).get(currentQuestion - 1);
        allAnswers.get(tab)[lineNum - 1] = answer;
        setAttempted(answer != 0);
    }

    public void setAttempted(boolean attempted) {
        int tab = tabs.getSelectedIndex();
        allAttempts.get(tab)[currentQuestion - 1] = attempted;
        if (currentQuestion - 1 < navButtons.size()) {
            navButtons.get(currentQuestion - 1).setForeground(
                    attempted ? Color.GREEN : Color.WHITE);
        }
    }

    public void loadTabAttempts() {
        int tab = tabs.getSelectedIndex();
        boolean[] attempts = allAttempts.get(tab);
        for (int i = 0; i < navButtons.size() && i < attempts.length; i++) {
            navButtons.get(i).setForeground(attempts[i] ? Color.GREEN : Color.WHITE);
        }
    }

    // =========================================================================
    // EXAMINEE DETAILS
    // =========================================================================

    public String examineeName = "";
    public String examineeSection = "";
    public String examineeRegNo = "";
    public String examStartTime = "";

    public void saveUserDetails(String name, String section, String regNo,
            String startTime, String endTime) {
        this.examineeName = name;
        this.examineeSection = section;
        this.examineeRegNo = regNo;
        if (!startTime.isEmpty())
            this.examStartTime = startTime;

        try {
            java.io.File file = new java.io.File("examinee_details.json");
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                String endStr = endTime.isEmpty()
                        ? "null"
                        : "\"" + escapeJson(endTime) + "\"";
                writer.write("{\n" +
                        "  \"name\": \"" + escapeJson(this.examineeName) + "\",\n" +
                        "  \"section\": \"" + escapeJson(this.examineeSection) + "\",\n" +
                        "  \"registrationNumber\": \"" + escapeJson(this.examineeRegNo) + "\",\n" +
                        "  \"startTime\": \"" + escapeJson(this.examStartTime) + "\",\n" +
                        "  \"endTime\": " + endStr + "\n" +
                        "}");
            }
        } catch (java.io.IOException e) {
            /* non-critical */ }
    }

    private String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // =========================================================================
    // SOLUTION FILE GENERATION (formerly admin-only, now run at test-end)
    // =========================================================================

    /**
     * Reads every loaded subject's questions and writes
     * solutions/<subject>.json in the format expected by evaluator.py:
     * {
     * "<QuestionID>": {
     * "Question": "...", "a": "...", "b": "...", "c": "...", "d": "..."
     * }, ...
     * }
     * This mirrors what the old ADMIN shortcut in Details.java did.
     */
    private void generateSolutionFiles() {
        try {
            Files.createDirectories(Path.of("solutions"));
        } catch (IOException e) {
            System.err.println("[Solution gen] Could not create solutions/: " + e.getMessage());
            return;
        }

        for (int i = 0; i < subjectNames.size(); i++) {
            String subjectName = subjectNames.get(i);
            HashMap<Integer, String[]> questions = allQuestions.get(i);

            StringBuilder sb = new StringBuilder("{\n");
            boolean firstEntry = true;

            for (int lineNum = 1; lineNum <= questions.size(); lineNum++) {
                String[] data = questions.get(lineNum);
                if (data == null)
                    continue;

                long qid = getQuestionID(data[0]);
                if (!firstEntry)
                    sb.append(",\n");
                sb.append("  \"").append(qid).append("\": {\n");
                sb.append("    \"Question\": \"").append(escJson(data[0])).append("\",\n");
                sb.append("    \"a\": \"").append(escJson(data[1])).append("\",\n");
                sb.append("    \"b\": \"").append(escJson(data[2])).append("\",\n");
                sb.append("    \"c\": \"").append(escJson(data[3])).append("\",\n");
                sb.append("    \"d\": \"").append(escJson(data[4])).append("\"\n");
                sb.append("  }");
                firstEntry = false;
            }
            sb.append("\n}");

            Path outFile = Path.of("solutions", subjectName + ".json");
            try {
                Files.writeString(outFile, sb.toString());
                System.out.println("[Solution gen] Written: " + outFile);
            } catch (IOException e) {
                System.err.println("[Solution gen] Failed to write " + outFile + ": " + e.getMessage());
            }
        }
    }

    /** JSON-safe escaping for solution file generation. */
    private String escJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    // =========================================================================
    // END TEST
    // =========================================================================

    /**
     * Launches run_evaluator.bat located in the same directory as the running
     * JAR/class files. The working directory (from which Java was launched)
     * is passed as the CBT path so the bat file can find responses, solutions,
     * and examinee_details without any hardcoded paths.
     */
    private void runEvaluator() {
        try {
            // Resolve the directory that contains run_evaluator.bat.
            // System property "user.dir" is the working directory from which
            // the JVM was started — i.e. the single shared folder.
            String workDir = System.getProperty("user.dir");
            File batFile = new File(workDir, "run_evaluator.bat");

            if (!batFile.exists()) {
                System.err.println("[Evaluator] run_evaluator.bat not found at: "
                        + batFile.getAbsolutePath());
                return;
            }

            // cmd /c runs the bat and then closes the cmd window automatically.
            // We pass workDir as the CBT_DIR argument so the bat/evaluator knows
            // where to find responses.json, examinee_details.json, etc.
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd", "/c", batFile.getAbsolutePath(), workDir);
            pb.directory(new File(workDir));
            pb.inheritIO(); // evaluator output appears in the same console
            pb.start(); // fire-and-forget; evaluator runs independently

            System.out.println("[Evaluator] Launched: " + batFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("[Evaluator] Failed to launch run_evaluator.bat: "
                    + e.getMessage());
        }
    }

    private void finishTest() {
        try {
            if (adminMode) {
                // 1. Generate solutions/<subject>.json files containing question and options text
                generateSolutionFiles();

                // 2. Save selected options to solutions/solution.json and solution.json
                Files.createDirectories(Path.of("solutions"));
                String solJson = buildCombinedResponseJson();
                Files.writeString(Path.of("solutions", "solution.json"), solJson);
                Files.writeString(Path.of("solution.json"), solJson);
                System.out.println("[Admin Mode] Solution saved to solutions/solution.json and solution.json");
                return;
            }

            // 1. Save responses
            saveResponsesToFile();

            // 2. Generate per-subject solution files from loaded question data
            generateSolutionFiles();

            // 3. Download master solution.json from the URL in config.json or .env
            if (!preExist) {
                downloadSolution();
            } else {
                System.out.println("[Solution] PRE_EXIST is true — skipping download.");
            }

            // 4. Save examinee end-time
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss");
            String endTimeStr = java.time.LocalDateTime.now().format(dtf);
            saveUserDetails(examineeName, examineeSection, examineeRegNo,
                    examStartTime, endTimeStr);

        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

        // 5. Delete downloaded question CSVs
        for (File f : downloadedFiles) {
            if (f.exists())
                f.delete();
        }

        // 6. Launch the evaluator script
        runEvaluator();
    }

    private void endTest() {
        endTestB = true;
        showingDialog = true;
        setAlwaysOnTop(false);
        try {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Do you want to end test now?", "Confirm",
                    JOptionPane.YES_NO_OPTION);

            if (choice == JOptionPane.YES_OPTION) {
                finishTest();
                setVisible(false);
                JOptionPane.showMessageDialog(this,
                        "The test has been ended", "Note", JOptionPane.INFORMATION_MESSAGE);
                System.exit(0);

            } else {
                endTestB = false;
                showingDialog = false;
                setAlwaysOnTop(true);
                toFront();
                requestFocus();
            }
        } catch (Exception ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            endTestB = false;
            showingDialog = false;
            setAlwaysOnTop(true);
        }
    }

    // =========================================================================
    // MAIN
    // =========================================================================

    public static void main(String[] args) {
        FlatDarkLaf.install();

        // Check for --admin-mode runtime parameter
        for (String arg : args) {
            if ("--admin-mode".equals(arg)) {
                adminMode = true;
                break;
            }
        }

        // Load environment variables
        java.util.Map<String, String> env = loadEnv();
        preExist = Boolean.parseBoolean(env.getOrDefault("PRE_EXIST", "false"));
        if (env.containsKey("CONFIG_LINK")) {
            CONFIG_URL = env.get("CONFIG_LINK");
        }
        if (env.containsKey("QUESTION_LINK")) {
            QUESTIONS_URL = env.get("QUESTION_LINK");
        }
        if (env.containsKey("SOLUTION_LINK")) {
            solutionLink = env.get("SOLUTION_LINK");
        }

        if (!preExist) {
            // Download config first (provides duration + solution URL)
            downloadConfig();

            // Download question CSVs
            downloadQuestions();
        } else {
            System.out.println("=== Skipping downloads (PRE_EXIST is true) ===");
            loadLocalConfig();
        }

        Main mn = new Main();
        mn.loadData();
        mn.buildUI();
        mn.setQuestion();

        java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String startTimeStr = java.time.LocalDateTime.now().format(dtf);

        if (!adminMode) {
            Details dl = new Details(mn, true);
            dl.setLocationRelativeTo(null);
            dl.setVisible(true);

            if (!dl.started) {
                System.exit(0);
            }
            mn.saveUserDetails(dl.getExaminerName(), dl.getSection(), dl.getRegNo(),
                    startTimeStr, "");
        } else {
            mn.saveUserDetails("Admin", "Admin", "Admin", startTimeStr, "");
        }

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                String sec1 = (sec % 60 < 10) ? "0" + sec % 60 : "" + sec % 60;
                mn.timeLabel.setText(sec / 60 + ":" + sec1);
                sec--;

                if (sec < 0) {
                    mn.endTestB = true;
                    mn.showingDialog = true;
                    mn.setAlwaysOnTop(false);
                    mn.setEnabled(false);

                    JOptionPane.showMessageDialog(mn, "Exam time is over.");
                    mn.setVisible(false);

                    mn.finishTest();

                    JOptionPane.showMessageDialog(mn,
                            "Thank you for your attempt. The results will be declared shortly.");
                    timer.cancel();
                    System.exit(0);
                }
            }
        }, 0, 1000);

        java.awt.EventQueue.invokeLater(() -> mn.setVisible(true));
    }
}