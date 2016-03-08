package roadtodoird.com.uploader.Models;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Created by misterj on 20/1/16.
 */
public class Part implements Serializable {
    private int start;
    private int end;
    private int ID;
    private int chunkSize;
    private byte[] data;

    public Part(int ID, int start, int end) {
        this.ID = ID;
        this.start = start;
        this.end = end;

        this.chunkSize = this.end - this.start + 1;
    }

    public int getStart() {
        return this.start;
    }

    public int getEnd() {
        return this.end;
    }

    public int getID() {
        return this.ID;
    }

    public byte[] getData() {
        return this.data;
    }

    public void setData(byte[] data) {
        this.data = Arrays.copyOf(data, data.length);
    }
}
