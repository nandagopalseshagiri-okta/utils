package com.okta.certchainmgr;

import sun.security.provider.X509Factory;

import javax.xml.bind.DatatypeConverter;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class CertChainManager {
    public static String CHAIN_FILE_EXT = ".cchain";
    private Map<String, List<X509Certificate>> certs = new HashMap<>();
    private Map<String, List<X509Certificate>> rootCerts = new HashMap<>();
    private Map<String, List<X509Certificate>> byIssuers = new HashMap<>();
    private Map<String, X509Certificate> certByHashes = new HashMap<>();

    public class CertChain {
        private List<X509Certificate> chain;
        private String sha256;

        public CertChain(List<X509Certificate> chain) {
            this.chain = chain;
        }

        public CertChain(List<X509Certificate> chain, String sha256) {
            this.chain = chain;
            this.sha256 = sha256;
        }

        public List<X509Certificate> getChain() {
            return chain;
        }

        public String getSha256() {
            return sha256;
        }

        public void completeChain() {
            this.sha256 = sha256(this.chain);
        }
    }

    public class LoadResult {
        public List<String> chainHeads;
        public int ingnoredLeafCertCount;
        public int danglingChainsCount;
        public CertChainBuilder certChainBuilder;

        public LoadResult(List<String> heads, int leavesIgnored, int danglingChainsCount, CertChainBuilder certChainBuilder) {
            chainHeads = heads;
            ingnoredLeafCertCount = leavesIgnored;
            this.danglingChainsCount = danglingChainsCount;
            this.certChainBuilder = certChainBuilder;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(chainHeads.toString());
            sb.append(", ingnoredLeafCertCount: ");
            sb.append(ingnoredLeafCertCount);
            sb.append(", danglingChainsCount: ");
            sb.append(danglingChainsCount);
            return sb.toString();
        }
    }

    class CertChainBuilder {
        private List<CertChain> fullChains = new ArrayList<>();
        private List<List<X509Certificate>> chains = new ArrayList<>();
        private Map<String, List<List<X509Certificate>>> chainTopOpenEnd = new HashMap<>();
        private Map<String, List<List<X509Certificate>>> chainBottomOpenEnd = new HashMap<>();
        private int ignoredLeaves = 0;

        public List<List<X509Certificate>> getChains() {
            return chains;
        }

        public List<CertChain> getFullChains() {
            return fullChains;
        }

        public void addCert(X509Certificate certificate) {
            String subject = certificate.getSubjectDN().toString();
            String issuer = certificate.getIssuerDN().toString();
            if (chainTopOpenEnd.containsKey(subject)) {
                addToTop(chainTopOpenEnd.get(subject), certificate);
            } else if (chainBottomOpenEnd.containsKey(issuer)) {
                addToBottom(chainBottomOpenEnd.get(issuer), certificate);
            } else {
                List<X509Certificate> newChain = new ArrayList<>();
                newChain.add(certificate);
                chains.add(newChain);
                addToTopIndex(certificate, newChain);
                addToBottomIndex(certificate, newChain);
            }
        }

        private void addToTop(List<List<X509Certificate>> chains, X509Certificate certificate) {
            int i = 0;
            while (i < chains.size()) {
                try {
                    chains.get(i).get(0).verify(certificate.getPublicKey());
                    chains.get(i).add(0, certificate);
                    List<X509Certificate> chain = chains.remove(i);
                    addToTopIndex(certificate, chain);
                } catch (Exception e) {
                    ++i;
                }
            }
        }

        private boolean addToTopIndex(X509Certificate certificate, List<X509Certificate> chain) {
            if (isCertRoot(certificate)) {
                fullChains.add(new CertChain(chain));
                return true;
            }
            String key = certificate.getIssuerDN().toString();
            chainTopOpenEnd.putIfAbsent(key, new ArrayList<>());
            chainTopOpenEnd.get(key).add(chain);
            return false;
        }

        private void addToBottom(List<List<X509Certificate>> chains, X509Certificate certificate) {
            int i = 0;
            while (i < chains.size()) {
                try {
                    List<X509Certificate> chain = chains.get(i);
                    certificate.verify(chain.get(chain.size() - 1).getPublicKey());
                    chain.add(certificate);
                    chains.remove(i);
                    addToBottomIndex(certificate, chain);
                } catch (Exception e) {
                    ++i;
                }
            }
        }

        private void addToBottomIndex(X509Certificate certificate, List<X509Certificate> chain) {
            String key = certificate.getSubjectDN().toString();
            chainBottomOpenEnd.putIfAbsent(key, new ArrayList<>());
            chainBottomOpenEnd.get(key).add(chain);
        }

        public int getIgnoredLeaves() {
            return ignoredLeaves;
        }

        public void incrementIgnoredLeaves(int ignoredLeaves) {
            this.ignoredLeaves += ignoredLeaves;
        }

        public void done() {
            fullChains.stream().forEach(certChain -> certChain.completeChain());
        }
    }

    public LoadResult loadCertChains(String filePath) {
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(filePath);
        } catch (FileNotFoundException e) {
            throw new CCException("File error - " + e.getMessage(), e);
        }
        return loadCertChains(fileInputStream);
    }

    private static CCException handleException(Exception argEx, int count) {
        try {
            throw argEx;
        } catch (CertificateException e) {
            return new CCException(String.format("Error %1$s loading cert # %$2d", e.getMessage(), count), e);
        } catch (IOException e) {
            return new CCException("IO Error " + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            return new CCException("Crypto Algo no available " + e.getMessage(), e);
        } catch (CCException e) {
            return e;
        } catch (Exception e) {
            return new CCException("Unknown exception", e);
        }
    }

    /**
     * Loads certs from a stream and forms as many cert chains as possible and returns a simple printable result
     * Often calling makeOrAddToCertChainBuilder directly is what most programs would need. This method is just a quick testing
     * method.
     * @param inputStream
     * @return
     */
    public LoadResult loadCertChains(InputStream inputStream) {
        CertChainBuilder certChainBuilder = makeOrAddToCertChainBuilder(inputStream, null);
        certChainBuilder.done();
        List<String> chainHeads = new ArrayList<>();
        certChainBuilder.getFullChains().stream().forEach((lc) -> {
            lc.getChain().stream().findFirst().map(c -> c.getSubjectDN().toString()).map(s -> chainHeads.add(s));
        });
        return new LoadResult(chainHeads, certChainBuilder.getIgnoredLeaves(),
                certChainBuilder.getChains().size() - certChainBuilder.getFullChains().size(), certChainBuilder);
    }

    /**
     * This will create a CertChainBuilder from a stream of certs in no particular order. Passing the returned builder
     * in subsequent calls will allow building chain or chains using certs from multiple streams. Like root certs could
     * come from one stream and intermediates from a different stream etc.
     * Once building is complete you must call done on the builder to indicate that the all well rooted chains are
     * done - so sha256 for each chain can be computed.
     *
     * This method modifies the state of the object - like it stores the certs it has seen - meaning if the same cert is
     * seen coming through a different stream (in a subsequent call - the duplicate cert would be ignored.
     * @param inputStream - stream of certs.
     * @param certChainBuilderOrNull - pass null if you want create a new builder or pass the last returned
     *                               object to continue to add more chains to the same builder by reading certs from
     *                               multiple streams
     * @return
     */
    public CertChainBuilder makeOrAddToCertChainBuilder(InputStream inputStream, CertChainBuilder certChainBuilderOrNull) {
        int count = 0;
        int ignoredLeaves = 0;
        try {
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            while (bis.available() > 0) {
                X509Certificate cert = readCert(bis);
                count++;
                String sha256 = sha256(cert);
                if (certByHashes.containsKey(sha256)) {
                    continue;
                }
                certByHashes.put(sha256, cert);
                if (cert.getBasicConstraints() < 0) {
                    ignoredLeaves++;
                    continue;
                }
                boolean ignored = isCertRoot(cert) ? addToMapBySubject(rootCerts, cert) : addToMapBySubject(certs, cert);
                addToMap(byIssuers, cert, (c) -> c.getIssuerDN().toString());
            }
            if (rootCerts.size() <= 0) {
                throw new CCException("At least one root cert should be present", null);
            }

            CertChainBuilder certChainBuilder = certChainBuilderOrNull != null? certChainBuilderOrNull : new CertChainBuilder();

            rootCerts.values().stream().forEach((lc) -> {
                lc.stream().forEach((c) -> certChainBuilder.addCert(c));
            });
            certs.values().stream().forEach((lc) -> {
                lc.stream().forEach(((c) -> certChainBuilder.addCert(c)));
            });

            certChainBuilder.incrementIgnoredLeaves(ignoredLeaves);
            return certChainBuilder;
        } catch (Exception e) {
            throw handleException(e, count);
        }
    }

    /**
     * This function assume that the stream contains a well formed chain - that is - first cert should be the root
     * followed by 1st intermediate and the then second and so on.
     * This is an optimization - instead of re-building the cert chain using makeOrAddToCertChainBuilder on a well formed
     * chain, which could be expensive, so this method could be used to create chains from well formed streams.
     * @param inputStream
     * @return
     */
    public CertChain readChainFromStream(InputStream inputStream) {
        int count = 0;
        Charset utf8 = Charset.forName("UTF8");
        List<X509Certificate> certs = new ArrayList<>();
        try {
            MessageDigest finalSha = MessageDigest.getInstance("SHA-256");
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            while (bis.available() > 0) {
                X509Certificate cert = readCert(bis);
                count++;
                certs.add(cert);
                String sha256 = sha256(cert);
                finalSha.update(sha256.getBytes(utf8));
            }
            return new CertChain(certs, DatatypeConverter.printHexBinary(finalSha.digest()).toLowerCase());
        } catch (Exception e) {
            throw handleException(e, count);
        }
    }

    /**
     * After reading CertChains from multiples streams this method can be used to de-duplicate the cert chains
     * and form a map from sha256 of the chain to the chain itself.
     * Later when new chains are being added this map can be consulted to see if a chain already exists.
     *
     * @param chains
     * @return
     */
    public Map<String, CertChain> mapChains(List<CertChain> chains) {
        Map<String, CertChain> certChainMap = new HashMap<>();
        chains.stream().forEach(c -> {
            certChainMap.put(c.getSha256(), c);
        });
        return certChainMap;
    }

    public List<CertChain> readAllWellFormedChainsFromDir(String dirPath) {
        List<CertChain> allChains = new ArrayList<>();
        File f = new File(dirPath);

        FilenameFilter filter = (f1, name) -> name.endsWith(CHAIN_FILE_EXT);
        Arrays.stream(f.list(filter)).map(name -> {
            File chainFile = new File(f, name);
            try {
                return readChainFromStream(new FileInputStream(chainFile.getPath()));
            } catch (FileNotFoundException e) {
                return null;
            }
        }).filter(Objects::nonNull).forEach(chain -> {
            allChains.add(chain);
        });

        return allChains;
    }

    public class WriteChainResult {
        private Map<String, Exception> writeException = new HashMap<>();
        private Map<String, CertChain> duplicateChains = new HashMap<>();
        private Map<String, String> successfulWrites = new HashMap<>();

        public Map<String, CertChain> getDuplicateChains() {
            return duplicateChains;
        }

        public Map<String, Exception> getWriteException() {
            return writeException;
        }

        public Map<String, String> getSuccessfulWrites() {
            return successfulWrites;
        }
    }

    public WriteChainResult writeNewChains(CertChainBuilder builder, Map<String, CertChain> existingChainMap, String dirPath) {
        WriteChainResult writeChainResult = new WriteChainResult();
        builder.getFullChains().stream().forEach(certChain -> {
            if (existingChainMap.containsKey(certChain.getSha256())) {
                writeChainResult.getDuplicateChains().put(certChain.getSha256(), certChain);
                return;
            }
            Exception e = writeChain(certChain, dirPath);
            if (e != null) {
                writeChainResult.getWriteException().put(certChain.getSha256(), e);
            } else {
                // The value should be the path of the file that contains the chain. For now we can do without it.
                writeChainResult.getSuccessfulWrites().put(certChain.getSha256(), "");
            }
        });
        return writeChainResult;
    }

    private Exception writeChain(CertChain certChain, String dirPath) {
        String certFileName = certChain.getSha256() + CHAIN_FILE_EXT;
        String tempPath = "/tmp/" + certFileName;
        try {
            FileWriter fileWriter = new FileWriter(tempPath);
            Exception e = certChain.getChain().stream().map(c -> writeCert(c, fileWriter)).filter(Objects::nonNull).findFirst().orElse(null);
            if (e != null) {
                tryDeleteFile(tempPath);
                return e;
            }
            fileWriter.flush();
            fileWriter.close();
            Files.move(Paths.get(tempPath), Paths.get(dirPath, certFileName));
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private void tryDeleteFile(String path) {
        try {
            Files.delete(Paths.get(path));
        } catch (Exception ignored) {}
    }

    private Exception writeCert(X509Certificate cert, OutputStreamWriter outputStreamWriter) {
        try {
            String certString = DatatypeConverter.printBase64Binary(cert.getEncoded()).replaceAll("(.{64})", "$1\n");
            if (!certString.endsWith("\n")) {
                certString = certString + "\n";
            }
            outputStreamWriter.write(X509Factory.BEGIN_CERT + "\n");
            outputStreamWriter.write(certString);
            outputStreamWriter.write(X509Factory.END_CERT + "\n");
            outputStreamWriter.flush();
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    private static String sha256(List<X509Certificate> certs) {
        int[] count = {0};
        try {
            Charset utf8 = Charset.forName("UTF8");
            MessageDigest finalSha = MessageDigest.getInstance("SHA-256");
            certs.stream().forEach(c -> {
                finalSha.update(sha256_RE(c).getBytes(utf8));
                count[0]++;
            });
            return DatatypeConverter.printHexBinary(finalSha.digest()).toLowerCase();
        } catch (Exception e) {
            throw handleException(e, count[0]);
        }
    }

    private boolean addToMap(Map<String, List<X509Certificate>> map, X509Certificate cert, Function<X509Certificate, String> keyFunc) {
        String key = keyFunc.apply(cert);
        map.putIfAbsent(key, new ArrayList<>());
        return map.get(key).add(cert);
    }

    private boolean addToMapBySubject(Map<String, List<X509Certificate>> map, X509Certificate cert) {
        return addToMap(map, cert, (c) -> c.getSubjectDN().toString());
    }

    private X509Certificate readCert(InputStream inputStream) throws CertificateException {
        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        return (X509Certificate) fact.generateCertificate(inputStream);
    }

    private boolean isCertRoot(X509Certificate certificate) {
        return certificate != null && certificate.getBasicConstraints() > 0 && certificate.getSubjectDN().equals(certificate.getIssuerDN());
    }

    private static String sha256(X509Certificate cert) throws NoSuchAlgorithmException, CertificateEncodingException {
        return DatatypeConverter.printHexBinary(
                MessageDigest.getInstance("SHA-256").digest(
                        cert.getEncoded())).toLowerCase();
    }

    private static String sha256_RE(X509Certificate cert) {
        try {
            return sha256(cert);
        } catch (Exception e) {
            throw handleException(e, 0);
        }
    }

    private void matchAkid() {
        //        String akidOID = "2.5.29.35";
//        byte[] akid = targetCert.getExtensionValue(akidOID);
//        if (akid == null) {
//            // skipping strong match due to absence of akid in the cert.
//            return true;
//        }
//        String strAkid = javax.xml.bind.DatatypeConverter.printHexBinary(akid);
//
//        String skidOID = "2.5.29.14";
//        byte[] skid = certificate.getExtensionValue(skidOID);
//        String strSkid = skid != null ? javax.xml.bind.DatatypeConverter.printHexBinary(skid) : "";
//        return skid != null && strAkid.contains(strSkid);
    }
}
