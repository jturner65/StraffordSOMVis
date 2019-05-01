package strafford_SOM_PKG.straff_RawDataHandling;

import com.fasterxml.jackson.databind.ObjectMapper;

import base_Utils_Objects.MessageObject;

public class LinkEvent extends EventRawData{
    //these should all be lowercase - these are exact key substrings we wish to match in json, to keep and use to build training data - all the rest of json data is being tossed
    private static final String[] relevantExactKeys = {"jps"};
    
    public LinkEvent(MessageObject _msgObj,String _id, String _json, ObjectMapper _mapper, boolean hasJson) {super(_msgObj,_id, _json, _mapper,"links",hasJson);this.isBadRec = rawJpMapOfArrays.size() == 0;}

    //return the order event's relevant query keys for json
    @Override
    public String[] getRelevantExactKeys() {        return relevantExactKeys;    }    
    //custom event-specific info to return to build CSV to use to build examples
    @Override
    public String getIndivTrainDataForCSV() {        return "";}    //must include trailing comma if ever returns data
    //custom descriptive debug info relevant to this class
    @Override
    protected String dbgGetCustInfoStr() {    return "";}
    @Override
    public String toString() {
        String res = super.toString();
        res +=getJpsToString();
        return res;
    }
    
}//OrderEvent
