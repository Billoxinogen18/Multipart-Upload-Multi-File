package roadtodoird.com.uploader.Models;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by misterj on 19/1/16.
 */
public class PartInfo {

    private HashMap<Integer, Boolean> myMap;
    private int numParts;

    public PartInfo(int numParts) {
        this.numParts = numParts;
        myMap = new HashMap<>(numParts);
    }

    public PartInfo() {

    }

    @Override
    public String toString() {
        return super.toString();
    }

    public void addPartWithVal(Integer id, Boolean val) {
        synchronized (this.myMap) {
            myMap.put(id, val);
        }
    }

    public void addPart(Integer id) {
        synchronized (this.myMap) {
            myMap.put(id, false);
        }
    }

    public HashMap<Integer, Boolean> getPartInfo() {
        synchronized (this.myMap) {
            return myMap;
        }

    }


    public void setPartInfo(HashMap<Integer, Boolean> inputMap) {
        synchronized (this.myMap) {
            myMap = inputMap;
        }
    }

    public void setIndividualPart(int id, Boolean val) {
        synchronized (this.myMap) {
            myMap.put(id, val);
        }
    }
}
