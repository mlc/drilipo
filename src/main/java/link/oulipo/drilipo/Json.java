package link.oulipo.drilipo;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.MediaType;
import okio.Buffer;
import okio.ByteString;

import java.io.IOException;
import java.io.InputStream;

public class Json {
    private static final JsonFactory jf = new MappingJsonFactory(new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
            .registerModule(new JavaTimeModule()));
    public static final MediaType MIME_TYPE = MediaType.parse("application/json");

    public static JsonParser parser(InputStream in) throws IOException {
        return jf.createParser(in);
    }

    public static ByteString stringify(Object obj) {
        Buffer buf = new Buffer();
        try {
            jf.createGenerator(buf.outputStream()).writeObject(obj);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return buf.readByteString();
    }
}
