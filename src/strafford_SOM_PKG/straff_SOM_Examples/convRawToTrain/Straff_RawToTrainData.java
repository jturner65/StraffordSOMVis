package strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain;

import java.util.*;

import base_Utils_Objects.*;
import base_Utils_Objects.io.MessageObject;
import base_Utils_Objects.io.MsgCodes;
import base_Utils_Objects.vectorObjs.Tuple;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.Straff_BaseRawData;

/**
 * this class holds information from a single record and performs conversion from 
 * the raw data to the format used to construct training examples (preproc examples)
 *  as well as to/from csv for preprocced examples of products (using this for propsects
 *  has beend deprecated in favor of using jpOccurrence structure)
 * @author john
 *
 */
public abstract class Straff_RawToTrainData{
	//object for managing logging and informational/error messages
	private static final MessageObject msgObj = MessageObject.buildMe();
	protected static final String jpgrpStTag = "JPG_St,", jpgrpEndTag = "JPG_End,";
	//magic value for opt key field in map, to use for non-opt records. 
	protected static final int FauxOptVal = 3;
	//array of jpg/jp records for this training data example
	protected ArrayList<Straff_JpgJpDataRecord> listOfJpgsJps;
	//type of instancing/owning example's source data (prospect, product, etc)
	public final String sourceTypeName;
	
	public Straff_RawToTrainData(String _sourceTypeName) {
		listOfJpgsJps = new ArrayList<Straff_JpgJpDataRecord>(); 
		sourceTypeName = _sourceTypeName;
	}
	
	public abstract void addJPG_JPDataFromCSVString(String _csvDataStr);
	protected void addJPG_JPDataRecsFromCSVStr(Integer optKey, String _csvDataStr) {
		//boolean isOptEvent = ((-2 <= optKey) && (optKey <= 2));
		listOfJpgsJps = new ArrayList<Straff_JpgJpDataRecord>(); 	//order of recs is priority of jpgs
		String[] strAra1 = _csvDataStr.split("numJPGs,");//use idx 1
		String[] strAraVals = strAra1[1].trim().split(","+jpgrpStTag);//1st element will be # of JPDataRecs, next elements will be Data rec vals
		Integer numDataRecs = Integer.parseInt(strAraVals[0].trim());
		for (int i=0;i<numDataRecs;++i) {
			String csvString = strAraVals[i+1];
			String[] csvVals = csvString.split(",");
			//typeVal is specific type of record - opt value for opt records, source kind for source event records
			Integer typeVal = Integer.parseInt(csvVals[1]), JPG = Integer.parseInt(csvVals[3]);
			Straff_JpgJpDataRecord rec = new Straff_JpgJpDataRecord(JPG,i, typeVal, csvString);
			listOfJpgsJps.add(rec);			
		}
	}//addEventDataFromCSVStrByKey	
	
	//TODO can the following 2 be aggregated/combined?  perhaps the set of int tuples is sufficient instead of set of ints
	//return a hash set of all the jps in this raw training data example
	public HashSet<Integer> getAllJpsInData(){
		HashSet<Integer> res = new HashSet<Integer>();
		for (Straff_JpgJpDataRecord jpgRec : listOfJpgsJps) {
			ArrayList<Integer> jps = jpgRec.getJPlist();
			for(Integer jp : jps) {res.add(jp);}
		}
		return res;		
	}//getAllJpsInData
	
	//return a hash set of all tuples of jpg,jp relations in data
	public HashSet<Tuple<Integer,Integer>> getAllJpgJpsInData(){
		HashSet<Tuple<Integer,Integer>> res = new HashSet<Tuple<Integer,Integer>>();
		for (Straff_JpgJpDataRecord jpgRec : listOfJpgsJps) {
			Integer jpg = jpgRec.getJPG();
			ArrayList<Integer> jps = jpgRec.getJPlist();
			for(Integer jp : jps) {res.add(new Tuple<Integer,Integer>(jpg, jp));}
		}
		return res;		
	}//getAllJpgJpsInData	
	
