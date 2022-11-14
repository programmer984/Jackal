import org.example.IpUtils;
import org.junit.Assert;
import org.junit.Test;

import java.net.SocketAddress;
import java.net.UnknownHostException;

public class Tests {

    @Test
    public void IpParseTest() throws UnknownHostException {
        SocketAddress address = IpUtils.parseIpAndPort("192.168.1.100:27015");
        Assert.assertNotNull(address);
    }
}
