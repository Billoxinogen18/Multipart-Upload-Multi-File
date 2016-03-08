package roadtodoird.com.uploader.Models;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.*;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import roadtodoird.com.uploader.Services.DbManager;
import roadtodoird.com.uploader.Support.CountingFileRequestBody;
import roadtodoird.com.uploader.Support.CountingRequestBody;

/**
 * Created by misterj on 19/1/16.
 */
public class UploadTaskCallable implements Callable<Integer> {
    private FIleInfo mFile;
    private Part mPart;
    private Boolean taskDONE;
    private Context mContext;
    private Handler handler;

    private long uploaded;

    private OkHttpClient client;

    private RandomAccessFile mRandomAccessFile;

    private Integer curPostInList;

    public UploadTaskCallable(Context c, FIleInfo file, Part part, Handler mainHandler, OkHttpClient client, RandomAccessFile randomFileObj, int curPos) {
        this.mContext = c;
        this.mFile = file;
        this.mPart = part;
        this.taskDONE = false;
        this.handler = mainHandler;
        this.client = client;
        this.mRandomAccessFile = randomFileObj;

        this.curPostInList = curPos;
    }

    @Override
    public Integer call() throws Exception {

        Integer returnVal = task();

        return returnVal;
    }

    private Integer task() throws InterruptedException {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

        final MediaType MEDIA_TYPE_MARKDOWN
                = MediaType.parse("application/json; charset=utf-8");

        mPart.setData(getBytes(mPart.getStart(), mPart.getEnd()));
        System.out.println(mPart.getStart() + " -- " + mPart.getEnd());

        //JSON here
        String postBody = "{" +
                "\"chunkid\":" + String.valueOf(mPart.getID()) + "," +
                "\"filename\":" + "\"" + mFile.getFileName() + "\"," +
                "\"data\":" + "\"" + mPart.getData().toString() + "\"" +
                "}";

        //System.out.println("Size:" + mPart.getData().length);
        //System.out.println("Uploading:" + mPart.getID());
        //System.out.println(postBody);


        //Check upload link
        //Always

        RequestBody mMultiPart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                        // .addFormDataPart("data", "data", RequestBody.create(MediaType.parse("image/png"), mPart.getData()))
                .addPart(
                        Headers.of("Content-Disposition", "form-data; name=\"image\"; filename=\"" + mFile.getFileName() + "." + getMimeType(mFile.getFileURI()) + "\""),
                        new CountingFileRequestBody(mPart.getData(), "image/*", new CountingFileRequestBody.ProgressListener() {
                            @Override
                            public void transferred(long num) {
                                float progress = (num / (float) mFile.getFileSize());
                                //System.out.println("PARTPROG: " + mPart.getID() + " -- " + progress);

                                Message m = new Message();
                                Bundle mBundle = new Bundle();
                                mBundle.putLong("count", num);
                                mBundle.putInt("id", mPart.getID());
                                mBundle.putInt("PosFromThread", curPostInList);
                                m.obj = mBundle;

                                handler.sendMessageDelayed(m, 200);

                                uploaded = num;

                            }
                        })
                )
                .build();

        /*
        CountingRequestBody countingbody = new CountingRequestBody(mMultiPart, new CountingRequestBody.Listener() {
            @Override
            public void onRequestProgress(long bytesWritten, long contentLength) {
                float progress = 100f * bytesWritten / contentLength;
            }
        });
        */

        Request mMultiPartRequest = new Request.Builder()
                .header("chunk-id", String.valueOf(mPart.getID()))
                        //.addHeader("filename", mFile.getFileName())
                        //.addHeader("fileID", String.valueOf(mFile.getServerFileID()))
                .url("http://103.233.25.47:5252/upload")
                .post(mMultiPart)
                .build();


        Request request = new Request.Builder()
                .url("http://solulabdev.com/itransfer/dev/api/v1/upload")
                .post(RequestBody.create(MEDIA_TYPE_MARKDOWN, postBody))
                .build();
        /*
        Request request = new Request.Builder()
                .url("http://solulabdev.com/itransfer/dev/api/v1/upload")
                .post(formBody)
                .build();
        */


        Response response = null;
        Call call = null;
        try {
            call = client.newCall(mMultiPartRequest);
            response = call.execute();
        } catch (IOException e) {
            e.printStackTrace();
        }


        while (call.isExecuted()) {
            if (Thread.interrupted() || response.isSuccessful()) {
                //Manually Cancele Okhttp to when Thread Interrupted.
                System.out.println("Cancelling Thread : " + mPart.getID());
                call.cancel();
                break;
            }
        }

        if (response != null && response.isSuccessful()) {
            System.out.println("AT: " + mPart.getID());
            System.out.println("UPLOADED : " + uploaded);
            try {
                String jsonData = response.body().string();

                JSONObject Jobject = new JSONObject(jsonData);
                System.out.println(Jobject);

                String flag = (((JSONObject) Jobject.get("data")).get("flag-complete")).toString();

                //System.out.println((((JSONObject) Jobject.get("data")).get("flagComplete")) + " " + flag);

                if (flag.compareTo("Y") == 0) {
                    System.out.println("YES " + mPart.getID());

                    /*
                    DbManager manager = dbManager.getInstance();
                    SQLiteDatabase db = manager.openDatabase();
                    int res = manager.updatePartInfo(mFile, Integer.valueOf(mPart.getID()), db);
                    DbManager.getInstance().closeDatabase();
                    */

                    //*************************** UPDATE CACHE
                    /*
                    for (Map.Entry<FIleInfo, PartInfo> entry : mCache.entrySet()) {
                        if (entry.getKey().getFileName().compareTo(mFile.getFileName()) == 0) {
                            PartInfo mPartInfo = entry.getValue();
                            mPartInfo.setIndividualPart(mPart.getID(), true);
                            break;
                        }

                    }
                    */
                    //***************************

                    /*
                    return mPart.getID();
                    */
                }
                return mPart.getID();


            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }


        } else {
            //failed
            if (response != null) response.body().close();
        }


        return 0;

    }

    private String getMimeType(Uri u) {
        String uri = u.toString();
        int index = uri.lastIndexOf('/');
        uri = uri.substring(index + 1);
        index = uri.lastIndexOf('.');
        return uri.substring(index + 1);
    }

    private synchronized byte[] getBytes(int start, int end) {

        synchronized (mRandomAccessFile) {
            int chunkSize = end - start + 1;
            System.out.println("ChunkSize:" + chunkSize);

            byte[] dataArr = new byte[chunkSize];

            if (mRandomAccessFile != null) {
                try {
                    mRandomAccessFile.seek(start);
                    System.out.println("SEEK: " + mRandomAccessFile.getFilePointer());

                    int count = mRandomAccessFile.read(dataArr);

                    //if (count < 0) continue;
                    //System.out.println(dataArr.length);
                    System.out.println("READ DONE");
                    return dataArr;


                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return dataArr;
        }

    }
}
