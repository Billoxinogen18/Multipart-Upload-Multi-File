package roadtodoird.com.uploader.Services;

import java.util.ArrayList;
import java.util.List;

import roadtodoird.com.uploader.Models.Part;

/**
 * Created by misterj on 12/2/16.
 */
public class QueueControl {
    private int startPartInfo;
    private int endPartInfo;
    private List<Part> mPartList = new ArrayList<>();

    private static final int QUEUE_SIZE_LIMIT = (int) (5.0 * 1024 * 1024);    // 1 MB limit

    public QueueControl(List<Part> list) {
        this.startPartInfo = 0;
        this.mPartList = list;
        this.endPartInfo = list.size();
    }

    public void viableRange() {
        long curSize = 0;
        int i = 0;
        for (i = startPartInfo; i < endPartInfo; i++) {
            int chunkSize = mPartList.get(i).getEnd() - mPartList.get(i).getStart() + 1;
            curSize += chunkSize;
            if (curSize >= QUEUE_SIZE_LIMIT)
                break;
        }
        //move a part back
        this.endPartInfo = i - 1;
    }

    public void setRanges(int start, int end) {

        this.startPartInfo = end + 1;
        this.endPartInfo = this.mPartList.size();

    }

    public int getstart() {
        return this.startPartInfo;
    }

    public int getend() {
        return this.endPartInfo;
    }

    public int getListSize() {
        if (mPartList != null) return this.mPartList.size();
        else return 0;
    }
}
