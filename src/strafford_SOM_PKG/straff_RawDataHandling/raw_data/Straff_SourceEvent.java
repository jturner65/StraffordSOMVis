package strafford_SOM_PKG.straff_RawDataHandling.raw_data;

import com.fasterxml.jackson.databind.ObjectMapper;

import base_Utils_Objects.io.MessageObject;

public class Straff_SourceEvent extends Straff_EventRawData{
    //these should all be lowercase - these are exact key substrings we wish to match, to keep and use to build training data - all the rest of json data is being tossed
    private static final String[] relevantExactKeys = {"jps", "type"};
    //type of source record in event
    private Integer sourceType;
    
    public Straff_SourceEvent(MessageObject _msgObj,String _id, String _json, ObjectMapper _mapper, boolean hasJson) {
        super(_msgObj,_id, _json, _mapper,"source",hasJson);
        sourceType = Integer.parseInt(dscrObject.mapOfRelevantJson.get("type").toString());    
    }
    
    public Integer getSourceType() {return sourceType;}
    
    //return the opt event's relevant query keys for json
    @Override
    public String[] getRelevantExactKeys() {        return relevantExactKeys;    }
    //custom event-specific info to return to build CSV to use to build examples
    @Override
    public String getIndivTrainDataForCSV() {        return "Source_Type,"+sourceType+",";}
    //custom descriptive debug info relevant to this class
    @Override
    protected String dbgGetCustInfoStr() {return "Source Type : "+ sourceType;}

    @Override
    public String toString() {
        String res = super.toString() + " Source Type : " +sourceType;
        res +=getJpsToString();
        return res;
    }
}//class SourceEvent

