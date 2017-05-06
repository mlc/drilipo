package link.oulipo.drilipo;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.kms.AWSKMS;
import com.amazonaws.services.kms.AWSKMSClient;
import com.amazonaws.services.kms.model.DecryptRequest;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.util.Base64;
import com.google.common.base.Charsets;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DrilipoFunc implements RequestHandler<String, State> {
    private static final AWSKMS kms = new AWSKMSClient().withRegion(Regions.US_WEST_1);
    private static final AmazonS3 s3 = new AmazonS3Client().withRegion(Regions.US_WEST_1);
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .protocols(ImmutableList.of(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionSpecs(ImmutableList.of(ConnectionSpec.MODERN_TLS))
            .addInterceptor(new DrilipoInterceptor())
            .build();
    private static final Supplier<TwitterApi> TWITTER = Suppliers.memoizeWithExpiration(
            Suppliers.compose(creds -> new TwitterApi(client, creds), getFromKms("TWITTER_CREDS")),
            1, TimeUnit.HOURS);
    private static final OulipoLinkApi OULIPO_LINK = new OulipoLinkApi(client);
    private static final Supplier<MastodonApi> MASTODON = Suppliers.compose(
            key -> new MastodonApi(client, System.getenv("MASTODON_SERVER"), key), getFromKms("MASTODON_TOKEN"));

    @Override
    public State handleRequest(String input, Context context) {
        try {
            State state = State.retrieve(s3);
            findBestTweet(state).ifPresent(best -> {
                context.getLogger().log(String.format("found a post!\n%s\n%s\n", best.text, best.getUrl()));
                MastodonApi.Status toot = post(best);
                context.getLogger().log(String.format("toot at %s\n", toot.url));
            });
            state.save(s3);
            return state;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public MastodonApi.Status post(Tweet tweet) {
        try {
            String status = "“" + tweet.text + "” " + OULIPO_LINK.shrink(tweet.getUrl());
            return MASTODON.get().post(status, MastodonApi.Visibility.PRIVATE);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public Optional<Tweet> findBestTweet(State state) throws IOException {
        TwitterApi twitterApi = TWITTER.get();
        // If we've run before, and there's a new lipogrammatic tweet available, post it immediately!
        if (state.sinceId > 0) {
            List<Tweet> newTweets = twitterApi.dril(null, state.sinceId);
            Optional<Tweet> hotNewTweet = newTweets.stream()
                    .filter(isInteresting())
                    .min(Comparator.comparing(t -> t.id));
            if (hotNewTweet.isPresent()) {
                state.sinceId = hotNewTweet.get().id;
                return hotNewTweet;
            } else {
                // update sinceId for next time we're called
                newTweets.stream().mapToLong(t -> t.id).max().ifPresent(max -> state.sinceId = max);
            }
        }

        // otherwise, find the newest old tweet we haven't posted yet
        while(true) {
            Long max = state.maxId == 0 ? null : state.maxId;
            List<Tweet> oldTweets = twitterApi.dril(max, null);
            if (oldTweets.isEmpty())
                return Optional.empty();

            if (state.sinceId == 0)
                state.sinceId = oldTweets.stream().mapToLong(t -> t.id).max().orElseThrow(IllegalStateException::new);

            Optional<Tweet> hotOldTweet = oldTweets.stream()
                    .filter(isInteresting())
                    .max(Comparator.comparing(t -> t.id));
            if (hotOldTweet.isPresent()) {
                state.maxId = hotOldTweet.get().id - 1L;
                return hotOldTweet;
            } else {
                state.maxId = oldTweets.stream().mapToLong(t -> t.id).min().orElseThrow(IllegalStateException::new) - 1L;
            }
        }
    }

    private Predicate<Tweet> isInteresting() {
        return t -> t.isLipogrammatic() &&
                t.entities.media.isEmpty() &&
                t.retweeted_status == null &&
                t.in_reply_to_status_id == null;
    }

    private static Supplier<String> getFromKms(String name) {
        return Suppliers.memoize(() -> {
            byte[] encrypted = Base64.decode(System.getenv(name));
            DecryptRequest request = new DecryptRequest()
                    .withCiphertextBlob(ByteBuffer.wrap(encrypted));
            ByteBuffer plainText = kms.decrypt(request).getPlaintext();
            return new String(plainText.array(), Charsets.UTF_8);
        });
    }
}
