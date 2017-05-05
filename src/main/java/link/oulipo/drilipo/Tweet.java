package link.oulipo.drilipo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import java.util.List;

public class Tweet {
    private static HttpUrl TWITTER_BASE = HttpUrl.parse("https://twitter.com/");

    public static final TypeReference<List<Tweet>> LIST_OF_TWEETS = new TypeReference<List<Tweet>>() {};

    public String created_at;
    public long id;
    public String text;
    public boolean truncated;
    public String source;
    public Long in_reply_to_status_id;
    public Tweet retweeted_status;
    public int retweet_count;
    public int favorite_count;
    public boolean is_quote_status;

    @JsonIgnore
    public boolean isLipogrammatic() {
        return Oulipo.isLipogrammatic(text);
    }

    @JsonIgnore
    public HttpUrl getUrl() {
        return TWITTER_BASE.newBuilder()
                .addPathSegment("dril")
                .addPathSegment("status")
                .addPathSegment(Long.toString(id))
                .build();
    }
}
