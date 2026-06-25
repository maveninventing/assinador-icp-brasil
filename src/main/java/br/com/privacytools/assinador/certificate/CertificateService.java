package br.com.privacytools.assinador.certificate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

public final class CertificateService {

    public List<CertificateEntry> loadWindowsCertificates() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("Windows-MY");
        keyStore.load(null, null);
        return listSigningCertificates("Windows", keyStore, keyStore.getProvider());
    }

    public List<CertificateEntry> loadPkcs11Token(Path driverDll, char[] pin) throws Exception {
        if (driverDll == null || !Files.isRegularFile(driverDll)) {
            throw new IOException("A DLL do driver PKCS#11 não foi encontrada.");
        }

        Provider baseProvider = Security.getProvider("SunPKCS11");
        if (baseProvider == null) {
            throw new IllegalStateException("O provedor SunPKCS11 não está disponível nesta instalação do Java.");
        }

        Path configFile = createPkcs11Config(driverDll);
        Provider tokenProvider = baseProvider.configure(configFile.toAbsolutePath().toString());
        if (Security.getProvider(tokenProvider.getName()) == null) {
            Security.addProvider(tokenProvider);
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS11", tokenProvider);
        keyStore.load(null, pin);
        return listSigningCertificates("Token PKCS#11", keyStore, tokenProvider);
    }

    private Path createPkcs11Config(Path driverDll) throws IOException {
        String normalizedLibrary = driverDll.toAbsolutePath().toString().replace('\\', '/');
        String providerName = "ICPToken" + Long.toUnsignedString(System.nanoTime());
        String config = "name=" + providerName + System.lineSeparator()
                + "library=" + normalizedLibrary + System.lineSeparator()
                + "slotListIndex=0" + System.lineSeparator()
                + "attributes=compatibility" + System.lineSeparator();

        Path configFile = Files.createTempFile("assinador-pkcs11-", ".cfg");
        Files.writeString(configFile, config, StandardCharsets.UTF_8);
        configFile.toFile().deleteOnExit();
        return configFile;
    }

    private List<CertificateEntry> listSigningCertificates(
            String source,
            KeyStore keyStore,
            Provider provider
    ) throws Exception {
        List<CertificateEntry> result = new ArrayList<>();
        Enumeration<String> aliases = keyStore.aliases();

        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!keyStore.isKeyEntry(alias)) {
                continue;
            }
            if (!(keyStore.getCertificate(alias) instanceof X509Certificate certificate)) {
                continue;
            }
            if (!allowsDigitalSignature(certificate)) {
                continue;
            }
            result.add(new CertificateEntry(source, alias, keyStore, provider, certificate));
        }

        result.sort(Comparator
                .comparing(CertificateEntry::isCurrentlyValid).reversed()
                .thenComparing(entry -> CertificateNameUtils.commonName(entry.certificate()), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private boolean allowsDigitalSignature(X509Certificate certificate) {
        boolean[] keyUsage = certificate.getKeyUsage();
        if (keyUsage == null) {
            return true;
        }
        boolean digitalSignature = keyUsage.length > 0 && keyUsage[0];
        boolean contentCommitment = keyUsage.length > 1 && keyUsage[1];
        return digitalSignature || contentCommitment;
    }
}
