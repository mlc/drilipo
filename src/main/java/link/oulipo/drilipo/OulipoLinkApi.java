package link.oulipo.drilipo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;

public class OulipoLinkApi {
    private final OkHttpClient client;

    public OulipoLinkApi(OkHttpClient client) {
        this.client = client;
    }

    public HttpUrl shrink(HttpUrl urlLong) throws IOException {
        OLRequest olRequest = new OLRequest(urlLong);
        Request req = new Request.Builder()
                .url("https://api.oulipo.link/create")
                .post(RequestBody.create(Json.stringify(olRequest), Json.MIME_TYPE))
                .build();

        OLResponse olr = Json.parse(OLResponse.class, client, req);

        if (olr.error != null)
            throw new IOException(olr.error);

        return olr.urlShort;
    }

    static class OLRequest {
        @JsonProperty("url_long") public final HttpUrl urlLong;

        @JsonCreator
        public OLRequest(@JsonProperty("url_long") HttpUrl urlLong) {
            this.urlLong = urlLong;
        }

        @JsonProperty("cdn_prefix")
        public String getCdnPrefix() {
            return System.getenv("OULIPO_CDN_PREFIX");
        }
    }

    static class OLResponse {
        @JsonProperty("error") public final String error;
        @JsonProperty("url_long") public final HttpUrl urlLong;
        @JsonProperty("url_short") public final HttpUrl urlShort;

        @JsonCreator
        public OLResponse(@JsonProperty("error") String error,
                          @JsonProperty("url_long") HttpUrl urlLong,
                          @JsonProperty("url_short") HttpUrl urlShort) {
            this.error = error;
            this.urlLong = urlLong;
            this.urlShort = urlShort;
        }
    }
}
