package link.oulipo.drilipo;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okio.ByteString;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DrilipoFunc implements RequestHandler<Object, State> {
    private static final S3Client s3 = S3Client.builder().region(Region.US_WEST_1).build();
    private static final SnsClient sns = SnsClient.builder().region(Region.US_WEST_1).build();
    private static final SsmClient ssm = SsmClient.builder().region(Region.US_WEST_1).build();
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .protocols(ImmutableList.of(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectionSpecs(ImmutableList.of(ConnectionSpec.MODERN_TLS))
            .addInterceptor(new DrilipoInterceptor())
            .build();
    private static final Supplier<TwitterApi> TWITTER = Suppliers.memoizeWithExpiration(
            Suppliers.compose(creds -> new TwitterApi(client, creds), getFromSsm("twitter_creds")),
            1, TimeUnit.HOURS);
    private static final OulipoLinkApi OULIPO_LINK = new OulipoLinkApi(client);
    private static final Supplier<MastodonApi> MASTODON = Suppliers.compose(
            key -> new MastodonApi(client, System.getenv("MASTODON_SERVER"), key), getFromSsm("mastodon_token"));

    @Override
    public State handleRequest(Object input, Context context) {
        try {
            State state = State.retrieve(s3);
            final MastodonApi mastodon = MASTODON.get();
            findBestTweet(state).ifPresent(best -> {
                context.getLogger().log(String.format("found a post!\n%s\n%s\n", best.text, best.getUrl()));
                MastodonApi.Status toot = post(best, mastodon);
                context.getLogger().log(String.format("toot at %s\n", toot.url));
            });
            try {
                readNotifs(mastodon, state);
            } catch (Exception ex) {
                StringWriter sw = new StringWriter();
                sw.write("couln't send notifs\n");
                ex.printStackTrace(new PrintWriter(sw));
                context.getLogger().log(sw.toString());
            }
            state.save(s3);
            return state;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public MastodonApi.Status post(Tweet tweet, MastodonApi mastodon) {
        try {
            String status = tweet.text + "\n" + OULIPO_LINK.shrink(tweet.getUrl());
            return mastodon.post(status, MastodonApi.Visibility.PUBLIC);
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

    public void readNotifs(MastodonApi mastodon, State state) throws IOException {
        List<MastodonApi.Notification> notifications = mastodon.notifications(state.notifsSince);
        if (notifications.isEmpty())
            return;

        state.notifsSince = notifications.stream().mapToLong(n -> n.id).max().orElseThrow(IllegalStateException::new);

        final ZoneId est = ZoneId.of("America/New_York");
        final DateTimeFormatter dtf = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG);
        String notifText = notifications.stream()
                .map(n -> ZonedDateTime.ofInstant(n.created_at, est).format(dtf) + "\n" + n.summarize())
                .collect(Collectors.joining("\n\n"));
        PublishRequest publishRequest = PublishRequest.builder()
                .topicArn(System.getenv("NOTIFS_ARN"))
                .message(notifText)
                .subject("drilipo notifs")
                .build();
        sns.publish(publishRequest);
    }

    private Predicate<Tweet> isInteresting() {
        return t -> t.isLipogrammatic() &&
                (t.entities == null || t.entities.media == null || t.entities.media.isEmpty()) &&
                t.retweeted_status == null &&
                t.in_reply_to_status_id == null;
    }

    private static Supplier<String> getFromSsm(String name) {
        return Suppliers.memoize(() -> {
            GetParameterRequest req = GetParameterRequest.builder().name("/drilipobot/" + name).withDecryption(true).build();
            String encoded = ssm.getParameter(req).parameter().value();
            return ByteString.decodeBase64(encoded).utf8();
        });
    }
}