	public abstract void addEventDataFromEventObj(Straff_BaseRawData ev);	
	protected void addEventDataRecsFromRawData(Integer optVal, Straff_BaseRawData ev, boolean isOptEvent) {
		//boolean isOptEvent = ((-2 <= optVal) && (optVal <= 2));
		TreeMap<Integer, ArrayList<Integer>> newEvMapOfJPAras = ev.rawJpMapOfArrays;//keyed by jpg, or -1 for jpg order array, value is ordered list of jps/jpgs (again for -1 key)
		if (newEvMapOfJPAras.size() == 0) {					//if returns an empty list from event raw data then either unspecified, which is bad record, or infers entire list of jp data
			if (isOptEvent){							//for opt events, empty jpg-jp array means apply specified opt val to all jps						
				//Adding an opt event with an empty JPgroup-keyed JP list - means apply opt value to all jps;
				//use jpg -10, jp -9 as sentinel value to denote executing an opt on all jpgs/jps - usually means opt out on everything, but other opts possible
				ArrayList<Integer>tmp=new ArrayList<Integer>(Arrays.asList(-9));
				newEvMapOfJPAras.put(-10, tmp);				
			} else {										//should never happen, means empty jp array of non-opt events (like order events) - should always have an order jpg/jp data.
				msgObj.dispMessage("StraffRawToTrainData","addEventDataRecsFromRawData (" +sourceTypeName+")" , "Warning : Attempting to add a non-Opt event (" + ev.TypeOfData + "-type) with an empty JPgroup-keyed JP list - event had no corresponding usable data so being ignored.", MsgCodes.warning1);
				return;
			}
		}			
		//-1 means this array is the list, in order, of JPGs in this eventrawdata.  if there's no entry then that means there's only 1 jpg in this eventrawdata
		ArrayList<Integer> newJPGOrderArray = newEvMapOfJPAras.get(-1);
		if(newJPGOrderArray == null) {//adding only single jpg, without existing preference order
			newJPGOrderArray = new ArrayList<Integer> ();
			newJPGOrderArray.add(newEvMapOfJPAras.firstKey());//this adds only jpg to array	
		}
		Straff_JpgJpDataRecord rec;
		for (int i = 0; i < newJPGOrderArray.size(); ++i) {					//for every jpg key in ordered array
			int jpg = newJPGOrderArray.get(i);								//get list of jps for this jpg				
			rec = new Straff_JpgJpDataRecord(jpg,i,optVal);						//build a jpg->jp record for this jpg, passing order of jps under this jpg
			ArrayList<Integer> jpgs = newEvMapOfJPAras.get(jpg);			//get list of jps		
			for (int j=0;j<jpgs.size();++j) {rec.addToJPList(jpgs.get(j));}	//add each in order
			listOfJpgsJps.add(rec);				
		}			
	}//_buildListOfJpgJps
	
	//different event types will have different record formats to write/read
	protected abstract String getRecCSVString(Straff_JpgJpDataRecord rec);
	
	protected String buildJPGJP_CSVString() {
		String res = "";	
		for (int i=0;i<listOfJpgsJps.size();++i) {
//			JpgJpDataRecord rec = listOfJpgsJps.get(i);
//			res += "JPGJP_Start,optKey,"+rec.getOptVal()+","+rec.getCSVString()+"JPGJP_End,";			
			res += getRecCSVString(listOfJpgsJps.get(i));
		}		
		return res;		
	}//buildCSVString	
	
	public abstract String buildCSVString();
	
	@Override
	public String toString() {
		String res="";
		for (Straff_JpgJpDataRecord jpRec : listOfJpgsJps) { res += "\t" + jpRec.toString()+"\n";}
		return res;
 	}
}//class StraffTrainData

/////////////////////////////////////////////////////////////////////////////////
