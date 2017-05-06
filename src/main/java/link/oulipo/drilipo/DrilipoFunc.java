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
import java.util.concurrent.TimeUnit;

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
            context.getLogger().log(MASTODON.get().getCurrentUser().toString());
            return state;
//            return TWITTER.get().dril(468878821169827839L, null)
//                    .stream()
//                    .filter(t -> t.isLipogrammatic() && t.retweeted_status == null && t.in_reply_to_status_id == null)
//                    .map(t -> {
//                        try {
//                            return t.text + " " + OULIPO_LINK.shrink(t.getUrl());
//                        } catch (IOException e) {
//                            throw new RuntimeException(e);
//                        }
//                    })
//                    .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
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
