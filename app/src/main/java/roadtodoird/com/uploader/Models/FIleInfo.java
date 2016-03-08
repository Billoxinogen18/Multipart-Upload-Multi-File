package roadtodoird.com.uploader.Models;

import android.net.Uri;

import java.io.Serializable;

/**
 * Created by misterj on 19/1/16.
 */
public class FIleInfo implements Serializable {
    private String fileName;
    private Integer fileSize;
    private String mimeType;
    private Uri fileURI;
    private Long serverFileID;

    public FIleInfo(String name, Integer size, String mimeType) {
        this.fileName = name;
        this.fileSize = size;
        this.mimeType = mimeType;
    }

    public void setFileLocalPath(Uri uri) {
        this.fileURI = uri;
    }

    public void setServerFileID(Long id) {
        this.serverFileID = id;
    }

    public String getFileName() {
        return this.fileName;
    }

    public Integer getFileSize() {
        return this.fileSize;
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public Uri getFileURI() {
        return this.fileURI;
    }

    public Long getServerFileID() {
        return this.serverFileID;
    }

    @Override
    public String toString() {
        return super.toString();
    }

}

