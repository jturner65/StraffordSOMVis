package strafford_SOM_PKG.straff_RawDataHandling.raw_data;

import com.fasterxml.jackson.databind.ObjectMapper;

import base_Utils_Objects.io.MessageObject;

//class to hold a raw record of jp data
public class Straff_JpDescData extends Straff_JobPracticeData{
	 private Integer jpgrp = -1;
	 private String jpgrpName = "none";
	 //keys in json relevant for this data
	 private static final String[] relevantExactKeys = {"name","job_practice_group"};
	 
	 public Straff_JpDescData(MessageObject _msgObj,String _id, String _json, ObjectMapper _mapper, boolean hasJson) { 
	     super(_msgObj,_id, _json, _mapper,"jpDesc",hasJson);
	     String jpgrpString = "";
	     Object jpgrpJSon = dscrObject.mapOfRelevantJson.get("job_practice_group");        
	     if ((null != jpgrpJSon) && (!isBadRec)) {
	         jpgrpString = jpgrpJSon.toString().replace("{","").replace("}","").trim();
	         if (jpgrpString.length() > 0) {
	             String jpgIDStr = jpgrpString.split("id=")[1].trim().split(",")[0].trim();
	             jpgrp = Integer.parseInt(jpgIDStr);
	             jpgrpName = jpgrpString.split("name=")[1].trim().split(",")[0].trim();        
	         }
	     }        
	 }//ctor
	 
	 public Integer getParentJPGrp() {return jpgrp;}
	 public String getParentJPGrpName() {return jpgrpName;}    
	 @Override
	 public String[] getRelevantExactKeys() {        return relevantExactKeys;}
	 @Override
	 public String toString() {
	     String res = super.toString() ;
	     res +="JP : " + ID + " | Name : " + name + " | JpGrp : " + jpgrp + " | JpGrp Name : " + jpgrpName;
	     return res;
	 }

}//class jpDescData
