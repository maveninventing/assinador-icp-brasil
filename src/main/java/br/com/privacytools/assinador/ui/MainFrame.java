package br.com.privacytools.assinador.ui;

import br.com.privacytools.assinador.certificate.CertificateEntry;
import br.com.privacytools.assinador.certificate.CertificateService;
import br.com.privacytools.assinador.signing.PdfSignerService;
import br.com.privacytools.assinador.signing.SignaturePlacement;
import br.com.privacytools.assinador.util.FileNameUtils;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MainFrame extends JFrame {
    private final CertificateService certificateService = new CertificateService();
    private final PdfSignerService signerService = new PdfSignerService();
    private final PdfPreviewPanel previewPanel = new PdfPreviewPanel();
    private final DefaultComboBoxModel<CertificateEntry> certificateModel = new DefaultComboBoxModel<>();
    private final JComboBox<CertificateEntry> certificateCombo = new JComboBox<>(certificateModel);
    private final JTextField pdfPathField = new JTextField();
    private final JTextField reasonField = new JTextField("Assinatura digital do documento");
    private final JLabel pageLabel = new JLabel("Página 0 de 0");
    private final JLabel statusLabel = new JLabel("Pronto");
    private final JButton previousButton = new JButton("◀ Anterior");
    private final JButton nextButton = new JButton("Próxima ▶");
    private final JButton signButton = new JButton("Assinar PDF");
    private final JButton reloadCertificatesButton = new JButton("Atualizar certificados");
    private final JButton tokenButton = new JButton("Carregar token PKCS#11...");
    private final JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(70.0, 30.0, 180.0, 5.0));
    private final JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(25.0, 15.0, 80.0, 5.0));
    private final List<CertificateEntry> tokenCertificates = new ArrayList<>();

    private Path selectedPdf;

    public MainFrame() {
        super("Assinador ICP-Brasil");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 760));
        setSize(1200, 860);
        setLocationRelativeTo(null);

        buildUi();
        bindActions();
        updateControls();
        loadWindowsCertificates();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                try {
                    previewPanel.close();
                } catch (Exception ignored) {
                    // Nada a fazer durante o encerramento.
                }
            }
        });
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        JLabel title = new JLabel("Assinador digital de documentos PDF");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        JPanel configuration = new JPanel(new GridBagLayout());
        configuration.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Documento e certificado"),
                BorderFactory.createEmptyBorder(4, 6, 6, 6)
        ));

        pdfPathField.setEditable(false);
        JButton choosePdfButton = new JButton("Escolher PDF...");
        choosePdfButton.setActionCommand("choosePdf");

        addRow(configuration, 0, "PDF de origem:", pdfPathField, choosePdfButton);
        addRow(configuration, 1, "Certificado:", certificateCombo, reloadCertificatesButton, tokenButton);

        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sizePanel.add(new JLabel("Largura:"));
        sizePanel.add(widthSpinner);
        sizePanel.add(new JLabel("mm   Altura:"));
        sizePanel.add(heightSpinner);
        sizePanel.add(new JLabel("mm"));
        addRow(configuration, 2, "Área visível:", sizePanel);
        addRow(configuration, 3, "Motivo:", reasonField);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(title);
        north.add(Box.createVerticalStrut(8));
        north.add(configuration);
        root.add(north, BorderLayout.NORTH);

        JScrollPane previewScroll = new JScrollPane(previewPanel);
        previewScroll.setBorder(BorderFactory.createTitledBorder(
                "Clique para posicionar e arraste a caixa de assinatura"
        ));
        root.add(previewScroll, BorderLayout.CENTER);

        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 3));
        navigation.add(previousButton);
        navigation.add(pageLabel);
        navigation.add(nextButton);

        signButton.setFont(signButton.getFont().deriveFont(Font.BOLD, 15f));
        JPanel actions = new JPanel(new BorderLayout(8, 8));
        actions.add(navigation, BorderLayout.CENTER);
        actions.add(signButton, BorderLayout.EAST);
        actions.add(statusLabel, BorderLayout.SOUTH);
        root.add(actions, BorderLayout.SOUTH);

        choosePdfButton.addActionListener(event -> choosePdf());
    }

    private void bindActions() {
        reloadCertificatesButton.addActionListener(event -> loadWindowsCertificates());
        tokenButton.addActionListener(event -> loadPkcs11Token());
        previousButton.addActionListener(event -> changePage(false));
        nextButton.addActionListener(event -> changePage(true));
        signButton.addActionListener(event -> signPdf());

        previewPanel.addPropertyChangeListener("page", event -> updatePageLabel());
        widthSpinner.addChangeListener(event -> updateSignatureSize());
        heightSpinner.addChangeListener(event -> updateSignatureSize());
        certificateCombo.addActionListener(event -> updateControls());
    }

    private void addRow(JPanel panel, int row, String labelText, java.awt.Component... components) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.WEST;
        labelConstraints.insets = new Insets(5, 3, 5, 8);
        panel.add(new JLabel(labelText), labelConstraints);

        for (int index = 0; index < components.length; index++) {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = index + 1;
            constraints.gridy = row;
            constraints.insets = new Insets(5, 3, 5, 3);
            constraints.anchor = GridBagConstraints.WEST;
            constraints.fill = index == 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
            constraints.weightx = index == 0 ? 1.0 : 0.0;
            panel.add(components[index], constraints);
        }
    }

    private void choosePdf() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Escolha o PDF que será assinado");
        chooser.setFileFilter(new FileNameExtensionFilter("Documentos PDF", "pdf"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path file = chooser.getSelectedFile().toPath();
        setBusy(true, "Abrindo e renderizando o PDF...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                previewPanel.load(file);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    selectedPdf = file;
                    pdfPathField.setText(file.toAbsolutePath().toString());
                    updateSignatureSize();
                    updatePageLabel();
                    setBusy(false, "PDF carregado. Posicione a assinatura.");
                } catch (Exception error) {
                    selectedPdf = null;
                    showError("Não foi possível abrir o PDF", rootCause(error));
                    setBusy(false, "Falha ao abrir o PDF");
                }
                updateControls();
            }
        }.execute();
    }

    private void loadWindowsCertificates() {
        setBusy(true, "Lendo certificados instalados no Windows...");
        new SwingWorker<List<CertificateEntry>, Void>() {
            @Override
            protected List<CertificateEntry> doInBackground() throws Exception {
                return certificateService.loadWindowsCertificates();
            }

            @Override
            protected void done() {
                try {
                    List<CertificateEntry> windowsCertificates = get();
                    rebuildCertificateModel(windowsCertificates);
                    setBusy(false, certificateModel.getSize() + " certificado(s) disponível(is)");
                } catch (Exception error) {
                    rebuildCertificateModel(List.of());
                    showError("Não foi possível acessar o repositório de certificados do Windows", rootCause(error));
                    setBusy(false, "Falha ao carregar certificados");
                }
                updateControls();
            }
        }.execute();
    }

    private void loadPkcs11Token() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecione a DLL PKCS#11 fornecida pelo fabricante do token");
        chooser.setFileFilter(new FileNameExtensionFilter("Biblioteca PKCS#11 (*.dll)", "dll"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        JPasswordField passwordField = new JPasswordField(18);
        int answer = JOptionPane.showConfirmDialog(
                this,
                passwordField,
                "PIN do token A3",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }

        char[] pin = passwordField.getPassword();
        Path driverDll = chooser.getSelectedFile().toPath();
        setBusy(true, "Conectando ao token A3...");

        new SwingWorker<List<CertificateEntry>, Void>() {
            @Override
            protected List<CertificateEntry> doInBackground() throws Exception {
                try {
                    return certificateService.loadPkcs11Token(driverDll, pin);
                } finally {
                    java.util.Arrays.fill(pin, '\0');
                }
            }

            @Override
            protected void done() {
                try {
                    List<CertificateEntry> loaded = get();
                    tokenCertificates.addAll(loaded);
                    for (CertificateEntry entry : loaded) {
                        certificateModel.addElement(entry);
                    }
                    if (!loaded.isEmpty()) {
                        certificateCombo.setSelectedItem(loaded.get(0));
                    }
                    setBusy(false, loaded.size() + " certificado(s) carregado(s) do token");
                } catch (Exception error) {
                    showError("Não foi possível acessar o token", rootCause(error));
                    setBusy(false, "Falha ao acessar o token");
                }
                updateControls();
            }
        }.execute();
    }

    private void rebuildCertificateModel(List<CertificateEntry> windowsCertificates) {
        CertificateEntry selected = (CertificateEntry) certificateCombo.getSelectedItem();
        certificateModel.removeAllElements();
        windowsCertificates.forEach(certificateModel::addElement);
        tokenCertificates.forEach(certificateModel::addElement);

        if (selected != null) {
            certificateCombo.setSelectedItem(selected);
        }
        if (certificateCombo.getSelectedIndex() < 0 && certificateModel.getSize() > 0) {
            certificateCombo.setSelectedIndex(0);
        }
    }

    private void changePage(boolean next) {
        try {
            if (next) {
                previewPanel.nextPage();
            } else {
                previewPanel.previousPage();
            }
            updatePageLabel();
        } catch (Exception error) {
            showError("Não foi possível trocar a página", error);
        }
    }

    private void updateSignatureSize() {
        double width = ((Number) widthSpinner.getValue()).doubleValue();
        double height = ((Number) heightSpinner.getValue()).doubleValue();
        previewPanel.setSignatureSizeMm(width, height);
    }

    private void signPdf() {
        CertificateEntry certificate = (CertificateEntry) certificateCombo.getSelectedItem();
        SignaturePlacement placement = previewPanel.getPlacement();
        if (selectedPdf == null || certificate == null || placement == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Selecione o PDF, o certificado e posicione a assinatura.",
                    "Dados incompletos",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        Path output = FileNameUtils.signedOutput(selectedPdf);
        if (Files.exists(output)) {
            int answer = JOptionPane.showConfirmDialog(
                    this,
                    "O arquivo já existe:\n" + output + "\n\nDeseja substituí-lo?",
                    "Confirmar substituição",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }

        setBusy(true, certificate.source().startsWith("Windows")
                ? "Aguardando o certificado. O driver do token poderá solicitar o PIN..."
                : "Assinando com o token A3...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                signerService.sign(selectedPdf, output, certificate, placement, reasonField.getText());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    setBusy(false, "Documento assinado com sucesso: " + output.getFileName());
                    JOptionPane.showMessageDialog(
                            MainFrame.this,
                            "Documento assinado com sucesso:\n" + output.toAbsolutePath(),
                            "Assinatura concluída",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception error) {
                    showError("Não foi possível assinar o documento", rootCause(error));
                    setBusy(false, "Falha na assinatura");
                }
                updateControls();
            }
        }.execute();
    }

    private void updatePageLabel() {
        pageLabel.setText("Página " + previewPanel.getCurrentPageNumber() + " de " + previewPanel.getPageCount());
        updateControls();
    }

    private void updateControls() {
        boolean hasDocument = previewPanel.hasDocument();
        boolean hasCertificate = certificateCombo.getSelectedItem() != null;
        previousButton.setEnabled(hasDocument && previewPanel.getCurrentPageNumber() > 1);
        nextButton.setEnabled(hasDocument && previewPanel.getCurrentPageNumber() < previewPanel.getPageCount());
        signButton.setEnabled(hasDocument && hasCertificate && selectedPdf != null);
    }

    private void setBusy(boolean busy, String status) {
        statusLabel.setText(status);
        signButton.setEnabled(!busy && selectedPdf != null && certificateCombo.getSelectedItem() != null);
        reloadCertificatesButton.setEnabled(!busy);
        tokenButton.setEnabled(!busy);
        certificateCombo.setEnabled(!busy);
        previousButton.setEnabled(!busy && previewPanel.hasDocument() && previewPanel.getCurrentPageNumber() > 1);
        nextButton.setEnabled(!busy && previewPanel.hasDocument()
                && previewPanel.getCurrentPageNumber() < previewPanel.getPageCount());
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private void showError(String title, Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            message = error.toString();
        }
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }
}
