package strafford_SOM_PKG.straff_RawDataHandling.raw_data;

import com.fasterxml.jackson.databind.ObjectMapper;

import base_Utils_Objects.MessageObject;

public class OptEvent extends EventRawData{
    //these should all be lowercase - these are exact key substrings we wish to match, to keep and use to build training data - all the rest of json data is being tossed
    private static final String[] relevantExactKeys = {"jps", "type"};
    //type of opt choice in event
    private Integer optType;
    
    public OptEvent(MessageObject _msgObj,String _id, String _json, ObjectMapper _mapper, boolean hasJson) {
        super(_msgObj,_id, _json, _mapper,"opts",hasJson);
        optType = Integer.parseInt(dscrObject.mapOfRelevantJson.get("type").toString());    
    }
    
    public Integer getOptType() {return optType;}
    
    //return the opt event's relevant query keys for json
    @Override
    public String[] getRelevantExactKeys() {        return relevantExactKeys;    }
    //custom event-specific info to return to build CSV to use to build examples
    @Override
    public String getIndivTrainDataForCSV() {        return "Opt_Type,"+optType+",";}
    //custom descriptive debug info relevant to this class
    @Override
    protected String dbgGetCustInfoStr() {return "Opt Type : "+ optType;}

    @Override
    public String toString() {
        String res = super.toString() + " Opt Type : " +optType;
        res +=getJpsToString();
        return res;
    }
}//class OptEvent

