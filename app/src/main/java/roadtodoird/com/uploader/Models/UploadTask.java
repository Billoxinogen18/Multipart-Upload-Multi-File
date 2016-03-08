package roadtodoird.com.uploader.Models;

import android.app.DownloadManager;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.util.Base64;


import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import roadtodoird.com.uploader.Services.DbHelper;
import roadtodoird.com.uploader.Services.DbManager;

/**
 * Created by misterj on 19/1/16.
 */
public class UploadTask implements Runnable {
    private FIleInfo mFile;
    private Part mPart;
    private Boolean taskDONE;
    private Context mContext;

    private DbManager dbManager;
    private HashMap<FIleInfo, PartInfo> mCache = new HashMap<>();

    public UploadTask(Context c, FIleInfo file, Part part, DbManager mDbManager, HashMap<FIleInfo, PartInfo> map) {
        this.mContext = c;
        this.mFile = file;
        this.mPart = part;
        this.dbManager = mDbManager;
        this.mCache.putAll(map);
        this.taskDONE = false;
    }

    @Override
    public void run() {

        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LESS_FAVORABLE);

        final MediaType MEDIA_TYPE_MARKDOWN
                = MediaType.parse("application/json; charset=utf-8");

        OkHttpClient client = new OkHttpClient();


        //JSON here
        String postBody = "{" +
                "\"chunkid\":" + String.valueOf(mPart.getID()) + "," +
                "\"filename\":" + "\"" + mFile.getFileName() + "\"," +
                "\"data\":" + "\"" + mPart.getData().toString() + "\"" +
                "}";

        System.out.println("Size:" + mPart.getData().length);
        System.out.println("Uploading:" + mPart.getID());
        System.out.println(postBody);


        RequestBody mMultiPart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("data", "data", RequestBody.create(MediaType.parse("image/png"), mPart.getData()))
                .build();


        Request mMultiPartRequest = new Request.Builder()
                .header("chunkid", String.valueOf(mPart.getID()))
                .addHeader("filename", mFile.getFileName())
                .url("http://solulabdev.com/itransfer/dev/api/v1/upload")
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
        try {
            response = client.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }


        if (response != null && response.isSuccessful()) {


            try {
                String jsonData = response.body().string();

                JSONObject Jobject = new JSONObject(jsonData);
                System.out.println(Jobject);

                String flag = (((JSONObject) Jobject.get("data")).get("flagComplete")).toString();

                //System.out.println((((JSONObject) Jobject.get("data")).get("flagComplete")) + " " + flag);

                if (flag.compareTo("Y") == 0) {
                    System.out.println("YES " + mPart.getID());

                    DbManager manager = dbManager.getInstance();
                    SQLiteDatabase db = manager.openDatabase();
                    int res = manager.updatePartInfo(mFile, Integer.valueOf(mPart.getID()), db);
                    DbManager.getInstance().closeDatabase();

                    //*************************** UPDATE CACHE
                    for (Map.Entry<FIleInfo, PartInfo> entry : mCache.entrySet()) {
                        if (entry.getKey().getFileName().compareTo(mFile.getFileName()) == 0) {
                            PartInfo mPartInfo = entry.getValue();
                            mPartInfo.setIndividualPart(mPart.getID(), true);
                            break;
                        }

                    }
                    //***************************
                }


            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }


        } else {
            //failed
            if (response != null) response.body().close();
        }


    }

    public String convertB64(byte[] buffer) {

        return Base64.encodeToString(buffer, Base64.DEFAULT);

    }
}
