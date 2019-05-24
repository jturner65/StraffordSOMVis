package strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain;

import java.util.ArrayList;
import java.util.TreeMap;

import strafford_SOM_PKG.straff_RawDataHandling.raw_data.BaseRawData;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.TcTagData;


/**
 * this class corresponds to the data for a training/testing data point for a product.  It is built from relevant data from TC_Taggings
 * we can treat it like an event-based data point, but doesn't have any date so wont be added to any kind of jpoccurence structure.
 * Also, this is designed expecting that TC data will ever only have 1 JPGroup, with 1 or more, priority-ordered jps from that group.
 * @author john
 *
 */
public class TcTagRawToTrainData extends StraffRawToTrainData{
	public TcTagRawToTrainData(TcTagData ev) {
		super();
		addEventDataFromEventObj(ev);
	}	//put in child ctor in case child-event specific data needed for training	
	
	public TcTagRawToTrainData(String _taggingCSVStr){
		super();
		addJPG_JPDataFromCSVString(_taggingCSVStr);	
	}//put in child ctor in case child-event specific data needed for training		}//ctor from rawDataObj
	
	//get map of jps and their order as specified in raw data.  NOTE : this is assuming there is only a single JPgroup represented by this TCTagData.  If there are >1 then this data will fail
	public TreeMap<Integer, Integer> getJPOrderMap(){
		int priority=0;
		TreeMap<Integer,Integer> orderMap = new TreeMap<Integer,Integer>();
		for(int i=0;i<listOfJpgsJps.size();++i) {
			JpgJpDataRecord jpRec = listOfJpgsJps.get(i);
			ArrayList<Integer> jpList = jpRec.getJPlist();
			for(int j=0;j<jpList.size(); ++j) {		orderMap.put(jpList.get(j), priority++);}
		}
		return orderMap;
	}//getJPOrderMap()	
	@Override
	protected String getRecCSVString(JpgJpDataRecord rec) {		return jpgrpStTag+"optKey,"+rec.getOptVal()+","+rec.getCSVString()+jpgrpEndTag;};	
	@Override
	public void addEventDataFromEventObj(BaseRawData ev) {	super.addEventDataRecsFromRawData(FauxOptVal,ev, false);}//addEventDataFromEventObj
	@Override
	public void addJPG_JPDataFromCSVString(String _csvDataStr) {super.addJPG_JPDataRecsFromCSVStr(FauxOptVal,_csvDataStr);	}//addJPG_JPDataFromCSVString

	@Override
	public String buildCSVString() {
		String res = "TCTagSt,numJPGs,1,";	//needs numJPGs tag for parsing - expected to always only have a single jp group
		res += buildJPGJP_CSVString();
		res += "TCTagEnd,";			
		return res;		
	}	
}//TcTagTrainData
