package strafford_SOM_PKG.straff_RawDataHandling.raw_data;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.base.Straff_BaseRawData;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.json.Straff_ProspectDescr;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.json.base.Straff_JSONDescr;

public class Straff_ProspectData extends Straff_BaseRawData {
    //these should all be lowercase - these are exact key substrings we wish to match, to keep and use to build training data - all the rest of json data is being tossed
    //private static final String[] relevantExactKeys = {"jp","lu"};
    private static final String dateKeyInJSON = "udate",scndryDateKeyInJSON = "cdate";
    private static final String[] relevantExactKeys = {dateKeyInJSON,scndryDateKeyInJSON};
    //lu date from json
    private Date luDate ;
    //if this prospect record is empty/lacking all info.  might not be bad
    //public boolean isEmptyPrspctRec = false;
    
    public Straff_ProspectData(MessageObject _msgObj,String _id, String _json, ObjectMapper _mapper, boolean hasJson) {
        super(_msgObj,_id, _json, _mapper, "prospect", hasJson);
        //String jpsList = dscrObject.mapOfRelevantJson.get("jp").toString();
        String luDateStr = "";
        try {
            luDateStr = dscrObject.mapOfRelevantJson.get(dateKeyInJSON).toString();
            if(luDateStr.length() > 0) {luDate = Straff_BaseRawData.buildDateFromString(luDateStr);}
            else {//try cdate
                luDateStr = dscrObject.mapOfRelevantJson.get(scndryDateKeyInJSON).toString();
                if(luDateStr.length() > 0) {luDate = Straff_BaseRawData.buildDateFromString(luDateStr);}
                else {                
                    msgObj.dispMessage("prospectData","constructor"," No existing appropriate date for record id : " + _id + " json string " + _json, MsgCodes.error1);
                    //isEmptyPrspctRec = jpsList.length() - 2 == 0;//if jpsList is length 2, then no jps in this record, and no last update means this record has very little pertinent data-neither a date nor a jp associated with it
                    isBadRec = true;
                }
            }
            rawJpMapOfArrays = new TreeMap<Integer, ArrayList<Integer>>();
        } catch (Exception e) {
            isBadRec = true;
        }

//        if (null==jpsList) {
//            mapMgr.msgObj.dispMessage("prospectData","constructor"," Null jpsList for json string " + _json, MsgCodes.error1);
//        } else {
//            rawJpMapOfArrays = dscrObject.convertToJpgJps(jpsList);            
//        }
    }//ctor
    
    //build descriptor object from json map
    @Override
    protected Straff_JSONDescr buildDescrObjectFromJsonMap(Map<String, Object> jsonMap) {return new Straff_ProspectDescr(msgObj, jsonMap,getRelevantExactKeys());    }    
    //return prospect objects' relevant query keys for json
    @Override
    public String[] getRelevantExactKeys() {        return relevantExactKeys;    }    
    //most relevant "record date" for prospects
    @Override
    public Date getDate() {return luDate;}    
    //currently no init data for prospect records
    @Override
    public void procInitData(String[] _strAra) {}        
    //custom descriptive debug info relevant to this class
    @Override
    protected String dbgGetCustInfoStr() {        return "";    }    
    @Override
    public String toString() {
        String res = super.toString() ;
        res +=getJpsToString();
        return res;
    }
}//prospectData
