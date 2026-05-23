import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Details dialog — collects examinee info before the test starts.
 *
 * The admin shortcut (ADMIN1999 / passcode1) has been removed.
 * Solution file generation now happens automatically in Main.java
 * after the test ends.
 */
public class Details extends javax.swing.JDialog {

    public Details(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        setTitle("EvalSys");
        try {
            java.net.URL iconUrl = getClass().getResource("/logo.png");
            if (iconUrl != null) {
                setIconImage(new javax.swing.ImageIcon(iconUrl).getImage());
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {

        title = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        name = new javax.swing.JTextField();
        section = new javax.swing.JTextField();
        roll = new javax.swing.JTextField();
        start = new java.awt.Button();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        title.setFont(new java.awt.Font("Segoe UI", 0, 36));
        title.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        try {
            title.setIcon(new javax.swing.ImageIcon(getClass().getResource("/logo.png")));
        } catch (Exception e) {
            /* icon optional */ }
        title.setText("EvalSys");

        jLabel1.setFont(new java.awt.Font("Arial", 0, 24));
        jLabel1.setText("Enter the following Details:");

        jLabel2.setFont(new java.awt.Font("Arial", 0, 24));
        jLabel2.setText("Name:");

        jLabel3.setFont(new java.awt.Font("Arial", 0, 24));
        jLabel3.setText("Section:");

        jLabel4.setFont(new java.awt.Font("Arial", 0, 24));
        jLabel4.setText("Registration Number:");

        name.setFont(new java.awt.Font("Arial", 0, 24));
        section.setFont(new java.awt.Font("Arial", 0, 24));
        roll.setFont(new java.awt.Font("Arial", 0, 24));

        start.setBackground(new java.awt.Color(0, 153, 51));
        start.setLabel("Start Test");
        start.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(title, javax.swing.GroupLayout.DEFAULT_SIZE, 692, Short.MAX_VALUE)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(start, javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout
                                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addComponent(jLabel2)
                                                        .addComponent(jLabel3))
                                                .addGap(18, 18, 18)
                                                .addGroup(layout
                                                        .createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addGroup(layout.createSequentialGroup()
                                                                .addGroup(layout.createParallelGroup(
                                                                        javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addComponent(jLabel1)
                                                                        .addGroup(layout.createSequentialGroup()
                                                                                .addComponent(section,
                                                                                        javax.swing.GroupLayout.PREFERRED_SIZE,
                                                                                        78,
                                                                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                                .addGap(10, 10, 10)
                                                                                .addComponent(jLabel4)
                                                                                .addPreferredGap(
                                                                                        javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                                                .addComponent(roll,
                                                                                        javax.swing.GroupLayout.PREFERRED_SIZE,
                                                                                        75,
                                                                                        javax.swing.GroupLayout.PREFERRED_SIZE)))
                                                                .addGap(0, 0, Short.MAX_VALUE))
                                                        .addComponent(name))))
                                .addContainerGap()));
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(title)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel2)
                                        .addComponent(name, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(roll, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel3)
                                        .addComponent(jLabel4)
                                        .addComponent(section, javax.swing.GroupLayout.PREFERRED_SIZE,
                                                javax.swing.GroupLayout.DEFAULT_SIZE,
                                                javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 62,
                                        Short.MAX_VALUE)
                                .addComponent(start, javax.swing.GroupLayout.PREFERRED_SIZE,
                                        javax.swing.GroupLayout.DEFAULT_SIZE,
                                        javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addContainerGap()));

        pack();
    }

    public boolean started = false;

    private void startActionPerformed(java.awt.event.ActionEvent evt) {
        // Normal student flow only — admin shortcut removed
        if (name.getText().trim().isEmpty()
                || section.getText().trim().isEmpty()
                || roll.getText().trim().isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this,
                    "Please fill all details.", "Error",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        started = true;
        this.dispose();
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    public String getExaminerName() {
        return name.getText().trim();
    }

    public String getSection() {
        return section.getText().trim();
    }

    public String getRegNo() {
        return roll.getText().trim();
    }

    public String recent = "";

    // Variables declaration
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JTextField name;
    private javax.swing.JTextField roll;
    private javax.swing.JTextField section;
    private java.awt.Button start;
    private javax.swing.JLabel title;
}