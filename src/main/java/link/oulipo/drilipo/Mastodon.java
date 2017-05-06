package link.oulipo.drilipo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.MoreObjects;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
import java.time.Instant;

public class Mastodon {
    private final OkHttpClient client;
    private final String host;
    private final String token;
    private final HttpUrl baseUrl;

    public Mastodon(OkHttpClient client, String host, String token) {
        this.client = client;
        this.host = host;
        this.token = token;

        baseUrl = new HttpUrl.Builder()
                .scheme("https")
                .host(host)
                .addPathSegment("api")
                .addPathSegment("v1")
                .build();
    }

    public String toString() {
        return "Mastodon client for " + host;
    }

    public Account getCurrentUser() throws IOException {
        Request req = new Request.Builder()
                .get()
                .url(baseUrl.newBuilder()
                        .addPathSegment("accounts")
                        .addPathSegment("verify_credentials")
                        .build())
                .header("Authorization", authHeader())
                .build();
        System.out.println(req.url());
        return Json.parse(Account.class, client, req);
    }

    private String authHeader() {
        return "Bearer " + token;
    }

    public static class Account {
        public long id;
        public String username;
        public String acct;
        public String display_name;
        public boolean locked;
        public @JsonFormat(shape = JsonFormat.Shape.STRING) Instant created_at;
        public int followers_count;
        public int following_count;
        public int statuses_count;
        public String note;
        public HttpUrl url;
        public HttpUrl avatar;
        public HttpUrl avatar_static;
        public HttpUrl header;
        public HttpUrl header_static;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", id)
                    .add("username", username)
                    .add("acct", acct)
                    .add("display_name", display_name)
                    .add("locked", locked)
                    .add("created_at", created_at)
                    .add("followers_count", followers_count)
                    .add("following_count", following_count)
                    .add("statuses_count", statuses_count)
                    .add("note", note)
                    .add("url", url)
                    .add("avatar", avatar)
                    .add("avatar_static", avatar_static)
                    .add("header", header)
                    .add("header_static", header_static)
                    .toString();
        }
    }
}
