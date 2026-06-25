package br.com.privacytools.assinador.certificate;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.security.cert.X509Certificate;

public final class CertificateNameUtils {
    private CertificateNameUtils() {
    }

    public static String commonName(X509Certificate certificate) {
        String dn = certificate.getSubjectX500Principal().getName();
        try {
            for (Rdn rdn : new LdapName(dn).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return String.valueOf(rdn.getValue());
                }
            }
        } catch (InvalidNameException ignored) {
            // Usa o DN completo como fallback.
        }
        return dn;
    }
}
