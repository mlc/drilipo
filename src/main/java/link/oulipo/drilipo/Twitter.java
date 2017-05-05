package link.oulipo.drilipo;

import com.fasterxml.jackson.core.JsonParser;
import okhttp3.*;

import java.io.IOException;
import java.util.List;

public class Twitter {

    private final OkHttpClient client;
    private final String usernameAndPassword;

    private String bearerToken;

    public Twitter(OkHttpClient client, String usernameAndPassword) {
        this.client = client;
        this.usernameAndPassword = usernameAndPassword;
    }

    public List<Tweet> dril(Long maxId, Long sinceId) throws IOException {
        HttpUrl.Builder url = HttpUrl.parse("https://api.twitter.com/1.1/statuses/user_timeline.json").newBuilder()
                .addQueryParameter("screen_name", "dril")
                .addQueryParameter("include_rts", "0")
                .addQueryParameter("count", "200")
                .addQueryParameter("trim_user", "1");
        if (maxId != null)
            url.addQueryParameter("max_id", maxId.toString());
        if (sinceId != null)
            url.addQueryParameter("since_id", sinceId.toString());

        Request req = new Request.Builder()
                .url(url.build())
                .get()
                .header("Authorization", authHeader())
                .build();

        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful())
                throw new IOException(resp.code() + " " + resp.message());

            JsonParser jp = Json.parser(resp.body().byteStream());
            return jp.readValueAs(Tweet.LIST_OF_TWEETS);
        }
    }

    private synchronized String authHeader() throws IOException {
        if (bearerToken == null) {
            bearerToken = getBearerToken();
        }
        return "Bearer " + bearerToken;
    }

    private String getBearerToken() throws IOException {
        Request req = new Request.Builder()
                .url("https://api.twitter.com/oauth2/token")
                .post(new FormBody.Builder()
                        .add("grant_type", "client_credentials")
                        .build())
                .header("Authorization", "Basic " + usernameAndPassword)
                .build();
        try (Response resp = client.newCall(req).execute()) {
            if (!resp.isSuccessful())
                throw new IOException(resp.code() + " " + resp.message());

            JsonParser jp = Json.parser(resp.body().byteStream());
            return jp.readValueAs(AccessToken.class).access_token;
        }
    }
}
