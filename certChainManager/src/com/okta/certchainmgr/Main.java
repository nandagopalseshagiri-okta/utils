package com.okta.certchainmgr;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        try {
            // First we will load all the chains from the cert store directory
            CertChainManager certChainManager = new CertChainManager();
            String storeDir = "/Users/nandagopalseshagiri/ccstore-test";
            Map<String, CertChainManager.CertChain> existingCerts = certChainManager.mapChains(
                    certChainManager.readAllWellFormedChainsFromDir(storeDir));

            // Now we ask the user to input their set of certs from which we will form chains
            System.out.println("enter the path to certs file");
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(System.in));

            String filePath = reader.readLine();
            CertChainManager.LoadResult lr = certChainManager.loadCertChains(filePath);

            System.out.println("Completed loading of certs from " + filePath);

            // Certs loaded from user's input are not written to store automatically - we need to do that by calling
            // writeNewChains and passing the certChainBuilder. Passing existingCerts in not absolutely necessary because
            // duplicates chains will get the same sha and as the file names in the store will use the sha of the chain - the file would
            // just fail to be moved to store directory as a file with same sha would already exist.
            CertChainManager.WriteChainResult wr = certChainManager.writeNewChains(lr.certChainBuilder, existingCerts, storeDir);
            System.out.println(String.format("%d chains were written", wr.getSuccessfulWrites().size()));
            System.out.println(String.format("%d chains were duplicate", wr.getDuplicateChains().size()));
            System.out.println(String.format("%d chains failed to be written", wr.getWriteException().size()));
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }
}
