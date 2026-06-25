package br.com.privacytools.assinador;

import br.com.privacytools.assinador.ui.MainFrame;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.security.Security;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            JOptionPane.showMessageDialog(
                    null,
                    "Este aplicativo foi projetado para Windows 10/11.",
                    "Sistema não suportado",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Mantém o tema padrão do Swing.
            }

            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> JOptionPane.showMessageDialog(
                    null,
                    error.getMessage() == null ? error.toString() : error.getMessage(),
                    "Erro inesperado",
                    JOptionPane.ERROR_MESSAGE
            ));

            new MainFrame().setVisible(true);
        });
    }
}
