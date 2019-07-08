package strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.events;

import strafford_SOM_PKG.straff_RawDataHandling.raw_data.Straff_SourceEvent;
import strafford_SOM_PKG.straff_RawDataHandling.raw_data.base.Straff_BaseRawData;
import strafford_SOM_PKG.straff_SOM_Examples.convRawToTrain.Straff_JpgJpDataRecord;

public class Straff_SrcEventRawToTrainData extends StraffEvntRawToTrainData{
	public Straff_SrcEventRawToTrainData(Straff_SourceEvent ev) {	super(ev, "Source Event->Prospect");		addEventDataFromEventObj(ev);}	//put in child ctor in case child-event specific data needed for training
	
	public Straff_SrcEventRawToTrainData(Integer _evIDStr, String _evTypeStr, String _evDateStr, String _evntStr){	super(_evIDStr, _evTypeStr, _evDateStr, "Source Event->Prospect");addJPG_JPDataFromCSVString(_evntStr);}	//put in child ctor in case child-event specific data needed for training			
	@Override
	public void addEventDataFromEventObj(Straff_BaseRawData ev) {
		Integer srcValKey = ((Straff_SourceEvent)ev).getSourceType();
		super.addEventDataRecsFromRawData(srcValKey, ev, false);
	}
	//source type value in csv record between tags srcType and JPG tag
	private Integer getSrcValFromCSVStr(String _csvDataStr) {
		Integer res=  0;
		String[] resAra = _csvDataStr.trim().split("srcType,");
		String[] resAra2 = resAra[1].trim().split(",JPG");		
		res = Integer.parseInt(resAra2[0]);
		return res;
	}//getOptValFromCSVStr	
	@Override
	public void addJPG_JPDataFromCSVString(String _csvDataStr) {
		Integer srcValKey = getSrcValFromCSVStr(_csvDataStr);
		super.addJPG_JPDataRecsFromCSVStr(srcValKey, _csvDataStr);
	}
	//get the output string holding the relevant info for an individual event record of this kind of data
	@Override
	protected String getRecCSVString(Straff_JpgJpDataRecord rec) {		return jpgrpStTag+"srcType,"+rec.getOptVal()+","+rec.getCSVString()+jpgrpEndTag;};
}//class SrcEventTrainData
