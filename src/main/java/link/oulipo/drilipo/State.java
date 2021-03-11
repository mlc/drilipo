package link.oulipo.drilipo;

import com.google.common.base.MoreObjects;
import okhttp3.CacheControl;
import okio.Buffer;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import java.io.IOException;

/**
 * Persistent state, stored between invocations.
 */
public class State {
    public long sinceId;
    public long maxId;
    public long notifsSince;

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("sinceId", sinceId)
                .add("maxId", maxId)
                .add("notifsSince", notifsSince)
                .toString();
    }

    public void save(S3Client s3) {
        Buffer buffer = new Buffer();
        try {
            Json.stringify(this, buffer.outputStream());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(System.getenv("CONFIG_BUCKET"))
                .key(System.getenv("CONFIG_KEY"))
                .contentType(Json.MIME_TYPE.toString())
        .cacheControl(new CacheControl.Builder().noCache().noStore().build().toString())
                .contentLength(buffer.size())
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build();
        s3.putObject(req, RequestBody.fromInputStream(buffer.inputStream(), buffer.size()));
    }

    public static State retrieve(S3Client s3) throws IOException {
        GetObjectRequest req = GetObjectRequest.builder()
                .bucket(System.getenv("CONFIG_BUCKET"))
                .key(System.getenv("CONFIG_KEY"))
                .build();
        try (ResponseInputStream<GetObjectResponse> obj = s3.getObject(req)) {
            return Json.parser(obj).readValueAs(State.class);
        } catch (NoSuchKeyException ex) {
            // first run (or state was manually reset for some reason)? just make a new State
            return new State();
        }
    }
}
