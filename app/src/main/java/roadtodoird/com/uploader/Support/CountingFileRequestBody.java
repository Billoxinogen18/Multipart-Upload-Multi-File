package roadtodoird.com.uploader.Support;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.internal.Util;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;

/**
 * Created by misterj on 29/1/16.
 */
public class CountingFileRequestBody extends RequestBody {

    //change segment size...............
    private int SEGMENT_SIZE; // okio.Segment.SIZE

    private final ByteArrayInputStream byteStream;
    private final ProgressListener listener;
    private final String contentType;
    private final long chunkSize;

    public CountingFileRequestBody(byte[] data, String contentType, ProgressListener listener) {
        this.byteStream = new ByteArrayInputStream(data);
        this.contentType = contentType;
        this.listener = listener;
        this.chunkSize = data.length;

        this.SEGMENT_SIZE = (int) chunkSize;
    }

    @Override
    public long contentLength() {
        return chunkSize;
    }

    @Override
    public MediaType contentType() {
        return MediaType.parse(contentType);
    }

    @Override
    public void writeTo(BufferedSink sink) throws IOException {
        Source source = null;
        try {
            source = Okio.source(byteStream);

            long total = 0;
            long read;

            while ((read = source.read(sink.buffer(), SEGMENT_SIZE)) != -1) {
                total += read;
                sink.flush();
                this.listener.transferred(total);

            }
        } finally {
            Util.closeQuietly(source);
        }
    }

    public interface ProgressListener {
        void transferred(long num);
    }

}