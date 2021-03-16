package link.oulipo.drilipo;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import okio.ByteString;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Json {
    private static final JsonFactory jf = new MappingJsonFactory(new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
            .registerModule(new OkHttpModule())
            .registerModule(new JavaTimeModule()));
    public static final MediaType MIME_TYPE = MediaType.parse("application/json");

    public static JsonParser parser(InputStream in) throws IOException {
        return jf.createParser(in);
    }

    public static ByteString stringify(Object obj) {
        Buffer buf = new Buffer();
        try {
            stringify(obj, buf.outputStream());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return buf.readByteString();
    }

    public static void stringify(Object obj, OutputStream out) throws IOException {
        jf.createGenerator(out).writeObject(obj);
    }

    public static <T> T parse(Class<T> klass, OkHttpClient client, Request request) throws IOException {
        return parse(client, request, jp -> jp.readValueAs(klass));
    }

    public static <T> T parse(TypeReference<T> klass, OkHttpClient client, Request request) throws IOException {
        return parse(client, request, jp -> jp.readValueAs(klass));
    }

    private static <T> T parse(OkHttpClient client, Request request, ValueReader<T> parser) throws IOException {
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful())
                throw new IOException(resp.code() + " " + resp.message());

            JsonParser jp = Json.parser(resp.body().byteStream());
            return parser.readValue(jp);
        }
    }

    @FunctionalInterface
    private interface ValueReader<T> {
        T readValue(JsonParser jp) throws IOException;
    }

    static class OkHttpModule extends SimpleModule {
        public OkHttpModule() {
            addSerializer(new ToStringSerializer(HttpUrl.class));
            addDeserializer(HttpUrl.class, new FromStringDeserializer<HttpUrl>(HttpUrl.class) {
                @Override
                protected HttpUrl _deserialize(String value, DeserializationContext ctxt) throws IOException {
                    HttpUrl result = HttpUrl.parse(value);
                    if (result == null)
                        return (HttpUrl) ctxt.handleWeirdStringValue(HttpUrl.class, value, "invalid URL");
                    return result;
                }
            });
        }
    }
}
