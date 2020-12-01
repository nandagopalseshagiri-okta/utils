package com.okta.certchainmgr;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;

public class Main {
    public static void main(String[] args) {
        try {
            System.out.println("enter the path to certs file");
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(System.in));

            String filePath = reader.readLine();
            CertChainManager certChainManager = new CertChainManager();
            System.out.println(certChainManager.loadCertChains(filePath));
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }
}
