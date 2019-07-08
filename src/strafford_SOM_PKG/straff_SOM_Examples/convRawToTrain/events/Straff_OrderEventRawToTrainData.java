package strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.events;

import strafford_SOM_PKG.straff_RawDataHandling.raw_data.Straff_OrderEvent;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.base.Straff_BaseRawData;
import strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.Straff_JpgJpDataRecord;

public class Straff_OrderEventRawToTrainData extends StraffEvntRawToTrainData{
	public Straff_OrderEventRawToTrainData(Straff_OrderEvent ev) {		super(ev, "Order Event->Prospect");		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training		
	public Straff_OrderEventRawToTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){super(_evIDStr, _evTypeStr, _evDateStr, "Order Event->Prospect");addJPG_JPDataFromCSVString(_evntStr);	}//put in child ctor in case child-event specific data needed for training	
	@Override
	public void addEventDataFromEventObj(Straff_BaseRawData ev) {	super.addEventDataRecsFromRawData(FauxOptVal,ev, false);}//addEventDataFromEventObj
	@Override
	public void addJPG_JPDataFromCSVString(String _csvDataStr) {super.addJPG_JPDataRecsFromCSVStr(FauxOptVal,_csvDataStr);	}//addJPG_JPDataFromCSVString
	//get the output string holding the relevant info for an individual event record of this kind of data
	@Override
	protected String getRecCSVString(Straff_JpgJpDataRecord rec) {		return jpgrpStTag+"optKey,"+rec.getOptVal()+","+rec.getCSVString()+jpgrpEndTag;};
}//class OrderEventTrainData
