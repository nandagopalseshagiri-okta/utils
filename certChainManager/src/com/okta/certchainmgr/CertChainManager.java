package com.okta.certchainmgr;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class CertChainManager {
    public class LoadResult {
        public List<String> chainHeads;
        public int ingnoredLeafCertCount;
        public int danglingChainsCount;

        public LoadResult(List<String> heads, int leavesIgnored, int danglingChainsCount) {
            chainHeads = heads;
            ingnoredLeafCertCount = leavesIgnored;
            this.danglingChainsCount = danglingChainsCount;
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
        private List<List<X509Certificate>> fullChains = new ArrayList<>();
        private List<List<X509Certificate>> chains = new ArrayList<>();
        private Map<String, List<List<X509Certificate>>> chainTopOpenEnd = new HashMap<>();
        private Map<String, List<List<X509Certificate>>> chainBottomOpenEnd = new HashMap<>();

        public List<List<X509Certificate>> getChains() {
            return chains;
        }

        public List<List<X509Certificate>> getFullChains() {
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
                fullChains.add(chain);
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
    }

    public LoadResult loadCertChains(String filePath) {
        FileInputStream fileInputStream = null;
        try {
            new FileInputStream(filePath);
        } catch (FileNotFoundException e) {
            throw new CCException("File error - " + e.getMessage(), e);
        }
        return loadCertChains(fileInputStream);
    }

    public LoadResult loadCertChains(InputStream inputStream) {
        int count = 0;
        Map<String, List<X509Certificate>> certs = new HashMap<>();
        Map<String, List<X509Certificate>> rootCerts = new HashMap<>();
        Map<String, List<X509Certificate>> byIssuers = new HashMap<>();
        int ignoredLeaves = 0;
        try {
            BufferedInputStream bis = new BufferedInputStream(inputStream);
            while (bis.available() > 0) {
                X509Certificate cert = readCert(bis);
                count++;
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

            CertChainBuilder certChainBuilder = new CertChainBuilder();

            rootCerts.values().stream().forEach((lc) -> {
                lc.stream().forEach((c) -> certChainBuilder.addCert(c));
            });
            certs.values().stream().forEach((lc) -> {
                lc.stream().forEach(((c) -> certChainBuilder.addCert(c)));
            });

            List<String> chainHeads = new ArrayList<>();
            certChainBuilder.getFullChains().stream().forEach((lc) -> {
                lc.stream().findFirst().map(c -> c.getSubjectDN().toString()).map(s -> chainHeads.add(s));
            });
            return new LoadResult(chainHeads, ignoredLeaves, certChainBuilder.getChains().size() - certChainBuilder.getFullChains().size());
        } catch (CertificateException e) {
            throw new CCException(String.format("Error %1$s loading cert # %$2d", e.getMessage(), count), e);
        } catch (IOException e) {
            throw new CCException("IO Error " + e.getMessage(), e);
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
