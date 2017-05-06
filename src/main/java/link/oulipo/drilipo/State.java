package link.oulipo.drilipo;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.google.common.base.MoreObjects;
import okhttp3.CacheControl;
import okio.Buffer;

import java.io.IOException;

/**
 * Persistant state, stored between invocations.
 */
public class State {
    public long sinceId;
    public long maxId;

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("sinceId", sinceId)
                .add("maxId", maxId)
                .toString();
    }

    public void save(AmazonS3 s3) {
        Buffer buffer = new Buffer();
        try {
            Json.stringify(this, buffer.outputStream());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(Json.MIME_TYPE.toString());
        metadata.setCacheControl(new CacheControl.Builder().noCache().noStore().build().toString());
        metadata.setContentLength(buffer.size());
        PutObjectRequest req = new PutObjectRequest(System.getenv("CONFIG_BUCKET"),
                System.getenv("CONFIG_KEY"),
                buffer.inputStream(),
                metadata)
                .withSSEAwsKeyManagementParams(new SSEAwsKeyManagementParams("b51d5029-fdb2-4d02-9677-864e6235ade6"));
        s3.putObject(req);
    }

    public static State retrieve(AmazonS3 s3) throws IOException {
        GetObjectRequest req = new GetObjectRequest(System.getenv("CONFIG_BUCKET"),
                System.getenv("CONFIG_KEY"));
        try (S3Object obj = s3.getObject(req)) {
            return Json.parser(obj.getObjectContent()).readValueAs(State.class);
        } catch (AmazonServiceException ase) {
            if ("NoSuchKey".equals(ase.getErrorCode())) {
                // first run (or state was manually reset for some reason)? just make a new State
                return new State();
            } else {
                throw ase;
            }
        }
    }
}
