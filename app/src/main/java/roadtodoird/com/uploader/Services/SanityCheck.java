package roadtodoird.com.uploader.Services;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.util.ArrayMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import roadtodoird.com.uploader.Models.FIleInfo;
import roadtodoird.com.uploader.Models.Part;

/**
 * Created by misterj on 25/1/16.
 */
public class SanityCheck extends AsyncTask<String, Void, ArrayList<Part>> {

    private Handler handler;

    public SanityCheck(Handler handler) {
        this.handler = handler;

    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected void onPostExecute(ArrayList<Part> parts) {
        super.onPostExecute(parts);
    }

    @Override
    protected ArrayList<Part> doInBackground(String... params) {
        String url = params[0];
        System.out.println(url);


        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.MINUTES)
                .writeTimeout(20, TimeUnit.MINUTES)
                .readTimeout(20, TimeUnit.MINUTES)
                .build();

        Request request = null;

        request = new Request.Builder()
                .url(url)
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

                String resp = Jobject.get("message").toString();
                if (resp.compareTo("File is successfully uploaded") == 0 ||
                        resp.compareTo("file is already uploaded") == 0) {

                    System.out.println("UPLOADED");

                    Message m = new Message();
                    Bundle mBundle = new Bundle();
                    mBundle.putLong("done", 100);

                    m.obj = mBundle;
                    handler.sendMessageDelayed(m, 800);

                    String downloadId = ((JSONObject) Jobject.get("data")).getString("download_url");
                    String s = "http://103.233.25.47:5252/download/" + downloadId;


                } else {

                    JSONArray Jarray = ((JSONObject) Jobject.get("data")).getJSONArray("ranges");
                    ArrayList<Part> mArray = new ArrayList<Part>(Jarray.length() + 1);
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
                    /*
                    for(int i=0;i<mArray.size();i++){
                        System.out.println("start->"+mArray.get(i).getStart());
                        System.out.println("end->"+mArray.get(i).getEnd());
                    }
                    */

                    return mArray;

                }

            } catch (JSONException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return new ArrayList<>();
    }
}
