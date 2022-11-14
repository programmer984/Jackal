import org.example.softTimer.TimersManager;
import org.example.tools.*;
import org.example.udphole.sync.NodeTool;

import java.security.GeneralSecurityException;

public class Java8DesktopFactory implements UdpHoleDataPipeFactory {
    final TimersManager timersManager = new TimersManager();

    @Override
    public Base64Tool createBase64Tool() {
        return new Base64Encoder();
    }

    @Override
    public SynchronizationTool createSynchronizationTool() {
        return new NodeTool(createJsonTool(), createHttpTool());
    }

    @Override
    public RsaEncryptionTool createRsaEncryptionTool() {
        try {
            return new RsaEncryption(createBase64Tool());
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public AesEncryptionTool createAesEncryptionTool() {
        try {
            return new AesEncryption();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public JsonTool createJsonTool() {
        return new Jackson();
    }

    @Override
    public HttpTool createHttpTool() {
        return new ApacheHttpClient();
    }

    @Override
    public TimersManager getTimersManager() {
        return timersManager;
    }
}
