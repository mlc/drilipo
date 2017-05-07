package link.oulipo.drilipo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Ascii;
import com.google.common.base.MoreObjects;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public class MastodonApi {
    private final OkHttpClient client;
    private final String host;
    private final String token;
    private final HttpUrl baseUrl;

    public MastodonApi(OkHttpClient client, String host, String token) {
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

    public Status post(String status, Visibility visibility) throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl.newBuilder().addPathSegment("statuses").build())
                .post(new FormBody.Builder()
                        .add("status", status)
                        .add("visibility", visibility.toApi())
                        .build())
                .header("Authorization", authHeader())
                .build();
        return Json.parse(Status.class, client, req);
    }

    public List<Notification> notifications(long sinceId) throws IOException {
        HttpUrl.Builder url = baseUrl.newBuilder().addPathSegment("notifications");
        if (sinceId > 0)
            url.addQueryParameter("since_id", Long.toString(sinceId));
        url.addQueryParameter("limit", "30");
        Request req = new Request.Builder()
                .url(url.build())
                .get()
                .header("Authorization", authHeader())
                .build();
        return Json.parse(LIST_OF_NOTIFICATIONS, client, req);
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

        @JsonIgnore
        public String getName() {
            if (display_name != null && !"".equals(display_name))
                return display_name;
            else
                return username;
        }

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

    public enum Visibility {
        DIRECT, PRIVATE, UNLISTED, PUBLIC;

        @JsonCreator
        public static Visibility fromApi(String value) {
            try {
                return valueOf(Ascii.toUpperCase(value));
            } catch (RuntimeException ex) {
                return null;
            }
        }

        @JsonValue
        public String toApi() {
            return Ascii.toLowerCase(name());
        }
    }

    public static class Status {
        public long id;
        public String uri;
        public HttpUrl url;
        public Account account;
        public Long in_reply_to_id;
        public Long in_reply_to_account_id;
        public Status reblog;
        public String content;
        public @JsonFormat(shape = JsonFormat.Shape.STRING) Instant created_at;
        public int reblogs_count;
        public int favourites_count;
        public boolean sensitive;
        public String spoiler_text;
        public Visibility visibility;

        @JsonIgnore
        public String text() {
            return Jsoup.parseBodyFragment(content).text();
        }
    }

    public static final TypeReference<List<Notification>> LIST_OF_NOTIFICATIONS = new TypeReference<List<Notification>>() {};

    public static class Notification {
        public long id;
        public String type;
        public @JsonFormat(shape = JsonFormat.Shape.STRING) Instant created_at;
        public Account account;
        public Status status;

        public String summarize() {
            try {
                switch (type) {
                    case "mention":
                        return account.getName() + " says " + status.text() + "\n" + status.url;

                    case "reblog":
                        return "you got a boost from " + account.getName() + "\n" + status.url;

                    case "favourite":
                        return account.getName() + " put a star on\n" + status.url;

                    case "follow":
                        return account.getName() + " is now following you\n" + account.url;

                    default:
                        return "unknown notif kind " + type + " for notif id " + id;
                }
            } catch (NullPointerException npe) {
                return "[couldn't do summary for notif id " + id + "]";
            }
        }
    }
}
