package br.com.privacytools.assinador.signing;

import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Hashtable;

final class CmsSignature implements SignatureInterface {
    private final PrivateKey privateKey;
    private final X509Certificate[] chain;
    private final Provider signingProvider;

    CmsSignature(PrivateKey privateKey, X509Certificate[] chain, Provider signingProvider) {
        this.privateKey = privateKey;
        this.chain = chain.clone();
        this.signingProvider = signingProvider;
    }

    @Override
    public byte[] sign(InputStream content) throws IOException {
        try {
            String algorithm = signatureAlgorithm(privateKey);
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();

            ContentSigner contentSigner = new JcaContentSignerBuilder(algorithm)
                    .setProvider(signingProvider)
                    .build(privateKey);

            DigestCalculatorProvider digestProvider = new JcaDigestCalculatorProviderBuilder()
                    .setProvider("BC")
                    .build();

            JcaSignerInfoGeneratorBuilder signerInfoBuilder = new JcaSignerInfoGeneratorBuilder(digestProvider);
            signerInfoBuilder.setSignedAttributeGenerator(createSignedAttributes(chain[0]));
            generator.addSignerInfoGenerator(signerInfoBuilder.build(contentSigner, chain[0]));
            generator.addCertificates(new JcaCertStore(Arrays.asList(chain)));

            CMSTypedData processable = new InputStreamProcessable(content);
            return generator.generate(processable, false).getEncoded();
        } catch (OperatorCreationException | CMSException | java.security.GeneralSecurityException error) {
            throw new IOException("Não foi possível criar a assinatura criptográfica: " + error.getMessage(), error);
        }
    }

    private DefaultSignedAttributeTableGenerator createSignedAttributes(X509Certificate signerCertificate)
            throws java.security.GeneralSecurityException {
        byte[] certificateHash = MessageDigest.getInstance("SHA-256").digest(signerCertificate.getEncoded());
        ESSCertIDv2 certId = new ESSCertIDv2(
                new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256),
                certificateHash
        );
        SigningCertificateV2 signingCertificate = new SigningCertificateV2(new ESSCertIDv2[]{certId});

        Hashtable<org.bouncycastle.asn1.ASN1ObjectIdentifier, Attribute> attributes = new Hashtable<>();
        attributes.put(
                PKCSObjectIdentifiers.id_aa_signingCertificateV2,
                new Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2, new DERSet(signingCertificate))
        );
        return new DefaultSignedAttributeTableGenerator(new AttributeTable(attributes));
    }

    private String signatureAlgorithm(PrivateKey key) {
        return switch (key.getAlgorithm().toUpperCase()) {
            case "RSA", "RSASSA-PSS" -> "SHA256withRSA";
            case "EC", "ECDSA" -> "SHA256withECDSA";
            case "DSA" -> "SHA256withDSA";
            default -> throw new IllegalArgumentException("Algoritmo de chave não suportado: " + key.getAlgorithm());
        };
    }

    private static final class InputStreamProcessable implements CMSTypedData {
        private final InputStream input;

        private InputStreamProcessable(InputStream input) {
            this.input = input;
        }

        @Override
        public ASN1ObjectIdentifier getContentType() {
            return CMSObjectIdentifiers.data;
        }

        @Override
        public Object getContent() {
            return input;
        }

        @Override
        public void write(OutputStream output) throws IOException {
            input.transferTo(output);
        }
    }
}
