package link.oulipo.drilipo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.HttpUrl;

import java.time.Instant;
import java.util.List;

public class Tweet {
    private static HttpUrl TWITTER_BASE = HttpUrl.parse("https://twitter.com/");

    public static final TypeReference<List<Tweet>> LIST_OF_TWEETS = new TypeReference<List<Tweet>>() {};

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "EEE MMM dd HH:mm:ss Z yyyy", locale = "en_US")
    public Instant created_at;
    public long id;
    public String text;
    public boolean truncated;
    public String source;
    public Long in_reply_to_status_id;
    public Tweet retweeted_status;
    public int retweet_count;
    public int favorite_count;
    public boolean is_quote_status;
    public Entities entities;

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

    public static class Entities {
        List<HashEntity> hashtags;
        List<HashEntity> symbols;
        List<UserEntity> user_mentions;
        List<UrlEntity> urls;
        List<MediaEntity> media;
    }

    public static class Entity {
        public int[] indices;
    }

    public static class HashEntity {
        public String text;
    }

    public static class UserEntity extends Entity {
        public long id;
        public String name;
        public String screen_name;
    }

    public static class UrlEntity extends Entity {
        public HttpUrl url;
        public HttpUrl expanded_url;
        public String display_url;
    }

    public static class MediaEntity extends UrlEntity {
        public long id;
        public HttpUrl media_url;
        public HttpUrl media_url_https;
    }
}
