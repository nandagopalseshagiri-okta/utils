import com.okta.certchainmgr.CertChainManager;
import org.junit.Test;

import javax.annotation.Resource;
import java.io.InputStream;

public class CertChainManagerTest {
    @Test
    public void test() {
        CertChainManager certChainManager = new CertChainManager();
        InputStream resourceInputStream = getClass().getResourceAsStream("certs.list");
        System.out.println(certChainManager.loadCertChains(resourceInputStream));
    }
}

