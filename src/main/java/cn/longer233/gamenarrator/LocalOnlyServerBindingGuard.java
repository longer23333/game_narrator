package cn.longer233.gamenarrator;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Prevents an unauthenticated GameNarrator instance from being exposed outside this computer. */
final class LocalOnlyServerBindingGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        validate(context.getEnvironment().getProperty("server.address", "127.0.0.1"),
                context.getEnvironment().getProperty("game-narrator.auth.require-login", Boolean.class, false));
    }

    static void validate(String configuredAddress) {
        validate(configuredAddress, false);
    }

    static void validate(String configuredAddress, boolean authenticatedApi) {
        if (authenticatedApi) return;
        String address = configuredAddress == null ? "" : configuredAddress.trim();
        if (address.startsWith("[") && address.endsWith("]")) {
            address = address.substring(1, address.length() - 1);
        }
        if (address.isBlank()) {
            throw rejected(configuredAddress);
        }
        try {
            InetAddress[] resolved = InetAddress.getAllByName(address);
            if (resolved.length == 0) throw rejected(configuredAddress);
            for (InetAddress candidate : resolved) {
                if (!candidate.isLoopbackAddress()) throw rejected(configuredAddress);
            }
        } catch (UnknownHostException exception) {
            throw new IllegalStateException("无法解析服务监听地址：" + address, exception);
        }
    }

    private static IllegalStateException rejected(String address) {
        return new IllegalStateException("安全策略拒绝非本机监听地址：" + address
                + "。当前版本没有 API 身份认证，只允许 127.0.0.1、::1 或 localhost；"
                + "开放局域网前必须先实现认证与授权。");
    }
}
