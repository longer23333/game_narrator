package cn.longer233.gamenarrator.mobile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ComputerCompanionClientTest {
    @Test public void acceptsOnlyLocalOrPrivateComputerAddresses() throws Exception {
        assertEquals("http://10.0.2.2:8080/", ComputerCompanionClient.validateAddress("http://10.0.2.2:8080").toString());
        assertEquals("https://192.168.1.8:8443/", ComputerCompanionClient.validateAddress("https://192.168.1.8:8443").toString());
        try { ComputerCompanionClient.validateAddress("https://8.8.8.8:8080"); fail("public address accepted"); }
        catch (IllegalArgumentException expected) { }
        try { ComputerCompanionClient.validateAddress("file:///tmp/server"); fail("file address accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
