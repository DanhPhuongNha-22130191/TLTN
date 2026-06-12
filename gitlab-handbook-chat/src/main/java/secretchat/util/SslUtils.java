package secretchat.util;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

public final class SslUtils {
    private static final System.Logger LOGGER = System.getLogger(SslUtils.class.getName());
    private static final SSLContext SSL_CONTEXT = createSSLContext();

    private SslUtils() {}

    public static SSLContext getSslContext() {
        return SSL_CONTEXT;
    }

    private static SSLContext createSSLContext() {
        try {
            // Load the certificate from resources
            try (InputStream caInput = SslUtils.class.getResourceAsStream("/secretchat-ca.pem")) {
                if (caInput == null) {
                    LOGGER.log(System.Logger.Level.INFO, "secretchat-ca.pem not found in classpath, using system default SSLContext");
                    return SSLContext.getDefault();
                }

                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Certificate ca = cf.generateCertificate(caInput);

                // Create a KeyStore containing our trusted CA
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                keyStore.load(null, null);
                keyStore.setCertificateEntry("ca", ca);

                // Create a TrustManager that trusts the CAs in our KeyStore
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(keyStore);

                // Create an SSLContext that uses our TrustManager
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), null);
                LOGGER.log(System.Logger.Level.INFO, "Successfully loaded embedded secretchat-ca.pem for SSLContext");
                return sslContext;
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Failed to initialize custom SSLContext, falling back to default", e);
            try {
                return SSLContext.getDefault();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
