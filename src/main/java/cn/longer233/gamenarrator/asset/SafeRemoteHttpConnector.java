package cn.longer233.gamenarrator.asset;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;

@Component
public class SafeRemoteHttpConnector {
    private static final Set<Integer> REDIRECTS = Set.of(301, 302, 303, 307, 308);

    public HttpURLConnection open(URI initial, Map<String, String> headers, int maximumRedirects) throws IOException {
        URI current = initial;
        for (int redirect = 0; redirect <= maximumRedirects; redirect++) {
            InetAddress[] addresses = resolvePublicHttps(current);
            String pinnedHost=current.getHost();
            var manager=PoolingHttpClientConnectionManagerBuilder.create()
                    .setDnsResolver(pinnedDnsResolver(pinnedHost,addresses))
                    .build();
            RequestConfig config=RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(8))
                    .setResponseTimeout(Timeout.ofSeconds(30))
                    .setRedirectsEnabled(false)
                    .build();
            CloseableHttpClient client=HttpClients.custom().setConnectionManager(manager)
                    .setDefaultRequestConfig(config).disableRedirectHandling().build();
            HttpGet request=new HttpGet(current);
            headers.forEach(request::setHeader);
            CloseableHttpResponse response;
            try { response=client.execute(request); }
            catch (IOException failure) { client.close(); throw failure; }
            HttpURLConnection connection = new ApacheHttpURLConnection(current,client,response);
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
        resolvePublicHttps(uri);
    }

    private InetAddress[] resolvePublicHttps(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("只允许访问 HTTPS 公网素材地址");
        }
        try {
            InetAddress[] addresses=InetAddress.getAllByName(uri.getHost());
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                    throw new IllegalArgumentException("禁止访问本地或内网素材地址");
                }
            }
            if (addresses.length==0) throw new IllegalArgumentException("素材地址无法解析");
            return addresses;
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException("素材地址无法解析", exception);
        }
    }

    private static InetAddress[] rejectUnexpectedHost(String host) throws java.net.UnknownHostException {
        throw new java.net.UnknownHostException("未校验的远程主机："+host);
    }

    static DnsResolver pinnedDnsResolver(String pinnedHost,InetAddress[] addresses) {
        InetAddress[] pinned=addresses.clone();
        return new DnsResolver() {
            @Override public InetAddress[] resolve(String host) throws java.net.UnknownHostException {
                return host.equalsIgnoreCase(pinnedHost) ? pinned.clone() : rejectUnexpectedHost(host);
            }
            @Override public String resolveCanonicalHostname(String host) throws java.net.UnknownHostException {
                if (!host.equalsIgnoreCase(pinnedHost)) rejectUnexpectedHost(host);
                return pinnedHost;
            }
        };
    }

    private static final class ApacheHttpURLConnection extends HttpURLConnection {
        private final CloseableHttpClient client;
        private final CloseableHttpResponse response;
        private ApacheHttpURLConnection(URI uri,CloseableHttpClient client,CloseableHttpResponse response)
                throws java.net.MalformedURLException {
            super(uri.toURL()); this.client=client; this.response=response; this.connected=true;
        }
        @Override public int getResponseCode() { return response.getCode(); }
        @Override public String getHeaderField(String name) {
            var header=response.getFirstHeader(name);
            return header==null?null:header.getValue();
        }
        @Override public InputStream getInputStream() throws IOException {
            if(response.getEntity()==null) return InputStream.nullInputStream();
            return response.getEntity().getContent();
        }
        @Override public void disconnect() {
            try { response.close(); } catch(IOException ignored) { }
            try { client.close(); } catch(IOException ignored) { }
            connected=false;
        }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { connected=true; }
    }
}
