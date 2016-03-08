package roadtodoird.com.uploader.Services;

import android.webkit.DownloadListener;

/**
 * Created by misterj on 2/2/16.
 */
public class UploadInfo {
    public enum UploadState {
        TOSTART(0), /* Download has been created but not started yet */
        STARTED(1), /* File is downloading currently */
        PAUSED(2),  /* Download has been paused */
        STOPPED(3), /* Download has been canceled */
        FINISHED(4), /* Download has finished properly */
        PAUSEDBYUSER(5),
        BROKEN(6),
        DELETE(7),
        WARMINGUP(8);

        private int id;

        UploadState(int id) {
            this.id = id;
        }

        public int toInt() {
            return id;
        }

        public static UploadState fromInt(int id) {
            UploadState[] states = values();
            for (int i = 0; i < states.length; i++)
                if (states[i].id == id)
                    return states[i];
            return null;
        }
    }

    private UploadState state;
    private int percDone;
    private boolean FIRST_TIME = true;

    public UploadInfo(int state) {
        this.percDone = 0;
        this.state = UploadState.fromInt(state);
    }

    public void setState(UploadState state) {
        this.state = state;
    }

    public UploadState getState() {
        return this.state;
    }

    public void setPercDone(int p) {
        if (p > 100) p = 100;
        this.percDone = p;
        this.FIRST_TIME = false;
    }

    public int getPercDone() {
        return this.percDone;
    }

    public boolean isFirstTime() {
        return FIRST_TIME;
    }
}
