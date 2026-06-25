package br.com.privacytools.assinador.certificate;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;

public final class CertificateEntry {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String source;
    private final String alias;
    private final KeyStore keyStore;
    private final Provider provider;
    private final X509Certificate certificate;

    public CertificateEntry(
            String source,
            String alias,
            KeyStore keyStore,
            Provider provider,
            X509Certificate certificate
    ) {
        this.source = Objects.requireNonNull(source);
        this.alias = Objects.requireNonNull(alias);
        this.keyStore = Objects.requireNonNull(keyStore);
        this.provider = Objects.requireNonNull(provider);
        this.certificate = Objects.requireNonNull(certificate);
    }

    public String source() {
        return source;
    }

    public String alias() {
        return alias;
    }

    public Provider provider() {
        return provider;
    }

    public X509Certificate certificate() {
        return certificate;
    }

    public PrivateKey loadPrivateKey() throws Exception {
        return (PrivateKey) keyStore.getKey(alias, null);
    }

    public X509Certificate[] loadCertificateChain() throws Exception {
        Certificate[] chain = keyStore.getCertificateChain(alias);
        if (chain == null || chain.length == 0) {
            return new X509Certificate[]{certificate};
        }

        return Arrays.stream(chain)
                .filter(X509Certificate.class::isInstance)
                .map(X509Certificate.class::cast)
                .toArray(X509Certificate[]::new);
    }

    public boolean isCurrentlyValid() {
        try {
            certificate.checkValidity();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public String toString() {
        String name = CertificateNameUtils.commonName(certificate);
        String expiry = DATE_FORMAT.format(certificate.getNotAfter().toInstant().atZone(ZoneId.systemDefault()));
        String status = isCurrentlyValid() ? "válido" : "vencido/não vigente";
        return "%s — %s — vence em %s — %s".formatted(name, source, expiry, status);
    }
}
