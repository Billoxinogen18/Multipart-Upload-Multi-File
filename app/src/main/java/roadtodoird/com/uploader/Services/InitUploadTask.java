package roadtodoird.com.uploader.Services;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.util.ArrayMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import roadtodoird.com.uploader.MainActivity;
import roadtodoird.com.uploader.Models.FIleInfo;
import roadtodoird.com.uploader.Models.Part;


/**
 * Created by misterj on 20/1/16.
 */
public class InitUploadTask extends AsyncTask<String, Void, ArrayMap<FIleInfo, List<Part>>> {

    private Context mContext;
    private List<Uri> URIs = new ArrayList<Uri>();
    public ArrayMap<FIleInfo, List<Part>> mGlobalList = new ArrayMap<>();


    public InitUploadTask(Context context, List<Uri> fileURI) {
        super();
        this.mContext = context;
        this.URIs.addAll(fileURI);

    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected ArrayMap<FIleInfo, List<Part>> doInBackground(String... params) {

        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);

        String ROOT_URL = params[0];

        ArrayMap<FIleInfo, List<Part>> myMap = new ArrayMap<>();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.MINUTES)
                .writeTimeout(2, TimeUnit.MINUTES)
                .readTimeout(2, TimeUnit.MINUTES)
                .build();

        Request request = null;
        int index = 0;
        for (Uri uri : URIs) {

            FIleInfo mFileInfo = new FIleInfo(
                    getFileNameFromURI(uri.toString())
                    , getFileSize(uri)
                    , new FormatService().getFormat(getMimeType(uri)) + "/" + getMimeType(uri));
            //Very very important
            //**********************************
            mFileInfo.setFileLocalPath(uri);

            System.out.println(getFileNameFromURI(uri.toString()));
            System.out.println(getMimeType(uri));

            String requestURL = ROOT_URL +
                    "/" +
                    mFileInfo.getFileName() + "." + getMimeType(uri) +
                    "/" +
                    mFileInfo.getFileSize();

            System.out.println(requestURL);

            request = new Request.Builder()
                    .url(requestURL)
                    .build();

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

                    //Set server file id here.....................................

                    int serverFileId = (int) ((JSONObject) Jobject.get("data")).get("fileId");
                    mFileInfo.setServerFileID((long) serverFileId);

                    System.out.println("ServerFIleId:" + serverFileId);

                    JSONArray Jarray = ((JSONObject) Jobject.get("data")).getJSONArray("ranges");

                    //System.out.println(Jarray.length());
                    List<Part> mArray = new ArrayList<Part>(Jarray.length() + 1);

                    for (int i = 0; i < Jarray.length(); i++) {
                        JSONObject object = Jarray.getJSONObject(i);
                        //System.out.println(object.get("Chunk_Id"));
                        //System.out.println(object.get("Start_Range"));
                        //System.out.println(object.get("End_Range"));

                        int id = (int) object.get("chunk_id");
                        int start = (int) object.get("start_range");
                        int end = (int) object.get("end_range");

                        mArray.add(i, new Part(id, start, end));
                    }

                    myMap.put(mFileInfo, mArray);
                    //return myMap;

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                //return new HashMap<>();


            } else {

                return myMap;
                /*
                try {
                    response = client.newCall(request).execute();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                */
            }

        }

        return myMap;
    }

    @Override
    protected void onPostExecute(ArrayMap<FIleInfo, List<Part>> INFO) {
        super.onPostExecute(INFO);
        if (this.mGlobalList.size() > 0) {
            this.mGlobalList.clear();
        }

        this.mGlobalList = INFO;
    }

    private String getFileNameFromURI(String uri) {
        int index = uri.lastIndexOf('/');
        int end = uri.lastIndexOf('.');
        return uri.substring(index + 1, end);
    }

    private int getFileSize(Uri uri) {
        File file = new File(uri.getPath());
        return (int) file.length();
    }

    private String getMimeType(Uri u) {
        String uri = u.toString();
        int index = uri.lastIndexOf('/');
        uri = uri.substring(index + 1);
        index = uri.lastIndexOf('.');
        return uri.substring(index + 1);
    }
}
