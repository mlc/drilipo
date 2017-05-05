package link.oulipo.drilipo;

import com.google.common.net.HttpHeaders;
import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;

public class DrilipoInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        return chain.proceed(chain.request().newBuilder()
                .header("User-Agent", "drilipo/1.0")
                .addHeader(HttpHeaders.ACCEPT_CHARSET, "utf-8")
                .build());
    }
}
