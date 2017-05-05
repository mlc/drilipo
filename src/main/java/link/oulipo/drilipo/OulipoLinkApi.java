package link.oulipo.drilipo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import okhttp3.*;

import java.io.IOException;

public class OulipoLinkApi {
    private final OkHttpClient client;

    public OulipoLinkApi(OkHttpClient client) {
        this.client = client;
    }

    public String shrink(HttpUrl urlLong) throws IOException {
        OLRequest olRequest = new OLRequest(urlLong.toString());
        Request req = new Request.Builder()
                .url("https://oulipo.link/create")
                .post(RequestBody.create(Json.MIME_TYPE, Json.stringify(olRequest)))
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful())
                throw new IOException(resp.code() + " " + resp.message());

            JsonParser jp = Json.parser(resp.body().byteStream());
            OLResponse olr = jp.readValueAs(OLResponse.class);
            if (olr.error != null)
                throw new IOException(olr.error);

            return olr.urlShort;
        }
    }

    static class OLRequest {
        @JsonProperty("url_long") public final String urlLong;

        @JsonCreator
        public OLRequest(@JsonProperty("url_long") String urlLong) {
            this.urlLong = urlLong;
        }

        @JsonProperty("cdn_prefix")
        public String getCdnPrefix() {
            return System.getenv("OULIPO_CDN_PREFIX");
        }
    }

    static class OLResponse {
        @JsonProperty("error") public final String error;
        @JsonProperty("url_long") public final String urlLong;
        @JsonProperty("url_short") public final String urlShort;

        @JsonCreator
        public OLResponse(@JsonProperty("error") String error,
                          @JsonProperty("url_long") String urlLong,
                          @JsonProperty("url_short") String urlShort) {
            this.error = error;
            this.urlLong = urlLong;
            this.urlShort = urlShort;
        }
    }
}
