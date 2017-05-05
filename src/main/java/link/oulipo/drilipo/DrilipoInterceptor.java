package link.oulipo.drilipo;

import com.google.common.net.HttpHeaders;
import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;

public class DrilipoInterceptor implements Interceptor {
    private final String ua = "drilipo/1.0 " + okhttp3.internal.Version.userAgent();
    @Override
    public Response intercept(Chain chain) throws IOException {
        return chain.proceed(chain.request().newBuilder()
                .header("User-Agent", ua)
                .addHeader(HttpHeaders.ACCEPT_CHARSET, "utf-8")
                .build());
    }
}
