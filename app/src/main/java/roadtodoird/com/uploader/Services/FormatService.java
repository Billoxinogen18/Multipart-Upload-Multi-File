package roadtodoird.com.uploader.Services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by misterj on 21/1/16.
 */
public class FormatService {
    private Map<String, List<String>> formatMap = new HashMap<String, List<String>>();

    public FormatService() {
        formatMap.put("image", Arrays.asList("jpg", "jpeg", "png"));
        formatMap.put("video", Arrays.asList("mp4", "avi", "3gp"));
        formatMap.put("audio", Arrays.asList("mp3", "flac", "wv", "alac"));
        formatMap.put("text", Arrays.asList("txt"));
    }

    public String getFormat(String mime) {
        boolean found =false;
        String format = "";

        for (Map.Entry<String, List<String>> entry : formatMap.entrySet()) {
            List<String> temp = new ArrayList<String>();
            temp.addAll(entry.getValue());
            for(int i=0;i<temp.size();i++){
                if (temp.get(i) == mime){
                    found = true;
                    format= entry.getKey();
                    break;
                }
            }

            if(found) break;
        }

        return format;
    }
}
