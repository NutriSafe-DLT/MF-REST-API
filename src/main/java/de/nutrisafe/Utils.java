package de.nutrisafe;

import org.hyperledger.fabric.gateway.*;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.ResourceUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Utils {

    private Config config;

    public Utils(Config config) {
        this.config = config;
    }

    private Wallet loadWallet() throws IOException {
        Wallet wallet = Wallets.newInMemoryWallet();
        wallet.put(config.getCompany(), Identities.newX509Identity(config.getCompany(),
                Objects.requireNonNull(loadCertificate()),
                Objects.requireNonNull(loadPrivateKey())));
        return wallet;
    }

    private X509Certificate loadCertificate() {
        try {
            File file = ResourceUtils.getFile("classpath:" + config.getCertPath());
            byte[] encodedCert = Files.readAllBytes(file.toPath());
            ByteArrayInputStream inputStream = new ByteArrayInputStream(encodedCert);
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certFactory.generateCertificate(inputStream);
        } catch (Exception e) {
            System.err.println("[NutriSafe REST API] Could not load certificate.");
            e.printStackTrace();
        }
        return null;
    }

    private PrivateKey loadPrivateKey() {
        try {
            Reader reader = new InputStreamReader(new DefaultResourceLoader().getResource(
                    config.getPrivateKeyPath()).getInputStream(), UTF_8);
            String privateKeyPEM = FileCopyUtils.copyToString(reader);
            byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
            KeyFactory kf = KeyFactory.getInstance("EC");
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
            return kf.generatePrivate(keySpec);
        } catch (Exception e) {
            System.err.println("[NutriSafe REST API] Could not load private key.");
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Setting up a connection to the "NutriSafe" Network.
     * @return returning the contract which is used for submitting or evaluate a transaction.
     */
    private Contract prepareTransaction() {
        Contract contract = null;
        try {
            /*
             * Preparing a builder for our Gateway.
             * .discovery(): Service discovery for all transaction submissions is enabled.
            */
            Path networkConfigFile = ResourceUtils.getFile("classpath:" + config.getNetworkConfigPath()).toPath();
            Gateway.Builder builder = Gateway.createBuilder()
                    .identity(loadWallet(), config.getCompany())
                    .networkConfig(networkConfigFile);
                    //.discovery(true);

            final Gateway gateway = builder.connect();

            final Network network = gateway.getNetwork(config.getChannelName());

            contract = network.getContract(config.getChaincodeName());

        } catch (IOException e) {
            System.err.println("[NutriSafe REST API] Could not prepare the transaction.");
            e.printStackTrace();
        }
        return contract;
    }

    public String submitTransaction(final String function, String[] args, HashMap<String, byte[]> pArgs) {
        String ret = "";
        try {
            Contract contract = prepareTransaction();
            if(contract == null) throw new IOException();
            final byte[] result;
            if (pArgs.size() == 0){
                result = contract.createTransaction(function)
                        .submit(args);
            }
            else {
                result = contract.createTransaction(function)
                        .setTransient(pArgs)
                        .submit(args);
            }
            ret = new String(result, UTF_8);

        } catch (IOException | TimeoutException | ContractException | InterruptedException e) {
            e.printStackTrace();
            System.out.println(e);
        }
        return ret;
    }

    public String evaluateTransaction(final String function, final String[] args) throws Exception {
        String ret = "";
        try {
            Contract contract = prepareTransaction();
            if(contract == null) throw new IOException();
            byte[] result;
            if (args == null){
                result = contract.evaluateTransaction(function);
            }
            else {
                result = contract.evaluateTransaction(function, args);
            }
            System.out.println(new String(result, UTF_8));
            ret = new String(result, UTF_8);

        } catch (IOException | ContractException e) {
            e.printStackTrace();
            System.out.println(e);
        }
        return ret;
    }
}
