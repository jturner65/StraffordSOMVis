package strafford_SOM_PKG.straff_RawDataHandling.raw_data;

import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import base_Utils_Objects.io.MessageObject;

// class to describe tc_taggings data - this just consists of two columns of data per record, an id and a jp list
//note this data is not stored as json in the db
public class Straff_TcTagData extends Straff_BaseRawData{
  //doesn't use json so no list of keys, just following format used for events since jp lists follow same format in db
  private static final String[] relevantExactKeys = {};
  public Straff_TcTagData(MessageObject _msgObj,String _id, String _json, ObjectMapper _mapper, boolean hasJson) {    
      //change to true if tc-taggings ever follows event format of having json to describe the jpg/jp lists
      super(_msgObj,_id, _json, _mapper, "TC_Tags", hasJson);        
      //descr object not made in super if doesn't use json
      if (!hasJson) {//if doesn't use, wont' have json map - use dscrObject to convert string from string array in procInitData
          Map<String, Object> jsonMap = null;
          dscrObject = buildDescrObjectFromJsonMap(jsonMap);
      }                
  }//ctor

  @Override
  public Date getDate() {    return null;}//currently no date in tc record
  //initialize actual list of jps here
  @Override
  public void procInitData(String[] _strAra) {//idx 1 is jpg/jp listing of same format as json
      if (_strAra.length != 2) {
          System.out.print("TcTagData::procInitData : len of Str ara in tc taggings class : " +_strAra.length + " Entries : [");
          for (int i=0;i<_strAra.length-1;++i) {System.out.print(""+i+": -"+_strAra[i]+"- , ");}
          System.out.println(""+(_strAra.length-1)+": -"+_strAra[_strAra.length-1]+"-]");
      }
      rawJpMapOfArrays = dscrObject.convertToJpgJps(_strAra[1]);        
      //if(rawJpMapOfArrays.size() > 1) {System.out.println("tcTaggings procInitData OID : " + OID + " Str1 : " + _strAra[1] + " Raw Map of arrays : " + getJpsToString());}
  }//procInitData
  @Override
  public String[] getRelevantExactKeys() {    return relevantExactKeys;    }
  @Override
  protected JSONDescr buildDescrObjectFromJsonMap(Map<String, Object> jsonMap) {return new TCTagDescr(msgObj, jsonMap,getRelevantExactKeys());}
  @Override
  protected String dbgGetCustInfoStr() {    return "";    }    
  @Override
  public String toString() {
      String res = super.toString() + " : TC Tag :";
      res +=getJpsToString();
      return res;
  }
}//class TcTagData