package cn.longer233.gamenarrator.asset;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.Map;
import java.util.Set;

@Component
public class SafeRemoteHttpConnector {
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

    public HttpURLConnection open(URI initial, Map<String, String> headers, int maximumRedirects) throws IOException {
        URI current = initial;
        for (int redirect = 0; redirect <= maximumRedirects; redirect++) {
            validatePublicHttps(current);
            HttpURLConnection connection = (HttpURLConnection) current.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(30_000);
            headers.forEach(connection::setRequestProperty);
            int status = connection.getResponseCode();
            if (!REDIRECTS.contains(status)) return connection;
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || location.isBlank()) throw new IOException("远程素材重定向缺少 Location");
            if (redirect == maximumRedirects) throw new IOException("远程素材重定向次数过多");
            current = current.resolve(location);
        }
        throw new IOException("远程素材连接失败");
    }

    public void validatePublicHttps(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("只允许访问 HTTPS 公网素材地址");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                    throw new IllegalArgumentException("禁止访问本地或内网素材地址");
                }
            }
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException("素材地址无法解析", exception);
        }
    }
}
