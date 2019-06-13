package strafford_SOM_PKG.straff_RawDataHandling.raw_data;

import com.fasterxml.jackson.databind.ObjectMapper;

import base_Utils_Objects.io.MessageObject;

//class to hold a raw record of jp group data
public class JpgrpDescData extends JobPracticeData{
	 //keys in json relevant for this data
	 private static final String[] relevantExactKeys = {"name"};
	 
	 public JpgrpDescData(MessageObject _msgObj,String _id, String _json, ObjectMapper _mapper, boolean hasJson) { super(_msgObj,_id, _json, _mapper,"jpgrpDesc",hasJson);}
	 @Override
	 public String[] getRelevantExactKeys() {        return relevantExactKeys;}
	 @Override
	 public String toString() {
	     String res = super.toString() ;
	     res +="JpGrp : " + ID + " | Name : " + name ;
	     return res;
	 }
	 
}//class jpgDescData
